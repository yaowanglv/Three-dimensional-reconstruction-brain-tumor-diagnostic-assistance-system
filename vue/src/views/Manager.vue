<template>
  <div class="manager-layout">
    <header class="manager-header">
      <div class="brand-panel">
        <img
          v-if="logoEnabled"
          class="brand-logo"
          :src="logoUrl"
          alt="系统 Logo"
          @error="handleLogoError"
        >
        <div class="brand-copy">
          <span class="brand-title">{{ brandTitle }}</span>
        </div>
      </div>

      <div class="topbar">
        <div class="breadcrumb">
          <button class="breadcrumb-home" type="button" @click="goHome">首页</button>
          <span class="breadcrumb-separator">/</span>
          <span>{{ currentPageTitle }}</span>
        </div>

        <el-dropdown>
          <div class="user-entry">
            <img class="user-avatar" src="@/assets/imgs/头像.png" alt="">
            <span>{{ currentUser?.name || currentUser?.username || '用户' }}</span>
          </div>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item @click="openPasswordDialog">修改密码</el-dropdown-item>
              <el-dropdown-item divided @click="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </header>

    <div class="manager-body">
      <aside class="manager-sidebar">
        <el-menu
          router
          class="manager-menu"
          :default-openeds="defaultOpeneds"
          :default-active="router.currentRoute.value.path"
        >
          <el-sub-menu v-if="showUserMenu" index="user">
            <template #title>
              <el-icon><User /></el-icon>
              <span>{{ menuTitles.user }}</span>
            </template>
            <el-menu-item v-if="visibleRoutes.admin" :index="MANAGER_ROUTE_PATHS.admin">
              {{ routeTitles.admin }}
            </el-menu-item>
          </el-sub-menu>

          <el-sub-menu v-if="showDataMenu" index="data">
            <template #title>
              <el-icon><TrendCharts /></el-icon>
              <span>{{ menuTitles.data }}</span>
            </template>
            <el-menu-item v-if="visibleRoutes.dataview" :index="MANAGER_ROUTE_PATHS.dataview">
              {{ routeTitles.dataview }}
            </el-menu-item>
            <el-menu-item v-if="visibleRoutes.history" :index="MANAGER_ROUTE_PATHS.history">
              {{ routeTitles.history }}
            </el-menu-item>
            <el-menu-item v-if="isAdmin && visibleRoutes.config" :index="MANAGER_ROUTE_PATHS.config">
              {{ routeTitles.config }}
            </el-menu-item>
          </el-sub-menu>

          <el-sub-menu v-if="showDiagnosisMenu" index="diagnosis">
            <template #title>
              <el-icon><FirstAidKit /></el-icon>
              <span>{{ menuTitles.diagnosis }}</span>
            </template>
            <el-menu-item v-if="visibleRoutes.detect" :index="MANAGER_ROUTE_PATHS.detect">
              {{ routeTitles.detect }}
            </el-menu-item>
            <el-menu-item v-if="visibleRoutes.mask" :index="MANAGER_ROUTE_PATHS.mask">
              {{ routeTitles.mask }}
            </el-menu-item>
            <el-menu-item v-if="visibleRoutes.multi" :index="MANAGER_ROUTE_PATHS.multi">
              {{ routeTitles.multi }}
            </el-menu-item>
            <el-menu-item v-if="visibleRoutes.threedim" :index="MANAGER_ROUTE_PATHS.threedim">
              {{ routeTitles.threedim }}
            </el-menu-item>
          </el-sub-menu>
        </el-menu>
      </aside>

      <main class="manager-content">
        <RouterView v-slot="{ Component }">
          <KeepAlive :include="cachedWorkPages">
            <component :is="Component" />
          </KeepAlive>
        </RouterView>
      </main>
    </div>

    <el-dialog
      v-model="passwordDialogVisible"
      title="修改密码"
      width="420px"
      destroy-on-close
      @closed="resetPasswordForm"
    >
      <el-form
        ref="passwordFormRef"
        :model="passwordForm"
        :rules="passwordRules"
        class="password-form"
        label-width="92px"
      >
        <el-form-item prop="oldPassword" label="原密码">
          <el-input
            v-model="passwordForm.oldPassword"
            type="password"
            autocomplete="current-password"
            show-password
          />
        </el-form-item>
        <el-form-item prop="newPassword" label="新密码">
          <el-input
            v-model="passwordForm.newPassword"
            type="password"
            autocomplete="new-password"
            show-password
          />
        </el-form-item>
        <el-form-item prop="confirmPassword" label="确认密码">
          <el-input
            v-model="passwordForm.confirmPassword"
            type="password"
            autocomplete="new-password"
            show-password
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="passwordDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="passwordSubmitting" @click="submitPasswordChange">
            确认修改
          </el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onUnmounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { FirstAidKit, TrendCharts, User } from '@element-plus/icons-vue'
