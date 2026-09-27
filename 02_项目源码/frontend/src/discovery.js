export const rankingKinds = [
  { value: 'POPULAR', label: '本周热门', description: '按近七日阅读排序，同热度时优先收藏更多的作品。' },
  { value: 'COLLECTED', label: '收藏榜', description: '按当前书架收藏数排序，发现读者正在追的故事。' },
  { value: 'NEWEST', label: '新书榜', description: '按首次审核公开时间排序，邂逅刚刚启程的新故事。' },
  { value: 'UPDATED', label: '更新榜', description: '按章节最新审核通过时间排序，追上故事的新进展。' },
]
export function countText(value) { return Number(value || 0).toLocaleString('zh-CN') }
export function dateText(value) {
  if (!value) return '暂无公开更新'
  const date = new Date(/(?:Z|[+-]\d{2}:\d{2})$/.test(value) ? value : `${value}Z`)
  return Number.isNaN(date.getTime()) ? '暂无公开更新' : new Intl.DateTimeFormat('zh-CN', { timeZone: 'Asia/Shanghai', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false }).format(date)
}
let memoryGuestId = ''
export function guestIdentity(storage, randomId = () => crypto.randomUUID()) {
  try {
    storage ??= globalThis.sessionStorage
    const existing = storage.getItem('mojian_reading_guest')
    if (/^[\da-f]{8}-(?:[\da-f]{4}-){3}[\da-f]{12}$/i.test(existing || '')) return existing
    memoryGuestId ||= randomId()
    storage.setItem('mojian_reading_guest', memoryGuestId)
  } catch { memoryGuestId ||= randomId() }
  return memoryGuestId
}
