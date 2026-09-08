const GLOBAL_CONFIG_STORAGE_KEY = 'app_global_config'
export const CONFIG_SYNC_EVENT = 'app-config-updated'

export const DEFAULT_THREEDIM_LABEL_NAMES = {
  1: '坏死肿瘤核心',
  2: '瘤周水肿',
  3: '增强肿瘤'
}

export const MANAGER_ROUTE_PATHS = {
  admin: '/manager/admin',
  dataview: '/manager/dataview',
  history: '/manager/history',
  config: '/manager/config',
  detect: '/manager/detect',
  mask: '/manager/mask',
  multi: '/manager/multi',
  threedim: '/manager/threedim'
}

export const DEFAULT_MANAGER_MENU_TITLES = {
  user: '用户管理',
  data: '数据管理',
  diagnosis: '脑肿瘤智能辅助诊断与分析'
}

export const DEFAULT_MANAGER_ROUTES = {
  admin: { title: '管理员信息', visible: true },
  dataview: { title: '数据可视化', visible: true },
  history: { title: '检测历史', visible: true },
  config: { title: '系统配置', visible: true },
  detect: { title: '快速辅助诊断与分析', visible: true },
  mask: { title: '精准辅助分割与分析', visible: true },
  multi: { title: '批量图像分割', visible: true },
  threedim: { title: '3D脑肿瘤可视化', visible: true }
}

export const DEFAULT_GLOBAL_CONFIG = {
  backendUrl: 'http://localhost:9527',
  pythonDetectUrl: 'http://localhost:5001',
  pythonSegmentUrl: 'http://localhost:3408',
  pythonYoloSegmentUrl: 'http://localhost:5001',
  pythonUnetSegmentUrl: 'http://localhost:3408',
  defaultMaskSegmentService: 'unet',
  defaultLoginRouteKey: 'dataview',
  autoRefreshWorkPages: false,
  loginPageTitle: '脑诊智析',
  loginBrandTitle: '大模型驱动的脑肿瘤智能辅助诊断与分析系统',
  loginBrandSubtitle: '脑诊智析',
  managerLogoVisible: true,
  tabLogoVisible: true,
  threedimLabelNames: { ...DEFAULT_THREEDIM_LABEL_NAMES },
  managerBrandTitle: '脑诊智析——大模型驱动的脑肿瘤智能辅助诊断与分析系统',
  managerLogoPath: 'D:/YDS/code/vue/src/assets/imgs/lyw.png',
  multiFavorites: {
    input: [],
    output: []
  },
  threedimFavorites: [],
  managerMenuTitles: { ...DEFAULT_MANAGER_MENU_TITLES },
  managerRoutes: JSON.parse(JSON.stringify(DEFAULT_MANAGER_ROUTES))
}

export function normalizeGlobalConfig(config = {}) {
  const managerRoutes = { ...DEFAULT_MANAGER_ROUTES }
  Object.entries(config.managerRoutes || {}).forEach(([key, value]) => {
    managerRoutes[key] = {
      ...(DEFAULT_MANAGER_ROUTES[key] || { title: key, visible: true }),
      ...(value || {})
    }
  })

  const defaultLoginRouteKey = Object.prototype.hasOwnProperty.call(MANAGER_ROUTE_PATHS, config.defaultLoginRouteKey)
    ? config.defaultLoginRouteKey
    : DEFAULT_GLOBAL_CONFIG.defaultLoginRouteKey

  return {
    ...DEFAULT_GLOBAL_CONFIG,
    ...config,
    defaultLoginRouteKey,
    threedimLabelNames: {
      ...DEFAULT_THREEDIM_LABEL_NAMES,
      ...(config.threedimLabelNames || {})
    },
    managerMenuTitles: {
      ...DEFAULT_MANAGER_MENU_TITLES,
      ...(config.managerMenuTitles || {})
    },
    multiFavorites: {
      input: Array.isArray(config.multiFavorites?.input) ? config.multiFavorites.input : [],
      output: Array.isArray(config.multiFavorites?.output) ? config.multiFavorites.output : []
    },
    threedimFavorites: Array.isArray(config.threedimFavorites) ? config.threedimFavorites : [],
    managerRoutes
  }
}

export function getCachedGlobalConfig() {
  try {
    return normalizeGlobalConfig(JSON.parse(localStorage.getItem(GLOBAL_CONFIG_STORAGE_KEY) || '{}'))
  } catch {
    return normalizeGlobalConfig()
  }
}

export function cacheGlobalConfig(config = {}) {
  const nextConfig = normalizeGlobalConfig(config)
  localStorage.setItem(GLOBAL_CONFIG_STORAGE_KEY, JSON.stringify(nextConfig))
  window.dispatchEvent(new CustomEvent(CONFIG_SYNC_EVENT, {
    detail: { globalConfig: nextConfig }
  }))
}

export function getBackendBaseUrl() {
  return getCachedGlobalConfig().backendUrl || DEFAULT_GLOBAL_CONFIG.backendUrl
}

export function isWorkPageAutoRefreshEnabled() {
  const value = getCachedGlobalConfig().autoRefreshWorkPages
  return value === true || value === 'true' || value === '1'
}

export function getManagerRouteKeyByPath(path = '') {
  return Object.entries(MANAGER_ROUTE_PATHS).find(([, routePath]) => routePath === path)?.[0] || ''
}

export function getManagerRouteConfig(routeKey, config = getCachedGlobalConfig()) {
  return config.managerRoutes?.[routeKey] || DEFAULT_MANAGER_ROUTES[routeKey] || { title: routeKey, visible: true }
}

export function getManagerRouteTitle(routeKeyOrPath, config = getCachedGlobalConfig()) {
  const routeKey = routeKeyOrPath?.startsWith?.('/manager/')
    ? getManagerRouteKeyByPath(routeKeyOrPath)
    : routeKeyOrPath
  return getManagerRouteConfig(routeKey, config).title || DEFAULT_MANAGER_ROUTES[routeKey]?.title || '工作台'
}

export function isManagerRouteVisible(routeKey, config = getCachedGlobalConfig()) {
  return getManagerRouteConfig(routeKey, config).visible !== false
}

export function getVisibleManagerRouteKeys(role = '', config = getCachedGlobalConfig()) {
  const normalizedRole = String(role || '').toLowerCase()
  const candidates = normalizedRole === 'admin'
    ? ['admin', 'dataview', 'history', 'config', 'detect', 'mask', 'multi', 'threedim']
    : ['dataview', 'history', 'detect', 'mask', 'multi', 'threedim']
  return candidates.filter(routeKey => isManagerRouteVisible(routeKey, config))
}

export function getDefaultManagerPath(role = '', config = getCachedGlobalConfig()) {
  const visibleKeys = getVisibleManagerRouteKeys(role, config)
  const configuredKey = String(config?.defaultLoginRouteKey || '').trim()
  const fallbackKey = visibleKeys[0] || (String(role || '').toLowerCase() === 'admin' ? 'admin' : 'dataview')
  const targetKey = visibleKeys.includes(configuredKey) ? configuredKey : fallbackKey
  return MANAGER_ROUTE_PATHS[targetKey] || MANAGER_ROUTE_PATHS.dataview
}

export function getManagerMenuTitles(config = getCachedGlobalConfig()) {
  return config.managerMenuTitles || { ...DEFAULT_MANAGER_MENU_TITLES }
}

export function hasCustomManagerLogo(config = getCachedGlobalConfig()) {
  return !!String(config.managerLogoPath || '').trim()
}
