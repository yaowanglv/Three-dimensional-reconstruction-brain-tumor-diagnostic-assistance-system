<template>
  <div class="three-container">
    <div class="page-header">
      <h2>3D脑肿瘤可视化</h2>
      <p class="subtitle">基于批量分割结果的脑组织与肿瘤表面模型</p>
    </div>

    <section class="three-workspace">
      <aside class="control-panel">
        <el-form label-width="92px" class="case-form">
          <el-form-item label="Case路径">
            <div class="path-line">
              <el-input v-model="casePath" clearable placeholder="可手动输入或选择分割结果目录" />
              <el-button @click="caseBrowserVisible = true">
                <el-icon><FolderOpened /></el-icon>
                选择
              </el-button>
            </div>
          </el-form-item>
          <el-form-item label="模态">
            <el-input model-value="t1c" class="full-width" disabled />
          </el-form-item>
          <el-form-item label="归一化">
            <el-segmented v-model="normalizationMode" :options="normalizationOptions" />
          </el-form-item>
          <el-form-item label="最大面数">
            <el-input-number v-model="maxFacesWan" :min="2" :max="40" :step="1" />
            <span class="unit-text">万</span>
          </el-form-item>
          <el-form-item label="脑透明度">
            <el-slider v-model="brainOpacity" :min="0.05" :max="0.9" :step="0.05" />
          </el-form-item>
        </el-form>

        <div class="button-grid">
          <el-button type="primary" :loading="loading" :disabled="!casePath" @click="loadCase">
            <el-icon><Box /></el-icon>
            生成3D模型
          </el-button>
          <el-button @click="resetCamera()">
            <el-icon><Aim /></el-icon>
            重置视角
          </el-button>
          <el-button
            v-for="viewItem in medicalViews"
            :key="viewItem.key"
            :type="activeView === viewItem.key ? 'primary' : 'default'"
            @click="setView(viewItem.key)"
          >
            <el-icon><component :is="viewItem.icon" /></el-icon>
            {{ viewItem.label }}
          </el-button>
        </div>

        <div class="switch-list">
          <el-checkbox v-model="showBrain" @change="updateObjectVisibility">脑组织</el-checkbox>
          <el-checkbox v-model="autoRotate">自动旋转</el-checkbox>
          <el-checkbox
            v-for="label in tumorLabels"
            :key="label"
            v-model="visibleTumors[label]"
            @change="updateObjectVisibility"
          >
            {{ getTumorLabelName(label) }}
          </el-checkbox>
        </div>

        <div v-if="meshStats" class="stats-grid">
          <div>
            <span>切片</span>
            <strong>{{ meshStats.slices }}</strong>
          </div>
          <div>
            <span>标签</span>
            <strong>{{ formatTumorLabels(meshStats.labels) }}</strong>
          </div>
          <div>
            <span>脑面数</span>
            <strong>{{ meshStats.brainFaces }}</strong>
          </div>
          <div>
            <span>耗时</span>
            <strong>{{ meshStats.processingTimeMs }}ms</strong>
          </div>
        </div>
      </aside>

      <main class="viewer-shell">
        <div ref="canvasHost" class="canvas-host"></div>
        <div v-if="!meshLoaded && !loading" class="viewer-empty">
          <el-icon><Box /></el-icon>
          <span>等待3D模型</span>
        </div>
        <div v-if="loading" class="viewer-loading">
          <el-icon class="is-loading"><Loading /></el-icon>
          <span>正在生成模型</span>
        </div>
      </main>
    </section>

    <DirectoryBrowserDialog
      v-model:visible="caseBrowserVisible"
      title="选择分割结果文件夹"
      :initial-path="casePath"
      :favorite-paths="caseFavorites"
      @confirm="handleCaseSelected"
    />
  </div>
</template>

<script setup>
import { nextTick, onActivated, onDeactivated, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Aim, Box, FolderOpened, Loading, Top, View } from '@element-plus/icons-vue'
import * as THREE from 'three'
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js'
import request from '@/utils/request'
import DirectoryBrowserDialog from './components/DirectoryBrowserDialog.vue'
import { DEFAULT_THREEDIM_LABEL_NAMES, fetchGlobalConfig, getCachedGlobalConfig, isWorkPageAutoRefreshEnabled, onConfigUpdated } from '@/utils/config'
import { getCurrentUser } from '@/utils/auth'

