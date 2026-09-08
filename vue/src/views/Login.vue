<template>
  <div class="container">
    <!-- ==========================================
         LEFT: Brain MRI Scanner
         ========================================== -->
    <div class="panel-brand">
      <div class="scanner-area">
        <div class="scanner" ref="scannerRef">
          <div class="scanner-tick"></div>
          <div class="scanner-tick"></div>
          <div class="scanner-tick"></div>
          <div class="scanner-tick"></div>
          <div class="scanner-tick"></div>
          <div class="scanner-tick"></div>
          <div class="scanner-tick"></div>
          <div class="scanner-tick"></div>
          <div class="scanner-tick"></div>
          <div class="scanner-tick"></div>
          <div class="scanner-tick"></div>
          <div class="scanner-tick"></div>

          <div class="scanner-rings">
            <div class="scanner-inner"></div>
          </div>
          <div class="scanner-beam" ref="beamRef"></div>
          <div class="scanner-crosshair-h"></div>
          <div class="scanner-crosshair-v"></div>
          <div class="scanner-center"></div>

          <!-- Tumor marker -->
          <div class="marker marker-1" ref="markerRef">
            <div class="marker-dot"></div>
            <div class="marker-ring"></div>
            <div class="marker-ring-2"></div>
          </div>
        </div>

        <div class="brand-content">
          <div class="brand-title">{{ loginBrandTitle }}</div>
          <div class="brand-subtitle">{{ loginBrandSubtitle }}</div>
          <div class="brand-tagline">
            AI 脑部影像分析 · 实时病灶检测
          </div>
        </div>
      </div>

      <div class="particle particle-1"></div>
      <div class="particle particle-2"></div>
      <div class="particle particle-3"></div>
      <div class="particle particle-4"></div>
      <div class="particle particle-5"></div>
      <div class="particle particle-6"></div>
    </div>

    <!-- ==========================================
         RIGHT: Login Form
         ========================================== -->
    <div class="panel-form">
      <div class="form-wrapper">
        <div class="form-card">
          <div class="form-header">
            <h1>登录</h1>
            <p>欢迎回到脑诊智析</p>
          </div>

          <form @submit.prevent="login">
            <div class="input-group">
              <label for="username">用户名 </label>
              <div class="input-field">
                <span class="input-icon">
                  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                    <circle cx="12" cy="8" r="4.5"/>
                    <path d="M4 21c0-4.5 3.6-8 8-8s8 3.5 8 8"/>
                  </svg>
                </span>
                <input type="text" id="username" v-model="data.form.username" placeholder="请输入用户名或工号" autocomplete="username">
              </div>
            </div>

            <div class="input-group">
              <label for="password">密码</label>
              <div class="input-field">
                <span class="input-icon">
                  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                    <rect x="5" y="11" width="14" height="10" rx="2"/>
                    <circle cx="12" cy="16" r="1.2"/>
                    <path d="M8 11V7a4 4 0 0 1 8 0v4"/>
                  </svg>
                </span>
                <input :type="pwVisible ? 'text' : 'password'" id="password" v-model="data.form.password" placeholder="请输入密码" autocomplete="current-password">
                <button type="button" class="password-toggle" @click="pwVisible = !pwVisible" aria-label="切换密码可见性">
                  <svg v-if="!pwVisible" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                    <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
                    <circle cx="12" cy="12" r="3"/>
                  </svg>
                  <svg v-else width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                    <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"/>
                    <line x1="1" y1="1" x2="23" y2="23"/>
                  </svg>
                </button>
              </div>
            </div>

            <div class="form-options">
              <label class="remember-me">
                <input type="checkbox" checked>
                <span class="checkbox-custom"></span>
                记住密码
              </label>
              <a href="#" class="forgot-link">忘记密码？</a>
            </div>

            <button type="submit" class="btn-login" :disabled="loginLoading">{{ loginLoading ? '登录中…' : '登 录' }}</button>
          </form>
        </div>
      </div>
    </div>

    <!-- ==========================================
         Tweaks
         ========================================== -->
    <button class="tweaks-toggle" @click="tweaksOpen = !tweaksOpen" aria-label="调整设置">
      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
        <circle cx="12" cy="12" r="3"/>
        <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"/>
      </svg>
    </button>

    <div class="tweaks-panel" :class="{ open: tweaksOpen }">
      <h3>界面调整</h3>
      <div class="tweak-item">
        <span class="tweak-label">扫描动效</span>
        <label class="toggle-switch">
          <input type="checkbox" :checked="scanEnabled" @change="toggleScan">
          <span class="toggle-track"></span>
        </label>
      </div>
      <div class="tweak-item">
        <span class="tweak-label">粒子效果</span>
        <label class="toggle-switch">
          <input type="checkbox" :checked="particlesEnabled" @change="toggleParticles">
          <span class="toggle-track"></span>
        </label>
      </div>
      <div class="tweak-item">
        <span class="tweak-label">病灶标记</span>
        <label class="toggle-switch">
          <input type="checkbox" :checked="markersEnabled" @change="toggleMarkers">
          <span class="toggle-track"></span>
        </label>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, reactive, ref, onMounted, onBeforeUnmount, watch } from "vue";
