<template>
  <div class="multi-container">
    <div class="page-header">
      <h2>批量图像分割</h2>
      <p class="subtitle">MRI 切片批量标准化处理与分割结果导出</p>
    </div>

    <section class="workspace-band">
      <el-form class="batch-form" label-width="92px">
        <div class="form-grid">
          <el-form-item label="输入文件夹">
            <div class="field-line">
              <el-input v-model="folderPath" clearable placeholder="可手动输入或选择输入目录" />
              <el-button @click="folderBrowserVisible = true">
                <el-icon><FolderOpened /></el-icon>
                选择
              </el-button>
              <el-button :loading="validating" @click="validatePath">
                <el-icon><FolderChecked /></el-icon>
                验证
              </el-button>
            </div>
          </el-form-item>

          <el-form-item label="输出文件夹">
            <div class="field-line">
              <el-input v-model="outputPath" clearable placeholder="可手动输入输出目录，留空则使用后端默认目录" />
              <el-button @click="outputBrowserVisible = true">
                <el-icon><FolderOpened /></el-icon>
                选择
              </el-button>
              <el-button text @click="outputPath = ''">清空</el-button>
            </div>
          </el-form-item>

          <el-form-item label="模型选择">
            <el-select
              v-model="modelPath"
              class="full-width"
              clearable
              :loading="modelLoading"
              placeholder="选择分割模型"
            >
              <el-option
                v-for="item in modelOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>

          <el-form-item label="输入尺寸">
            <el-select v-model="inputSize" class="full-width" placeholder="选择输入尺寸">
              <el-option
                v-for="item in inputSizeOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>

          <el-form-item label="输入通道">
            <el-select v-model="nChannels" class="full-width" placeholder="选择输入通道数">
              <el-option
                v-for="item in channelOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>

          <el-form-item label="置信度">
            <div class="confidence-row">
              <el-slider v-model="confidence" :min="0.05" :max="0.95" :step="0.1" />
              <el-input-number v-model="confidence" :min="0.05" :max="0.95" :step="0.1" :precision="2" />
            </div>
          </el-form-item>
        </div>

        <div class="action-row">
          <el-button
            type="primary"
            :loading="processing"
            :disabled="!folderPath || processing"
            @click="startBatchSegment"
          >
            <el-icon><VideoPlay /></el-icon>
            {{ processing ? '批量分割中' : '开始批量分割' }}
          </el-button>
          <el-button :disabled="!jobId || !processing" @click="stopBatch">
            <el-icon><VideoPause /></el-icon>
            停止任务
          </el-button>
          <el-button :disabled="!resultsReady" @click="showResults = !showResults">
            <el-icon><View /></el-icon>
            {{ showResults ? '隐藏结果' : '显示结果' }}
          </el-button>
          <el-button type="success" :disabled="!canGoThreedim" @click="goToThreedim">
            <el-icon><Box /></el-icon>
            打开 3D 可视化
          </el-button>
        </div>
      </el-form>
    </section>

    <section v-if="validationResult || showJobProgress" class="status-band">
      <div v-if="validationResult" class="validation-strip">
        <el-tag :type="validationResult.exists && validationResult.directory ? 'success' : 'danger'">
          {{ validationResult.exists && validationResult.directory ? '路径可用' : '路径不可用' }}
        </el-tag>
        <span>{{ validationResult.path }}</span>
        <strong>{{ validationResult.totalFiles || 0 }} 个切片</strong>
      </div>

      <div v-if="showJobProgress" class="progress-panel">
        <div class="progress-head">
          <div>
            <strong>{{ statusText }}</strong>
            <span>{{ jobId }}</span>
          </div>
          <span>{{ progress }}%</span>
        </div>
        <el-progress :percentage="progress" :stroke-width="14" :status="progressStatus" />
        <div class="progress-meta">
          <span>{{ processedFiles }}/{{ totalFiles }} 张</span>
          <span>当前处理：{{ currentFile || '-' }}</span>
          <span>耗时：{{ formatDuration(elapsedSeconds) }}</span>
          <span>输出：{{ effectiveOutputPath || '-' }}</span>
        </div>
        <el-alert
          v-if="errorMessage"
          class="status-alert"
          type="error"
          :title="errorMessage"
          :closable="false"
        />
      </div>
    </section>

    <section v-if="showResults && resultsReady" ref="resultsSectionRef" class="results-band">
      <div class="section-title">
        <span>分割结果</span>
        <el-tag type="info">{{ resultLimit }} 张</el-tag>
      </div>

      <div class="result-scrubber">
        <span class="scrubber-limit">1</span>
        <el-slider
          class="slice-slider"
          v-model="selectedSlice"
          :min="1"
          :max="resultLimit"
          :step="1"
          :show-tooltip="false"
          :disabled="resultLimit <= 1"
          @change="clampSelectedSlice"
        />
        <span class="scrubber-limit">{{ resultLimit }}</span>
      </div>

      <div class="result-viewer" tabindex="0" @keydown.left.prevent="goPrevResult" @keydown.right.prevent="goNextResult">
        <img
          v-if="currentPreviewSrc"
          class="result-image"
          :src="currentPreviewSrc"
          :alt="currentResult?.fileName || '分割结果预览'"
          loading="eager"
        >
        <div v-else class="result-empty">
          <el-icon><Picture /></el-icon>
          <span>{{ historyPreviewLoading ? '正在加载历史预览...' : '当前切片暂无预览' }}</span>
        </div>

        <el-button
          class="result-nav result-nav-left"
          circle
          :disabled="resultLimit <= 1"
          @click="goPrevResult"
        >
          <el-icon><ArrowLeft /></el-icon>
        </el-button>
        <el-button
          class="result-nav result-nav-right"
          circle
          :disabled="resultLimit <= 1"
          @click="goNextResult"
        >
          <el-icon><ArrowRight /></el-icon>
        </el-button>

        <div class="slice-jump">
          <el-input
            v-model="sliceInputValue"
            class="slice-input"
            inputmode="numeric"
            @input="handleSliceInput"
            @change="commitSliceInput"
            @blur="commitSliceInput"
          />
          <span>/ {{ resultLimit }}</span>
        </div>
      </div>

      <div class="result-meta">
        <span>{{ currentResult?.fileName || `第 ${selectedSlice} 张` }}</span>
        <el-tag :type="currentResult?.status === 'completed' ? 'success' : 'danger'" effect="plain">
          {{ currentResult?.status === 'completed' ? '完成' : '失败' }}
        </el-tag>
        <div class="result-labels">
          <span class="result-labels-title">标签：</span>
          <template v-if="currentLabelBadges.length > 0">
            <span
              v-for="item in currentLabelBadges"
              :key="item.id"
              class="label-badge"
            >
              <span
                class="label-badge-dot"
                :style="{
                  borderColor: item.borderColor,
                  backgroundColor: item.fillColor
                }"
              ></span>
              <span class="label-badge-text">{{ item.name }}</span>
            </span>
          </template>
          <span v-else>-</span>
        </div>
        <span>标签：{{ formatLabels(currentResult?.labels) }}</span>
      </div>
    </section>

    <DirectoryBrowserDialog
      v-model:visible="folderBrowserVisible"
      title="选择输入文件夹"
      :initial-path="folderPath"
      :favorite-paths="inputFavorites"
      @confirm="handleFolderSelected"
    />

    <DirectoryBrowserDialog
      v-model:visible="outputBrowserVisible"
      title="选择输出文件夹"
      :initial-path="outputPath"
      :favorite-paths="outputFavorites"
      @confirm="handleOutputSelected"
    />
  </div>
