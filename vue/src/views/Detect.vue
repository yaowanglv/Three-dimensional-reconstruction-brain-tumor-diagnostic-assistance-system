<template>
  <div class="detect-container">
    <!-- 页面标题 -->
    <div class="page-header">
      <h2>脑肿瘤图像辅助诊断分析</h2>
      <p class="subtitle">使用不同的检测模型与国产大模型辅助检测与分析医学图像</p>
    </div>

    <!-- 操作区域 -->
    <div class="operation-area">
      <div class="operation-row">
        <!-- 图像上传 -->
        <el-upload
          class="upload-component"
          :action="`${apiBaseUrl}/detect/upload`"
          :headers="uploadHeaders"
          :data="uploadData"
          :show-file-list="false"
          :on-success="handleUploadSuccess"
          :on-error="handleUploadError"
          :before-upload="beforeUpload"
          accept=".jpg,.jpeg,.png,.dcm"
        >
          <el-button type="primary">
            <el-icon><Upload /></el-icon>
            上传医学影像
          </el-button>
        </el-upload>

        <!-- 模型选择下拉框 -->
        <el-select
          v-model="modelPath"
          placeholder="选择检测模型"
          class="model-select"
          size="default"
          :loading="modelLoading"
          clearable
        >
          <template #prefix>模型</template>
          <el-option
            v-for="item in modelOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>

        <!-- 置信度设置 -->
        <el-tooltip content="置信度阈值" placement="top">
          <el-input-number
            v-model="confThreshold"
            :min="0.05"
            :max="0.95"
            :step="0.1"
            :precision="2"
            class="threshold-input"
            size="default"
          />
        </el-tooltip>

        <!-- 开始检测按钮 -->
        <el-button
          type="success"
          @click="startDetection"
          :loading="detecting"
          :disabled="!currentRecord || currentRecord.detectStatus === 1 || !modelPath"
          class="control-button primary-action-button"
        >
          <el-icon><VideoPlay /></el-icon>
          {{ detecting ? '检测中...' : '开始检测' }}
        </el-button>

        <!-- AI模型选择 -->
        <el-select
          v-model="selectedModel"
          placeholder="选择AI大模型"
          class="ai-select"
        >
          <template #prefix>
            <span
              v-if="selectedAiModelMeta"
              class="ai-model-icon"
              :class="`ai-model-icon--${selectedAiModelMeta.value}`"
            >
              <img
                v-if="selectedAiModelMeta.iconSrc"
                :src="selectedAiModelMeta.iconSrc"
                :alt="selectedAiModelMeta.label"
              />
              <span v-else>{{ selectedAiModelMeta.icon }}</span>
            </span>
          </template>
          <el-option
            v-for="item in aiModelOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
            :disabled="!item.configured"
          >
            <span class="ai-option-label">
              <span class="ai-model-icon" :class="`ai-model-icon--${item.value}`">
                <img
                  v-if="getAiModelIconSrc(item.value)"
                  :src="getAiModelIconSrc(item.value)"
                  :alt="item.label"
                />
                <span v-else>{{ getAiModelIcon(item.value) }}</span>
              </span>
              <span>{{ item.label }}</span>
            </span>
            <el-tag
              v-if="!item.configured"
              size="small"
              type="info"
              class="ai-option-tag"
            >未配置</el-tag>
          </el-option>
        </el-select>

        <!-- AI分析按钮 -->
        <el-button
          type="warning"
          @click="startAiAnalysis"
          :loading="aiAnalyzing"
          :disabled="!canAiAnalyze"
          class="control-button"
        >
          <el-icon><ChatDotRound /></el-icon>
          {{ aiAnalyzing ? '分析中...' : 'AI辅助分析' }}
        </el-button>

        <!-- 测试连接按钮（开发调试用） -->
        <el-button
          v-if="showTestConnectionButton"
          type="info"
          @click="testConnection"
          class="control-button"
          size="small"
        >
          测试连接
        </el-button>

        <el-button
          type="primary"
          @click="exportReport"
          :disabled="!detectionData"
          class="control-button"
        >
          <el-icon><Document /></el-icon>
          导出检测报告
        </el-button>
      </div>

      <div class="feishu-row">
        <div
          class="feishu-config"
          :class="{ 'is-collapsed': !feishuConfigExpanded }"
          @click="openFeishuConfig"
        >
          <div class="feishu-config-header">
            <div class="feishu-config-title">
              <el-icon><Setting /></el-icon>
              <span>飞书权限 Token</span>
              <el-tag :type="hasFeishuToken ? 'success' : 'warning'" size="small">
                {{ hasFeishuToken ? '已配置' : '未配置' }}
              </el-tag>
            </div>
            <div class="feishu-config-actions">
              <span class="feishu-token-preview">{{ maskedFeishuToken }}</span>
              <el-button link type="primary" @click.stop="toggleFeishuConfig">
                <el-icon>
                  <ArrowDown v-if="feishuConfigExpanded" />
                  <ArrowRight v-else />
                </el-icon>
                {{ feishuConfigExpanded ? '收起' : '配置' }}
              </el-button>
            </div>
          </div>
          <div v-show="feishuConfigExpanded" class="feishu-config-body" @click.stop>
            <el-input
              v-model="feishuUserToken"
              class="feishu-token-input"
              type="password"
              show-password
              clearable
              placeholder="请输入飞书 user_access_token"
            />
            <el-button type="primary" @click="saveFeishuToken">
              <el-icon><Check /></el-icon>
              保存
            </el-button>
            <el-button @click="clearFeishuToken" :disabled="!hasFeishuToken && !feishuUserToken">
              清空
            </el-button>
          </div>
        </div>

        <el-button
          type="success"
          @click="uploadToFeishu"
          :loading="uploadingToFeishu"
          :disabled="!detectionData || !hasFeishuToken"
          class="feishu-upload-button primary-action-button"
        >
          <el-icon><Upload /></el-icon>
          上传到飞书
        </el-button>
      </div>
    </div>

    <div v-if="aiProgressVisible" class="ai-progress-panel">
      <div class="ai-progress-header">
        <span>正在生成AI大模型辅助分析报告，请稍后</span>
        <strong>{{ aiAnalysisProgress }}%</strong>
      </div>
      <el-progress
        :percentage="aiAnalysisProgress"
        :stroke-width="12"
        :color="aiProgressColors"
      />
    </div>

    <!-- 图像对比区域 -->
    <div class="image-compare-area" v-if="currentRecord">
      <el-row :gutter="20">
        <!-- 原始图像 -->
        <el-col :span="12">
          <div class="image-card">
            <div class="image-title">原始影像</div>
            <div class="image-wrapper">
              <img
                v-if="originalImageUrl"
                :src="originalImageUrl"
                alt="原始影像"
                class="medical-image"
              />
              <el-empty v-else description="暂无图像" />
            </div>
            <div class="image-info" v-if="currentRecord.originalImageName">
              <span>文件名: {{ currentRecord.originalImageName }}</span>
              <span>大小: {{ formatFileSize(currentRecord.originalImageSize) }}</span>
            </div>
          </div>
        </el-col>

        <!-- 检测结果图像 -->
        <el-col :span="12">
          <div class="image-card">
            <div class="image-title">
              检测结果
              <el-tag
                :type="detectStatusType"
                size="small"
                class="title-status"
              >
                {{ detectStatusText }}
              </el-tag>
            </div>
            <div class="image-wrapper">
              <img
                v-if="resultImageUrl"
                :src="resultImageUrl"
                alt="检测结果"
                class="medical-image"
                @error="handleImageError"
                crossorigin="anonymous"
              />
              <el-empty v-else description="等待检测">
                <template #description>
                  <div class="empty-detect-state">
                    <p>等待检测</p>
                    <p v-if="currentRecord.detectStatus === 1" class="running-tip">
                      <el-icon class="is-loading"><Loading /></el-icon>
                      正在分析图像...
                    </p>
                  </div>
                </template>
              </el-empty>
            </div>
            <!-- 检测数据概览 -->
            <div class="detection-summary" v-if="detectionData && detectionData.tumor_detected">
              <el-descriptions :column="2" size="small" border>
                <el-descriptions-item label="肿瘤类型（仅供参考）" :span="2">
                  <span class="tumor-type-text">
                    {{ getTumorTypeName(detectionData) }}
                  </span>
                </el-descriptions-item>
                <el-descriptions-item label="置信度" :span="2">
                  <el-progress
                    :percentage="Math.round((detectionData.confidence || 0) * 100)"
                    :color="confidenceColor"
                    :stroke-width="10"
                  />
                </el-descriptions-item>
                <el-descriptions-item label="检测框数量">
                  {{ detectionData.boxes?.length || 0 }} 个
                </el-descriptions-item>
                <el-descriptions-item label="处理时间">
                  {{ detectionData.processing_time_ms || '-' }} ms
                </el-descriptions-item>
              </el-descriptions>
            </div>
            <div class="detection-summary" v-else-if="detectionData && !detectionData.tumor_detected">
              <el-alert
                title="未检测到肿瘤"
                type="success"
                description="图像分析完成，未发现明显肿瘤病灶。"
                :closable="false"
              />
            </div>
          </div>
        </el-col>
      </el-row>
    </div>

    <!-- AI分析结果 -->
    <div class="ai-analysis-area" v-if="currentRecord?.aiAnalysisResult">
      <el-card>
        <template #header>
          <div class="card-header">
            <span>
              <el-icon><ChatLineRound /></el-icon>
              AI辅助分析报告
              <el-tag v-if="formatAiModelName(currentRecord.aiModel)" class="title-status" size="small" type="warning">
                {{ formatAiModelName(currentRecord.aiModel) }}
              </el-tag>
            </span>
            <span class="analysis-time">
              {{ formatDateTime(currentRecord.aiAnalysisTime) }}
            </span>
          </div>
        </template>
        <div class="analysis-content">
          <div class="analysis-text">
            <p
              v-for="(line, index) in aiAnalysisDisplayLines"
              :key="index"
              :class="{ 'analysis-disclaimer': isAnalysisDisclaimerLine(line), 'analysis-section-title': isAnalysisSectionTitleLine(line) }"
            >
              {{ line }}
            </p>
          </div>
        </div>
      </el-card>
    </div>

  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, onActivated, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Upload, VideoPlay, ChatDotRound, ChatLineRound, Loading, Document, Setting, Check, ArrowDown, ArrowRight } from '@element-plus/icons-vue'