import request from "@/utils/request";
import { setAuth } from "@/utils/auth";
import { ElMessage } from "element-plus";
import router from "@/router";
import { fetchBrandingConfig, getCachedGlobalConfig, getDefaultManagerPath, onConfigUpdated } from '@/utils/config'

const data = reactive({
  form: {},
})

const pwVisible = ref(false)
const loginLoading = ref(false)
const globalConfig = ref(getCachedGlobalConfig())
const loginPageTitle = computed(() => String(globalConfig.value?.loginPageTitle || '').trim() || '脑诊智析')
const loginBrandTitle = computed(() => String(globalConfig.value?.loginBrandTitle || '').trim() || '大模型驱动的脑肿瘤智能辅助诊断与分析系统')
const loginBrandSubtitle = computed(() => String(globalConfig.value?.loginBrandSubtitle || '').trim() || '脑诊智析')

const login = () => {
  const username = data.form.username
  const password = data.form.password

  if (!username || !password) {
    ElMessage.error('请填写账号和密码')
    return
  }

  loginLoading.value = true
  request.post('/api/auth/login', data.form).then(res => {
    if (res.code === '200') {
      setAuth(res.data.accessToken, res.data.refreshToken, res.data.userInfo)
      ElMessage.success('登录成功')
      router.push(getDefaultManagerPath(res.data.userInfo?.role, getCachedGlobalConfig()))
    } else {
      ElMessage.error(res.msg || '账号或密码错误')
    }
  }).catch(error => {
    console.error('登录请求失败:', error)
    ElMessage.error('账号或者密码错误')
  }).finally(() => {
    loginLoading.value = false
  })
}

/* Scanner animation state */
const scannerRef = ref(null)
const beamRef = ref(null)
const markerRef = ref(null)
const tweaksOpen = ref(false)
const scanEnabled = ref(true)
const particlesEnabled = ref(true)
const markersEnabled = ref(true)

const MARKER_ANGLE = 137
const BEAM_TOLERANCE = 12
const SCAN_DURATION = 4000

let animFrameId = null

function updateMarker() {
  const now = performance.now()
  const elapsed = (now % SCAN_DURATION) / SCAN_DURATION
  const currentAngle = elapsed * 360
  const diff = Math.abs(currentAngle - MARKER_ANGLE)
  const wrapped = Math.min(diff, 360 - diff)
  const isActive = wrapped < BEAM_TOLERANCE

  if (markerRef.value) {
    markerRef.value.classList.toggle('active', isActive)
  }
  animFrameId = requestAnimationFrame(updateMarker)
}

function toggleScan(e) {
  scanEnabled.value = e.target.checked
  if (beamRef.value) {
    beamRef.value.style.animationPlayState = scanEnabled.value ? 'running' : 'paused'
  }
}

function toggleParticles(e) {
  particlesEnabled.value = e.target.checked
  const particles = document.querySelectorAll('.particle')
  particles.forEach(p => { p.style.display = particlesEnabled.value ? '' : 'none' })
}

function toggleMarkers(e) {
  markersEnabled.value = e.target.checked
  if (markerRef.value) {
    markerRef.value.style.display = markersEnabled.value ? '' : 'none'
  }
}

/* Close tweaks panel on outside click */
function handleDocClick(e) {
  const panel = document.querySelector('.tweaks-panel')
  const toggle = document.querySelector('.tweaks-toggle')
  if (tweaksOpen.value && panel && !panel.contains(e.target) && toggle && !toggle.contains(e.target)) {
    tweaksOpen.value = false
  }
}

const applyGlobalConfig = (config) => {
  globalConfig.value = config || getCachedGlobalConfig()
}

