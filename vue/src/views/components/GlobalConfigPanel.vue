<template>
  <div class="global-config-panel">
    <el-form label-width="150px" :model="props.modelValue" :rules="rules">
      <el-form-item label="后端 API 地址" prop="backendUrl">
        <el-input v-model="props.modelValue.backendUrl" clearable placeholder="http://localhost:9527" />
      </el-form-item>
      <el-form-item label="Python 检测服务" prop="pythonDetectUrl">
        <el-input v-model="props.modelValue.pythonDetectUrl" clearable placeholder="http://localhost:5001" />
      </el-form-item>
      <el-form-item label="Python 分割服务" prop="pythonSegmentUrl">
        <el-input v-model="props.modelValue.pythonSegmentUrl" clearable placeholder="http://localhost:3408" />
      </el-form-item>
      <el-form-item label="YOLO 分割服务" prop="pythonYoloSegmentUrl">
        <el-input v-model="props.modelValue.pythonYoloSegmentUrl" clearable placeholder="http://localhost:5001" />
      </el-form-item>
      <el-form-item label="UNet 分割服务" prop="pythonUnetSegmentUrl">
        <el-input v-model="props.modelValue.pythonUnetSegmentUrl" clearable placeholder="http://localhost:3408" />
      </el-form-item>
      <el-form-item label="Mask 默认引擎">
        <el-segmented
          v-model="props.modelValue.defaultMaskSegmentService"
          :options="segmentServiceOptions"
        />
      </el-form-item>
      <el-form-item label="登录后默认页面">
        <div class="default-route-field">
          <el-select
            v-model="props.modelValue.defaultLoginRouteKey"
            placeholder="请选择登录后的默认跳转页面"
            style="width: 100%"
          >
            <el-option
              v-for="item in loginRouteOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
          <p class="field-hint">仅在当前角色拥有访问权限且页面未被隐藏时生效，否则会自动回退到该角色可访问的第一个页面。</p>
        </div>
      </el-form-item>
      <el-form-item label="工作页返回策略">
        <el-switch
          v-model="props.modelValue.autoRefreshWorkPages"
          active-text="自动刷新"
          inactive-text="保留上次画面"
        />
      </el-form-item>
      <el-form-item label="登录页标题">
        <el-input
          v-model="props.modelValue.loginPageTitle"
          clearable
          maxlength="120"
          placeholder="脑诊智析"
        />
      </el-form-item>
      <el-form-item label="登录页黑色标题">
        <el-input
          v-model="props.modelValue.loginBrandTitle"
          clearable
          maxlength="200"
          placeholder="大模型驱动的脑肿瘤智能辅助诊断与分析系统"
        />
      </el-form-item>
      <el-form-item label="登录页彩色标题">
        <el-input
          v-model="props.modelValue.loginBrandSubtitle"
          clearable
          maxlength="120"
          placeholder="脑诊智析"
        />
      </el-form-item>
      <el-form-item label="左侧系统标题">
        <el-input
          v-model="props.modelValue.managerBrandTitle"
          clearable
          maxlength="200"
          placeholder="脑诊智析——大模型驱动的脑肿瘤智能辅助诊断与分析系统"
        />
      </el-form-item>
      <el-form-item label="左侧菜单 Logo">
        <el-switch
          v-model="props.modelValue.managerLogoVisible"
          active-text="显示"
          inactive-text="隐藏"
        />
      </el-form-item>
      <el-form-item label="标签页 Logo">
        <el-switch
          v-model="props.modelValue.tabLogoVisible"
          active-text="显示"
          inactive-text="隐藏"
        />
      </el-form-item>
      <el-form-item label="Logo 路径">
        <div class="logo-path-field">
          <div class="inline-path-editor">
            <el-input
              v-model="props.modelValue.managerLogoPath"
              clearable
              placeholder="留空则使用系统默认 Logo"
            />
            <el-button @click="logoBrowserVisible = true">浏览</el-button>
            <el-button @click="props.modelValue.managerLogoPath = ''">恢复默认</el-button>
          </div>
          <p class="field-hint">Logo 显示开关优先于图片路径；关闭后即使已选择图片也不会显示。</p>
        </div>
      </el-form-item>

      <div class="group-block">
        <h3>批量分割常用目录</h3>
        <PathListEditor
          title="输入目录"
          :items="props.modelValue.multiFavorites.input"
          @add="(value) => addPath('multiInput', value)"
          @browse="openPathBrowser('multiInput')"
          @remove="(value) => removePath('multiInput', value)"
        />
        <PathListEditor
          title="输出目录"
          :items="props.modelValue.multiFavorites.output"
          @add="(value) => addPath('multiOutput', value)"
          @browse="openPathBrowser('multiOutput')"
          @remove="(value) => removePath('multiOutput', value)"
        />
      </div>

      <div class="group-block">
        <h3>3D 可视化常用目录</h3>
        <PathListEditor
          title="Case 目录"
          :items="props.modelValue.threedimFavorites"
          @add="(value) => addPath('threedim', value)"
          @browse="openPathBrowser('threedim')"
          @remove="(value) => removePath('threedim', value)"
        />
      </div>

      <div class="group-block">
        <h3>左侧菜单标题</h3>
        <el-form-item label="用户管理分组">
          <el-input v-model="props.modelValue.managerMenuTitles.user" clearable maxlength="80" />
        </el-form-item>
        <el-form-item label="数据管理分组">
          <el-input v-model="props.modelValue.managerMenuTitles.data" clearable maxlength="80" />
        </el-form-item>
        <el-form-item label="诊断分析分组">
          <el-input v-model="props.modelValue.managerMenuTitles.diagnosis" clearable maxlength="80" />
        </el-form-item>
      </div>

      <div class="group-block">
        <h3>菜单页面配置</h3>
        <div
          v-for="item in managerRouteItems"
          :key="item.key"
          class="route-config-row"
        >
          <div class="route-config-label">{{ item.label }}</div>
          <el-input
            v-model="props.modelValue.managerRoutes[item.key].title"
            clearable
            maxlength="80"
            class="route-title-input"
          />
          <el-switch
            v-model="props.modelValue.managerRoutes[item.key].visible"
            :disabled="item.key === 'config'"
            active-text="显示"
            inactive-text="隐藏"
          />
        </div>
      </div>

      <div class="group-block">
        <h3>3D 肿瘤标签名称</h3>
        <el-form-item label="标签 1">
          <el-input
            v-model="props.modelValue.threedimLabelNames[1]"
            clearable
            maxlength="50"
            placeholder="坏死肿瘤核心"
          />
        </el-form-item>
        <el-form-item label="标签 2">
          <el-input
            v-model="props.modelValue.threedimLabelNames[2]"
            clearable
            maxlength="50"
            placeholder="瘤周水肿"
          />
        </el-form-item>
        <el-form-item label="标签 3">
          <el-input
            v-model="props.modelValue.threedimLabelNames[3]"
            clearable
            maxlength="50"
            placeholder="增强肿瘤"
          />
        </el-form-item>
      </div>
    </el-form>

    <DirectoryBrowserDialog
      v-model:visible="logoBrowserVisible"
      title="选择 Logo 图片"
      :initial-path="props.modelValue.managerLogoPath"
      :directories-only="false"
      :image-files-only="true"
      select-mode="file"
      @confirm="handleLogoSelected"
    />

    <DirectoryBrowserDialog
      v-model:visible="pathBrowserVisible"
      title="选择常用目录"
      :initial-path="pathBrowserInitialPath"
      @confirm="handlePathBrowserConfirm"
    />
  </div>
