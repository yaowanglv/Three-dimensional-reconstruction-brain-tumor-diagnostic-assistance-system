

<template>
  <div class="not-found-page">
    <div class="not-found-card">
      <img class="not-found-image" src="@/assets/imgs/404.jpg" alt="">
      <h1>页面不存在</h1>
      <p>当前访问的地址无法匹配到系统页面。</p>
      <RouterLink class="not-found-link" to="/">返回主页</RouterLink>
    </div>
  </div>
</template>

<style scoped>
.not-found-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: var(--app-bg);
}

.not-found-card {
  width: min(560px, 100%);
  padding: 32px;
  text-align: center;
  background: var(--app-surface);
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius);
  box-shadow: var(--app-shadow);
}

.not-found-image {
  width: min(320px, 70%);
  display: block;
  margin: 0 auto 18px;
}

.not-found-card h1 {
  margin: 0;
  color: var(--app-text);
  font-size: 24px;
}

.not-found-card p {
  margin: 10px 0 22px;
  color: var(--app-text-muted);
}

.not-found-link {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 36px;
  padding: 0 18px;
  color: #ffffff;
  background: var(--app-primary);
  border-radius: 8px;
}

.not-found-link:hover {
  color: #ffffff;
  background: var(--app-primary-hover);
}
</style>
