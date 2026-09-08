<template>
  <div class="model-config-panel">
    <div class="panel-head">
      <div>
        <h3 class="panel-title">{{ title }}</h3>
        <p class="panel-subtitle">扫描 .pt、.pth、.onnx、.engine、.trt 模型文件，可为前端下拉框设置自定义名称。</p>
      </div>
    </div>

    <div class="folder-input-row">
      <el-input
        v-model="newFolderPath"
        clearable
        placeholder="输入模型文件夹路径，如 D:/models 或 /home/jetson/models"
        @keyup.enter="handleAddFolder"
      >
        <template #prefix>
          <el-icon><Folder /></el-icon>
        </template>
      </el-input>
      <el-button type="primary" :loading="scanning" @click="handleAddFolder">
        <el-icon><Plus /></el-icon>
        添加并扫描
      </el-button>
      <el-switch
        v-model="includeLastPt"
        class="last-pt-switch"
        active-text="是否导入last.pt"
      />
    </div>

    <el-table :data="models" class="models-table" height="340">
      <el-table-column label="前端显示名称" min-width="180">
        <template #default="{ row }">
          <div class="display-name-cell">
            <el-input
              v-if="editingId === row.id"
              v-model="editingName"
              size="small"
              maxlength="200"
              show-word-limit
              @keyup.enter="saveDisplayName(row)"
              @keyup.esc="cancelEdit"
            />
            <strong v-else>{{ row.displayName || row.modelName }}</strong>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="modelName" label="原文件名" min-width="180" />
      <el-table-column prop="folderPath" label="所属文件夹" min-width="220" show-overflow-tooltip />
      <el-table-column prop="modelType" label="类型" width="90">
        <template #default="{ row }">
          <el-tag size="small">{{ row.modelType }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="modelPath" label="完整路径" min-width="260" show-overflow-tooltip />
      <el-table-column label="操作" width="190" fixed="right">
        <template #default="{ row }">
          <template v-if="editingId === row.id">
            <el-button link type="primary" size="small" :loading="savingId === row.id" @click="saveDisplayName(row)">
              保存
            </el-button>
            <el-button link size="small" @click="cancelEdit">取消</el-button>
          </template>
          <template v-else>
            <el-button link type="primary" size="small" @click="startEdit(row)">
              改名
            </el-button>
            <el-button link type="primary" size="small" :loading="rescanningId === row.id" @click="handleRescan(row)">
              扫描
            </el-button>
            <el-button link type="danger" size="small" @click="handleDelete(row)">
              删除
            </el-button>
          </template>
        </template>
      </el-table-column>
    </el-table>

    <el-empty v-if="!models.length" description="暂无模型配置，请添加模型文件夹" />
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Folder, Plus } from '@element-plus/icons-vue'
import { addModelFolder, removeModel, rescanModels, updateModelDisplayName } from '@/utils/config'

const props = defineProps({
  pageType: { type: String, required: true },
  title: { type: String, default: '模型文件夹配置' },
  models: { type: Array, default: () => [] }
})

const emit = defineEmits(['refresh'])

const newFolderPath = ref('')
const includeLastPt = ref(false)
const scanning = ref(false)
const rescanningId = ref(null)
const editingId = ref(null)
const editingName = ref('')
const savingId = ref(null)

const handleAddFolder = async () => {
  const folderPath = newFolderPath.value.trim()
  if (!folderPath) {
    ElMessage.warning('请输入模型文件夹路径')
    return
  }

  scanning.value = true
  try {
    const res = await addModelFolder(props.pageType, folderPath, includeLastPt.value)
    if (res.code !== '200') {
      throw new Error(res.msg || '扫描失败')
    }
    newFolderPath.value = ''
    ElMessage.success('扫描完成，模型已添加到列表')
    emit('refresh', props.pageType)
  } catch (error) {
    ElMessage.error(error.message || '扫描失败')
  } finally {
    scanning.value = false
  }
}

const handleRescan = async (row) => {
  rescanningId.value = row.id
  try {
    const res = await rescanModels(props.pageType, row.folderPath, includeLastPt.value)
    if (res.code !== '200') {
      throw new Error(res.msg || '重新扫描失败')
    }
    ElMessage.success('重新扫描完成')
    emit('refresh', props.pageType)
  } catch (error) {
    ElMessage.error(error.message || '重新扫描失败')
  } finally {
    rescanningId.value = null
  }
}

const startEdit = (row) => {
  editingId.value = row.id
  editingName.value = row.displayName || row.modelName
}

const cancelEdit = () => {
  editingId.value = null
  editingName.value = ''
}

const saveDisplayName = async (row) => {
  const displayName = editingName.value.trim()
  if (!displayName) {
    ElMessage.warning('请输入前端显示名称')
    return
  }

  savingId.value = row.id
  try {
    const res = await updateModelDisplayName(row.id, displayName)
    if (res.code !== '200') {
      throw new Error(res.msg || '保存名称失败')
    }
    ElMessage.success('前端显示名称已保存')
    cancelEdit()
    emit('refresh', props.pageType)
  } catch (error) {
    ElMessage.error(error.message || '保存名称失败')
  } finally {
    savingId.value = null
  }
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(
      `确定删除模型 "${row.modelName}" 的配置记录吗？不会删除物理文件。`,
      '确认删除',
      { type: 'warning' }
    )
    const res = await removeModel(row.id)
    if (res.code !== '200') {
      throw new Error(res.msg || '删除失败')
    }
    ElMessage.success('已删除')
    emit('refresh', props.pageType)
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error.message || '删除失败')
    }
  }
}
</script>

<style scoped>
.model-config-panel {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
}

.panel-title {
  margin: 0;
  color: var(--app-text);
  font-size: 16px;
}

.panel-subtitle {
  margin: 6px 0 0;
  color: var(--app-text-muted);
  font-size: 13px;
}

.folder-input-row {
  display: flex;
  gap: 10px;
}

.folder-input-row .el-input {
  flex: 1 1 auto;
}

.last-pt-switch {
  flex: 0 0 auto;
  white-space: nowrap;
}

.models-table {
  width: 100%;
}

.display-name-cell {
  min-height: 28px;
  display: flex;
  align-items: center;
}

@media (max-width: 780px) {
  .folder-input-row {
    flex-wrap: wrap;
  }

  .folder-input-row .el-button {
    width: 100%;
  }

  .last-pt-switch {
    width: 100%;
    justify-content: flex-start;
  }
}
</style>
