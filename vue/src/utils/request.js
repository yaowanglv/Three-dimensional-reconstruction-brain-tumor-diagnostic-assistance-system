import { ElMessage } from 'element-plus'
import router from '../router'
import axios from 'axios'
import { clearAuth, getAccessToken, getRefreshToken, setAuth } from '@/utils/auth'
import { getBackendBaseUrl } from '@/utils/runtimeConfig'

const request = axios.create({
    baseURL: getBackendBaseUrl(),
    timeout: 30000
})

let isRefreshing = false
let refreshSubscribers = []

function notifyRefreshSubscribers(token) {
    refreshSubscribers.forEach(callback => callback(token))
    refreshSubscribers = []
}

function waitForRefresh() {
    return new Promise(resolve => {
        refreshSubscribers.push(resolve)
    })
}

async function refreshAccessToken() {
    if (isRefreshing) {
        return waitForRefresh()
    }

    isRefreshing = true
    try {
        const refreshToken = getRefreshToken()
        if (!refreshToken) {
            throw new Error('No refresh token')
        }

        const response = await axios.post(`${getBackendBaseUrl()}/api/auth/refresh`, { refreshToken })
        const res = response.data
        if (res.code !== '200') {
            throw new Error(res.msg || 'Refresh failed')
        }

        setAuth(res.data.accessToken, res.data.refreshToken, res.data.userInfo)
        notifyRefreshSubscribers(res.data.accessToken)
        return res.data.accessToken
    } finally {
        isRefreshing = false
    }
}

request.interceptors.request.use(config => {
    config.baseURL = getBackendBaseUrl()

    if (!(config.data instanceof FormData)) {
        config.headers['Content-Type'] = 'application/json;charset=utf-8'
    }

    const token = getAccessToken()
    if (token) {
        config.headers.Authorization = `Bearer ${token}`
    }

    return config
}, error => {
    return Promise.reject(error)
})

request.interceptors.response.use(
    response => {
        let res = response.data
        if (response.config.responseType === 'blob') {
            return res
        }
        if (typeof res === 'string') {
            res = res ? JSON.parse(res) : res
        }
        if (res.code === '401' || res.code === '401001' || res.code === '401002' || res.code === '401003') {
            clearAuth()
            ElMessage.error(res.msg || '登录已过期，请重新登录')
            router.push('/login')
        }
        if (res.code === '403' || res.code === '403001') {
            ElMessage.error(res.msg || '权限不足')
            router.push('/403')
        }
        return res
    },
    async error => {
        const originalRequest = error.config || {}

        if (error.response?.status === 401 && !originalRequest._retry && !originalRequest.url?.includes('/api/auth/refresh')) {
            originalRequest._retry = true
            try {
                const newToken = await refreshAccessToken()
                originalRequest.headers = originalRequest.headers || {}
                originalRequest.headers.Authorization = `Bearer ${newToken}`
                return request(originalRequest)
            } catch (refreshError) {
                clearAuth()
                ElMessage.error('登录已过期，请重新登录')
                router.push('/login')
                return Promise.reject(refreshError)
            }
        }

        if (error.response?.status === 403) {
            ElMessage.error(error.response?.data?.msg || '权限不足')
            router.push('/403')
        } else {
            ElMessage.error(error.response?.data?.msg || '请求失败')
        }

        return Promise.reject(error)
    }
)

export default request
