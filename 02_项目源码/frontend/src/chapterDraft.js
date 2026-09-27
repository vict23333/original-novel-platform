export const draftPrefix = 'mojian_chapter_draft_v1:'
export const draftLifetime = 7 * 24 * 60 * 60 * 1000
export function draftKey(userId, scope) {
  if (!Number.isSafeInteger(Number(userId)) || Number(userId) < 1 || !/^(?:chapter|novel-new):[1-9]\d*$/.test(scope)) throw new Error('草稿身份不正确')
  return `${draftPrefix}${userId}:${scope}`
}
export function draftForm(value) {
  if (!value || typeof value.title !== 'string' || value.title.length > 100 || typeof value.content !== 'string' || value.content.length > 20000 || !['string', 'number'].includes(typeof value.chapterNumber)) throw new Error('本地草稿格式不正确')
  return { chapterNumber: value.chapterNumber, title: value.title, content: value.content }
}
export function parseDraft(raw, now = Date.now()) {
  if (!raw) return null
  const value = JSON.parse(raw)
  if (value.schema !== 1 || !Number.isFinite(value.savedAt) || value.savedAt > now + 60000 || !Number.isSafeInteger(value.version) || value.version < 0) throw new Error('本地草稿格式不正确')
  if (now - value.savedAt > draftLifetime) return null
  return { ...value, form: draftForm(value.form) }
}
// 比较上次读取的完整值，另一标签页写入后不覆盖，也不清除它的草稿。
export function writeDraft(storage, key, expected, form, version, now = Date.now()) {
  if (storage.getItem(key) !== expected) throw new Error('另一标签页更新了本地草稿。请先保存到服务器或导出正文，本页暂不覆盖本地备份。')
  const raw = JSON.stringify({ schema: 1, savedAt: now, version, form: draftForm(form) })
  storage.setItem(key, raw)
  return raw
}
export function clearDraft(storage, key, expected) {
  if (storage.getItem(key) !== expected) return false
  storage.removeItem(key)
  return true
}
export function chapterText(form) { return `第 ${form.chapterNumber || '?'} 章 ${form.title || '未命名章节'}\r\n\r\n${form.content.replace(/\r\n?|\n/g, '\r\n')}` }
export function chapterFileName(form) { return `${(`第${form.chapterNumber || 0}章-${form.title || '未命名章节'}`).replace(/[<>:"/\\|?*\u0000-\u001f]/g, '_').slice(0, 100)}.txt` }