onMounted(() => {
  applyGlobalConfig(getCachedGlobalConfig())
  requestAnimationFrame(updateMarker)
  fetchBrandingConfig()
    .then((res) => {
      if (res.code === '200' && res.data) {
        applyGlobalConfig({
          ...getCachedGlobalConfig(),
          ...res.data
        })
      }
    })
    .catch((error) => {
      console.warn('加载登录页配置失败，继续使用本地缓存', error)
    })
  document.addEventListener('click', handleDocClick)
})

onBeforeUnmount(() => {
  if (animFrameId) cancelAnimationFrame(animFrameId)
  document.removeEventListener('click', handleDocClick)
  removeConfigListener?.()
})

watch(loginPageTitle, (value) => {
  if (typeof document !== 'undefined') {
    document.title = value
  }
}, { immediate: true })

const removeConfigListener = onConfigUpdated(({ globalConfig: config }) => {
  applyGlobalConfig(config || getCachedGlobalConfig())
})
</script>

<style>
/* ============================================
   Design Tokens — Light / Neuro
   ============================================ */
:root {
  --bg-page: #f0f4f8;
  --bg-left: #ffffff;
  --bg-right: #eef2f6;
  --card-bg: #ffffff;
  --card-border: #e8edf4;
  --card-shadow: 0 8px 40px rgba(0, 0, 0, 0.06), 0 2px 8px rgba(0, 0, 0, 0.04);
  --card-shadow-hover: 0 12px 48px rgba(0, 0, 0, 0.08);
  --primary: #4f46e5;
  --primary-light: rgba(79, 70, 229, 0.1);
  --primary-dim: rgba(79, 70, 229, 0.06);
  --accent: #06b6d4;
  --accent-dim: rgba(6, 182, 212, 0.08);
  --text-primary: #0f172a;
  --text-secondary: #475569;
  --text-muted: #94a3b8;
  --input-bg: #f8fafc;
  --input-border: #e2e8f0;
  --input-border-hover: #cbd5e1;
  --input-focus-ring: rgba(79, 70, 229, 0.18);
  --scan-ring: rgba(79, 70, 229, 0.15);
  --scan-ring-light: rgba(79, 70, 229, 0.08);
  --scan-crosshair: rgba(79, 70, 229, 0.12);
  --radius-card: 24px;
  --radius-input: 12px;
  --radius-button: 10px;
  --font-sans: "PingFang SC", "Microsoft YaHei", "Noto Sans SC", "HarmonyOS Sans", sans-serif;
  --scan-duration: 4s;
}