import request from '@/utils/request.js'
import { getAccessToken, getCurrentUser } from '@/utils/auth'
import { fetchAiConfig, fetchModels, formatModelOptions, getBackendBaseUrl, getThreshold, isWorkPageAutoRefreshEnabled, onConfigUpdated } from '@/utils/config'
import { clearPersistedFeishuUserToken, formatFeishuUploadError, getFeishuUserToken, loadPersistedFeishuUserToken, onFeishuUserTokenChange, savePersistedFeishuUserToken } from '@/utils/feishuToken'
import deepseekIcon from '@/assets/imgs/deepseek - logo.png'
import glmIcon from '@/assets/imgs/GLM.webp'
import kimiIcon from '@/assets/imgs/kimi.webp'
import html2canvas from 'html2canvas'
import jsPDF from 'jspdf'

defineOptions({ name: 'Detect' })

// ==================== 响应式数据 ====================

const AI_DISCLAIMER_TEXT = '本分析报告仅供参考，仅为辅助分析，不做实际诊断，诊断请到正规医院进行彻底检查与分析'
const LEGACY_AI_DISCLAIMER_TEXT = '本分析报告仅供参考，仅为辅助判断，不做实际诊断，诊断请到正规医院进行彻底检查与分析'
const AI_MODEL_LABELS = {
  deepseek: 'Deepseek-V4',
  kimi: 'Kimi-k2.6',
  glm: 'GLM-5.1',
  doubao: '豆包'
}
const AI_MODEL_ICONS = {
  deepseek: 'DS',
  kimi: 'K',
  glm: 'GLM',
  doubao: '豆'
}
const AI_MODEL_ICON_IMAGES = {
  deepseek: deepseekIcon,
  glm: glmIcon,
  kimi: kimiIcon
}
const AI_ANALYSIS_SECTION_TITLES = [
  '肿瘤类型分析',
  '位置与大小评估',
  '严重程度判断',
  '严重程度初步判断',
  '后续检查或治疗方案',
  '建议的后续检查或治疗方案',
  '需要注意的事项',
  '饮食建议',
  '作息建议',
  '运动建议',
  '是否能够进行运动的建议'
]

const route = useRoute()
const currentRecord = ref(null)
const detecting = ref(false)
const aiAnalyzing = ref(false)
const exporting = ref(false)
const uploadingToFeishu = ref(false)
const feishuUserToken = ref(getFeishuUserToken())
const feishuConfigExpanded = ref(false)
const aiProgressVisible = ref(false)
const aiAnalysisProgress = ref(0)
const showTestConnectionButton = false
const selectedModel = ref('deepseek')
const aiModelOptions = ref([
  { label: AI_MODEL_LABELS.deepseek, value: 'deepseek', configured: false },
  { label: AI_MODEL_LABELS.glm, value: 'glm', configured: false },
  { label: AI_MODEL_LABELS.kimi, value: 'kimi', configured: false }
])

// 模型配置
const modelPath = ref('')
const modelOptions = ref([])
const modelLoading = ref(false)
const confThreshold = ref(0.45)
let aiProgressTimer = null
let removeFeishuTokenListener = null
let removeConfigListener = null
let lastRouteRecordId = undefined

const aiProgressColors = [
  { color: '#b7791f', percentage: 55 },
  { color: '#256f7f', percentage: 92 },
  { color: '#2f8f5b', percentage: 100 }
]
const apiBaseUrl = computed(() => getBackendBaseUrl())
const uploadHeaders = computed(() => {
  const token = getAccessToken()
  return token ? { Authorization: `Bearer ${token}` } : {}
})

const hasFeishuToken = computed(() => !!feishuUserToken.value.trim())

const maskedFeishuToken = computed(() => {
  const token = feishuUserToken.value.trim()
  if (!token) return '保存后两个页面同步使用'
  if (token.length <= 12) return '已保存'
  return `${token.slice(0, 6)}...${token.slice(-4)}`
})

const selectedAiModelMeta = computed(() => {
  const model = aiModelOptions.value.find(item => item.value === selectedModel.value)
  if (!model) return null
  return {
    ...model,
    icon: getAiModelIcon(model.value),
    iconSrc: getAiModelIconSrc(model.value)
  }
})

// 上传数据
const uploadData = computed(() => {
  const user = getCurrentUser()
  return {
    userId: user.id || 1,
    userName: user.name || '管理员'
  }
})