defineOptions({ name: 'Threedim' })

const route = useRoute()
const canvasHost = ref(null)
const casePath = ref('')
const caseBrowserVisible = ref(false)
const caseFavorites = ref([])
const modality = ref('t1c')
const normalizationMode = ref('monai')
const maxFacesWan = ref(30)
const brainOpacity = ref(0.28)
const brainScale = ref(0.85)
const tumorScale = ref(1)
const showBrain = ref(true)
const autoRotate = ref(false)
const visibleTumors = ref({})
const loading = ref(false)
const meshLoaded = ref(false)
const meshStats = ref(null)
const tumorLabels = ref([])
const activeView = ref('oblique')
const tumorLabelNames = ref({ ...DEFAULT_THREEDIM_LABEL_NAMES })

const normalizationOptions = [
  { label: '轻量级', value: 'lightweight' },
  { label: 'MONAI', value: 'monai' }
]

const medicalViews = [
  { key: 'axial', label: '轴位', icon: Top },
  { key: 'coronal', label: '冠状位', icon: View },
  { key: 'sagittal', label: '矢状位', icon: View },
  { key: 'oblique', label: '三维斜位', icon: Aim }
]

const VIEW_TRANSITION_MS = 900
const AUTO_ROTATE_SPEED = 1.8

let renderer = null
let scene = null
let camera = null
let controls = null
let animationId = null
let resizeObserver = null
let meshRoot = null
let brainObject = null
let tumorObjects = new Map()
let viewTransition = null
let isScenePaused = false
let lastFrameTime = 0
let lastRouteCasePath = undefined
let removeConfigListener = null
let lastAutoBuildKey = ''
const meshCenter = new THREE.Vector3(0, 0, 0)
const meshSize = new THREE.Vector3(1, 1, 1)
let meshRadius = 2.5

const applyFavoriteConfig = (globalConfig = getCachedGlobalConfig()) => {
  caseFavorites.value = Array.isArray(globalConfig.threedimFavorites) ? globalConfig.threedimFavorites : []
}

const handleCaseSelected = (path) => {
  casePath.value = path
}

const maybeAutoBuildFromRoute = async () => {
  const autoBuild = String(route.query.autoBuild || '') === '1'
  const autoBuildKey = String(route.query.autoBuildKey || '')
  if (!autoBuild || !autoBuildKey || autoBuildKey === lastAutoBuildKey) {
    return
  }
  lastAutoBuildKey = autoBuildKey
  await loadCase()
}

const initScene = () => {
  if (!canvasHost.value || renderer) return

  scene = new THREE.Scene()
  scene.background = new THREE.Color(0x0f1720)
  meshRoot = new THREE.Group()
  scene.add(meshRoot)

  camera = new THREE.PerspectiveCamera(45, 1, 0.01, 2000)
  camera.position.set(2.6, 2.0, 2.4)

  renderer = new THREE.WebGLRenderer({ antialias: true, alpha: false })
  renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2))
  renderer.outputColorSpace = THREE.SRGBColorSpace
  canvasHost.value.appendChild(renderer.domElement)

  controls = new OrbitControls(camera, renderer.domElement)
  controls.enableDamping = true
  controls.dampingFactor = 0.08
  controls.target.set(0, 0, 0)
  controls.addEventListener('start', cancelViewTransition)

  const ambient = new THREE.AmbientLight(0xffffff, 0.72)
  scene.add(ambient)
  const key = new THREE.DirectionalLight(0xffffff, 1.2)
  key.position.set(2.5, 3.5, 2.0)
  scene.add(key)
  const fill = new THREE.DirectionalLight(0x9fd6ff, 0.45)
  fill.position.set(-2.0, -1.4, -2.0)
  scene.add(fill)

  const grid = new THREE.GridHelper(2.6, 8, 0x31515d, 0x22333c)
  grid.position.y = -1.12
  scene.add(grid)

  resizeObserver = new ResizeObserver(resizeRenderer)
  resizeObserver.observe(canvasHost.value)
  resizeRenderer()
  startAnimation()
}

const resizeRenderer = () => {
  if (!renderer || !canvasHost.value || !camera) return
  const width = canvasHost.value.clientWidth || 800
  const height = canvasHost.value.clientHeight || 560
  renderer.setSize(width, height, false)
  camera.aspect = width / height
  camera.updateProjectionMatrix()
}