*, *::before, *::after {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

html, body {
  height: 100%;
  font-family: var(--font-sans);
  background: var(--bg-page);
  color: var(--text-primary);
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}

/* ============================================
   Layout
   ============================================ */
.container {
  display: flex;
  height: 100vh;
  width: 100vw;
  overflow: hidden;
}

/* ---------- Left ---------- */
.panel-brand {
  flex: 0 0 55%;
  position: relative;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  background: var(--bg-left);
  overflow: hidden;
}

.panel-brand::before {
  content: '';
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(var(--primary-dim) 1px, transparent 1px),
    linear-gradient(90deg, var(--primary-dim) 1px, transparent 1px);
  background-size: 48px 48px;
  pointer-events: none;
  z-index: 1;
}

.panel-brand::after {
  content: '';
  position: absolute;
  top: -30%;
  right: -20%;
  width: 600px;
  height: 600px;
  background: radial-gradient(circle, rgba(79, 70, 229, 0.04) 0%, transparent 70%);
  pointer-events: none;
  z-index: 0;
}

/* ---------- Right ---------- */
.panel-form {
  flex: 0 0 45%;
  position: relative;
  display: flex;
  justify-content: center;
  align-items: center;
  background: var(--bg-right);
  overflow: hidden;
}

.panel-form::before {
  content: '';
  position: absolute;
  bottom: -30%;
  left: -20%;
  width: 500px;
  height: 500px;
  background: radial-gradient(circle, rgba(6, 182, 212, 0.04) 0%, transparent 70%);
  pointer-events: none;
}

/* ============================================
   Scanner — Brain MRI
   ============================================ */
.scanner-area {
  position: relative;
  z-index: 2;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 36px;
}

.scanner {
  position: relative;
  width: 340px;
  height: 340px;
  border-radius: 50%;
  background: url('@/assets/imgs/brain-mri.jpg') center / cover no-repeat;
  box-shadow:
    0 0 0 4px rgba(79, 70, 229, 0.12),
    0 8px 40px rgba(79, 70, 229, 0.08);
}

/* Outer glow ring */
.scanner::before {
  content: '';
  position: absolute;
  inset: -18px;
  border-radius: 50%;
  border: 1.5px solid var(--scan-ring);
  animation: pulse-ring 3s ease-in-out infinite;
}

@keyframes pulse-ring {
  0%, 100% { transform: scale(1); opacity: 1; }
  50% { transform: scale(1.04); opacity: 0.3; }
}

/* Vignette overlay for depth */
.scanner::after {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: 50%;
  background: radial-gradient(circle at 50% 50%, transparent 45%, rgba(79, 70, 229, 0.04) 100%);
  pointer-events: none;
  z-index: 1;
}

/* Rings */
.scanner-rings {
  position: absolute;
  inset: 0;
  border-radius: 50%;
  border: 1.5px solid var(--scan-ring);
  z-index: 2;
}

.scanner-rings::before,
.scanner-rings::after {
  content: '';
  position: absolute;
  border-radius: 50%;
  border: 1.5px solid transparent;
}

.scanner-rings::before {
  inset: 17%;
  border-color: var(--scan-ring);
}

.scanner-rings::after {
  inset: 34%;
  border-color: var(--scan-ring-light);
}

.scanner-inner {
  position: absolute;
  inset: 50%;
  border-radius: 50%;
  background: rgba(79, 70, 229, 0.06);
  border: 1px solid rgba(79, 70, 229, 0.12);
}

.scanner-center {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 5px;
  height: 5px;
  transform: translate(-50%, -50%);
  border-radius: 50%;
  background: var(--primary);
  box-shadow: 0 0 14px rgba(79, 70, 229, 0.4);
  z-index: 5;
}

/* Scan beam */
.scanner-beam {
  position: absolute;
  inset: 0;
  border-radius: 50%;
  background: conic-gradient(
    from 0deg,
    transparent 0deg,
    rgba(79, 70, 229, 0.18) 3deg,
    rgba(79, 70, 229, 0.05) 8deg,
    transparent 14deg
  );
  animation: scan-rotate var(--scan-duration) linear infinite;
  transform-origin: center;
  z-index: 3;
  pointer-events: none;
}

@keyframes scan-rotate {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

/* Crosshair */
.scanner-crosshair-h,
.scanner-crosshair-v {
  position: absolute;
  z-index: 2;
  pointer-events: none;
}

.scanner-crosshair-h {
  top: 50%;
  left: 4%;
  right: 4%;
  height: 1px;
  background: linear-gradient(90deg, transparent, var(--scan-crosshair) 30%, var(--scan-crosshair) 70%, transparent);
  transform: translateY(-50%);
}

.scanner-crosshair-v {
  left: 50%;
  top: 4%;
  bottom: 4%;
  width: 1px;
  background: linear-gradient(180deg, transparent, var(--scan-crosshair) 30%, var(--scan-crosshair) 70%, transparent);
  transform: translateX(-50%);
}

/* Tick marks */
.scanner-tick {
  position: absolute;
  z-index: 2;
  top: 0;
  left: 50%;
  width: 1.5px;
  height: 10px;
  background: rgba(79, 70, 229, 0.18);
  transform-origin: 0 170px;
}

.scanner-tick:nth-child(1)  { transform: translateX(-50%) rotate(0deg); }
.scanner-tick:nth-child(2)  { transform: translateX(-50%) rotate(30deg); }
.scanner-tick:nth-child(3)  { transform: translateX(-50%) rotate(60deg); }
.scanner-tick:nth-child(4)  { transform: translateX(-50%) rotate(90deg); }
.scanner-tick:nth-child(5)  { transform: translateX(-50%) rotate(120deg); }
.scanner-tick:nth-child(6)  { transform: translateX(-50%) rotate(150deg); }
.scanner-tick:nth-child(7)  { transform: translateX(-50%) rotate(180deg); }
.scanner-tick:nth-child(8)  { transform: translateX(-50%) rotate(210deg); }
.scanner-tick:nth-child(9)  { transform: translateX(-50%) rotate(240deg); }
.scanner-tick:nth-child(10) { transform: translateX(-50%) rotate(270deg); }
.scanner-tick:nth-child(11) { transform: translateX(-50%) rotate(300deg); }
.scanner-tick:nth-child(12) { transform: translateX(-50%) rotate(330deg); }

/* ============================================
   Tumor Marker — Single (from label file centroid)
   Position: based on YOLO segmentation centroid at (0.591, 0.599)
   Beam angle ~137° from top (clockwise)
   Turns red when scan beam passes over
   ============================================ */
.marker {
  position: absolute;
  z-index: 4;
  pointer-events: none;
  transition: all 0.4s ease;
}

.marker-dot {
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background: var(--primary);
  box-shadow: 0 0 14px rgba(79, 70, 229, 0.5);
  transition: all 0.4s ease;
}

.marker-ring {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 36px;
  height: 36px;
  margin: -18px 0 0 -18px;
  border-radius: 50%;
  border: 2px solid rgba(79, 70, 229, 0.3);
  animation: marker-pulse 2s ease-in-out infinite;
  transition: all 0.4s ease;
}

.marker-ring-2 {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 54px;
  height: 54px;
  margin: -27px 0 0 -27px;
  border-radius: 50%;
  border: 1.5px solid rgba(79, 70, 229, 0.1);
  animation: marker-pulse 2s ease-in-out 0.5s infinite;
  transition: all 0.4s ease;
}

.marker-1 { top: 59.9%; left: 59.1%; }

/* Active (scanned) state — glowing red */
.marker.active .marker-dot {
  background: #dc2626;
  box-shadow: 0 0 24px rgba(220, 38, 38, 0.7), 0 0 60px rgba(220, 38, 38, 0.3);
}
.marker.active .marker-ring {
  border-color: rgba(220, 38, 38, 0.5);
  box-shadow: 0 0 20px rgba(220, 38, 38, 0.2);
}
.marker.active .marker-ring-2 {
  border-color: rgba(220, 38, 38, 0.2);
}

@keyframes marker-pulse {
  0%, 100% { transform: scale(1); opacity: 0.5; }
  50% { transform: scale(1.4); opacity: 0.08; }
}

/* ============================================
   Brand Text
   ============================================ */
.brand-content {
  position: relative;
  z-index: 2;
  text-align: center;
  width: min(520px, calc(100vw - 64px));
  max-width: 100%;
  margin: 0 auto;
}

.brand-title {
  font-size: clamp(22px, 2.8vw, 30px);
  font-weight: 700;
  line-height: 1.5;
  color: var(--text-primary);
  white-space: normal;
  overflow-wrap: anywhere;
  word-break: break-word;
  margin-bottom: 14px;
}

.brand-subtitle {
  font-size: clamp(22px, 2.8vw, 32px);
  font-weight: 700;
  letter-spacing: 3px;
  background: linear-gradient(135deg, var(--primary), var(--accent));
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  margin-bottom: 4px;
}

.brand-tagline {
  margin-top: 24px;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--text-muted);
  letter-spacing: 2px;
  border: 1px solid var(--card-border);
  padding: 6px 16px;
  border-radius: 20px;
  background: white;
}

.brand-tagline::before {
  content: '';
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--primary);
  animation: dot-blink 2s ease-in-out infinite;
}