// 图像URL
const originalImageUrl = computed(() => {
  if (!currentRecord.value?.originalImageUrl) return ''
  return `${getBackendBaseUrl()}/files/${currentRecord.value.originalImageUrl}`
})

const resultImageUrl = computed(() => {
  if (!currentRecord.value?.resultImageUrl) return ''
  // 如果是完整URL（从Python服务直接返回的），直接使用
  if (currentRecord.value.resultImageUrl.startsWith('http')) {
    return currentRecord.value.resultImageUrl
  }
  // 否则使用Spring Boot的文件服务
  return `${getBackendBaseUrl()}/files/${currentRecord.value.resultImageUrl}`
})

const selectedModelName = computed(() => {
  const model = modelOptions.value.find(item => item.value === modelPath.value)
  return model?.label || getModelName(modelPath.value)
})

const aiAnalysisDisplayLines = computed(() => {
  return String(currentRecord.value?.aiAnalysisResult || '').split(/\r?\n/)
})

const isAnalysisDisclaimerLine = (line = '') => {
  const text = String(line).trim()
  return text === AI_DISCLAIMER_TEXT ||
    text === LEGACY_AI_DISCLAIMER_TEXT ||
    (text.includes('本分析报告仅供参考') && text.includes('不做实际诊断'))
}

const isAnalysisSectionTitleLine = (line = '') => {
  const text = String(line)
    .trim()
    .replace(/^(?:[\s\d一二三四五六七八九十]+[.、．)]|[（(][\d一二三四五六七八九十]+[）)])\s*/, '')
    .replace(/[：:].*$/, '')
    .trim()
  return AI_ANALYSIS_SECTION_TITLES.includes(text)
}

const formatAiModelName = (model) => {
  if (!model) return ''
  const key = String(model).toLowerCase()
  return Object.prototype.hasOwnProperty.call(AI_MODEL_LABELS, key) ? AI_MODEL_LABELS[key] : model
}

const getAiModelIcon = (model) => {
  const key = String(model || '').toLowerCase()
  return AI_MODEL_ICONS[key] || 'AI'
}

const getAiModelIconSrc = (model) => {
  const key = String(model || '').toLowerCase()
  return AI_MODEL_ICON_IMAGES[key] || ''
}

// 添加图像加载错误处理
const handleImageError = (e) => {
  console.error('图像加载失败:', e)
  ElMessage.error('结果图像加载失败')
}

// 肿瘤类型英文 -> 中文映射
const tumorTypeMapping = {
  'normal': '正常',
  'nt': '正常',
  'glioma': '神经胶质瘤',
  'glioma_tumor': '神经胶质瘤',
  'glioma tumor': '神经胶质瘤',
  'gl': '神经胶质瘤',
  'meningioma': '脑膜瘤',
  'meningioma_tumor': '脑膜瘤',
  'meningioma tumor': '脑膜瘤',
  'me': '脑膜瘤',
  'pituitary': '垂体瘤',
  'pituitary_tumor': '垂体瘤',
  'pituitary tumor': '垂体瘤',
  'pi': '垂体瘤',
  'tumor': '肿瘤',
  '0': '神经胶质瘤',
  '1': '脑膜瘤',
  '2': '垂体瘤'
}

const normalizeTumorType = (value) => {
  if (value === null || value === undefined) return ''
  return String(value).trim()
}

const mapTumorTypeName = (value) => {
  const rawValue = normalizeTumorType(value)
  if (!rawValue) return ''
  const lowerValue = rawValue.toLowerCase()
  if (['未知', 'unknown', 'none', 'null', 'undefined', '-'].includes(lowerValue)) return ''
  const normalizedKey = lowerValue.replace(/[-_]+/g, ' ')
  const underscoreKey = lowerValue.replace(/[\s-]+/g, '_')
  return tumorTypeMapping[lowerValue] ||
    tumorTypeMapping[normalizedKey] ||
    tumorTypeMapping[underscoreKey] ||
    rawValue
}

const normalizeDetectionBoxes = (boxes) => {
  if (Array.isArray(boxes)) return boxes
  if (typeof boxes === 'string') {
    try {
      const parsedBoxes = JSON.parse(boxes)
      return Array.isArray(parsedBoxes) ? parsedBoxes : []
    } catch {
      return []
    }
  }
  return []
}

const getMaxConfidenceBox = (boxes = []) => {
  const normalizedBoxes = normalizeDetectionBoxes(boxes)
  if (normalizedBoxes.length === 0) return null
  return normalizedBoxes.reduce((max, box) => {
    const currentConfidence = Number(box?.confidence ?? 0)
    const maxConfidence = Number(max?.confidence ?? 0)
    return currentConfidence > maxConfidence ? box : max
  }, normalizedBoxes[0])
}

const getBoxTumorTypeValue = (box) => {
  if (!box) return ''
  return box.tumor_type ||
    box.tumorType ||
    box.tumor_type_en ||
    box.label ||
    box.class_name ||
    box.className ||
    box.name ||
    box.cls_name ||
    box.class_id ||
    box.classId ||
    box.class ||
    box.cls
}

const getTumorTypeFromFileName = (fileName) => {
  const normalizedName = normalizeTumorType(fileName).toLowerCase()
  if (!normalizedName) return ''
  const match = normalizedName.match(/(?:^|[_\-.])(gl|me|pi|nt)(?:[_\-.]|$)/)
  return match ? mapTumorTypeName(match[1]) : ''
}

const getTumorTypeName = (data, record = currentRecord.value) => {
  if (!data) return '未知'
  const maxConfBox = getMaxConfidenceBox(data.boxes)
  const fileNameType = getTumorTypeFromFileName(
    record?.originalImageName ||
    data.original_image_name ||
    data.original_filename ||
    data.file_name ||
    data.filename
  )
  const candidates = [
    getBoxTumorTypeValue(maxConfBox),
    data.tumor_type_cn,
    data.tumor_type,
    data.tumor_type_en,
    data.tumorType,
    data.label,
    data.class_name,
    data.className,
    data.class_id,
    data.classId,
    data.class,
    data.cls,
    fileNameType
  ]

  for (const item of candidates) {
    const name = mapTumorTypeName(item)
    if (name) return name
  }
  return '未知'
}