import router from '@/router/index.js'
import request from '@/utils/request.js'
import { clearAuth, getCurrentUser, setUserInfo } from '@/utils/auth'
import {
  DEFAULT_GLOBAL_CONFIG,
  MANAGER_ROUTE_PATHS,
  fetchGlobalConfig,
  getCachedGlobalConfig,
  getDefaultManagerPath,
  getManagerLogoUrl,
  getManagerMenuTitles,
  getManagerRouteTitle,
  onConfigUpdated
} from '@/utils/config'

const currentUser = ref(null)
const passwordDialogVisible = ref(false)
const passwordSubmitting = ref(false)
const passwordFormRef = ref()
const logoLoadFailed = ref(false)
const globalConfig = ref(getCachedGlobalConfig())

const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const validateConfirmPassword = (rule, value, callback) => {
  if (value !== passwordForm.newPassword) {
    callback(new Error('两次输入的新密码不一致'))
    return
  }
  callback()
}

const passwordRules = {
  oldPassword: [
    { required: true, message: '请输入原密码', trigger: 'blur' }
  ],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' }
  ]
}

const isAdmin = computed(() => String(currentUser.value?.role || '').toLowerCase() === 'admin')
const menuTitles = computed(() => getManagerMenuTitles(globalConfig.value))
const routeTitles = computed(() => ({
  admin: getManagerRouteTitle('admin', globalConfig.value),
  dataview: getManagerRouteTitle('dataview', globalConfig.value),
  history: getManagerRouteTitle('history', globalConfig.value),
  config: getManagerRouteTitle('config', globalConfig.value),
  detect: getManagerRouteTitle('detect', globalConfig.value),
  mask: getManagerRouteTitle('mask', globalConfig.value),
  multi: getManagerRouteTitle('multi', globalConfig.value),
  threedim: getManagerRouteTitle('threedim', globalConfig.value)
}))

const visibleRoutes = computed(() => {
  const routes = globalConfig.value.managerRoutes || DEFAULT_GLOBAL_CONFIG.managerRoutes
  return {
    admin: isAdmin.value && routes.admin?.visible !== false,
    dataview: routes.dataview?.visible !== false,
    history: routes.history?.visible !== false,
    config: isAdmin.value && routes.config?.visible !== false,
    detect: routes.detect?.visible !== false,
    mask: routes.mask?.visible !== false,
    multi: routes.multi?.visible !== false,
    threedim: routes.threedim?.visible !== false
  }
})

const showUserMenu = computed(() => visibleRoutes.value.admin)
const showDataMenu = computed(() => visibleRoutes.value.dataview || visibleRoutes.value.history || visibleRoutes.value.config)
const showDiagnosisMenu = computed(() => visibleRoutes.value.detect || visibleRoutes.value.mask || visibleRoutes.value.multi || visibleRoutes.value.threedim)
const defaultOpeneds = computed(() => {
  const groups = []
  if (showUserMenu.value) groups.push('user')
  if (showDataMenu.value) groups.push('data')
  if (showDiagnosisMenu.value) groups.push('diagnosis')
  return groups
})