</template>

<script setup>
import { computed, nextTick, onActivated, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  ArrowLeft,
  ArrowRight,
  Box,
  FolderChecked,
  FolderOpened,
  Picture,
  VideoPause,
  VideoPlay,
  View
} from '@element-plus/icons-vue'
import request from '@/utils/request'
import { getCurrentUser } from '@/utils/auth'
import { DEFAULT_THREEDIM_LABEL_NAMES, fetchModels, formatModelOptions, getCachedGlobalConfig, getThreshold, isWorkPageAutoRefreshEnabled, onConfigUpdated } from '@/utils/config'
import DirectoryBrowserDialog from './components/DirectoryBrowserDialog.vue'

defineOptions({ name: 'Multi' })

const router = useRouter()
const route = useRoute()
const folderPath = ref('')
const outputPath = ref('')
const effectiveOutputPath = ref('')
const modelPath = ref('')
const inputSize = ref(240)
const nChannels = ref(4)
const confidence = ref(0.25)
const jobId = ref('')
const progress = ref(0)
const totalFiles = ref(0)
const processedFiles = ref(0)
const currentFile = ref('')
const elapsedSeconds = ref(0)
const status = ref('idle')
const errorMessage = ref('')
const results = ref([])
const selectedSlice = ref(1)
const sliceInputValue = ref('1')
const showResults = ref(true)
const processing = ref(false)
const validating = ref(false)
const validationResult = ref(null)
const historyPreviewLoading = ref(false)
const resultsSectionRef = ref(null)
const modelOptions = ref([])
const modelLoading = ref(false)
const folderBrowserVisible = ref(false)
const outputBrowserVisible = ref(false)
const inputFavorites = ref([])
const outputFavorites = ref([])
const tumorLabelNames = ref({ ...DEFAULT_THREEDIM_LABEL_NAMES })