const escapeHtml = (value) => {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

const drawText = (ctx, text, x, y, options = {}) => {
  ctx.fillStyle = options.color || '#1f2933'
  ctx.font = options.font || '14px "Microsoft YaHei", "SimHei", Arial, sans-serif'
  ctx.textAlign = options.align || 'left'
  ctx.textBaseline = 'top'
  ctx.fillText(String(text ?? '-'), x, y)
}

const createReportHeaderCanvas = (record, data) => {
  const canvas = document.createElement('canvas')
  const scale = 2
  const width = 800
  const height = data ? 260 : 150
  canvas.width = width * scale
  canvas.height = height * scale
  canvas.style.width = `${width}px`
  canvas.style.height = `${height}px`

  const ctx = canvas.getContext('2d')
  ctx.scale(scale, scale)
  ctx.fillStyle = '#ffffff'
  ctx.fillRect(0, 0, width, height)

  drawText(ctx, '脑肿瘤辅助诊断与分析报告', width / 2, 16, {
    align: 'center',
    color: '#1f2933',
    font: '700 24px "Microsoft YaHei", "SimHei", Arial, sans-serif'
  })
  drawText(ctx, '大模型驱动的脑肿瘤智能辅助诊断与分析系统', width / 2, 52, {
    align: 'center',
    color: '#687782',
    font: '12px "Microsoft YaHei", "SimHei", Arial, sans-serif'
  })
  ctx.strokeStyle = '#256f7f'
  ctx.lineWidth = 2
  ctx.beginPath()
  ctx.moveTo(30, 76)
  ctx.lineTo(width - 30, 76)
  ctx.stroke()

  drawText(ctx, `报告编号: ${record?.id || '-'}`, 35, 94, { color: '#687782', font: '12px "Microsoft YaHei", "SimHei", Arial, sans-serif' })
  drawText(ctx, `生成时间: ${new Date().toLocaleString('zh-CN')}`, width / 2, 94, { align: 'center', color: '#687782', font: '12px "Microsoft YaHei", "SimHei", Arial, sans-serif' })
  drawText(ctx, `检测模型: ${record?.modelName || selectedModelName.value}`, width - 35, 94, { align: 'right', color: '#687782', font: '12px "Microsoft YaHei", "SimHei", Arial, sans-serif' })

  if (!data) return canvas

  const tumorTypeName = getTumorTypeName(data, record)
  const confidenceText = `${Math.round((Number(data.confidence) || 0) * 100)}%`
  const boxCountText = `${normalizeDetectionBoxes(data.boxes).length} 个`
  const processingTimeText = `${data.processing_time_ms || '-'} ms`

  ctx.fillStyle = '#f8fbfa'
  ctx.fillRect(30, 125, width - 60, 105)
  ctx.strokeStyle = '#e1e8e5'
  ctx.lineWidth = 1
  ctx.strokeRect(30, 125, width - 60, 105)
  ctx.fillStyle = '#256f7f'
  ctx.fillRect(45, 144, 4, 18)
  drawText(ctx, '检测结果详情', 58, 142, {
    color: '#1f2933',
    font: '700 16px "Microsoft YaHei", "SimHei", Arial, sans-serif'
  })

  drawText(ctx, '肿瘤类型:', 58, 178, { color: '#687782' })
  drawText(ctx, tumorTypeName, 128, 178, {
    color: '#c2413b',
    font: '700 14px "Microsoft YaHei", "SimHei", Arial, sans-serif'
  })
  drawText(ctx, '置信度:', 415, 178, { color: '#687782' })
  drawText(ctx, confidenceText, 472, 178, {
    color: '#256f7f',
    font: '700 14px "Microsoft YaHei", "SimHei", Arial, sans-serif'
  })
  drawText(ctx, '检测框数量:', 58, 206, { color: '#687782' })
  drawText(ctx, boxCountText, 142, 206, { color: '#1f2933' })
  drawText(ctx, '处理时间:', 415, 206, { color: '#687782' })
  drawText(ctx, processingTimeText, 486, 206, { color: '#1f2933' })

  return canvas
}

const wrapCanvasText = (ctx, text, maxWidth) => {
  const lines = []
  const paragraphs = String(text || '-').split('\n')

  paragraphs.forEach((paragraph) => {
    const content = paragraph.trimEnd()
    if (!content) {
      lines.push('')
      return
    }

    let line = ''
    for (const char of content) {
      const testLine = line + char
      if (ctx.measureText(testLine).width > maxWidth && line) {
        lines.push(line)
        line = char
      } else {
        line = testLine
      }
    }
    if (line) lines.push(line)
  })

  return lines
}

const wrapCanvasAnalysisText = (ctx, text, maxWidth) => {
  const lines = []
  const paragraphs = String(text || '-').split('\n')

  paragraphs.forEach((paragraph) => {
    const content = paragraph.trimEnd()
    const isDisclaimer = isAnalysisDisclaimerLine(content)
    const isSectionTitle = isAnalysisSectionTitleLine(content)
    if (!content) {
      lines.push({ text: '', isDisclaimer: false, isSectionTitle: false })
      return
    }

    ctx.font = `${isDisclaimer || isSectionTitle ? '700 ' : ''}14px "Microsoft YaHei", "SimHei", Arial, sans-serif`
    let line = ''
    for (const char of content) {
      const testLine = line + char
      if (ctx.measureText(testLine).width > maxWidth && line) {
        lines.push({ text: line, isDisclaimer, isSectionTitle })
        line = char
      } else {
        line = testLine
      }
    }
    if (line) lines.push({ text: line, isDisclaimer, isSectionTitle })
  })

  return lines
}

const createAiAnalysisCanvas = (record) => {
  const scale = 2
  const width = 800
  const padding = 30
  const contentWidth = width - padding * 2
  const lineHeight = 24
  const measureCanvas = document.createElement('canvas')
  const measureCtx = measureCanvas.getContext('2d')
  measureCtx.font = '14px "Microsoft YaHei", "SimHei", Arial, sans-serif'
  const lines = wrapCanvasAnalysisText(measureCtx, record?.aiAnalysisResult || '-', contentWidth)
  const height = Math.max(210, 140 + lines.length * lineHeight)

  const canvas = document.createElement('canvas')
  canvas.width = width * scale
  canvas.height = height * scale
  canvas.style.width = `${width}px`
  canvas.style.height = `${height}px`

  const ctx = canvas.getContext('2d')
  ctx.scale(scale, scale)
  ctx.fillStyle = '#ffffff'
  ctx.fillRect(0, 0, width, height)

  drawText(ctx, 'AI 辅助分析报告', width / 2, 20, {
    align: 'center',
    color: '#1f2933',
    font: '700 22px "Microsoft YaHei", "SimHei", Arial, sans-serif'
  })
  ctx.strokeStyle = '#b7791f'
  ctx.lineWidth = 2
  ctx.beginPath()
  ctx.moveTo(padding, 60)
  ctx.lineTo(width - padding, 60)
  ctx.stroke()

  drawText(ctx, `分析模型: ${formatAiModelName(record?.aiModel) || '-'}`, padding, 82, {
    color: '#687782',
    font: '12px "Microsoft YaHei", "SimHei", Arial, sans-serif'
  })
  drawText(ctx, `分析时间: ${formatDateTime(record?.aiAnalysisTime)}`, width - padding, 82, {
    align: 'right',
    color: '#687782',
    font: '12px "Microsoft YaHei", "SimHei", Arial, sans-serif'
  })

  let y = 120
  const safeBreaksPx = []
  lines.forEach((line) => {
    const lineY = y
    drawText(ctx, line.text, padding, y, {
      color: line.isDisclaimer ? '#c2413b' : '#1f2933',
      font: `${line.isDisclaimer || line.isSectionTitle ? '700 ' : ''}14px "Microsoft YaHei", "SimHei", Arial, sans-serif`
    })
    const rowHeight = line.text ? lineHeight : lineHeight * 0.65
    y += rowHeight
    safeBreaksPx.push(Math.round((lineY + rowHeight - 3) * scale))
  })
  canvas.pdfSafeBreaksPx = safeBreaksPx

  return canvas
}

const normalizeSafeBreaksPx = (breaks, canvasHeight) => {
  return [...new Set((breaks || [])
    .map((breakY) => Math.round(Number(breakY)))
    .filter((breakY) => Number.isFinite(breakY) && breakY > 0 && breakY < canvasHeight)
  )].sort((a, b) => a - b)
}

const getSafeSliceHeightPx = (sourceY, maxSliceHeightPx, canvasHeight, safeBreaksPx, minSliceHeightPx, tolerancePx) => {
  const remainingPx = canvasHeight - sourceY
  const hardSliceHeightPx = Math.max(1, Math.min(remainingPx, maxSliceHeightPx))

  if (!safeBreaksPx.length || remainingPx <= hardSliceHeightPx + tolerancePx) {
    return hardSliceHeightPx
  }

  const maxBreakY = sourceY + hardSliceHeightPx
  const minBreakY = sourceY + Math.min(hardSliceHeightPx - 1, minSliceHeightPx)
  let safeBreakY = null

  for (const breakY of safeBreaksPx) {
    if (breakY > minBreakY && breakY <= maxBreakY) {
      safeBreakY = breakY
    }
    if (breakY > maxBreakY) break
  }

  return safeBreakY ? Math.max(1, safeBreakY - sourceY) : hardSliceHeightPx
}

const addCanvasToPdfPaged = (pdf, canvas, options = {}) => {
  const pageWidth = pdf.internal.pageSize.getWidth()
  const pageHeight = pdf.internal.pageSize.getHeight()
  const x = options.x ?? 0
  const width = options.width ?? pageWidth
  const marginTop = options.marginTop ?? 10
  const marginBottom = options.marginBottom ?? 10
  let currentY = options.y ?? marginTop
  let sourceY = 0
  const pxPerPdfUnit = canvas.width / width
  const tolerancePx = 2
  const safeBreaksPx = normalizeSafeBreaksPx(options.safeBreaksPx || canvas.pdfSafeBreaksPx, canvas.height)
  const minSliceHeightPx = options.minSliceHeightPx ?? Math.max(24, Math.round(canvas.width * 0.03))

  while (sourceY < canvas.height - tolerancePx) {
    let availableHeight = pageHeight - currentY - marginBottom
    if (availableHeight <= 5) {
      pdf.addPage()
      currentY = marginTop
      availableHeight = pageHeight - currentY - marginBottom
    }

    const maxSliceHeightPx = Math.max(1, Math.floor(availableHeight * pxPerPdfUnit))
    const sliceHeightPx = getSafeSliceHeightPx(
      sourceY,
      maxSliceHeightPx,
      canvas.height,
      safeBreaksPx,
      minSliceHeightPx,
      tolerancePx
    )
    const sliceCanvas = document.createElement('canvas')
    sliceCanvas.width = canvas.width
    sliceCanvas.height = sliceHeightPx
    const sliceCtx = sliceCanvas.getContext('2d')
    sliceCtx.drawImage(
      canvas,
      0,
      sourceY,
      canvas.width,
      sliceHeightPx,
      0,
      0,
      canvas.width,
      sliceHeightPx
    )

    const sliceHeight = sliceHeightPx / pxPerPdfUnit
    pdf.addImage(sliceCanvas.toDataURL('image/png'), 'PNG', x, currentY, width, sliceHeight)
    sourceY += sliceHeightPx
    currentY += sliceHeight

    if (sourceY < canvas.height - tolerancePx) {
      pdf.addPage()
      currentY = marginTop
    }
  }

  return currentY
}

// 解析检测数据
const detectionData = computed(() => {
  if (!currentRecord.value?.detectionData) return null
  try {
    const data = JSON.parse(currentRecord.value.detectionData)
    if (data.detection && typeof data.detection === 'object') {
      Object.assign(data, data.detection)
    }
    data.boxes = normalizeDetectionBoxes(data.boxes)
    // 从 boxes 中提取最高置信度（顶层 confidence 可能不存在）
    if (data.boxes && data.boxes.length > 0) {
      const maxConfBox = getMaxConfidenceBox(data.boxes)
      data.confidence = Number(maxConfBox?.confidence ?? data.confidence ?? 0)
    }
    data.tumor_type_cn = getTumorTypeName(data, currentRecord.value)
    return data
  } catch {
    return null
  }
})

// 检测状态
const detectStatusType = computed(() => {
  const status = currentRecord.value?.detectStatus
  const types = { 0: 'info', 1: 'warning', 2: 'success', 3: 'danger' }
  return types[status] || 'info'
})

const detectStatusText = computed(() => {
  const status = currentRecord.value?.detectStatus
  const texts = { 0: '待检测', 1: '检测中', 2: '检测完成', 3: '检测失败' }
  return texts[status] || '未知'
})

// 是否可进行AI分析
const canAiAnalyze = computed(() => {
  return currentRecord.value?.detectStatus === 2 &&
         currentRecord.value?.aiStatus !== 1
})

// 置信度颜色
const confidenceColor = computed(() => {
  const conf = detectionData.value?.confidence || 0
  if (conf >= 0.9) return '#2f8f5b'
  if (conf >= 0.7) return '#b7791f'
  return '#c2413b'
})

// ==================== 方法 ====================

const clearAiProgressTimer = () => {
  if (aiProgressTimer) {
    clearInterval(aiProgressTimer)
    aiProgressTimer = null
  }
}

const startAiProgress = () => {
  clearAiProgressTimer()
  aiProgressVisible.value = true
  aiAnalysisProgress.value = 3
  aiProgressTimer = setInterval(() => {
    if (aiAnalysisProgress.value >= 92) return
    const step = aiAnalysisProgress.value < 35 ? 7 : aiAnalysisProgress.value < 70 ? 4 : 1
    aiAnalysisProgress.value = Math.min(92, aiAnalysisProgress.value + step)
  }, 500)
}

const completeAiProgress = () => {
  clearAiProgressTimer()
  aiAnalysisProgress.value = 100
  setTimeout(() => {
    aiProgressVisible.value = false
  }, 1200)
}

const stopAiProgress = () => {
  clearAiProgressTimer()
  aiProgressVisible.value = false
  aiAnalysisProgress.value = 0
}

const toggleFeishuConfig = () => {
  feishuConfigExpanded.value = !feishuConfigExpanded.value
}

const openFeishuConfig = () => {
  feishuConfigExpanded.value = true
}

const loadFeishuTokenConfig = async () => {
  try {
    const token = await loadPersistedFeishuUserToken()
    feishuUserToken.value = token
    if (!token) {
      feishuConfigExpanded.value = true
    }
  } catch (error) {
    console.error('鍔犺浇椋炰功 token 澶辫触:', error)
  }
}

const saveFeishuToken = async () => {
  const token = feishuUserToken.value.trim()
  if (!token) {
    ElMessage.warning('请输入飞书权限 token')
    return
  }

  await savePersistedFeishuUserToken(token)
  feishuUserToken.value = token
  feishuConfigExpanded.value = false
  ElMessage.success('飞书 token 已保存')
}

const clearFeishuToken = async () => {
  await clearPersistedFeishuUserToken()
  feishuUserToken.value = ''
  feishuConfigExpanded.value = true
  ElMessage.success('飞书 token 已清空')
}

// 上传前校验
const beforeUpload = (file) => {
  const validExts = ['jpg', 'jpeg', 'png', 'dcm', 'dicom']
  const ext = file.name.split('.').pop().toLowerCase()

  if (!validExts.includes(ext)) {
    ElMessage.error('请上传 JPG、PNG 或 DICOM 格式的医学影像')
    return false
  }

  const maxSize = 50 * 1024 * 1024 // 50MB
  if (file.size > maxSize) {
    ElMessage.error('文件大小不能超过 50MB')
    return false
  }

  return true
}

// 上传成功
const handleUploadSuccess = (res) => {
  if (res.code === '200') {
    ElMessage.success('影像上传成功')
    loadRecordById(res.data.id)
  } else {
    ElMessage.error(res.msg || '上传失败')
  }
}

// 上传失败
const handleUploadError = (error) => {
  console.error('上传错误详情:', error)
  let msg = '上传失败'
  if (error?.status === 0) {
    msg = `无法连接到服务器，请确保后端服务已启动 (${getBackendBaseUrl()})`
  } else if (error?.message) {
    msg = '上传失败: ' + error.message
  }
  ElMessage.error(msg)
}

// 开始检测
const startDetection = async () => {
  if (!currentRecord.value?.id) {
    ElMessage.warning('请先上传影像')
    return
  }
  
  if (!modelPath.value) {
    ElMessage.warning('请选择检测模型')
    return
  }

  detecting.value = true
  try {
    const res = await request.post(`/detect/startDetect/${currentRecord.value.id}`, null, {
      params: {
        ptPath: modelPath.value,
        conf: confThreshold.value
      }
    })
    if (res.code === '200') {
      ElMessage.success('检测完成')
      currentRecord.value = res.data
    } else {
      ElMessage.error(res.msg || '检测失败')
    }
  } catch (error) {
    console.error('检测失败:', error)
    ElMessage.error(error.response?.data?.msg || '检测请求失败')
  } finally {
    detecting.value = false
  }
}

// AI辅助分析
const startAiAnalysis = async () => {
  if (!selectedModel.value) {
    ElMessage.warning('请选择AI模型')
    return
  }
  
  if (!currentRecord.value?.id) {
    ElMessage.warning('请先选择检测记录')
    return
  }

  aiAnalyzing.value = true
  startAiProgress()
  try {
    const res = await request.post(
      `/detect/aiAnalysis/${currentRecord.value.id}?model=${selectedModel.value}`
    )
    if (res.code === '200') {
      completeAiProgress()
      ElMessage.success('AI大模型辅助分析报告已生成')
      currentRecord.value = res.data
    } else {
      stopAiProgress()
      ElMessage.error(res.msg || '分析失败')
    }
  } catch (error) {
    stopAiProgress()
    ElMessage.error('AI分析请求失败')
  } finally {
    aiAnalyzing.value = false
  }
}

// 导出检测报告为PDF
const exportReport = async () => {
  if (!currentRecord.value?.id) {
    ElMessage.warning('请先进行检测')
    return
  }

  exporting.value = true
  ElMessage.info('正在生成PDF报告，请稍后')

  try {
    const headerCanvas = createReportHeaderCanvas(currentRecord.value, detectionData.value)
    const headerImgData = headerCanvas.toDataURL('image/png')
    
    // 获取报告区域的DOM元素（对比图）
    const reportElement = document.querySelector('.image-compare-area')
    const canvas = await html2canvas(reportElement, {
      scale: 2,
      useCORS: true,
      allowTaint: true,
      backgroundColor: '#ffffff',
      logging: false
    })

    // 创建PDF
    const pdf = new jsPDF('p', 'mm', 'a4')
    const pdfWidth = pdf.internal.pageSize.getWidth()
    
    let currentY = 10

    // 添加标题区域到PDF
    const headerRatio = pdfWidth / headerCanvas.width
    const headerHeight = headerCanvas.height * headerRatio
    pdf.addImage(headerImgData, 'PNG', 0, currentY, pdfWidth, headerHeight)
    currentY += headerHeight + 5

    // 添加对比图到PDF，内容过长时自动分页，避免截断
    addCanvasToPdfPaged(pdf, canvas, {
      y: currentY,
      width: pdfWidth,
      marginTop: 10,
      marginBottom: 10
    })

    // 如果有AI分析结果，添加新页面
    if (currentRecord.value.aiAnalysisResult) {
      const aiCanvas = createAiAnalysisCanvas(currentRecord.value)
      pdf.addPage()
      addCanvasToPdfPaged(pdf, aiCanvas, {
        y: 10,
        width: pdfWidth,
        marginTop: 10,
        marginBottom: 10
      })
    }

    // 下载PDF
    const fileName = `脑肿瘤辅助诊断与分析报告_${currentRecord.value.id}_${new Date().toISOString().split('T')[0]}.pdf`
    pdf.save(fileName)

    ElMessage.success('PDF报告已生成')
  } catch (error) {
    console.error('导出PDF失败:', error)
    ElMessage.error('导出PDF失败: ' + error.message)
  } finally {
    exporting.value = false
  }
}

// 构建上传用PDF（compress=true时压缩以减小体积，quality为图片质量0~1）
const buildUploadPdf = (headerCanvas, mainCanvas, compressed, quality) => {
  if (quality === undefined) {
    quality = compressed ? 0.5 : 1.0
  }
  const headerFormat = compressed ? 'image/jpeg' : 'image/png'
  const imgFormat = compressed ? 'JPEG' : 'PNG'
  const jsCompression = compressed ? 'MEDIUM' : 'NONE'

  const headerImgData = headerCanvas.toDataURL(headerFormat, quality)
  const pdf = new jsPDF('p', 'mm', 'a4')
  const pdfWidth = pdf.internal.pageSize.getWidth()
  let currentY = 10

  const headerRatio = pdfWidth / headerCanvas.width
  const headerHeight = headerCanvas.height * headerRatio
  pdf.addImage(headerImgData, imgFormat, 0, currentY, pdfWidth, headerHeight, undefined, jsCompression)
  currentY += headerHeight + 5

  addCanvasToPdfPaged(pdf, mainCanvas, {
    y: currentY,
    width: pdfWidth,
    marginTop: 10,
    marginBottom: 10,
    imgFormat: imgFormat,
    quality: quality,
    jsCompression: jsCompression
  })

  if (currentRecord.value.aiAnalysisResult) {
    const aiCanvas = createAiAnalysisCanvas(currentRecord.value)
    pdf.addPage()
    addCanvasToPdfPaged(pdf, aiCanvas, {
      y: 10,
      width: pdfWidth,
      marginTop: 10,
      marginBottom: 10,
      imgFormat: imgFormat,
      quality: quality,
      jsCompression: jsCompression
    })
  }

  return pdf
}

// 上传到飞书云文档
const uploadToFeishu = async () => {
  if (!currentRecord.value?.id) {
    ElMessage.warning('请先进行检测')
    return
  }

  const userToken = feishuUserToken.value.trim()
  if (!userToken) {
    feishuConfigExpanded.value = true
    ElMessage.warning('请先配置飞书权限 token')
    return
  }

  uploadingToFeishu.value = true
  ElMessage.info('正在生成并上传PDF到飞书，请稍后')

  try {
    // 首先生成高质量PDF（与导出报告一致）
    const headerCanvas = createReportHeaderCanvas(currentRecord.value, detectionData.value)
    const reportElement = document.querySelector('.image-compare-area')

    // 飞书限制20MB，保留5%余量用19MB作为阈值
    const MAX_SIZE = 20 * 1024 * 1024
    // 压缩等级：渐进式递增（降低图片质量 + 缩小canvas分辨率）
    const compressionLevels = [
      { compressed: false, quality: 1.0, scale: 2, label: 'high' },
      { compressed: true, quality: 0.5, scale: 2, label: 'medium' },
      { compressed: true, quality: 0.25, scale: 2, label: 'low' },
      { compressed: true, quality: 0.15, scale: 1.5, label: 'very low' },
      { compressed: true, quality: 0.1, scale: 1, label: 'minimum' },
    ]

    let pdfBlob = null
    let canvas = null
    let prevScale = 0

    for (const level of compressionLevels) {
      // 仅在 scale 变化时重新渲染 canvas（避免重复渲染）
      if (!canvas || level.scale !== prevScale) {
        canvas = await html2canvas(reportElement, {
          scale: level.scale,
          useCORS: true,
          allowTaint: true,
          backgroundColor: '#ffffff',
          logging: false
        })
        prevScale = level.scale
      }

      const pdf = buildUploadPdf(headerCanvas, canvas, level.compressed, level.quality)
      const blob = pdf.output('blob')
      const sizeMB = (blob.size / 1024 / 1024).toFixed(1)
      console.log(`PDF生成(${level.label}): ${sizeMB}MB`)
      if (blob.size < MAX_SIZE) {
        pdfBlob = blob
        break
      }
    }

    // 如果所有压缩等级都超过20MB，用最低质量强行上传（仍有失败风险）
    if (!pdfBlob) {
      const finalCanvas = canvas || await html2canvas(reportElement, {
        scale: 1,
        useCORS: true,
        allowTaint: true,
        backgroundColor: '#ffffff',
        logging: false
      })
      const pdf = buildUploadPdf(headerCanvas, finalCanvas, true, 0.08)
      pdfBlob = pdf.output('blob')
      console.warn(`所有压缩等级均超过20MB限制，使用最低质量强行上传: ${(pdfBlob.size / 1024 / 1024).toFixed(1)}MB`)
    }

    const userName = currentRecord.value?.userName || '未知用户'
    const timestamp = new Date().toISOString().split('T')[0]
    const fileName = `脑肿瘤辅助诊断与分析报告_${timestamp}_${userName}.pdf`

    const formData = new FormData()
    formData.append('file', pdfBlob, fileName)
    formData.append('fileName', fileName)
    formData.append('fileType', 'pdf')

    const res = await request.post('/feishu/upload-with-user-token', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
        'X-User-Token': userToken
      }
    })

    if (res.code === '200') {
      ElMessage.success(`上传成功！文件token: ${res.data.fileToken}`)
    } else {
      ElMessage.error(formatFeishuUploadError(res))
    }
  } catch (error) {
    console.error('上传到飞书失败:', error)
    ElMessage.error(formatFeishuUploadError(error))
  } finally {
    uploadingToFeishu.value = false
  }
}

