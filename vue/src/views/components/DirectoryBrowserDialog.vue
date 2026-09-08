<template>
  <el-dialog
    :model-value="visible"
    :title="title"
    width="760px"
    destroy-on-close
    @close="emit('update:visible', false)"
  >
    <div v-if="favoritePaths.length > 0" class="favorites-panel">
      <span class="favorites-heading">从收藏路径开始</span>
      <div class="favorites-shortcuts">
        <el-tag
          v-for="item in favoritePaths"
          :key="item"
          class="favorite-shortcut"
          effect="plain"
          @click="openFavorite(item)"
        >
          {{ item }}
        </el-tag>
      </div>
    </div>

    <div class="browser-toolbar">
      <el-select
        v-model="selectedRootPath"
        class="root-select"
        placeholder="选择磁盘"
        @change="handleRootChange"
      >
        <el-option
          v-for="item in rootOptions"
          :key="item.path"
          :label="item.name"
          :value="item.path"
        />
      </el-select>
      <el-input
        v-model="pathDraft"
        class="path-input"
        clearable
        placeholder="可直接输入目录或文件路径"
        @keyup.enter="jumpToPath"
      />
      <el-button :loading="loading" @click="jumpToPath">跳转</el-button>
      <el-button :loading="loading" @click="loadCurrentPath">刷新</el-button>
    </div>

    <div class="browser-meta">
      <el-button link :disabled="!parentPath" @click="goParent">返回上级</el-button>
      <span>{{ currentPath || '-' }}</span>
    </div>

    <div v-loading="loading" class="browser-list">
      <div
        v-for="item in items"
        :key="item.path"
        class="browser-item"
        :class="{ 'is-active': item.path === selectedPath }"
        @click="selectItem(item)"
        @dblclick="openItem(item)"
      >
        <div class="browser-item-main">
          <el-icon>
            <Folder v-if="item.directory" />
            <Document v-else />
          </el-icon>
          <span>{{ item.name }}</span>
        </div>
        <span class="browser-item-type">{{ item.directory ? '文件夹' : '文件' }}</span>
      </div>

      <el-empty v-if="!loading && items.length === 0" description="当前目录没有可选项" />
    </div>

    <div class="browser-selection">
      <span>已选路径</span>
      <el-input v-model="selectedPath" readonly />
    </div>

    <template #footer>
      <div class="dialog-footer">
        <el-button @click="emit('update:visible', false)">取消</el-button>
        <el-button type="primary" :disabled="!canConfirm" @click="confirmSelection">确定</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Document, Folder } from '@element-plus/icons-vue'
import { browseFileSystem } from '@/utils/config'

const props = defineProps({
  visible: { type: Boolean, default: false },
  title: { type: String, default: '选择目录' },
  initialPath: { type: String, default: '' },
  favoritePaths: { type: Array, default: () => [] },
  directoriesOnly: { type: Boolean, default: true },
  imageFilesOnly: { type: Boolean, default: false },
  selectMode: {
    type: String,
    default: 'directory',
    validator: (value) => ['directory', 'file'].includes(value)
  }
})

const emit = defineEmits(['update:visible', 'confirm'])

const loading = ref(false)
const currentPath = ref('')
const parentPath = ref('')
const selectedPath = ref('')
const selectedType = ref('')
const selectedRootPath = ref('')
const rootOptions = ref([])
const items = ref([])
const pathDraft = ref('')

const canConfirm = computed(() => {
  if (!selectedPath.value) return false
  if (props.selectMode === 'file') {
    return selectedType.value === 'file'
  }
  return true
})

const syncDraft = (path) => {
  pathDraft.value = path || ''
}

const loadPath = async (path) => {
  loading.value = true
  try {
    const res = await browseFileSystem({
      path,
      directoriesOnly: props.directoriesOnly,
      imageFilesOnly: props.imageFilesOnly
    })
    if (res.code !== '200') {
      throw new Error(res.msg || '加载目录失败')
    }
    currentPath.value = res.data.path || ''
    parentPath.value = res.data.parentPath || ''
    rootOptions.value = Array.isArray(res.data.rootOptions) ? res.data.rootOptions : []
    items.value = Array.isArray(res.data.items) ? res.data.items : []
    syncDraft(currentPath.value)
    if (!selectedRootPath.value && rootOptions.value.length > 0) {
      selectedRootPath.value = rootOptions.value[0].path
    }
    if (!selectedPath.value && props.selectMode === 'directory') {
      selectedPath.value = currentPath.value
      selectedType.value = 'directory'
    }
  } catch (error) {
    ElMessage.error(error.message || '加载目录失败')
  } finally {
    loading.value = false
  }
}

