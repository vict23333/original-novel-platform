import { rankingKinds } from './discovery.js'

const sorts = new Set(rankingKinds.map(kind => kind.value))
function positiveInteger(value, fallback, max) {
  if (!['string', 'number'].includes(typeof value) || !/^\d+$/.test(String(value))) return fallback
  const number = Number(value)
  return Number.isSafeInteger(number) && number > 0 && number <= max ? number : fallback
}

// 地址栏属于外部输入：重复参数、非法枚举和越界数字统一回退。
export function normalizeLibraryQuery(query = {}) {
  return {
    keyword: typeof query.keyword === 'string' ? query.keyword.trim().slice(0, 100) : '',
    categoryId: String(positiveInteger(query.categoryId, '', Number.MAX_SAFE_INTEGER)),
    serialStatus: ['SERIALIZING', 'COMPLETED'].includes(query.serialStatus) ? query.serialStatus : '',
    sort: sorts.has(query.sort) ? query.sort : 'NEWEST',
    page: positiveInteger(query.page, 1, 100000),
  }
}

export function libraryLocation(value) {
  const state = normalizeLibraryQuery(value)
  const query = {}
  if (state.keyword) query.keyword = state.keyword
  if (state.categoryId) query.categoryId = state.categoryId
  if (state.serialStatus) query.serialStatus = state.serialStatus
  if (state.sort !== 'NEWEST') query.sort = state.sort
  if (state.page > 1) query.page = String(state.page)
  return { path: '/library', query }
}

export function libraryRequest(value) {
  const state = normalizeLibraryQuery(value)
  const query = new URLSearchParams({ ...state, page: String(state.page), size: '12' })
  if (!state.categoryId) query.delete('categoryId')
  if (!state.serialStatus) query.delete('serialStatus')
  return `/public/novels?${query}`
}