</template>

<script setup>
import { computed, defineComponent, h, ref } from 'vue'
import { ElButton, ElInput, ElTag } from 'element-plus'
import DirectoryBrowserDialog from './DirectoryBrowserDialog.vue'

const props = defineProps({
  modelValue: { type: Object, required: true }
})

const logoBrowserVisible = ref(false)
const pathBrowserVisible = ref(false)
const pathBrowserTarget = ref('')
const pathBrowserInitialPath = ref('')

const PathListEditor = defineComponent({
  name: 'PathListEditor',
  props: {
    title: { type: String, required: true },
    items: { type: Array, default: () => [] }
  },
  emits: ['add', 'browse', 'remove'],
  setup(componentProps, { emit }) {
    const draft = ref('')
    const addItem = () => {
      const value = String(draft.value || '').trim()
      if (!value) return
      emit('add', value)
      draft.value = ''
    }

    return () => h('div', { class: 'path-editor' }, [
      h('div', { class: 'path-editor-title' }, componentProps.title),
      h('div', { class: 'path-editor-inputs' }, [
        h(ElInput, {
          modelValue: draft.value,
          'onUpdate:modelValue': (value) => { draft.value = value },
          clearable: true,
          placeholder: '输入一个目录路径后点击添加',
          onKeyup: (event) => {
            if (event.key === 'Enter') addItem()
          }
        }),
        h(ElButton, { onClick: addItem }, () => '添加'),
        h(ElButton, { onClick: () => emit('browse') }, () => '浏览添加')
      ]),
      h('div', { class: 'path-editor-list' }, componentProps.items.length > 0
        ? componentProps.items.map(item => h(ElTag, {
          key: item,
          class: 'path-editor-tag',
          closable: true,
          effect: 'plain',
          onClose: () => emit('remove', item)
        }, () => item))
        : h('span', { class: 'path-editor-empty' }, '暂无预设目录'))
    ])
  }
})

const validateUrl = (rule, value, callback) => {
  if (!value) {
    callback(new Error('请输入服务地址'))
    return
  }
  if (!/^https?:\/\/.+/i.test(value)) {
    callback(new Error('地址必须以 http:// 或 https:// 开头'))
    return
  }
  callback()
}