// 加载记录详情
const loadRecordById = async (id) => {
  if (!id) return
  try {
    const res = await request.get(`/detect/selectById/${id}`)
    if (res.code === '200') {
      currentRecord.value = res.data
    } else {
      ElMessage.error(res.msg || '加载记录失败')
    }
  } catch (error) {
    console.error('加载记录失败', error)
    ElMessage.error('加载记录失败')
  }
}

const loadRecordFromRoute = (recordId) => {
  lastRouteRecordId = recordId
  if (recordId) {
    loadRecordById(recordId)
  } else if (isWorkPageAutoRefreshEnabled()) {
    currentRecord.value = null
  }
}

// 测试后端连接
const testConnection = async () => {
  try {
    const res = await request.get('/detect/health')
    if (res.code === '200') {
      ElMessage.success('后端连接正常: ' + res.data)
    } else {
      ElMessage.warning('后端响应异常: ' + res.msg)
    }
  } catch (error) {
    console.error('连接测试失败:', error)
    ElMessage.error('无法连接到后端服务，请确保: 1)后端已启动 2)端口9527可用')
  }
}

// ==================== 工具函数 ====================

const formatFileSize = (bytes) => {
  if (!bytes) return '-'
  const units = ['B', 'KB', 'MB', 'GB']
  let size = bytes
  let unitIndex = 0
  while (size >= 1024 && unitIndex < units.length - 1) {
    size /= 1024
    unitIndex++
  }
  return `${size.toFixed(2)} ${units[unitIndex]}`
}

