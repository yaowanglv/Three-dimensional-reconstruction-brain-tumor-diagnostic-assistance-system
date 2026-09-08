<template>
  <div class="config-container">
    <div class="page-header">
      <h2>系统配置</h2>
      <p class="subtitle">统一管理模型路径、阈值、服务地址和大模型密钥。</p>
    </div>

    <section class="config-band" v-loading="loading">
      <el-tabs v-model="activeTab" type="border-card">
        <el-tab-pane label="快速诊断配置" name="detect">
          <div class="tab-pane-body">
            <ModelConfigPanel
              page-type="detect"
              title="快速诊断模型文件夹"
              :models="models.detect"
              @refresh="loadModels"
            />
            <ThresholdConfigPanel v-model="thresholds.detect" label="快速诊断默认置信度阈值" />
          </div>
        </el-tab-pane>

        <el-tab-pane label="精准分割配置" name="mask">
          <div class="tab-pane-body">
            <ModelConfigPanel
              page-type="mask"
              title="精准分割模型文件夹"
              :models="models.mask"
              @refresh="loadModels"
            />
            <ThresholdConfigPanel v-model="thresholds.mask" label="精准分割默认置信度阈值" />
          </div>
        </el-tab-pane>

        <el-tab-pane label="批量分割配置" name="multi">
          <div class="tab-pane-body">
            <ModelConfigPanel
              page-type="multi"
              title="批量分割模型文件夹"
              :models="models.multi"
              @refresh="loadModels"
            />
            <ThresholdConfigPanel v-model="thresholds.multi" label="批量分割默认置信度阈值" />
          </div>
        </el-tab-pane>

        <el-tab-pane label="全局设置" name="global">
          <div class="tab-pane-body">
            <GlobalConfigPanel v-model="globalConfig" />
          </div>
        </el-tab-pane>

        <el-tab-pane label="AI 密钥配置" name="ai">
          <div class="tab-pane-body">
            <section class="ai-config-section">
              <div class="section-heading">
                <h3>大模型 API Key</h3>
                <p>默认读取后端 `application.yml` 中的密钥。这里填写后会覆盖默认值；点击“恢复默认”会移除覆盖值并继续使用默认密钥。</p>
              </div>

              <div
                v-for="item in aiModelCards"
                :key="item.key"
                class="ai-config-card"
              >
                <div class="ai-config-card__header">
                  <div>
                    <h4>{{ item.title }}</h4>
                    <div class="ai-config-status">
                      <el-tag :type="getAiStatusTagType(item.key)" size="small">
                        {{ getAiStatusText(item.key) }}
                      </el-tag>
                      <el-tag v-if="aiRestoreFlags[item.key]" type="warning" size="small">
                        待恢复默认
                      </el-tag>
                    </div>
                  </div>
                  <el-button @click="restoreAiDefault(item.key)">
                    恢复默认
                  </el-button>
                </div>

                <el-input
                  v-model="aiDraft[item.field]"
                  type="password"
                  show-password
                  clearable
                  :placeholder="item.placeholder"
                  @input="aiRestoreFlags[item.key] = false"
                />

                <p class="ai-config-hint">{{ getAiHintText(item.key) }}</p>
              </div>
            </section>
          </div>
        </el-tab-pane>
      </el-tabs>
    </section>

    <div class="action-bar">
      <el-button @click="handleReset">重置为默认</el-button>
      <el-button type="primary" :loading="saving" @click="handleSave">
        <el-icon><Check /></el-icon>
        保存配置
      </el-button>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Check } from '@element-plus/icons-vue'
import {
  DEFAULT_GLOBAL_CONFIG,
  fetchAiConfig,
  fetchGlobalConfig,
  fetchModels,
  fetchThresholds,
  normalizeGlobalConfig,
  updateAiConfig,
  updateGlobalConfig,
  updateThresholds
} from '@/utils/config'
import ModelConfigPanel from './components/ModelConfigPanel.vue'
import ThresholdConfigPanel from './components/ThresholdConfigPanel.vue'
import GlobalConfigPanel from './components/GlobalConfigPanel.vue'

const defaultThresholds = {
  detect: 0.45,
  mask: 0.45,
  multi: 0.25
}

const activeTab = ref('detect')
const loading = ref(false)
const saving = ref(false)

const models = reactive({
  detect: [],
  mask: [],
  multi: []
})