const LABEL_COLOR_PRESETS = {
  yolo: {
    1: { border: 'rgb(226, 85, 85)', fill: 'rgba(226, 85, 85, 0.5)' },
    2: { border: 'rgb(70, 130, 230)', fill: 'rgba(70, 130, 230, 0.5)' },
    3: { border: 'rgb(235, 150, 45)', fill: 'rgba(235, 150, 45, 0.58)' }
  },
  multi: {
    1: { border: 'rgb(220, 60, 60)', fill: 'rgba(220, 60, 60, 0.38)' },
    2: { border: 'rgb(60, 180, 95)', fill: 'rgba(60, 180, 95, 0.38)' },
    3: { border: 'rgb(70, 105, 220)', fill: 'rgba(70, 105, 220, 0.38)' }
  },
  default: {
    1: { border: 'rgb(220, 60, 60)', fill: 'rgba(220, 60, 60, 0.38)' },
    2: { border: 'rgb(60, 180, 95)', fill: 'rgba(60, 180, 95, 0.38)' },
    3: { border: 'rgb(70, 105, 220)', fill: 'rgba(70, 105, 220, 0.38)' }
  }
}

const inputSizeOptions = [
  { label: '128x128', value: 128 },
  { label: '240x240 默认', value: 240 },
  { label: '256x256', value: 256 },
  { label: '512x512', value: 512 }
]

const channelOptions = [
  { label: '1 通道', value: 1 },
  { label: '3 通道', value: 3 },
  { label: '4 通道（BraTS）', value: 4 }
]

let pollTimer = null
let removeConfigListener = null
let validationHideTimer = null
let lastSliceWarning = ''
let lastRouteRecordId = undefined
let lastRouteOutputPath = ''

const statusText = computed(() => {
  const map = {
    idle: '等待任务',
    pending: '等待执行',
    processing: '正在处理',
    completed: '处理完成',
    failed: '处理失败',
    stopped: '已停止'
  }
  return map[status.value] || status.value
})

const progressStatus = computed(() => {
  if (status.value === 'failed') return 'exception'
  return undefined
})

const showJobProgress = computed(() => !!jobId.value && status.value !== 'completed')
const canGoThreedim = computed(() => status.value === 'completed' && !!effectiveOutputPath.value)
const resultsReady = computed(() => status.value === 'completed' && results.value.length > 0)
const resultLimit = computed(() => Math.max(1, Number(totalFiles.value) || results.value.length || 1))
const currentResult = computed(() => results.value[selectedSlice.value - 1] || null)
const currentPreviewSrc = computed(() => {
  const base64 = currentResult.value?.previewBase64
  if (!base64) return ''
  return String(base64).startsWith('data:') ? base64 : `data:image/png;base64,${base64}`
})
const currentLabelBadges = computed(() => buildLabelBadges(currentResult.value))

const applyFavoriteConfig = (globalConfig = getCachedGlobalConfig()) => {
  inputFavorites.value = Array.isArray(globalConfig.multiFavorites?.input) ? globalConfig.multiFavorites.input : []
  outputFavorites.value = Array.isArray(globalConfig.multiFavorites?.output) ? globalConfig.multiFavorites.output : []
}

const applyTumorLabelConfig = (globalConfig = getCachedGlobalConfig()) => {
  tumorLabelNames.value = {
    ...DEFAULT_THREEDIM_LABEL_NAMES,
    ...(globalConfig?.threedimLabelNames || {})
  }
}

const getQueryValue = (value) => {
  return Array.isArray(value) ? value[0] : value
}

const getRouteRecordId = () => getQueryValue(route.query.recordId)

