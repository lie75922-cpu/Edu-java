import { computed, ref } from 'vue'

const apiBaseUrl = (import.meta.env.VITE_API_BASE_URL || '/api/v1').replace(/\/+$/, '')

const storedSession = (() => {
  try {
    return JSON.parse(localStorage.getItem('edu-session') || 'null')
  } catch {
    return null
  }
})()

export const session = ref(storedSession)
export const loading = ref(false)
export const notice = ref('')
export const error = ref('')
export const currentView = ref(storedSession ? '首页' : '登录')
export const selectedCourse = ref(null)

export const roles = computed(() => session.value?.user?.roles || [])
export const isStudent = computed(() => roles.value.includes('STUDENT'))
export const isTeacher = computed(() => roles.value.includes('TEACHER'))
export const isTeachingAdmin = computed(() => roles.value.includes('TEACH_ADMIN'))
export const isSystemAdmin = computed(() => roles.value.includes('SYSTEM_ADMIN'))
export const canTeach = computed(() => isTeacher.value || isTeachingAdmin.value || isSystemAdmin.value)
export const canManage = computed(() => isTeachingAdmin.value || isSystemAdmin.value)
export const authenticated = computed(() => Boolean(session.value?.accessToken))

export function setMessage(message = '', failure = '') {
  notice.value = message
  error.value = failure
}

export function rememberSession(value) {
  session.value = value
  if (value) localStorage.setItem('edu-session', JSON.stringify(value))
  else localStorage.removeItem('edu-session')
}

export function logout() {
  rememberSession(null)
  selectedCourse.value = null
  currentView.value = '登录'
  setMessage()
}

export async function api(path, options = {}) {
  const headers = { ...(options.headers || {}) }
  if (session.value?.accessToken) headers.Authorization = `Bearer ${session.value.accessToken}`
  if (options.body) headers['Content-Type'] = 'application/json'
  const response = await fetch(`${apiBaseUrl}${path}`, { ...options, headers })
  const payload = await response.json().catch(() => null)
  if (!response.ok || payload?.code !== 'OK') {
    if (response.status === 401) logout()
    throw new Error(payload?.message || `请求失败（${response.status}）`)
  }
  return payload.data
}

export async function run(action, successMessage = '') {
  loading.value = true
  setMessage()
  try {
    const result = await action()
    if (successMessage) notice.value = successMessage
    return result
  } catch (reason) {
    error.value = reason?.message || '请求失败，请稍后重试。'
    return null
  } finally {
    loading.value = false
  }
}

export async function login(username, password) {
  const result = await run(() => api('/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username, password })
  }))
  if (result) {
    rememberSession(result)
    currentView.value = '首页'
  }
  return result
}

export async function register(username, password, nickname) {
  const result = await run(() => api('/auth/register', {
    method: 'POST',
    body: JSON.stringify({ username, password, nickname })
  }))
  if (result) {
    rememberSession(result)
    currentView.value = '首页'
  }
  return result
}

export function roleName() {
  if (isSystemAdmin.value) return '系统管理员'
  if (isTeachingAdmin.value) return '教学管理员'
  if (isTeacher.value) return '教师'
  return '学生'
}

export function statusText(status) {
  if (status === 'UNKNOWN') return '暂无学习数据'
  if (status === 'OBSERVED') return '已有学习记录'
  if (status === 'ACTIVE') return '进行中'
  if (status === 'PUBLISHED') return '已发布'
  if (status === 'READY') return '待发布'
  if (status === 'DRAFT') return '草稿'
  if (status === 'APPROVED') return '已审核'
  if (status === 'REJECTED') return '已拒绝'
  return status || '—'
}

export function percent(value, digits = 0) {
  if (value === null || value === undefined || Number.isNaN(Number(value))) return '—'
  return `${(Number(value) * 100).toFixed(digits)}%`
}

export function newRequestId() {
  if (globalThis.crypto?.randomUUID) return globalThis.crypto.randomUUID()
  return `answer-${Date.now()}-${Math.random().toString(16).slice(2)}`
}
