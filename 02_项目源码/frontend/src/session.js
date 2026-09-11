import { reactive } from 'vue'

export const session = reactive({ user: null, token: sessionStorage.getItem('novel_token') || '' })
export const roleNames = { READER: '读者', AUTHOR: '作者', ADMIN: '管理员' }
export function clearSession() {
  session.token = ''; session.user = null; sessionStorage.removeItem('novel_token')
}
export function acceptLogin(result) {
  session.token = result.accessToken; session.user = result.user
  sessionStorage.setItem('novel_token', result.accessToken)
}
export async function api(path, { method = 'GET', body, publicRequest = false } = {}) {
  const headers = { Accept: 'application/json' }
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  if (session.token && !publicRequest) headers.Authorization = `Bearer ${session.token}`
  let response
  try { response = await fetch(`/api${path}`, { method, headers, credentials: 'omit', body: body === undefined ? undefined : JSON.stringify(body) }) }
  catch { throw new Error('暂时无法连接服务，请检查网络后重试。输入内容已保留。') }
  const data = response.status === 204 ? null : await response.json().catch(() => null)
  if (!response.ok) {
    if (response.status === 401 && !publicRequest) clearSession()
    const error = new Error(data?.message || '请求失败，请稍后重试。')
    error.status = response.status; throw error
  }
  return data
}