const getRouteOutputPath = () => {
  return getQueryValue(route.query.outputPath) || getQueryValue(route.query.casePath) || ''
}

const toNumber = (value, fallback = 0) => {
  const next = Number(value)
  return Number.isFinite(next) ? next : fallback
}

const parseDetectionData = (record = {}) => {
  if (!record.detectionData) return {}
  try {
    return typeof record.detectionData === 'string'
      ? JSON.parse(record.detectionData)
      : record.detectionData
  } catch (error) {
    return {}
  }
}

const getBatchRecordStatus = (record = {}, data = {}) => {
  if (data.status) return String(data.status)
  const map = {
    0: 'pending',
    1: 'processing',
    2: 'completed',
    3: 'failed'
  }
  const statusFromRecord = map[Number(record.detectStatus)]
  if (statusFromRecord) return statusFromRecord
  return Array.isArray(data.results) && data.results.length > 0 ? 'completed' : 'idle'
}

const hasPreviewBase64 = (items = []) => {
  return Array.isArray(items) && items.some(item => !!item?.previewBase64)
}

const applyRouteOutputPath = (path) => {
  const nextPath = String(path || '').trim()
  lastRouteOutputPath = nextPath
  if (!nextPath) return
  outputPath.value = nextPath
  effectiveOutputPath.value = nextPath
}

const applyBatchRecord = (record = {}) => {
  const data = parseDetectionData(record)
  const nextOutputPath = data.casePath || data.outputPath || record.casePath || record.outputPath || getRouteOutputPath()
  const nextResults = Array.isArray(data.results) ? data.results : []
  const nextTotalFiles = toNumber(data.totalFiles, toNumber(record.totalSlices, nextResults.length))
  const nextStatus = getBatchRecordStatus(record, data)
  const nextProcessedFiles = toNumber(
    data.processedFiles,
    nextStatus === 'completed' ? nextTotalFiles : 0
  )

  clearPollTimer()
  clearValidationHideTimer()
  lastRouteOutputPath = String(nextOutputPath || '')
  folderPath.value = data.folderPath || record.originalImageUrl || folderPath.value
  outputPath.value = nextOutputPath || outputPath.value
  effectiveOutputPath.value = nextOutputPath || effectiveOutputPath.value
  jobId.value = data.jobId || ''
  modelPath.value = data.modelPath || modelPath.value
  inputSize.value = toNumber(data.inputSize, toNumber(record.inputSize, inputSize.value))
  confidence.value = data.confidence !== undefined ? toNumber(data.confidence, confidence.value) : confidence.value
  status.value = nextStatus
  totalFiles.value = nextTotalFiles
  processedFiles.value = nextProcessedFiles
  progress.value = data.progress !== undefined
    ? toNumber(data.progress)
    : (nextTotalFiles > 0 ? Math.round(nextProcessedFiles * 100 / nextTotalFiles) : (nextStatus === 'completed' ? 100 : 0))
  currentFile.value = data.currentFile || ''
  elapsedSeconds.value = toNumber(data.elapsedSeconds)
  errorMessage.value = data.errorMessage || ''
  results.value = nextResults
  selectedSlice.value = Math.min(75, Math.max(1, nextTotalFiles || nextResults.length || 1))
  sliceInputValue.value = String(selectedSlice.value)
  showResults.value = nextResults.length > 0
  processing.value = ['pending', 'processing'].includes(nextStatus)
  validationResult.value = null

  if (nextStatus === 'completed' && nextOutputPath && !hasPreviewBase64(nextResults)) {
    loadCaseResultsPreviews(record.id, nextOutputPath)
  }
}

const loadCaseResultsPreviews = async (recordId, outputPathValue) => {
  const nextOutputPath = String(outputPathValue || '').trim()
  if (!nextOutputPath || historyPreviewLoading.value) return

  historyPreviewLoading.value = true
  try {
    const res = await request.post('/multi/caseResults', {
      recordId,
      outputPath: nextOutputPath
    })
    if (res.code !== '200') {
      throw new Error(res.msg || '恢复历史预览失败')
    }

    const data = res.data || {}
    const restoredResults = Array.isArray(data.results) ? data.results : []
    if (restoredResults.length === 0) {
      throw new Error('未找到可恢复的分割结果')
    }

    results.value = restoredResults
    status.value = data.status || 'completed'
    totalFiles.value = toNumber(data.totalFiles, restoredResults.length)
    processedFiles.value = toNumber(data.processedFiles, totalFiles.value)
    progress.value = toNumber(data.progress, 100)
    effectiveOutputPath.value = data.outputPath || nextOutputPath
    outputPath.value = data.outputPath || nextOutputPath
    errorMessage.value = data.errorMessage || ''
    selectedSlice.value = Math.min(selectedSlice.value, Math.max(1, restoredResults.length))
    sliceInputValue.value = String(selectedSlice.value)
    showResults.value = true
  } catch (error) {
    console.error('恢复历史预览失败', error)
    errorMessage.value = error.message || '恢复历史预览失败'
    ElMessage.warning(errorMessage.value)
  } finally {
    historyPreviewLoading.value = false
  }
}