const thresholds = reactive({ ...defaultThresholds })
const globalConfig = reactive(normalizeGlobalConfig(DEFAULT_GLOBAL_CONFIG))
const aiConfig = reactive({
  deepseek: { configured: false, hasCustom: false, hasDefault: false, usingCustom: false, usingDefault: false },
  glm: { configured: false, hasCustom: false, hasDefault: false, usingCustom: false, usingDefault: false },
  kimi: { configured: false, hasCustom: false, hasDefault: false, usingCustom: false, usingDefault: false }
})
const aiDraft = reactive({
  deepseekApiKey: '',
  glmApiKey: '',
  kimiApiKey: ''
})
const aiRestoreFlags = reactive({
  deepseek: false,
  glm: false,
  kimi: false
})

const aiModelCards = [
  { key: 'deepseek', field: 'deepseekApiKey', title: 'DeepSeek API Key', placeholder: '输入新的 DeepSeek API Key 作为覆盖值' },
  { key: 'glm', field: 'glmApiKey', title: 'GLM（智谱）API Key', placeholder: '输入新的 GLM API Key 作为覆盖值' },
  { key: 'kimi', field: 'kimiApiKey', title: 'Kimi API Key', placeholder: '输入新的 Kimi API Key 作为覆盖值' }
]

const loadModels = async (pageType) => {
  const pages = pageType ? [pageType] : ['detect', 'mask', 'multi']
  await Promise.all(pages.map(async page => {
    const res = await fetchModels(page)
    if (res.code === '200') {
      models[page] = res.data || []
    }
  }))
}

const loadThresholds = async () => {
  const res = await fetchThresholds()
  if (res.code === '200' && res.data) {
    Object.assign(thresholds, {
      detect: Number(res.data.detect ?? defaultThresholds.detect),
      mask: Number(res.data.mask ?? defaultThresholds.mask),
      multi: Number(res.data.multi ?? defaultThresholds.multi)
    })
  }
}

const loadGlobalConfig = async () => {
  const res = await fetchGlobalConfig()
  if (res.code === '200' && res.data) {
    Object.assign(globalConfig, normalizeGlobalConfig(res.data))
  }
}

const resetAiDrafts = () => {
  aiDraft.deepseekApiKey = ''
  aiDraft.glmApiKey = ''
  aiDraft.kimiApiKey = ''
}

const resetAiRestoreFlags = () => {
  aiRestoreFlags.deepseek = false
  aiRestoreFlags.glm = false
  aiRestoreFlags.kimi = false
}

const loadAiConfigState = async () => {
  const res = await fetchAiConfig()
  if (res.code === '200' && res.data) {
    Object.assign(aiConfig.deepseek, res.data.deepseek || {})
    Object.assign(aiConfig.glm, res.data.glm || {})
    Object.assign(aiConfig.kimi, res.data.kimi || {})
  }
  resetAiDrafts()
  resetAiRestoreFlags()
}

const loadConfig = async () => {
  loading.value = true
  try {
    await Promise.all([loadModels(), loadThresholds(), loadGlobalConfig(), loadAiConfigState()])
  } catch (error) {
    ElMessage.error(error.message || '加载配置失败')
  } finally {
    loading.value = false
  }
}

const restoreAiDefault = (model) => {
  aiRestoreFlags[model] = true
  aiDraft[`${model}ApiKey`] = ''
}

const getAiStatusText = (model) => {
  const status = aiConfig[model]
  if (aiRestoreFlags[model]) return '保存后恢复默认'
  if (status.usingCustom) return '当前使用覆盖密钥'
  if (status.usingDefault) return '当前使用默认密钥'
  if (status.hasDefault) return '已配置默认密钥'
  return '未配置密钥'
}

const getAiStatusTagType = (model) => {
  const status = aiConfig[model]
  if (aiRestoreFlags[model]) return 'warning'
  return status.configured ? 'success' : 'info'
}

const getAiHintText = (model) => {
  const status = aiConfig[model]
  if (aiRestoreFlags[model]) {
    return '本次保存会删除覆盖值并回退到默认密钥。'
  }
  if (status.usingCustom) {
    return '当前已存在覆盖密钥。输入新值后会替换现有覆盖值。'
  }
  if (status.usingDefault) {
    return '当前正在使用默认密钥。输入新值后会改为使用覆盖密钥。'
  }
  if (status.hasDefault) {
    return '当前可直接使用默认密钥；如果需要单独配置，可在这里填写覆盖值。'
  }
  return '当前没有可用密钥，请填写覆盖值，或在后端默认配置中补充密钥。'
}

