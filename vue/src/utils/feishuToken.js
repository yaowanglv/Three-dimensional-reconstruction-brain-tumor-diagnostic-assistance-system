import request from '@/utils/request.js'

const FEISHU_USER_TOKEN_KEY = 'brain-tumor-feishu-user-token'
const FEISHU_USER_TOKEN_CHANGE_EVENT = 'feishu-user-token-change'

export function getFeishuUserToken() {
  return localStorage.getItem(FEISHU_USER_TOKEN_KEY) || ''
}

export function setFeishuUserToken(token) {
  const value = String(token || '').trim()
  if (value) {
    localStorage.setItem(FEISHU_USER_TOKEN_KEY, value)
  } else {
    localStorage.removeItem(FEISHU_USER_TOKEN_KEY)
  }

  window.dispatchEvent(new CustomEvent(FEISHU_USER_TOKEN_CHANGE_EVENT, {
    detail: { token: value }
  }))

  return value
}

export async function loadPersistedFeishuUserToken() {
  const localToken = getFeishuUserToken()

  try {
    const res = await request.get('/feishu/user-token')
    const remoteToken = String(res?.data?.token || '').trim()

    if (remoteToken && remoteToken !== localToken) {
      setFeishuUserToken(remoteToken)
      return remoteToken
    }

    return localToken
  } catch (error) {
    return localToken
  }
}

export async function savePersistedFeishuUserToken(token) {
  const value = setFeishuUserToken(token)
  const res = await request.put('/feishu/user-token', { token: value })
  if (res.code !== '200') {
    throw new Error(res.msg || '飞书 token 保存失败')
  }
  return value
}

export async function clearPersistedFeishuUserToken() {
  setFeishuUserToken('')
  const res = await request.delete('/feishu/user-token')
  if (res.code !== '200') {
    throw new Error(res.msg || '飞书 token 清空失败')
  }
}

export function onFeishuUserTokenChange(callback) {
  const handleCustomChange = (event) => {
    callback(event.detail?.token || getFeishuUserToken())
  }

  const handleStorageChange = (event) => {
    if (event.key === FEISHU_USER_TOKEN_KEY) {
      callback(event.newValue || '')
    }
  }

  window.addEventListener(FEISHU_USER_TOKEN_CHANGE_EVENT, handleCustomChange)
  window.addEventListener('storage', handleStorageChange)

  return () => {
    window.removeEventListener(FEISHU_USER_TOKEN_CHANGE_EVENT, handleCustomChange)
    window.removeEventListener('storage', handleStorageChange)
  }
}

export function formatFeishuUploadError(error) {
  const rawMessage = String(
    error?.response?.data?.msg ||
    error?.response?.data?.message ||
    error?.msg ||
    error?.message ||
    ''
  )
  const lowerMessage = rawMessage.toLowerCase()

  if (
    lowerMessage.includes('access token invalid') ||
    lowerMessage.includes('invalid access token') ||
    lowerMessage.includes('invalid user_access_token') ||
    lowerMessage.includes('invalid authorization') ||
    lowerMessage.includes('token invalid')
  ) {
    return '飞书权限 token 无效，请重新配置后再上传'
  }

  if (
    lowerMessage.includes('access token expired') ||
    lowerMessage.includes('token expired') ||
    lowerMessage.includes('expired access token') ||
    lowerMessage.includes('expired user_access_token') ||
    lowerMessage.includes('authorization has expired')
  ) {
    return '飞书权限 token 已过期，请重新获取并保存后再上传'
  }

  if (
    lowerMessage.includes('permission denied') ||
    lowerMessage.includes('no permission') ||
    lowerMessage.includes('forbidden') ||
    lowerMessage.includes('scope') ||
    lowerMessage.includes('unauthorized')
  ) {
    return '飞书权限不足，请确认 token 已授权云文档上传权限'
  }

  if (rawMessage.includes('文件大小超过20MB')) {
    return '上传失败：PDF 文件大小超过飞书 20MB 限制'
  }

  if (rawMessage.includes('请先配置飞书文件夹token')) {
    return '上传失败：后端未配置飞书目标文件夹 token'
  }

  if (!rawMessage || lowerMessage === 'request failed') {
    return '上传到飞书失败，请稍后重试'
  }

  return rawMessage.replace(/^上传失败[:：]\s*/i, '上传到飞书失败：')
}