const animate = () => {
  if (isScenePaused) {
    animationId = null
    return
  }
  animationId = window.requestAnimationFrame(animate)
  const now = performance.now()
  const deltaSeconds = lastFrameTime ? Math.min((now - lastFrameTime) / 1000, 0.08) : 0
  lastFrameTime = now
  updateViewTransition()
  updateMeshAutoRotation(deltaSeconds)
  if (controls) {
    controls.autoRotate = false
    controls.update()
  }
  renderer?.render(scene, camera)
}

const startAnimation = () => {
  if (animationId || !renderer) return
  isScenePaused = false
  lastFrameTime = performance.now()
  animate()
}

const pauseAnimation = () => {
  isScenePaused = true
  lastFrameTime = 0
  if (animationId) {
    window.cancelAnimationFrame(animationId)
    animationId = null
  }
}

const loadCase = async () => {
  casePath.value = String(casePath.value || '').trim()
  if (!casePath.value) {
    ElMessage.warning('请输入或选择 Case 路径')
    return
  }
  loading.value = true
  try {
    const res = await request.post('/mesh/build', {
      casePath: casePath.value,
      modality: modality.value,
      normalizationMode: normalizationMode.value,
      maxTotalFaces: maxFacesWan.value * 10000,
      brainScale: brainScale.value,
      tumorScale: tumorScale.value,
      userId: getCurrentUser().id || 1,
      userName: getCurrentUser().name || getCurrentUser().username || '',
      saveHistory: !route.query.recordId
    }, {
      timeout: 180000
    })
    if (res.code !== '200') {
      throw new Error(res.msg || '生成3D模型失败')
    }
    renderMesh(res.data)
    ElMessage.success('3D模型已生成')
  } catch (error) {
    ElMessage.error(error.message || '生成3D模型失败')
  } finally {
    loading.value = false
  }
}

const getTumorLabelName = (label) => {
  return tumorLabelNames.value[Number(label)] || `标签 ${label}`
}

const formatTumorLabels = (labels) => {
  if (!Array.isArray(labels) || labels.length === 0) return '-'
  return labels.map(getTumorLabelName).join(', ')
}

const applyTumorLabelNames = (config = {}) => {
  tumorLabelNames.value = {
    ...DEFAULT_THREEDIM_LABEL_NAMES,
    ...(config.threedimLabelNames || {})
  }
}

const loadTumorLabelNames = async () => {
  applyTumorLabelNames(getCachedGlobalConfig())
  try {
    const res = await fetchGlobalConfig()
    if (res.code === '200' && res.data) {
      applyTumorLabelNames(res.data)
    }
  } catch (error) {
    console.warn('加载3D标签配置失败，使用本地缓存', error)
  }
}

const renderMesh = (data) => {
  initScene()
  clearMeshes()
  meshRoot = meshRoot || new THREE.Group()
  if (!meshRoot.parent) {
    scene.add(meshRoot)
  }
  meshStats.value = data?.stats || null

  if (data?.brain?.vertices?.length) {
    brainObject = createMeshObject(data.brain, {
      color: '#86d5e8',
      opacity: brainOpacity.value,
      transparent: true,
      metalness: 0.05,
      roughness: 0.72
    })
    brainObject.name = 'brain'
    meshRoot.add(brainObject)
  }

  const tumors = data?.tumors || {}
  tumorLabels.value = Object.keys(tumors)
  visibleTumors.value = tumorLabels.value.reduce((acc, label) => {
    acc[label] = true
    return acc
  }, {})
  tumorLabels.value.forEach((label) => {
    const tumor = tumors[label]
    if (!tumor?.vertices?.length) return
    const obj = createMeshObject(tumor, {
      color: tumor.color || '#e25555',
      opacity: 0.95,
      transparent: false,
      metalness: 0.12,
      roughness: 0.46
    })
    obj.name = `tumor_${label}`
    meshRoot.add(obj)
    tumorObjects.set(label, obj)
  })

  meshLoaded.value = true
  updateObjectVisibility()
  updateMeshBounds()
  resetCamera({ immediate: true })
}