const loadRecordById = async (id) => {
  if (!id) return
  try {
    const res = await request.get(`/detect/selectById/${id}`)
    if (res.code === '200') {
      applyBatchRecord(res.data)
    } else {
      ElMessage.error(res.msg || '加载病例分割记录失败')
    }
  } catch (error) {
    console.error('加载病例分割记录失败', error)
    ElMessage.error('加载病例分割记录失败')
  }
}

const loadRecordFromRoute = (recordId) => {
  lastRouteRecordId = recordId
  if (recordId) {
    loadRecordById(recordId)
    return
  }
  applyRouteOutputPath(getRouteOutputPath())
}

watch(resultLimit, () => {
  clampSelectedSlice()
})

watch(selectedSlice, (value) => {
  sliceInputValue.value = String(value)
})

watch(
  () => route.query.recordId,
  () => loadRecordFromRoute(getRouteRecordId())
)

watch(
  () => [route.query.casePath, route.query.outputPath],
  () => {
    if (!getRouteRecordId()) {
      applyRouteOutputPath(getRouteOutputPath())
    }
  }
)

const handleFolderSelected = (path) => {
  folderPath.value = path
  validationResult.value = null
}

const handleOutputSelected = (path) => {
  outputPath.value = path
}

const validatePath = async () => {
  if (!folderPath.value) {
    ElMessage.warning('请选择或输入输入文件夹')
    return
  }
  validating.value = true
  try {
    const res = await request.post('/multi/validatePath', { path: folderPath.value })
    if (res.code === '200') {
      validationResult.value = res.data
      scheduleValidationHide()
      if (res.data.exists && res.data.directory) {
        ElMessage.success(`路径可用，找到 ${res.data.totalFiles || 0} 个切片`)
      } else {
        ElMessage.error('路径不可用')
      }
    } else {
      ElMessage.error(res.msg || '路径验证失败')
    }
  } finally {
    validating.value = false
  }
}

const startBatchSegment = async () => {
  folderPath.value = String(folderPath.value || '').trim()
  outputPath.value = String(outputPath.value || '').trim()

  if (!folderPath.value) {
    ElMessage.warning('请选择或输入输入文件夹')
    return
  }

  clearPollTimer()
  processing.value = true
  status.value = 'processing'
  progress.value = 0
  processedFiles.value = 0
  currentFile.value = ''
  errorMessage.value = ''
  results.value = []
  selectedSlice.value = 1

  try {
    const user = getCurrentUser()
    const res = await request.post('/multi/startBatch', {
      folderPath: folderPath.value,
      outputPath: outputPath.value,
      modelPath: modelPath.value,
      inputSize: inputSize.value,
      nChannels: nChannels.value,
      confidence: confidence.value,
      userId: user.id || 1,
      userName: user.name || user.username || ''
    })
    if (res.code !== '200') {
      throw new Error(res.msg || '启动批量分割失败')
    }
    jobId.value = res.data.jobId
    totalFiles.value = res.data.totalFiles || 0
    effectiveOutputPath.value = res.data.outputPath || outputPath.value
    ElMessage.success('批量分割任务已启动')
    pollProgress()
  } catch (error) {
    processing.value = false
    status.value = 'failed'
    errorMessage.value = error.message || '启动失败'
    ElMessage.error(error.message || '启动批量分割失败')
  }
}

const pollProgress = () => {
  if (!jobId.value) return
  clearPollTimer()
  fetchProgress()
  pollTimer = window.setInterval(fetchProgress, 2000)
}