const formatDateTime = (datetime) => {
  if (!datetime) return '-'
  return new Date(datetime).toLocaleString('zh-CN')
}

// 获取模型名称：D:\...\YOLOv11\weights\best.pt -> YOLOv11
const getModelName = (fullPath) => {
  if (!fullPath) return '-'
  const parts = String(fullPath).split(/[\\/]/).filter(Boolean)
  if (!parts.length) return '-'

  const fileName = parts[parts.length - 1]
  const parentName = parts[parts.length - 2]
  const grandParentName = parts[parts.length - 3]
  const lowerFileName = fileName.toLowerCase()

  if ((lowerFileName === 'best.pt' || lowerFileName === 'last.pt') && parentName) {
    return parentName.toLowerCase() === 'weights' && grandParentName ? grandParentName : parentName
  }

  return fileName
}

// 加载AI模型配置状态
const loadAiConfigStatus = async () => {
  try {
    let status = null

    const configRes = await fetchAiConfig()
    if (configRes.code === '200' && configRes.data) {
      status = {
        deepseek: !!configRes.data.deepseek?.configured,
        glm: !!configRes.data.glm?.configured,
        kimi: !!configRes.data.kimi?.configured
      }
    } else {
      const res = await request.get('/detect/aiConfigStatus')
      if (res.code === '200') {
        status = res.data
      }
    }

    if (status) {
      aiModelOptions.value = [
        { label: AI_MODEL_LABELS.deepseek, value: 'deepseek', configured: status.deepseek },
        { label: AI_MODEL_LABELS.glm, value: 'glm', configured: status.glm },
        { label: AI_MODEL_LABELS.kimi, value: 'kimi', configured: status.kimi }
      ]
      // 自动选择第一个已配置的模型
      const firstConfigured = aiModelOptions.value.find(m => m.configured)
      if (firstConfigured) {
        selectedModel.value = firstConfigured.value
      }
    }
  } catch (error) {
    console.error('加载AI配置状态失败', error)
  }
}