@keyframes dot-blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.2; }
}

/* ============================================
   Particles
   ============================================ */
.particle {
  position: absolute;
  border-radius: 50%;
  pointer-events: none;
  z-index: 1;
  opacity: 0;
}
.particle-1 { width: 3px; height: 3px; top: 15%; left: 20%; background: var(--primary); animation: particle-float 8s ease-in-out 0s infinite; }
.particle-2 { width: 4px; height: 4px; top: 35%; left: 82%; background: var(--accent); animation: particle-float 10s ease-in-out 1s infinite; }
.particle-3 { width: 3px; height: 3px; top: 60%; left: 12%; background: var(--primary); animation: particle-float 7s ease-in-out 2s infinite; }
.particle-4 { width: 2px; height: 2px; top: 80%; left: 72%; background: var(--accent); animation: particle-float 9s ease-in-out 3s infinite; }
.particle-5 { width: 3px; height: 3px; top: 22%; left: 60%; background: var(--primary); animation: particle-float 11s ease-in-out 0.5s infinite; }
.particle-6 { width: 2px; height: 2px; top: 72%; left: 35%; background: var(--accent); animation: particle-float 8s ease-in-out 1.5s infinite; }

@keyframes particle-float {
  0% { transform: translateY(0) translateX(0); opacity: 0; }
  10% { opacity: 0.2; }
  90% { opacity: 0.12; }
  100% { transform: translateY(-120px) translateX(40px); opacity: 0; }
}

