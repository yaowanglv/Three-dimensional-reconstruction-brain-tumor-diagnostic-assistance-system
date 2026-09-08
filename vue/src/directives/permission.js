import { hasPermission, hasRole } from '@/utils/auth'

export const permission = {
  mounted(el, binding) {
    const value = binding.value
    if (!value) return

    const values = Array.isArray(value) ? value : [value]
    if (!values.some(item => hasPermission(item))) {
      el.parentNode?.removeChild(el)
    }
  }
}

export const role = {
  mounted(el, binding) {
    const value = binding.value
    if (!value) return

    const values = Array.isArray(value) ? value : [value]
    if (!values.some(item => hasRole(item))) {
      el.parentNode?.removeChild(el)
    }
  }
}
