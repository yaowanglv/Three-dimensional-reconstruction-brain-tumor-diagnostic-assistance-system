<template>
  <div class="forbidden-page">
    <section class="forbidden-panel">
      <h1>403</h1>
      <h2>权限不足</h2>
      <p>抱歉，当前账号没有访问该页面的权限。</p>
      <el-button type="primary" @click="goHome">返回首页</el-button>
    </section>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { getCurrentUser } from '@/utils/auth'
import { getCachedGlobalConfig, getDefaultManagerPath } from '@/utils/config'

const router = useRouter()

const goHome = () => {
  const user = getCurrentUser()
  router.push(getDefaultManagerPath(user.role, getCachedGlobalConfig()))
}
</script>

<style scoped>
.forbidden-page {
  display: flex;
  min-height: 100vh;
  align-items: center;
  justify-content: center;
  background: var(--app-bg, #f4f7fb);
}

.forbidden-panel {
  text-align: center;
}

.forbidden-panel h1 {
  margin: 0;
  color: #d64545;
  font-size: 96px;
  line-height: 1;
}

.forbidden-panel h2 {
  margin: 18px 0 10px;
  color: var(--app-text, #1f2937);
}

.forbidden-panel p {
  margin-bottom: 24px;
  color: var(--app-text-muted, #64748b);
}
</style>
