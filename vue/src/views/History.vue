<template>
  <div class="history-page page-shell">
    <section class="page-header">
      <div>
        <h2>检测历史</h2>
        <p class="subtitle">查看、复查和删除历史影像检测记录</p>
      </div>
      <div class="header-actions">
        <el-button type="primary" :icon="Refresh" :loading="loading" @click="loadHistory">
          刷新
        </el-button>
      </div>
    </section>

    <section class="filter-card">
      <div class="filter-grid">
        <el-select
          v-model="selectedUserId"
          clearable
          filterable
          placeholder="检测用户"
          :loading="userLoading"
        >
          <el-option
            v-for="item in userOptions"
            :key="item.id"
            :label="getUserOptionLabel(item)"
            :value="item.id"
          />
        </el-select>

        <el-select v-model="selectedAiModel" clearable placeholder="大模型">
          <el-option
            v-for="item in aiModelOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>

        <el-date-picker
          v-model="selectedCreateDate"
          type="date"
          value-format="YYYY-MM-DD"
          placeholder="构建日期"
        />

        <el-select v-model="selectedDetectStatus" clearable placeholder="检测状态">
          <el-option
            v-for="item in detectStatusOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>

        <el-select
          v-model="selectedAiUsed"
          clearable
          placeholder="是否使用AI分析"
          @change="handleFilterChange"
          @clear="handleFilterChange"
        >
          <el-option label="已使用AI分析" :value="true" />
          <el-option label="未使用AI分析" :value="false" />
        </el-select>

        <el-select v-model="selectedDetectMode" clearable placeholder="病灶分析类别">
          <el-option label="检测" value="detect" />
          <el-option label="单图分割" value="segment" />
          <el-option label="病例分割" value="batch_segment" />
          <el-option label="三维重建" value="threedim" />
        </el-select>
      </div>

      <div class="filter-actions">
        <el-button type="primary" @click="handleSearch">
          查询
        </el-button>
        <el-button @click="resetFilters">重置</el-button>
      </div>
    </section>

    <section class="history-card">
      <el-table :data="historyList" class="history-table" v-loading="loading">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column label="检测用户" width="150" show-overflow-tooltip>
          <template #default="{ row }">
            {{ getDetectorName(row) }}
          </template>
        </el-table-column>
        <el-table-column label="文件/病例" show-overflow-tooltip min-width="180">
          <template #default="{ row }">
            {{ getRecordName(row) }}
          </template>
        </el-table-column>
        <el-table-column prop="detectStatus" label="检测状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.detectStatus)" size="small">
              {{ getStatusText(row.detectStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="病灶分析类别" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="getDetectModeType(row.detectMode)" size="small">
              {{ getDetectModeText(row.detectMode) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="aiStatus" label="AI分析" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="getAiStatusType(row.aiStatus)" size="small">
              {{ getAiStatusText(row.aiStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="大模型" width="130" align="center">
          <template #default="{ row }">
            <el-tag v-if="getAiModelName(row)" size="small" type="warning">
              {{ getAiModelName(row) }}
            </el-tag>
            <span v-else class="empty-text">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="构建时间" width="180" align="center">
          <template #default="{ row }">
            {{ formatDateTime(row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" size="small" @click="viewRecord(row)">查看</el-button>
            <el-button type="danger" size="small" @click="deleteRecord(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :page-sizes="[5, 10, 20]"
          layout="total, sizes, prev, pager, next, jumper"
          :total="total"
          @size-change="loadHistory"
          @current-change="loadHistory"
        />
      </div>
    </section>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import request from '@/utils/request.js'
import { getCurrentUser, hasRole } from '@/utils/auth'

const router = useRouter()
const historyList = ref([])
const loading = ref(false)
const userLoading = ref(false)
const userOptions = ref([])
const selectedUserId = ref(null)
const selectedAiModel = ref('')
const selectedCreateDate = ref('')
const selectedDetectStatus = ref(null)
const selectedAiUsed = ref(null)
const selectedDetectMode = ref('')
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const aiModelOptions = [
  { label: 'Deepseek-V4', value: 'deepseek' },
  { label: 'Kimi-k2.6', value: 'kimi' }
]
const detectStatusOptions = [
  { label: '待检测', value: 0 },
  { label: '检测中', value: 1 },
  { label: '完成', value: 2 },
  { label: '失败', value: 3 }
]

const loadHistory = async () => {
  loading.value = true
  try {
    const params = {
      pageNum: pageNum.value,
      pageSize: pageSize.value
    }
    appendParam(params, 'userId', selectedUserId.value)
    appendParam(params, 'aiModel', selectedAiModel.value)
    appendParam(params, 'createDate', selectedCreateDate.value)
    appendParam(params, 'detectStatus', selectedDetectStatus.value)
    appendParam(params, 'detectMode', selectedDetectMode.value)
    if (selectedAiUsed.value !== null && selectedAiUsed.value !== undefined) {
      params.aiUsed = selectedAiUsed.value ? '1' : '0'
    }

    const res = await request.get('/detect/selectPage', {
      params
    })
    if (res.code === '200') {
      historyList.value = res.data.list
      total.value = res.data.total
    } else {
      ElMessage.error(res.msg || '加载历史记录失败')
    }
  } catch (error) {
    ElMessage.error('加载历史记录失败')
  } finally {
    loading.value = false
  }
}

const appendParam = (params, key, value) => {
  if (value !== null && value !== undefined && value !== '') {
    params[key] = value
  }
}

const loadUsers = async () => {
  userLoading.value = true
  try {
    if (!hasRole('admin')) {
      const currentUser = getCurrentUser()
      userOptions.value = currentUser.id ? [currentUser] : []
      selectedUserId.value = currentUser.id || null
      return
    }

    const res = await request.get('/admin/selectAll')
    if (res.code === '200') {
      userOptions.value = res.data || []
    } else {
      ElMessage.error(res.msg || '加载检测用户失败')
    }
  } catch (error) {
    ElMessage.error('加载检测用户失败')
  } finally {
    userLoading.value = false
  }
}

const handleFilterChange = () => {
  pageNum.value = 1
  loadHistory()
}

const handleSearch = () => {
  handleFilterChange()
}

const resetFilters = () => {
  selectedUserId.value = null
  selectedAiModel.value = ''
  selectedCreateDate.value = ''
  selectedDetectStatus.value = null
  selectedAiUsed.value = null
  selectedDetectMode.value = ''
  handleFilterChange()
}

const viewRecord = (row) => {
  if (row.detectMode === 'batch_segment') {
    const casePath = row.casePath || row.outputPath || getDetectionCasePath(row)
    const query = { recordId: row.id }
    if (casePath) {
      query.casePath = casePath
      query.outputPath = casePath
    }
    router.push({
      path: '/manager/multi',
      query
    })
    return
  }
  if (row.detectMode === 'threedim') {
    const casePath = row.casePath || row.outputPath || getDetectionCasePath(row)
    if (!casePath) {
      ElMessage.warning('该记录缺少病例路径，无法查看三维重建')
      return
    }
    router.push({
      path: '/manager/threedim',
      query: { casePath, recordId: row.id }
    })
    return
  }
  if (row.detectMode === 'segment') {
    router.push({
      path: '/manager/mask',
      query: { recordId: row.id }
    })
    return
  }
  router.push({
    path: '/manager/detect',
    query: { recordId: row.id }
  })
}

const deleteRecord = async (id) => {
  try {
    await ElMessageBox.confirm('确定删除这条检测记录吗？', '提示', {
      type: 'warning',
      confirmButtonText: '确定',
      cancelButtonText: '取消'
    })
    const res = await request.delete(`/detect/delete/${id}`)
    if (res.code === '200') {
      ElMessage.success('删除成功')
      loadHistory()
    } else {
      ElMessage.error(res.msg || '删除失败')
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

const formatDateTime = (datetime) => {
  if (!datetime) return '-'
  return new Date(datetime).toLocaleString('zh-CN')
}

const getUserOptionLabel = (user) => {
  return user.name || user.username || `用户ID:${user.id || '-'}`
}

const getDetectorName = (row) => {
  return row.detectorName || row.userName || `用户ID:${row.userId || '-'}`
}

const getRecordName = (row) => {
  return row.originalImageName || row.casePath || row.outputPath || '-'
}

const getDetectionCasePath = (row) => {
  try {
    const data = typeof row.detectionData === 'string' ? JSON.parse(row.detectionData) : row.detectionData
    return data?.casePath || data?.outputPath || ''
  } catch (error) {
    return ''
  }
}

const getStatusType = (status) => {
  const types = { 0: 'info', 1: 'warning', 2: 'success', 3: 'danger' }
  return types[status] || 'info'
}

const getStatusText = (status) => {
  const texts = { 0: '待检测', 1: '检测中', 2: '完成', 3: '失败' }
  return texts[status] || '未知'
}

const getAiStatusType = (status) => {
  const types = { 0: 'info', 1: 'warning', 2: 'success', 3: 'danger' }
  return types[status] || 'info'
}

const getAiStatusText = (status) => {
  const texts = { 0: '未分析', 1: '分析中', 2: '完成', 3: '失败' }
  return texts[status] || '未知'
}

const getDetectModeText = (mode) => {
  const texts = {
    detect: '检测',
    segment: '单图分割',
    batch_segment: '病例分割',
    threedim: '三维重建'
  }
  return texts[mode] || '检测'
}

const getDetectModeType = (mode) => {
  const types = {
    detect: 'warning',
    segment: 'success',
    batch_segment: 'primary',
    threedim: 'info'
  }
  return types[mode] || 'warning'
}

const getAiModelName = (row) => {
  if (row.aiStatus !== 2 || !row.aiModel) return ''
  const model = String(row.aiModel).toLowerCase()
  const names = {
    deepseek: 'Deepseek-V4',
    glm: '',
    kimi: 'Kimi-k2.6',
    doubao: '豆包'
  }
  return Object.prototype.hasOwnProperty.call(names, model) ? names[model] : row.aiModel
}

onMounted(() => {
  loadUsers()
  loadHistory()
})
</script>

<style scoped>
.history-page {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 24px;
  background: var(--app-surface);
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius);
  box-shadow: var(--app-shadow);
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.page-header h2 {
  margin: 0 0 8px;
  color: var(--app-text);
  font-size: 22px;
  font-weight: 700;
}

.subtitle {
  margin: 0;
  color: var(--app-text-muted);
  font-size: 14px;
}

.filter-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 16px;
  background: var(--app-surface);
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius);
  box-shadow: var(--app-shadow);
}

.filter-grid {
  display: grid;
  flex: 1;
  grid-template-columns: repeat(6, minmax(140px, 1fr));
  gap: 12px;
}

.filter-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

:deep(.filter-grid .el-date-editor) {
  width: 100%;
}

.history-card {
  padding: 16px;
  background: var(--app-surface);
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius);
  box-shadow: var(--app-shadow);
}

.history-table {
  width: 100%;
}

.pagination-wrapper {
  margin-top: 20px;
  text-align: right;
}

.empty-text {
  color: var(--app-text-muted);
}

@media (max-width: 760px) {
  .page-header {
    align-items: flex-start;
    flex-direction: column;
    gap: 14px;
  }

  .header-actions,
  .filter-card,
  .filter-actions {
    width: 100%;
  }

  .filter-card {
    align-items: stretch;
    flex-direction: column;
  }

  .filter-grid {
    grid-template-columns: 1fr;
  }
}
</style>