// 加载可用模型列表
const loadModels = async () => {
  modelLoading.value = true
  try {
    const res = await fetchModels('detect')
    if (res.code === '200') {
      modelOptions.value = formatModelOptions(res.data)
      if (modelOptions.value.length > 0 && !modelPath.value) {
        modelPath.value = modelOptions.value[0].value
      }
    } else {
      ElMessage.warning('加载模型列表失败: ' + res.msg)
    }
  } catch (error) {
    console.error('加载模型列表失败', error)
    ElMessage.error('无法加载模型列表，请检查后端服务')
  } finally {
    modelLoading.value = false
  }
}

const applyConfig = async () => {
  confThreshold.value = await getThreshold('detect', 0.45)
}

// ==================== 生命周期 ====================

onMounted(() => {
  loadRecordFromRoute(route.query.recordId)
  applyConfig()
  loadModels()
  loadAiConfigStatus()
  loadFeishuTokenConfig()
  removeFeishuTokenListener = onFeishuUserTokenChange((token) => {
    feishuUserToken.value = token
    if (!token) {
      feishuConfigExpanded.value = true
    }
  })
  removeConfigListener = onConfigUpdated(({ thresholds }) => {
    if (thresholds?.detect !== undefined) {
      confThreshold.value = Number(thresholds.detect)
    }
  })
})

onUnmounted(() => {
  clearAiProgressTimer()
  removeFeishuTokenListener?.()
  removeConfigListener?.()
})

onActivated(() => {
  const recordId = route.query.recordId
  if (isWorkPageAutoRefreshEnabled() || recordId !== lastRouteRecordId) {
    loadRecordFromRoute(recordId)
    applyConfig()
    loadModels()
    loadAiConfigStatus()
  }
})