const buildAiPayload = () => {
  const restoreDefaultModels = Object.entries(aiRestoreFlags)
    .filter(([, value]) => value)
    .map(([key]) => key)

  return {
    deepseekApiKey: aiDraft.deepseekApiKey.trim() || undefined,
    glmApiKey: aiDraft.glmApiKey.trim() || undefined,
    kimiApiKey: aiDraft.kimiApiKey.trim() || undefined,
    restoreDefaultModels
  }
}

const hasAiChanges = computed(() => {
  return Boolean(
    aiDraft.deepseekApiKey.trim() ||
    aiDraft.glmApiKey.trim() ||
    aiDraft.kimiApiKey.trim() ||
    aiRestoreFlags.deepseek ||
    aiRestoreFlags.glm ||
    aiRestoreFlags.kimi
  )
})

const handleSave = async () => {
  saving.value = true
  try {
    const thresholdRes = await updateThresholds({ ...thresholds })
    if (thresholdRes.code !== '200') {
      throw new Error(thresholdRes.msg || '保存阈值失败')
    }

    const globalRes = await updateGlobalConfig(JSON.parse(JSON.stringify(globalConfig)))
    if (globalRes.code !== '200') {
      throw new Error(globalRes.msg || '保存全局配置失败')
    }

    if (hasAiChanges.value) {
      const aiRes = await updateAiConfig(buildAiPayload())
      if (aiRes.code !== '200') {
        throw new Error(aiRes.msg || '保存 AI 密钥配置失败')
      }
    }

    ElMessage.success('配置已保存')
    await Promise.all([loadThresholds(), loadGlobalConfig(), loadAiConfigState()])
  } catch (error) {
    ElMessage.error(error.message || '保存配置失败')
  } finally {
    saving.value = false
  }
}

const handleReset = async () => {
  try {
    await ElMessageBox.confirm('确定将阈值、全局设置以及 AI 覆盖密钥恢复为默认吗？', '确认重置', { type: 'warning' })
    Object.assign(thresholds, defaultThresholds)
    Object.assign(globalConfig, normalizeGlobalConfig(DEFAULT_GLOBAL_CONFIG))
    resetAiDrafts()
    aiRestoreFlags.deepseek = true
    aiRestoreFlags.glm = true
    aiRestoreFlags.kimi = true
    await handleSave()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error.message || '重置失败')
    }
  }
}

onMounted(loadConfig)
</script>

<style scoped>
.config-container {
  min-height: calc(100vh - 64px);
  padding: 20px;
  background: var(--app-bg);
}

.page-header,
.config-band {
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
  color: var(--app-text);
  font-size: 22px;
  font-weight: 700;
}

.subtitle {
  margin: 0;
  color: var(--app-text-muted);
}

.config-band {
  padding: 18px;
}

.tab-pane-body {
  display: flex;
  flex-direction: column;
  gap: 18px;
  padding: 4px;
}

.ai-config-section {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.section-heading h3 {
  margin: 0 0 8px;
  font-size: 18px;
  color: var(--app-text);
}

.section-heading p {
  margin: 0;
  color: var(--app-text-muted);
  line-height: 1.6;
}

.ai-config-card {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px;
  border: 1px solid var(--app-border);
  border-radius: 10px;
  background: var(--app-surface-soft);
}

.ai-config-card__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.ai-config-card__header h4 {
  margin: 0 0 8px;
  color: var(--app-text);
  font-size: 16px;
}

.ai-config-status {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.ai-config-hint {
  margin: 0;
  color: var(--app-text-muted);
  font-size: 13px;
  line-height: 1.6;
}

.action-bar {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

:deep(.el-tabs--border-card) {
  border-color: var(--app-border);
  box-shadow: none;
}

:deep(.el-tabs--border-card > .el-tabs__header) {
  background: var(--app-surface-soft);
  border-bottom-color: var(--app-border);
}

@media (max-width: 780px) {
  .config-container {
    padding: 14px;
  }

  .ai-config-card__header,
  .action-bar {
    flex-direction: column;
  }

  .action-bar .el-button {
    width: 100%;
  }
}
</style>