const createMeshObject = (mesh, options) => {
  const geometry = new THREE.BufferGeometry()
  const vertices = new Float32Array(mesh.vertices.flat())
  const indices = new Uint32Array(mesh.faces.flat())
  geometry.setAttribute('position', new THREE.BufferAttribute(vertices, 3))
  geometry.setIndex(new THREE.BufferAttribute(indices, 1))
  geometry.computeVertexNormals()
  geometry.computeBoundingSphere()

  const material = new THREE.MeshStandardMaterial({
    color: new THREE.Color(options.color),
    opacity: options.opacity,
    transparent: options.transparent,
    metalness: options.metalness,
    roughness: options.roughness,
    side: THREE.DoubleSide,
    depthWrite: !options.transparent
  })
  return new THREE.Mesh(geometry, material)
}

const clearMeshes = () => {
  if (!scene || !meshRoot) return
  if (brainObject) {
    disposeObject(brainObject)
    meshRoot.remove(brainObject)
    brainObject = null
  }
  tumorObjects.forEach((obj) => {
    disposeObject(obj)
    meshRoot.remove(obj)
  })
  tumorObjects.clear()
  meshRoot.position.set(0, 0, 0)
  meshRoot.rotation.set(0, 0, 0)
  meshCenter.set(0, 0, 0)
  meshSize.set(1, 1, 1)
  meshRadius = 2.5
}

const disposeObject = (obj) => {
  obj.geometry?.dispose()
  if (Array.isArray(obj.material)) {
    obj.material.forEach((material) => material.dispose())
  } else {
    obj.material?.dispose()
  }
}

const updateObjectVisibility = () => {
  if (brainObject) {
    brainObject.visible = showBrain.value
  }
  tumorObjects.forEach((obj, label) => {
    obj.visible = visibleTumors.value[label] !== false
  })
}

const updateBrainOpacity = () => {
  if (brainObject?.material) {
    brainObject.material.opacity = brainOpacity.value
    brainObject.material.needsUpdate = true
  }
}

const updateMeshBounds = () => {
  const box = new THREE.Box3()
  let hasObject = false

  if (brainObject) {
    box.expandByObject(brainObject)
    hasObject = true
  }
  tumorObjects.forEach((obj) => {
    box.expandByObject(obj)
    hasObject = true
  })

  if (!hasObject || box.isEmpty()) {
    meshCenter.set(0, 0, 0)
    meshSize.set(1, 1, 1)
    meshRadius = 2.5
    return
  }

  box.getCenter(meshCenter)
  box.getSize(meshSize)
  meshRadius = Math.max(meshSize.x, meshSize.y, meshSize.z, 1) * 0.5
  if (meshRoot) {
    const centerOffset = meshCenter.clone()
    meshRoot.children.forEach((child) => {
      child.position.sub(centerOffset)
    })
    meshRoot.position.set(0, 0, 0)
    meshCenter.set(0, 0, 0)
  }
}

const getAutoRotateAxis = () => {
  if (!camera || !controls) return new THREE.Vector3(0, 0, -1)
  return camera.position.clone().sub(controls.target).normalize().negate()
}

const updateMeshAutoRotation = (deltaSeconds) => {
  if (!meshRoot || !autoRotate.value || viewTransition || !deltaSeconds) return
  const radiansPerSecond = (Math.PI * 2 / 60) * AUTO_ROTATE_SPEED
  meshRoot.rotateOnWorldAxis(getAutoRotateAxis(), radiansPerSecond * deltaSeconds)
}

const getCameraDistance = () => {
  const halfFov = THREE.MathUtils.degToRad(camera.fov * 0.5)
  return (meshRadius / Math.sin(halfFov)) * 1.35
}

const getCurrentCameraDistance = () => {
  if (!camera || !controls) return getCameraDistance()
  return Math.max(camera.position.distanceTo(controls.target), meshRadius * 0.2, 0.1)
}

const getViewCameraState = (view = 'oblique', options = {}) => {
  const distance = options.fitToMesh ? getCameraDistance() : getCurrentCameraDistance()
  const target = meshCenter.clone()
  const offset = new THREE.Vector3(distance * 0.72, distance * 0.52, distance * 0.82)
  const up = new THREE.Vector3(0, 1, 0)

  if (view === 'axial') {
    offset.set(0, 0, distance)
    up.set(0, 1, 0)
  } else if (view === 'coronal') {
    offset.set(0, -distance, 0)
    up.set(0, 0, 1)
  } else if (view === 'sagittal') {
    offset.set(distance, 0, 0)
    up.set(0, 0, 1)
  }

  return {
    position: target.clone().add(offset),
    target,
    up,
    distance
  }
}