/* ============================================
   Form Card
   ============================================ */
.form-wrapper {
  position: relative;
  z-index: 2;
  width: 100%;
  max-width: 420px;
  padding: 0 24px;
}

.form-card {
  background: var(--card-bg);
  border: 1px solid var(--card-border);
  border-radius: var(--radius-card);
  padding: 44px 40px 40px;
  box-shadow: var(--card-shadow);
  transition: box-shadow 0.3s, border-color 0.3s;
}

.form-card:hover {
  border-color: #dce3ec;
  box-shadow: var(--card-shadow-hover);
}

.form-header {
  margin-bottom: 36px;
}

.form-header h1 {
  font-size: 26px;
  font-weight: 600;
  margin-bottom: 8px;
  letter-spacing: 0.5px;
}

.form-header p {
  font-size: 14px;
  color: var(--text-secondary);
  font-weight: 400;
}

/* ============================================
   Inputs
   ============================================ */
.input-group {
  margin-bottom: 20px;
  position: relative;
}

.input-group label {
  display: block;
  font-size: 13px;
  font-weight: 500;
  color: var(--text-secondary);
  margin-bottom: 8px;
  letter-spacing: 0.3px;
}

.input-field {
  position: relative;
}

.input-field input {
  width: 100%;
  padding: 14px 16px 14px 44px;
  background: var(--input-bg);
  border: 1.5px solid var(--input-border);
  border-radius: var(--radius-input);
  color: var(--text-primary);
  font-size: 15px;
  font-family: var(--font-sans);
  outline: none;
  transition: all 0.25s ease;
}

.input-field input::placeholder {
  color: var(--text-muted);
  font-size: 14px;
}

.input-field input:hover {
  border-color: var(--input-border-hover);
}

.input-field input:focus {
  border-color: var(--primary);
  background: white;
  box-shadow: 0 0 0 4px var(--input-focus-ring);
}

.input-icon {
  position: absolute;
  left: 16px;
  top: 50%;
  transform: translateY(-50%);
  width: 18px;
  height: 18px;
  color: var(--text-muted);
  pointer-events: none;
  transition: color 0.25s;
}

.input-field:focus-within .input-icon {
  color: var(--primary);
}

.password-toggle {
  position: absolute;
  right: 14px;
  top: 50%;
  transform: translateY(-50%);
  background: none;
  border: none;
  color: var(--text-muted);
  cursor: pointer;
  padding: 4px;
  transition: color 0.2s;
  line-height: 1;
  display: flex;
}

.password-toggle:hover {
  color: var(--text-secondary);
}

/* ============================================
   Options
   ============================================ */
.form-options {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: 20px 0 28px;
}

.remember-me {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  font-size: 13px;
  color: var(--text-secondary);
  user-select: none;
}

.remember-me input[type="checkbox"] { display: none; }

.checkbox-custom {
  width: 18px;
  height: 18px;
  border: 1.5px solid var(--input-border);
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
  flex-shrink: 0;
}

.remember-me input:checked + .checkbox-custom {
  background: #256f7f;
  border-color: #256f7f;
}

.remember-me input:checked + .checkbox-custom::after {
  content: '';
  width: 5px;
  height: 9px;
  border: solid white;
  border-width: 0 2px 2px 0;
  transform: rotate(45deg);
  margin-top: -1px;
}

.remember-me:hover .checkbox-custom {
  border-color: var(--input-border-hover);
}

.forgot-link {
  font-size: 13px;
  color: var(--text-muted);
  text-decoration: none;
  transition: color 0.2s;
}

.forgot-link:hover {
  color: var(--primary);
}

/* ============================================
   Button
   ============================================ */
.btn-login {
  width: 100%;
  padding: 14px;
  border: none;
  border-radius: var(--radius-button);
  background: linear-gradient(135deg, #256f7f, #1f5f6d);
  color: white;
  font-size: 16px;
  font-weight: 600;
  font-family: var(--font-sans);
  cursor: pointer;
  transition: all 0.3s ease;
  position: relative;
  overflow: hidden;
  letter-spacing: 1px;
}

.btn-login:hover {
  transform: translateY(-1px);
  box-shadow: 0 8px 28px rgba(37, 111, 127, 0.3);
}

.btn-login:active {
  transform: translateY(0);
  box-shadow: none;
}

.btn-login:disabled {
  opacity: 0.8;
  cursor: not-allowed;
  transform: none !important;
  box-shadow: none !important;
}

.btn-login::after {
  content: '';
  position: absolute;
  top: -50%;
  left: -60%;
  width: 40%;
  height: 200%;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.12), transparent);
  transform: rotate(25deg);
  animation: btn-shimmer 4s ease-in-out infinite;
}