const fetchProgress = async () => {
  if (!jobId.value) return
  try {
    const res = await request.get(`/multi/progress/${jobId.value}`)
    if (res.code !== '200') {
      throw new Error(res.msg || '查询进度失败')
    }
    applyJobState(res.data)
    if (['completed', 'failed', 'stopped'].includes(status.value)) {
      processing.value = false
      clearPollTimer()
    }
  } catch (error) {
    errorMessage.value = error.message || '查询进度失败'
  }
}

const applyJobState = (data = {}) => {
  const nextStatus = data.status || status.value
  const wasCompleted = status.value === 'completed'
  status.value = nextStatus
  progress.value = Number(data.progress || 0)
  totalFiles.value = Number(data.totalFiles || 0)
  processedFiles.value = Number(data.processedFiles || 0)
  currentFile.value = data.currentFile || ''
  elapsedSeconds.value = Number(data.elapsedSeconds || 0)
  effectiveOutputPath.value = data.outputPath || effectiveOutputPath.value || outputPath.value
  errorMessage.value = data.errorMessage || ''

  if (nextStatus === 'completed') {
    results.value = Array.isArray(data.results) ? data.results : []
    selectedSlice.value = Math.min(75, Math.max(1, Number(data.totalFiles) || results.value.length || 1))
    showResults.value = results.value.length > 0
    if (!wasCompleted && results.value.length > 0) {
      scrollToResults()
    }
    return
  }

  results.value = []
}

const stopBatch = async () => {
  if (!jobId.value) return
  const res = await request.post('/multi/stopBatch', { jobId: jobId.value })
  if (res.code === '200') {
    applyJobState(res.data)
    processing.value = false
    clearPollTimer()
    ElMessage.success('任务已停止')
  } else {
    ElMessage.error(res.msg || '停止任务失败')
  }
}

const goToThreedim = () => {
  router.push({
    path: '/manager/threedim',
    query: {
      casePath: effectiveOutputPath.value,
      autoBuild: '1',
      autoBuildKey: `${Date.now()}`
    }
  })
}

const loadModels = async () => {
  modelLoading.value = true
  try {
    const res = await fetchModels('multi')
    if (res.code === '200') {
      modelOptions.value = formatModelOptions(res.data)
      if (!modelPath.value && modelOptions.value.length > 0) {
        modelPath.value = modelOptions.value[0].value
      }
    }
  } finally {
    modelLoading.value = false
  }
}

const applyConfig = async () => {
  confidence.value = await getThreshold('multi', 0.25)
}

const clampSelectedSlice = () => {
  const next = Math.round(Number(selectedSlice.value) || 1)
  selectedSlice.value = Math.min(resultLimit.value, Math.max(1, next))
}

const warnSliceInput = (message) => {
  if (lastSliceWarning === message) return
  lastSliceWarning = message
  ElMessage.warning(message)
  window.setTimeout(() => {
    if (lastSliceWarning === message) {
      lastSliceWarning = ''
    }
  }, 1200)
}

const handleSliceInput = (value) => {
  const rawValue = String(value || '').trim()
  if (!rawValue) return

  const numericValue = Number(rawValue)
  if (!Number.isFinite(numericValue)) {
    warnSliceInput('请输入有效的切片张数')
    return
  }

  if (numericValue > resultLimit.value) {
    warnSliceInput(`输入张数不能超过总切片数量（${resultLimit.value} 张）`)
    selectedSlice.value = resultLimit.value
    sliceInputValue.value = String(resultLimit.value)
    return
  }

  if (numericValue < 1) {
    warnSliceInput('切片张数不能小于 1')
    selectedSlice.value = 1
    sliceInputValue.value = '1'
    return
  }

  selectedSlice.value = Math.round(numericValue)
}

const commitSliceInput = () => {
  if (!String(sliceInputValue.value || '').trim()) {
    sliceInputValue.value = String(selectedSlice.value)
    return
  }
  handleSliceInput(sliceInputValue.value)
  sliceInputValue.value = String(selectedSlice.value)
}

const goPrevResult = () => {
  selectedSlice.value = selectedSlice.value <= 1 ? resultLimit.value : selectedSlice.value - 1
}

const goNextResult = () => {
  selectedSlice.value = selectedSlice.value >= resultLimit.value ? 1 : selectedSlice.value + 1
}

const scheduleValidationHide = () => {
  clearValidationHideTimer()
  validationHideTimer = window.setTimeout(() => {
    validationResult.value = null
    validationHideTimer = null
  }, 3000)
}