watch(
  () => route.query.recordId,
  (recordId) => loadRecordFromRoute(recordId)
)
</script>

<style scoped>
.detect-container {
  padding: 20px;
  min-height: calc(100vh - 60px);
  background: var(--app-bg);
}

.page-header {
  margin-bottom: 18px;
  padding: 20px 24px;
  background: var(--app-surface);
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius);
  box-shadow: var(--app-shadow);
  text-align: left;
}

.page-header h2 {
  margin: 0 0 8px 0;
  color: var(--app-text);
  font-size: 22px;
  font-weight: 700;
}

.subtitle {
  color: var(--app-text-muted);
  margin: 0;
  font-size: 14px;
}

.operation-area {
  --operation-gap: 8px;
  --upload-control-width: 136px;
  --model-control-width: 210px;
  --threshold-control-width: 120px;
  --primary-action-width: 112px;
  --feishu-config-width: calc(var(--upload-control-width) + var(--model-control-width) + var(--threshold-control-width) + var(--operation-gap) * 2);
  display: flex;
  flex-direction: column;
  align-items: stretch;
  margin-bottom: 18px;
  padding: 16px;
  background: var(--app-surface);
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius);
  box-shadow: var(--app-shadow);
  gap: 12px;
}

.operation-row,
.feishu-row {
  display: flex;
  align-items: center;
  gap: var(--operation-gap);
  width: 100%;
  overflow-x: auto;
}

.feishu-row {
  align-items: flex-start;
}

.model-select {
  width: var(--model-control-width);
  flex: 0 0 var(--model-control-width);
}

.threshold-input {
  width: var(--threshold-control-width);
  flex: 0 0 var(--threshold-control-width);
}

.ai-select {
  width: 180px;
  flex: 0 0 180px;
  margin-left: 0;
}

.ai-select :deep(.el-select__prefix) {
  display: flex;
  align-items: center;
}

.control-button {
  flex: 0 0 auto;
  margin-left: 0;
}

.upload-component {
  flex: 0 0 var(--upload-control-width);
  width: var(--upload-control-width);
}

.upload-component :deep(.el-button) {
  width: 100%;
}

.primary-action-button {
  flex-basis: var(--primary-action-width);
  width: var(--primary-action-width);
}

.feishu-config {
  flex: 0 0 var(--feishu-config-width);
  width: var(--feishu-config-width);
  padding: 12px;
  background: var(--app-surface-soft);
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius);
  cursor: pointer;
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}

.feishu-config:hover {
  border-color: var(--app-primary);
  box-shadow: 0 0 0 2px rgba(37, 111, 127, 0.08);
}

.feishu-config.is-collapsed {
  height: 32px;
  min-height: 32px;
  padding-top: 0;
  padding-bottom: 0;
  display: flex;
  align-items: center;
}

.feishu-config-header,
.feishu-config-title,
.feishu-config-actions,
.feishu-config-body {
  display: flex;
  align-items: center;
  gap: 8px;
}

.feishu-config-header {
  justify-content: space-between;
  min-height: 32px;
  width: 100%;
}

.feishu-config-title {
  color: var(--app-text);
  font-weight: 700;
  white-space: nowrap;
}

.feishu-config-actions {
  color: var(--app-text-muted);
  min-width: 0;
  flex: 1 1 auto;
  justify-content: flex-end;
}

.feishu-token-preview {
  max-width: 118px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12px;
}

.feishu-config-body {
  margin-top: 12px;
  cursor: default;
  flex-wrap: wrap;
}

.feishu-token-input {
  flex: 1 0 100%;
  min-width: 0;
}

.feishu-upload-button {
  flex: 0 0 var(--primary-action-width);
  width: var(--primary-action-width);
  min-width: 0;
}

.ai-option-label {
  float: left;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.ai-option-tag {
  float: right;
  margin-left: 10px;
}

.ai-model-icon {
  width: 20px;
  height: 20px;
  border-radius: 4px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 20px;
  overflow: hidden;
  color: #ffffff;
  font-size: 9px;
  font-weight: 700;
  line-height: 1;
}

.ai-model-icon img {
  width: 88%;
  height: 88%;
  object-fit: contain;
  display: block;
}

.ai-model-icon--deepseek {
  background: #ffffff;
  border: 1px solid #d8e2e7;
}

.ai-model-icon--kimi {
  background: #5b5fc7;
}

.ai-model-icon--glm {
  background: #2f8f5b;
}

.ai-model-icon--doubao {
  background: #c2413b;
}

.ai-progress-panel {
  margin-bottom: 18px;
  padding: 16px 18px;
  background: #fff8ec;
  border: 1px solid #e6c98d;
  border-radius: var(--app-radius);
  box-shadow: var(--app-shadow);
}

.ai-progress-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
  color: #7a4b12;
  font-weight: 600;
}

.ai-progress-header strong {
  color: var(--app-primary);
  font-size: 16px;
}

.image-compare-area {
  margin-bottom: 18px;
}

.image-card {
  background: var(--app-surface);
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius);
  box-shadow: var(--app-shadow);
  overflow: hidden;
  height: 100%;
}

.image-title {
  padding: 15px;
  background: var(--app-surface-soft);
  color: var(--app-text);
  font-weight: 700;
  border-bottom: 1px solid var(--app-border);
  display: flex;
  align-items: center;
}

.title-status {
  margin-left: 10px;
}

.image-wrapper {
  height: 400px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fbfdfc;
  padding: 20px;
}

.empty-detect-state {
  text-align: center;
}

.running-tip {
  color: var(--app-primary);
  margin-top: 10px;
}

.medical-image {
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
}

.image-info {
  padding: 10px 15px;
  font-size: 12px;
  color: var(--app-text-muted);
  background: var(--app-surface-soft);
}

.image-info span {
  margin-right: 20px;
}

.detection-summary {
  padding: 15px;
  border-top: 1px solid var(--app-border);
}

.tumor-type-text {
  display: inline-block;
  color: #c2413b;
  font-weight: 700;
  line-height: 1.4;
}

.ai-analysis-area {
  margin-bottom: 18px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.analysis-time {
  color: var(--app-text-muted);
  font-size: 12px;
}

.analysis-content {
  max-height: 400px;
  overflow-y: auto;
}

.analysis-text {
  margin: 0;
  font-family: inherit;
  line-height: 1.8;
  color: var(--app-text);
  font-size: 14px;
}

.analysis-text p {
  min-height: 1.8em;
  margin: 0;
  white-space: pre-wrap;
  overflow-wrap: break-word;
}

.analysis-disclaimer {
  color: #c2413b;
  font-weight: 700;
}

.analysis-section-title {
  font-weight: 700;
}

:deep(.el-button .el-icon) {
  margin-right: 5px;
}

:deep(.el-card__header) {
  background: var(--app-surface-soft);
  border-bottom-color: var(--app-border);
}

:deep(.el-card__body) {
  background: var(--app-surface);
}

@media (max-width: 900px) {
  .detect-container {
    padding: 14px;
  }

  :deep(.el-col) {
    max-width: 100%;
    flex: 0 0 100%;
  }

  .operation-row,
  .feishu-row,
  .feishu-config-body {
    flex-wrap: wrap;
    overflow-x: visible;
  }

  .feishu-config {
    flex-basis: 100%;
    width: 100%;
    min-width: 100%;
  }

  .feishu-upload-button {
    flex-basis: 100%;
    width: 100%;
  }
}
</style>