const rules = {
  backendUrl: [{ validator: validateUrl, trigger: 'blur' }],
  pythonDetectUrl: [{ validator: validateUrl, trigger: 'blur' }],
  pythonSegmentUrl: [{ validator: validateUrl, trigger: 'blur' }],
  pythonYoloSegmentUrl: [{ validator: validateUrl, trigger: 'blur' }],
  pythonUnetSegmentUrl: [{ validator: validateUrl, trigger: 'blur' }]
}

const segmentServiceOptions = [
  { label: 'UNet', value: 'unet' },
  { label: 'YOLO', value: 'yolo' }
]

const managerRouteItems = [
  { key: 'admin', label: '管理员信息' },
  { key: 'dataview', label: '数据可视化' },
  { key: 'history', label: '检测历史' },
  { key: 'config', label: '系统配置' },
  { key: 'detect', label: '快速辅助诊断与分析' },
  { key: 'mask', label: '精准辅助分割与分析' },
  { key: 'multi', label: '批量图像分割' },
  { key: 'threedim', label: '3D脑肿瘤可视化' }
]

const loginRouteOptions = computed(() => managerRouteItems.map((item) => ({
  value: item.key,
  label: props.modelValue.managerRoutes?.[item.key]?.title || item.label
})))

const normalizePaths = (items) => {
  const next = []
  for (const item of items) {
    const value = String(item || '').trim()
    if (!value || next.includes(value)) continue
    next.push(value)
    if (next.length >= 8) break
  }
  return next
}

const addPath = (target, value) => {
  if (target === 'multiInput') {
    props.modelValue.multiFavorites.input = normalizePaths([value, ...props.modelValue.multiFavorites.input])
    return
  }
  if (target === 'multiOutput') {
    props.modelValue.multiFavorites.output = normalizePaths([value, ...props.modelValue.multiFavorites.output])
    return
  }
  props.modelValue.threedimFavorites = normalizePaths([value, ...props.modelValue.threedimFavorites])
}

const removePath = (target, value) => {
  if (target === 'multiInput') {
    props.modelValue.multiFavorites.input = props.modelValue.multiFavorites.input.filter(item => item !== value)
    return
  }
  if (target === 'multiOutput') {
    props.modelValue.multiFavorites.output = props.modelValue.multiFavorites.output.filter(item => item !== value)
    return
  }
  props.modelValue.threedimFavorites = props.modelValue.threedimFavorites.filter(item => item !== value)
}

const openPathBrowser = (target) => {
  pathBrowserTarget.value = target
  if (target === 'multiInput') {
    pathBrowserInitialPath.value = props.modelValue.multiFavorites.input[0] || ''
  } else if (target === 'multiOutput') {
    pathBrowserInitialPath.value = props.modelValue.multiFavorites.output[0] || ''
  } else {
    pathBrowserInitialPath.value = props.modelValue.threedimFavorites[0] || ''
  }
  pathBrowserVisible.value = true
}

const handlePathBrowserConfirm = (path) => {
  pathBrowserVisible.value = false
  if (path && pathBrowserTarget.value) {
    addPath(pathBrowserTarget.value, path)
  }
}

const handleLogoSelected = (path) => {
  logoBrowserVisible.value = false
  if (path) {
    props.modelValue.managerLogoPath = path
  }
}
</script>

<style scoped>
.global-config-panel {
  max-width: 920px;
}

.inline-path-editor,
.route-config-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.inline-path-editor {
  width: 100%;
}

.logo-path-field {
  width: 100%;
}

.default-route-field {
  width: 100%;
}

.field-hint {
  margin: 6px 0 0;
  color: var(--app-text-muted);
  font-size: 12px;
  line-height: 1.5;
}

.inline-path-editor .el-input {
  flex: 1 1 auto;
}

.group-block {
  padding-top: 10px;
  margin-top: 8px;
  border-top: 1px solid var(--app-border);
}

.group-block h3 {
  margin: 0 0 14px;
  color: var(--app-text);
  font-size: 15px;
  font-weight: 700;
}

.path-editor {
  margin-bottom: 14px;
}

.path-editor-title {
  margin-bottom: 8px;
  color: var(--app-text);
  font-size: 14px;
  font-weight: 600;
}

.path-editor-inputs,
.path-editor-list {
  display: flex;
  align-items: center;
  gap: 10px;
}

.path-editor-inputs {
  margin-bottom: 8px;
}

.path-editor-inputs :deep(.el-input) {
  flex: 1 1 auto;
}

.path-editor-list {
  flex-wrap: wrap;
}

.path-editor-tag {
  max-width: 100%;
}

.path-editor-empty {
  color: var(--app-text-muted);
  font-size: 13px;
}

.route-config-row {
  margin-bottom: 12px;
}

.route-config-label {
  width: 120px;
  color: var(--app-text);
  flex: 0 0 auto;
}

.route-title-input {
  flex: 1 1 auto;
}
</style>