const clearValidationHideTimer = () => {
  if (validationHideTimer) {
    window.clearTimeout(validationHideTimer)
    validationHideTimer = null
  }
}

const scrollToResults = () => {
  nextTick(() => {
    resultsSectionRef.value?.scrollIntoView({ behavior: 'smooth', block: 'center' })
  })
}

const getTumorLabelName = (label) => {
  return tumorLabelNames.value[Number(label)] || `鏍囩 ${label}`
}

const getLabelColorPreset = (result) => {
  const inferMode = String(result?.inferMode || '').toLowerCase()
  const mode = String(result?.mode || '').toLowerCase()
  if (inferMode && LABEL_COLOR_PRESETS[inferMode]) return LABEL_COLOR_PRESETS[inferMode]
  if (mode && LABEL_COLOR_PRESETS[mode]) return LABEL_COLOR_PRESETS[mode]
  return LABEL_COLOR_PRESETS.default
}

const buildLabelBadges = (result) => {
  const labels = Array.isArray(result?.labels) ? result.labels : []
  if (labels.length === 0) return []
  const colorPreset = getLabelColorPreset(result)
  return labels.map((label) => {
    const labelId = Number(label)
    const colors = colorPreset[labelId] || LABEL_COLOR_PRESETS.default[labelId] || {
      border: 'rgb(107, 114, 128)',
      fill: 'rgba(107, 114, 128, 0.3)'
    }
    return {
      id: labelId,
      name: getTumorLabelName(labelId),
      borderColor: colors.border,
      fillColor: colors.fill
    }
  })
}

const formatLabels = (labels) => {
  if (!Array.isArray(labels) || labels.length === 0) return '-'
  return labels.map(getTumorLabelName).join(', ')
}

const formatDuration = (seconds) => {
  const sec = Number(seconds || 0)
  const mins = Math.floor(sec / 60)
  const rest = sec % 60
  return mins > 0 ? `${mins}分 ${rest}秒` : `${rest}秒`
}

const clearPollTimer = () => {
  if (pollTimer) {
    window.clearInterval(pollTimer)
    pollTimer = null
  }
}

const resetBatchState = () => {
  clearPollTimer()
  clearValidationHideTimer()
  effectiveOutputPath.value = ''
  jobId.value = ''
  progress.value = 0
  totalFiles.value = 0
  processedFiles.value = 0
  currentFile.value = ''
  elapsedSeconds.value = 0
  status.value = 'idle'
  errorMessage.value = ''
  results.value = []
  selectedSlice.value = 1
  sliceInputValue.value = '1'
  processing.value = false
  validationResult.value = null
}

onMounted(() => {
  applyFavoriteConfig()
  applyTumorLabelConfig()
  applyConfig()
  loadModels()
  loadRecordFromRoute(getRouteRecordId())
  removeConfigListener = onConfigUpdated(({ thresholds, globalConfig }) => {
    if (thresholds?.multi !== undefined) {
      confidence.value = Number(thresholds.multi)
    }
    const nextGlobalConfig = globalConfig || getCachedGlobalConfig()
    applyFavoriteConfig(nextGlobalConfig)
    applyTumorLabelConfig(nextGlobalConfig)
  })
})

onActivated(() => {
  const recordId = getRouteRecordId()
  const routeOutputPath = getRouteOutputPath()
  if (recordId && (isWorkPageAutoRefreshEnabled() || recordId !== lastRouteRecordId)) {
    loadRecordFromRoute(recordId)
  } else if (routeOutputPath && routeOutputPath !== lastRouteOutputPath) {
    applyRouteOutputPath(routeOutputPath)
  } else if (isWorkPageAutoRefreshEnabled() && !recordId && !routeOutputPath) {
    resetBatchState()
    applyConfig()
    loadModels()
    return
  }
  if (jobId.value && processing.value) {
    pollProgress()
  }
})

onUnmounted(() => {
  clearPollTimer()
  clearValidationHideTimer()
  removeConfigListener?.()
})
</script>

<style scoped>
.multi-container {
  min-height: calc(100vh - 64px);
  padding: 20px;
  background: var(--app-bg);
}

.page-header,
.workspace-band,
.status-band,
.results-band {
  margin-bottom: 18px;
  background: var(--app-surface);
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius);
  box-shadow: var(--app-shadow);
}