@keyframes btn-shimmer {
  0% { left: -60%; }
  100% { left: 120%; }
}

/* ============================================
   Tweaks
   ============================================ */
.tweaks-toggle {
  position: fixed;
  bottom: 24px;
  right: 24px;
  z-index: 100;
  width: 40px;
  height: 40px;
  border-radius: 50%;
  border: 1px solid var(--card-border);
  background: var(--card-bg);
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
  color: var(--text-muted);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.3s;
}

.tweaks-toggle:hover {
  border-color: var(--primary);
  color: var(--primary);
  box-shadow: 0 4px 16px rgba(79, 70, 229, 0.15);
}

.tweaks-panel {
  position: fixed;
  bottom: 72px;
  right: 24px;
  z-index: 99;
  background: white;
  border: 1px solid var(--card-border);
  border-radius: 16px;
  padding: 20px 22px;
  min-width: 200px;
  transform: translateY(12px);
  opacity: 0;
  pointer-events: none;
  transition: all 0.3s ease;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.08);
}

.tweaks-panel.open {
  transform: translateY(0);
  opacity: 1;
  pointer-events: auto;
}

.tweaks-panel h3 {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-muted);
  letter-spacing: 2px;
  margin-bottom: 16px;
  text-transform: uppercase;
}

.tweak-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14px;
}

.tweak-item:last-child { margin-bottom: 0; }

.tweak-label {
  font-size: 13px;
  color: var(--text-secondary);
}

.toggle-switch {
  position: relative;
  width: 36px;
  height: 20px;
  cursor: pointer;
}

.toggle-switch input { display: none; }

.toggle-track {
  position: absolute;
  inset: 0;
  border-radius: 10px;
  background: #e2e8f0;
  transition: background 0.3s;
}

.toggle-track::after {
  content: '';
  position: absolute;
  top: 2px;
  left: 2px;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: white;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
  transition: all 0.3s;
}

.toggle-switch input:checked + .toggle-track { background: var(--primary); }
.toggle-switch input:checked + .toggle-track::after { left: 18px; background: white; }

/* ============================================
   Entry Animations
   ============================================ */
@keyframes fade-in-up {
  from { opacity: 0; transform: translateY(20px); }
  to { opacity: 1; transform: translateY(0); }
}

.form-card { animation: fade-in-up 0.6s ease-out; }
.brand-content { animation: fade-in-up 0.6s ease-out 0.1s both; }
.scanner { animation: fade-in-up 0.6s ease-out 0.05s both; }

/* ============================================
   Responsive
   ============================================ */
@media (max-width: 900px) {
  .container { flex-direction: column; }
  .panel-brand {
    flex: 0 0 auto;
    height: 45vh;
    min-height: 340px;
    padding: 24px;
  }
  .panel-form {
    flex: 1;
    min-height: 400px;
  }
  .scanner { width: 220px; height: 220px; }
  .scanner-tick { transform-origin: 0 110px; }
  .brand-content {
    width: min(360px, calc(100vw - 48px));
  }
  .brand-title { font-size: clamp(18px, 3.2vw, 24px); }
  .brand-subtitle { font-size: clamp(18px, 3.5vw, 26px); }
  .form-card { padding: 32px 24px 28px; }
  .tweaks-panel { right: 12px; bottom: 64px; }
  .tweaks-toggle { right: 12px; bottom: 16px; }
}

@media (max-width: 480px) {
  .panel-brand { height: 36vh; min-height: 260px; }
  .scanner { width: 160px; height: 160px; }
  .scanner-tick { transform-origin: 0 80px; height: 7px; }
  .brand-content { display: none; }
  .form-wrapper { padding: 0 16px; }
  .form-card { padding: 28px 20px 24px; border-radius: 20px; }
  .tweaks-panel { right: 8px; bottom: 60px; min-width: 180px; }
  .tweaks-toggle { right: 8px; bottom: 12px; }
}
</style>