const applyCameraLimits = (distance) => {
  camera.near = Math.max(distance / 1000, 0.01)
  camera.far = Math.max(distance * 5, meshRadius * 8, 100)
  camera.updateProjectionMatrix()
  controls.minDistance = Math.max(distance * 0.06, 0.05)
  controls.maxDistance = Math.max(distance * 4, 10)
}

const applyCameraState = (state) => {
  camera.position.copy(state.position)
  camera.up.copy(state.up).normalize()
  controls.target.copy(state.target)
  camera.lookAt(controls.target)
  controls.update()
}

const easeInOutCubic = (value) => {
  return value < 0.5
    ? 4 * value * value * value
    : 1 - Math.pow(-2 * value + 2, 3) / 2
}

const fitCamera = (view = 'oblique', options = {}) => {
  if (!camera || !controls) return

  const nextState = getViewCameraState(view, options)
  applyCameraLimits(nextState.distance)
  const resetMeshRotation = options.resetMeshRotation !== false
  const nextRootQuaternion = new THREE.Quaternion()

  if (options.immediate || !meshLoaded.value) {
    viewTransition = null
    if (resetMeshRotation) {
      meshRoot?.quaternion.copy(nextRootQuaternion)
    }
    applyCameraState(nextState)
    return
  }

  viewTransition = {
    startedAt: performance.now(),
    duration: VIEW_TRANSITION_MS,
    fromPosition: camera.position.clone(),
    toPosition: nextState.position,
    fromTarget: controls.target.clone(),
    toTarget: nextState.target,
    fromUp: camera.up.clone(),
    toUp: nextState.up,
    fromRootQuaternion: resetMeshRotation && meshRoot ? meshRoot.quaternion.clone() : null,
    toRootQuaternion: resetMeshRotation && meshRoot ? nextRootQuaternion : null
  }
}

const updateViewTransition = () => {
  if (!viewTransition || !camera || !controls) return

  const elapsed = performance.now() - viewTransition.startedAt
  const progress = Math.min(elapsed / viewTransition.duration, 1)
  const eased = easeInOutCubic(progress)

  camera.position.lerpVectors(viewTransition.fromPosition, viewTransition.toPosition, eased)
  controls.target.lerpVectors(viewTransition.fromTarget, viewTransition.toTarget, eased)
  camera.up.lerpVectors(viewTransition.fromUp, viewTransition.toUp, eased).normalize()
  camera.lookAt(controls.target)
  if (meshRoot && viewTransition.fromRootQuaternion && viewTransition.toRootQuaternion) {
    meshRoot.quaternion.slerpQuaternions(
      viewTransition.fromRootQuaternion,
      viewTransition.toRootQuaternion,
      eased
    )
  }

  if (progress >= 1) {
    camera.position.copy(viewTransition.toPosition)
    controls.target.copy(viewTransition.toTarget)
    camera.up.copy(viewTransition.toUp).normalize()
    camera.lookAt(controls.target)
    if (meshRoot && viewTransition.toRootQuaternion) {
      meshRoot.quaternion.copy(viewTransition.toRootQuaternion)
    }
    viewTransition = null
  }
}

const cancelViewTransition = () => {
  viewTransition = null
}

const resetCamera = (options = {}) => {
  activeView.value = 'oblique'
  fitCamera('oblique', { ...options, fitToMesh: true })
}

const setView = (view) => {
  activeView.value = view
  fitCamera(view)
}

const destroyScene = () => {
  pauseAnimation()
  resizeObserver?.disconnect()
  clearMeshes()
  controls?.dispose()
  renderer?.dispose()
  renderer?.domElement?.remove()
  renderer = null
  scene = null
  meshRoot = null
  camera = null
  controls = null
  viewTransition = null
  isScenePaused = false
}

const resetViewerState = () => {
  clearMeshes()
  casePath.value = route.query.casePath ? String(route.query.casePath) : ''
  lastRouteCasePath = casePath.value
  meshLoaded.value = false
  meshStats.value = null
  tumorLabels.value = []
  visibleTumors.value = {}
  activeView.value = 'oblique'
}

watch(brainOpacity, updateBrainOpacity)