.page-header {
  padding: 20px 24px;
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

.workspace-band,
.status-band,
.results-band {
  padding: 18px;
}

.form-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  column-gap: 20px;
}

.field-line,
.confidence-row,
.action-row,
.validation-strip,
.progress-meta,
.section-title,
.result-meta {
  display: flex;
  align-items: center;
  gap: 10px;
}

.field-line {
  width: 100%;
}

.field-line .el-input {
  flex: 1 1 auto;
}

.full-width {
  width: 100%;
}

.confidence-row {
  width: 100%;
}

.confidence-row .el-slider {
  flex: 1 1 auto;
}

.confidence-row .el-input-number {
  width: 132px;
}

.action-row {
  flex-wrap: wrap;
  padding-left: 92px;
}

.validation-strip {
  min-height: 36px;
  margin-bottom: 14px;
  color: var(--app-text-muted);
}

.validation-strip span {
  flex: 1 1 auto;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.progress-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
  color: var(--app-text);
}

.progress-head div {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.progress-head span {
  color: var(--app-text-muted);
}

.progress-meta {
  flex-wrap: wrap;
  margin-top: 10px;
  color: var(--app-text-muted);
  font-size: 13px;
}

.status-alert {
  margin-top: 12px;
}

.section-title {
  justify-content: space-between;
  margin-bottom: 10px;
  font-weight: 700;
  color: var(--app-text);
}

.result-scrubber {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  padding: 0 4px 8px;
}

.scrubber-limit {
  min-width: 24px;
  color: var(--app-text-muted);
  font-size: 13px;
  text-align: center;
}

:deep(.slice-slider .el-slider__button) {
  width: 8px;
  height: 26px;
  border-radius: 4px;
}

:deep(.slice-slider .el-slider__button-wrapper) {
  top: -17px;
}

.result-viewer {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  min-height: 420px;
  max-height: min(68vh, 720px);
  aspect-ratio: 16 / 9;
  overflow: hidden;
  border: 1px solid var(--app-border);
  border-radius: 8px;
  background: #0b1220;
  outline: none;
}

.result-image {
  width: 100%;
  height: 100%;
  object-fit: contain;
  background: #0b1220;
}

.result-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  color: rgba(255, 255, 255, 0.72);
}

.result-empty .el-icon {
  font-size: 40px;
}

.result-nav {
  position: absolute;
  bottom: 18px;
  width: 42px;
  height: 42px;
  border: 1px solid rgba(255, 255, 255, 0.36);
  background: rgba(15, 23, 42, 0.78);
  color: #fff;
  backdrop-filter: blur(6px);
}

.result-nav-left {
  left: 18px;
}

.result-nav-right {
  right: 18px;
}

.slice-jump {
  position: absolute;
  bottom: 16px;
  left: 50%;
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 166px;
  padding: 6px 10px;
  border: 1px solid rgba(255, 255, 255, 0.26);
  border-radius: 8px;
  background: rgba(15, 23, 42, 0.82);
  color: #fff;
  transform: translateX(-50%);
  backdrop-filter: blur(6px);
}

.slice-jump .slice-input {
  width: 92px;
}

:deep(.slice-jump .slice-input .el-input__wrapper) {
  background: rgba(255, 255, 255, 0.96);
}

:deep(.slice-jump .slice-input .el-input__inner) {
  text-align: center;
}

.result-meta {
  flex-wrap: wrap;
  min-height: 32px;
  margin-top: 12px;
  color: var(--app-text-muted);
  font-size: 13px;
}

:deep(.result-meta > span:last-child) {
  display: none;
}

.result-labels {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.result-labels-title {
  flex: 0 0 auto;
}

.label-badge {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.label-badge-dot {
  width: 12px;
  height: 12px;
  border: 2px solid transparent;
  border-radius: 50%;
  box-sizing: border-box;
  flex: 0 0 auto;
}

.label-badge-text {
  color: var(--app-text);
  white-space: nowrap;
}

@media (max-width: 920px) {
  .multi-container {
    padding: 14px;
  }

  .form-grid {
    grid-template-columns: 1fr;
  }

  .action-row {
    padding-left: 0;
  }

  .field-line,
  .confidence-row {
    flex-wrap: wrap;
  }

  .result-viewer {
    min-height: 280px;
    aspect-ratio: 4 / 3;
  }

  .slice-jump {
    min-width: 144px;
  }

  .slice-jump .slice-input {
    width: 84px;
  }
}
</style>