const cachedWorkPages = ['Detect', 'Mask', 'Multi', 'Threedim']
const brandTitle = computed(() => globalConfig.value.managerBrandTitle || DEFAULT_GLOBAL_CONFIG.managerBrandTitle)
const logoEnabled = computed(() => (
  globalConfig.value.managerLogoVisible !== false
  && !!String(globalConfig.value.managerLogoPath || '').trim()
  && !logoLoadFailed.value
))
const logoUrl = computed(() => {
  return `${getManagerLogoUrl()}?t=${encodeURIComponent(globalConfig.value.managerLogoPath || '')}`
})
const homePath = computed(() => getDefaultManagerPath(currentUser.value?.role, globalConfig.value))
const currentPageTitle = computed(() => getManagerRouteTitle(router.currentRoute.value.path, globalConfig.value))

const applyConfig = (config) => {
  globalConfig.value = config || getCachedGlobalConfig()
  logoLoadFailed.value = false
}

const loadGlobalConfig = async () => {
  try {
    const res = await fetchGlobalConfig()
    if (res.code === '200' && res.data) {
      applyConfig(getCachedGlobalConfig())
    }
  } catch (error) {
    console.warn('加载全局配置失败，继续使用本地缓存', error)
    applyConfig(getCachedGlobalConfig())
  }
}

const redirectUnauthorizedAdminPage = () => {
  if (!isAdmin.value && ['/manager/admin', '/manager/config'].includes(router.currentRoute.value.path)) {
    router.replace(homePath.value)
    return
  }

  const path = router.currentRoute.value.path
  const routeKey = Object.entries(MANAGER_ROUTE_PATHS).find(([, value]) => value === path)?.[0]
  if (routeKey && visibleRoutes.value[routeKey] === false) {
    router.replace(homePath.value)
  }
}

const goHome = () => {
  router.push(homePath.value)
}

const resetPasswordForm = () => {
  passwordForm.oldPassword = ''
  passwordForm.newPassword = ''
  passwordForm.confirmPassword = ''
  passwordFormRef.value?.clearValidate?.()
}

const openPasswordDialog = () => {
  resetPasswordForm()
  passwordDialogVisible.value = true
}

const submitPasswordChange = () => {
  if (!currentUser.value?.id) {
    ElMessage.error('当前用户信息不存在，请重新登录')
    return
  }

  passwordFormRef.value?.validate(async (valid) => {
    if (!valid) return
    passwordSubmitting.value = true
    try {
      const res = await request.put('/admin/changePassword', {
        id: currentUser.value.id,
        oldPassword: passwordForm.oldPassword,
        newPassword: passwordForm.newPassword
      })
      if (res.code === '200') {
        const savedUser = { ...currentUser.value }
        if ('password' in savedUser) {
          savedUser.password = passwordForm.newPassword
        }
        currentUser.value = savedUser
        setUserInfo(savedUser)
        ElMessage.success('密码修改成功')
        passwordDialogVisible.value = false
      } else {
        ElMessage.error(res.msg || '密码修改失败')
      }
    } catch (error) {
      console.error('修改密码失败:', error)
      ElMessage.error('修改密码失败，请稍后重试')
    } finally {
      passwordSubmitting.value = false
    }
  })
}

const handleLogoError = () => {
  if (String(globalConfig.value.managerLogoPath || '').trim()) {
    logoLoadFailed.value = true
  }
}

const logout = async () => {
  try {
    await request.post('/api/auth/logout')
  } catch (error) {
    console.warn('退出登录请求失败，已清除本地登录状态', error)
  } finally {
    clearAuth()
    router.replace('/login')
  }
}

const user = getCurrentUser()
if (user.id) {
  currentUser.value = user
} else {
  router.replace('/login')
}

loadGlobalConfig().then(() => {
  redirectUnauthorizedAdminPage()
})