const openFavorite = async (path) => {
  selectedPath.value = ''
  selectedType.value = ''
  syncDraft(path)
  await loadPath(path)
  if (props.selectMode === 'directory') {
    selectedPath.value = currentPath.value
    selectedType.value = 'directory'
  }
}

const jumpToPath = async () => {
  const targetPath = String(pathDraft.value || '').trim()
  await loadPath(targetPath)
  if (props.selectMode === 'directory') {
    selectedPath.value = currentPath.value
    selectedType.value = 'directory'
  }
}

const loadCurrentPath = () => loadPath(currentPath.value || props.initialPath || '')

const selectItem = (item) => {
  if (props.selectMode === 'file' && item.directory) return
  selectedPath.value = item.path
  selectedType.value = item.directory ? 'directory' : 'file'
}

const openItem = (item) => {
  if (!item.directory) {
    if (!props.directoriesOnly) {
      selectedPath.value = item.path
      selectedType.value = 'file'
    }
    return
  }
  if (props.selectMode === 'directory') {
    selectedPath.value = item.path
    selectedType.value = 'directory'
  }
  loadPath(item.path)
}

const goParent = () => {
  if (!parentPath.value) return
  selectedPath.value = props.selectMode === 'directory' ? parentPath.value : ''
  selectedType.value = props.selectMode === 'directory' ? 'directory' : ''
  loadPath(parentPath.value)
}

const handleRootChange = (value) => {
  if (!value) return
  selectedPath.value = props.selectMode === 'directory' ? value : ''
  selectedType.value = props.selectMode === 'directory' ? 'directory' : ''
  loadPath(value)
}

const confirmSelection = () => {
  emit('confirm', selectedPath.value)
  emit('update:visible', false)
}

watch(
  () => props.visible,
  (visible) => {
    if (!visible) return
    selectedPath.value = props.initialPath || ''
    selectedType.value = props.selectMode === 'file' && /\.[^\\/]+$/.test(props.initialPath || '')
      ? 'file'
      : props.selectMode === 'directory'
        ? 'directory'
        : ''
    syncDraft(props.initialPath || '')
    loadPath(props.initialPath || '')
  }
)
</script>

<style scoped>
.browser-toolbar,
.browser-meta,
.browser-item,
.browser-item-main,
.browser-selection,
.dialog-footer,
.favorites-panel,
.favorites-shortcuts {
  display: flex;
  align-items: center;
  gap: 10px;
}

.favorites-panel {
  flex-direction: column;
  align-items: flex-start;
  margin-bottom: 12px;
}

.favorites-heading {
  color: var(--app-text);
  font-size: 13px;
  font-weight: 600;
}

.favorites-shortcuts {
  flex-wrap: wrap;
}

.favorite-shortcut {
  cursor: pointer;
  max-width: 100%;
}

.browser-toolbar {
  margin-bottom: 12px;
}

.root-select {
  width: 120px;
}

.path-input {
  flex: 1 1 auto;
}

.browser-meta {
  justify-content: space-between;
  margin-bottom: 12px;
  color: var(--app-text-muted);
  font-size: 13px;
}

.browser-list {
  min-height: 320px;
  max-height: 420px;
  overflow: auto;
  padding: 8px;
  border: 1px solid var(--app-border);
  border-radius: 8px;
  background: var(--app-surface-soft);
}

.browser-item {
  justify-content: space-between;
  padding: 10px 12px;
  border-radius: 8px;
  cursor: pointer;
}

.browser-item:hover,
.browser-item.is-active {
  background: var(--app-primary-soft);
}

.browser-item-main {
  min-width: 0;
  color: var(--app-text);
}

.browser-item-main span {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.browser-item-type {
  color: var(--app-text-muted);
  font-size: 12px;
}

.browser-selection {
  margin-top: 12px;
}

.browser-selection span {
  width: 64px;
  color: var(--app-text-muted);
  flex: 0 0 auto;
}

.dialog-footer {
  justify-content: flex-end;
}
</style>