onMounted(async () => {
  applyFavoriteConfig()
  casePath.value = route.query.casePath ? String(route.query.casePath) : ''
  lastRouteCasePath = casePath.value
  await loadTumorLabelNames()
  await nextTick()
  initScene()
  await maybeAutoBuildFromRoute()
  removeConfigListener = onConfigUpdated(({ globalConfig }) => {
    applyFavoriteConfig(globalConfig || getCachedGlobalConfig())
  })
})

onActivated(async () => {
  await loadTumorLabelNames()
  await nextTick()
  initScene()
  const nextCasePath = route.query.casePath ? String(route.query.casePath) : ''
  if (isWorkPageAutoRefreshEnabled()) {
    resetViewerState()
  } else if (nextCasePath && nextCasePath !== lastRouteCasePath) {
    casePath.value = nextCasePath
    lastRouteCasePath = nextCasePath
    clearMeshes()
    meshLoaded.value = false
    meshStats.value = null
    tumorLabels.value = []
    visibleTumors.value = {}
  }
  await maybeAutoBuildFromRoute()
  resizeRenderer()
  startAnimation()
})

onDeactivated(() => {
  pauseAnimation()
})

onUnmounted(() => {
  destroyScene()
  removeConfigListener?.()
})
</script>

<style scoped>
.three-container {
  min-height: calc(100vh - 64px);
  padding: 20px;
  background: var(--app-bg);
}

.page-header {
  margin-bottom: 18px;
  padding: 20px 24px;
  background: var(--app-surface);
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius);
  box-shadow: var(--app-shadow);
}

.page-header h2 {
  margin: 0 0 8px;
  font-size: 22px;
  color: var(--app-text);
}

.subtitle {
  margin: 0;
  color: var(--app-text-muted);
}

.three-workspace {
  display: grid;
  grid-template-columns: 340px minmax(0, 1fr);
  gap: 18px;
  min-height: calc(100vh - 168px);
}

.control-panel,
.viewer-shell {
  background: var(--app-surface);
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius);
  box-shadow: var(--app-shadow);
}

.control-panel {
  padding: 18px;
  overflow: auto;
}

.case-form :deep(.el-form-item) {
  margin-bottom: 14px;
}

.path-line {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
}

.path-line .el-input {
  flex: 1 1 auto;
}

.full-width {
  width: 100%;
}

.unit-text {
  margin-left: 8px;
  color: var(--app-text-muted);
}

.button-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  margin: 4px 0 16px;
}

.button-grid .el-button {
  width: 100%;
  margin-left: 0;
}

.switch-list {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 10px;
  padding: 12px;
  background: var(--app-surface-soft);
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius);
}

.switch-list :deep(.el-checkbox) {
  width: 100%;
  min-width: 0;
  height: auto;
  margin-right: 0;
  margin-left: 0;
  white-space: normal;
}

.switch-list :deep(.el-checkbox__label) {
  min-width: 0;
  line-height: 1.35;
  overflow-wrap: anywhere;
}

.stats-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  margin-top: 16px;
}

.stats-grid div {
  padding: 10px;
  background: var(--app-surface-soft);
  border: 1px solid var(--app-border);
  border-radius: 6px;
}

.stats-grid span {
  display: block;
  color: var(--app-text-muted);
  font-size: 12px;
}

.stats-grid strong {
  display: block;
  margin-top: 4px;
  color: var(--app-text);
  font-size: 16px;
  overflow-wrap: anywhere;
}

.viewer-shell {
  position: relative;
  min-height: 560px;
  overflow: hidden;
  background: #0f1720;
}

.canvas-host {
  width: 100%;
  height: 100%;
  min-height: 560px;
}

.canvas-host :deep(canvas) {
  display: block;
  width: 100%;
  height: 100%;
}

.viewer-empty,
.viewer-loading {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  color: rgba(255, 255, 255, 0.82);
  font-size: 16px;
  pointer-events: none;
}

.viewer-empty .el-icon,
.viewer-loading .el-icon {
  font-size: 24px;
}

@media (max-width: 1040px) {
  .three-container {
    padding: 14px;
  }

  .three-workspace {
    grid-template-columns: 1fr;
  }

  .control-panel {
    overflow: visible;
  }

  .path-line {
    flex-wrap: wrap;
  }

}
</style>