const removeConfigListener = onConfigUpdated(({ globalConfig: config }) => {
  applyConfig(config || getCachedGlobalConfig())
  redirectUnauthorizedAdminPage()
})

watch(
  () => router.currentRoute.value.path,
  () => redirectUnauthorizedAdminPage()
)

onUnmounted(() => {
  removeConfigListener?.()
})
</script>

<style scoped>
.manager-layout {
  min-height: 100vh;
  background: var(--app-bg);
}

.manager-header {
  position: sticky;
  top: 0;
  z-index: 10;
  display: flex;
  height: 64px;
  background: var(--app-surface);
  border-bottom: 1px solid var(--app-border);
}

.brand-panel {
  display: flex;
  align-items: center;
  width: 320px;
  flex: 0 0 320px;
  padding: 0 16px;
  gap: 10px;
  background: var(--app-primary);
  color: #ffffff;
}

.brand-logo {
  width: 44px;
  height: 44px;
  border-radius: 8px;
  object-fit: contain;
  background: #ffffff;
  padding: 4px;
}

.brand-copy {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.brand-title {
  color: #ffffff;
  font-size: 16px;
  font-weight: 700;
  white-space: normal;
  word-break: break-word;
  overflow-wrap: break-word;
  line-height: 1.4;
}

.topbar {
  display: flex;
  flex: 1;
  align-items: center;
  justify-content: space-between;
  min-width: 0;
  padding: 0 24px;
}

.breadcrumb {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--app-text-muted);
  font-size: 14px;
}

.breadcrumb-home {
  padding: 0;
  color: var(--app-primary);
  font: inherit;
  background: transparent;
  border: 0;
  cursor: pointer;
}

.breadcrumb-home:hover {
  color: var(--app-primary-hover);
}

.breadcrumb-separator {
  color: var(--app-border-strong);
}

.user-entry {
  display: flex;
  align-items: center;
  gap: 10px;
  color: var(--app-text);
  cursor: pointer;
}

.user-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  border: 1px solid var(--app-border);
  object-fit: cover;
}

.manager-body {
  display: flex;
  min-height: calc(100vh - 64px);
}

.manager-sidebar {
  width: 320px;
  flex: 0 0 320px;
  background: var(--app-surface);
  border-right: 1px solid var(--app-border);
}

.manager-menu {
  min-height: calc(100vh - 64px);
  border-right: 0;
  background: transparent;
}

.manager-content {
  flex: 1;
  min-width: 0;
  background: var(--app-bg);
}

:deep(.manager-menu.el-menu--vertical .el-sub-menu__title),
:deep(.manager-menu.el-menu--vertical .el-menu-item) {
  height: auto;
  min-height: 42px;
  padding-top: 10px;
  padding-bottom: 10px;
  white-space: normal !important;
  line-height: 1.4;
  word-break: break-word;
  overflow-wrap: break-word;
}

:deep(.manager-menu.el-menu--vertical .el-sub-menu__title) {
  min-height: 48px;
  color: var(--app-text);
  font-weight: 600;
}

:deep(.manager-menu.el-menu--vertical .el-menu-item) {
  color: var(--app-text-muted);
}

:deep(.manager-menu.el-menu--vertical .el-menu-item.is-active) {
  color: var(--app-primary);
  background: var(--app-primary-soft);
  font-weight: 600;
}

:deep(.el-dropdown) {
  cursor: pointer;
}

:deep(.el-tooltip__trigger) {
  outline: none;
}

.password-form {
  padding: 8px 8px 0 0;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

@media (max-width: 900px) {
  .manager-header {
    height: auto;
    flex-direction: column;
  }

  .brand-panel,
  .manager-sidebar {
    width: 100%;
    flex-basis: auto;
  }

  .topbar {
    min-height: 56px;
  }

  .manager-body {
    flex-direction: column;
  }

  .manager-menu {
    min-height: auto;
  }
}
</style>


