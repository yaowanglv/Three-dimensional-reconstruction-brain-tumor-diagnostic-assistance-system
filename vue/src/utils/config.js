import request from './request'
export {
  cacheGlobalConfig,
  CONFIG_SYNC_EVENT,
  DEFAULT_GLOBAL_CONFIG,
  DEFAULT_MANAGER_MENU_TITLES,
  DEFAULT_MANAGER_ROUTES,
  DEFAULT_THREEDIM_LABEL_NAMES,
  MANAGER_ROUTE_PATHS,
  getBackendBaseUrl,
  getCachedGlobalConfig,
  getDefaultManagerPath,
  getManagerMenuTitles,
  getManagerRouteConfig,
  getManagerRouteKeyByPath,
  getManagerRouteTitle,
  getVisibleManagerRouteKeys,
  hasCustomManagerLogo,
  isManagerRouteVisible,
  isWorkPageAutoRefreshEnabled,
  normalizeGlobalConfig
} from './runtimeConfig'
import { cacheGlobalConfig, CONFIG_SYNC_EVENT, getCachedGlobalConfig } from './runtimeConfig'

const THRESHOLDS_STORAGE_KEY = 'app_thresholds'
const EMPTY_FAVICON = 'data:image/svg+xml,%3Csvg xmlns=%22http://www.w3.org/2000/svg%22/%3E'
const ICON_LINK_SELECTOR = [
  'link[rel~="icon"]',
  'link[rel="shortcut icon"]',
  'link[rel="apple-touch-icon"]',
  'link[rel="mask-icon"]'
].join(',')

export function fetchModels(pageType) {
  return request.get('/api/config/models', { params: { pageType } })
}

export function addModelFolder(pageType, folderPath, includeLastPt = false) {
  return request.post('/api/config/models/folder', { pageType, folderPath, includeLastPt })
}

export function removeModel(id) {
  return request.delete(`/api/config/models/${id}`)
}

export function updateModelDisplayName(id, displayName) {
  return request.put(`/api/config/models/${id}/display-name`, { displayName })
}

export function rescanModels(pageType, folderPath, includeLastPt = false) {
  return request.post('/api/config/models/scan', { pageType, folderPath, includeLastPt })
}

export function browseFileSystem({ path = '', directoriesOnly = true, imageFilesOnly = false } = {}) {
  return request.get('/api/config/fs/browse', {
    params: { path, directoriesOnly, imageFilesOnly }
  })
}

export function getManagerLogoUrl() {
  const baseUrl = getCachedGlobalConfig().backendUrl || ''
  return `${baseUrl}/api/config/logo`
}

export function syncDocumentBranding(globalConfig = getCachedGlobalConfig()) {
  if (typeof document === 'undefined') return

  const tabLogoVisible = globalConfig?.tabLogoVisible !== false
  const hasLogoPath = !!String(globalConfig?.managerLogoPath || '').trim()
  const favicon = document.createElement('link')

  document.querySelectorAll(ICON_LINK_SELECTOR).forEach(link => link.remove())

  favicon.setAttribute('rel', 'icon')
  favicon.setAttribute('type', tabLogoVisible && hasLogoPath ? 'image/png' : 'image/svg+xml')
  favicon.setAttribute('href', tabLogoVisible && hasLogoPath
    ? `${getManagerLogoUrl()}?t=${encodeURIComponent(globalConfig.managerLogoPath)}&v=${Date.now()}`
    : EMPTY_FAVICON
  )
  document.head.appendChild(favicon)
}

export async function fetchBrandingConfig() {
  return request.get('/api/config/branding')
}

export function cacheThresholds(thresholds = {}) {
  localStorage.setItem(THRESHOLDS_STORAGE_KEY, JSON.stringify(thresholds))
  window.dispatchEvent(new CustomEvent(CONFIG_SYNC_EVENT, {
    detail: { thresholds, globalConfig: getCachedGlobalConfig() }
  }))
}

export function getCachedThresholds() {
  try {
    return JSON.parse(localStorage.getItem(THRESHOLDS_STORAGE_KEY) || '{}')
  } catch {
    return {}
  }
}

export function onConfigUpdated(callback) {
  const eventHandler = (event) => callback(event.detail || {})
  const storageHandler = (event) => {
    if (!event || event.key === THRESHOLDS_STORAGE_KEY || event.key === 'app_global_config') {
      callback({
        thresholds: getCachedThresholds(),
        globalConfig: getCachedGlobalConfig()
      })
    }
  }
  window.addEventListener(CONFIG_SYNC_EVENT, eventHandler)
  window.addEventListener('storage', storageHandler)
  return () => {
    window.removeEventListener(CONFIG_SYNC_EVENT, eventHandler)
    window.removeEventListener('storage', storageHandler)
  }
}

export async function fetchThresholds() {
  const res = await request.get('/api/config/thresholds')
  if (res.code === '200' && res.data) {
    cacheThresholds(res.data)
  }
  return res
}

export async function updateThresholds(thresholds) {
  const res = await request.put('/api/config/thresholds', thresholds)
  if (res.code === '200') {
    cacheThresholds(res.data || thresholds)
  }
  return res
}

export async function getThreshold(pageType, defaultValue = 0.45) {
  try {
    const res = await fetchThresholds()
    if (res.code === '200' && res.data) {
      return Number(res.data[pageType] ?? defaultValue)
    }
  } catch (error) {
    console.warn('获取阈值失败，使用默认值', error)
  }
  const cached = getCachedThresholds()
  return Number(cached[pageType] ?? defaultValue)
}

export async function fetchGlobalConfig() {
  const res = await request.get('/api/config/global')
  if (res.code === '200' && res.data) {
    cacheGlobalConfig(res.data)
    syncDocumentBranding(getCachedGlobalConfig())
  }
  return res
}

export async function updateGlobalConfig(config) {
  const res = await request.put('/api/config/global', config)
  if (res.code === '200') {
    cacheGlobalConfig({ ...config, ...(res.data || {}) })
    syncDocumentBranding(getCachedGlobalConfig())
  }
  return res
}

export function fetchAiConfig() {
  return request.get('/api/config/ai')
}

export function updateAiConfig(config) {
  return request.put('/api/config/ai', config)
}

export function formatModelOptions(models) {
  if (!Array.isArray(models)) {
    return []
  }
  return models.map(model => ({
    label: model.displayName || model.modelName,
    originalLabel: model.modelName,
    value: model.modelPath,
    ...model
  }))
}
