import axios from 'axios'

export const api = axios.create({ baseURL: import.meta.env.VITE_API_BASE_URL || '/api/v1', timeout: 15000 })
api.interceptors.request.use(config => {
  const token = localStorage.getItem('delivery_token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})
api.interceptors.response.use(response => {
  const body = response.data
  if (body.code !== 0) return Promise.reject(Object.assign(new Error(body.message || '请求失败'), { code: body.code, requestId: body.requestId }))
  return body.data
}, error => {
  const body = error.response?.data
  if (error.response?.status === 401) window.dispatchEvent(new Event('delivery:unauthorized'))
  const messages = { OUT_OF_STOCK: '库存刚刚发生变化，暂时无法购买，请返回商品列表。', TOO_MANY_REQUESTS: '操作过于频繁，请稍后再试。', AUTH_REQUIRED: '登录已过期，请重新登录。', FORBIDDEN: '你没有权限执行这项操作。', CODE_ALREADY_USED: '该兑换码已使用，无法重复兑换。' }
  const message = messages[body?.code] || body?.message || (error.code === 'ECONNABORTED' ? '请求超时，请重试；支付结果可在订单详情确认。' : '服务暂时无法连接，请稍后重试。')
  return Promise.reject(Object.assign(new Error(message), { code: body?.code, status: error.response?.status, requestId: body?.requestId }))
})

export const money = value => Number(value ?? 0).toFixed(2)
export const time = value => value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '—'
export const orderStates = { WAIT_PAY: '待支付', PAID: '待发货', DELIVERED: '已发货', COMPLETED: '已完成', CANCELLED: '已取消', EXPIRED: '已超时' }
export const codeStates = { UNUSED: '未分配', LOCKED: '处理中', ASSIGNED: '已分配', USED: '已兑换', EXPIRED: '已过期', DISABLED: '已作废' }
export const stateType = state => ({ WAIT_PAY: 'warning', PAID: 'warning', DELIVERED: 'primary', COMPLETED: 'success', UNUSED: 'success', ASSIGNED: 'primary', USED: 'success', CANCELLED: 'info', EXPIRED: 'info', DISABLED: 'danger' }[state] || 'info')
export const safeCover = url => {
  if (!url) return ''
  try { const parsed = new URL(url, window.location.origin); return ['http:', 'https:'].includes(parsed.protocol) ? parsed.href : '' } catch { return '' }
}
