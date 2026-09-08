import { createRouter, createWebHistory } from 'vue-router'
import { getCurrentUser, hasPermission, hasRole, isAuthenticated } from '@/utils/auth'
import { ElMessage } from 'element-plus'
import { getCachedGlobalConfig, getDefaultManagerPath, getManagerRouteTitle } from '@/utils/config'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/', redirect: '/login' },
    {
      path: '/manager',
      component: () => import('../views/Manager.vue'),
      meta: { requiresAuth: true },
      children: [
        {
          path: 'admin',
          meta: { routeKey: 'admin', requiresAuth: true, requiresRole: 'admin' },
          component: () => import('../views/Admin.vue')
        },
        {
          path: 'detect',
          meta: { routeKey: 'detect', requiresAuth: true, requiresPermission: 'detect:create' },
          component: () => import('../views/Detect.vue')
        },
        {
          path: 'mask',
          meta: { routeKey: 'mask', requiresAuth: true, requiresPermission: 'detect:create' },
          component: () => import('../views/Mask.vue')
        },
        {
          path: 'multi',
          meta: { routeKey: 'multi', requiresAuth: true, requiresPermission: 'detect:create' },
          component: () => import('../views/Multi.vue')
        },
        {
          path: 'threedim',
          meta: { routeKey: 'threedim', requiresAuth: true, requiresPermission: 'detect:create' },
          component: () => import('../views/Threedim.vue')
        },
        {
          path: 'history',
          meta: { routeKey: 'history', requiresAuth: true, requiresPermission: 'detect:view' },
          component: () => import('../views/History.vue')
        },
        {
          path: 'dataview',
          meta: { routeKey: 'dataview', requiresAuth: true, requiresPermission: 'data:view' },
          component: () => import('../views/Dataview.vue')
        },
        {
          path: 'config',
          meta: { routeKey: 'config', requiresAuth: true, requiresRole: 'admin' },
          component: () => import('../views/Config.vue')
        }
      ]
    },
    { path: '/login', component: () => import('../views/Login.vue') },
    { path: '/403', name: 'Forbidden', component: () => import('../views/403.vue') },
    { path: '/notFound', name: '404', component: () => import('../views/404.vue') },
    { path: '/:pathMatch(.*)*', redirect: '/notFound' }
  ]
})

router.beforeEach((to) => {
  if (to.meta?.routeKey) {
    to.meta.name = getManagerRouteTitle(to.meta.routeKey, getCachedGlobalConfig())
  }

  if (!to.meta?.requiresAuth) {
    if (to.path === '/login' && isAuthenticated()) {
      const user = getCurrentUser()
      return getDefaultManagerPath(user.role, getCachedGlobalConfig())
    }
    return true
  }

  if (!isAuthenticated()) {
    return '/login'
  }

  if (to.meta.requiresRole && !hasRole(to.meta.requiresRole)) {
    ElMessage.error('权限不足')
    return '/403'
  }

  if (to.meta.requiresPermission && !hasPermission(to.meta.requiresPermission)) {
    ElMessage.error('权限不足')
    return '/403'
  }

  return true
})

export default router
