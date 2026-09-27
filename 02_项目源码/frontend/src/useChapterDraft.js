import { ref } from 'vue'
import { clearDraft, draftKey, parseDraft, writeDraft, chapterText, chapterFileName } from './chapterDraft.js'

export function useChapterDraft() {
  const recovery = ref(null), savedAt = ref(null), warning = ref('')
  let key = null, expected = null
  function initialize(userId, scope, serverForm) {
    key = null; expected = null; recovery.value = null; savedAt.value = null; warning.value = ''
    try {
      key = draftKey(userId, scope); expected = localStorage.getItem(key)
      const draft = parseDraft(expected)
      if (draft && JSON.stringify(draft.form) !== JSON.stringify(serverForm)) recovery.value = draft
      else if (expected) { clearDraft(localStorage, key, expected); expected = null }
    } catch { warning.value = '无法读取本地草稿，请及时保存到服务器或导出正文。' }
  }
  function cache(form, version, dirty) {
    if (!key || recovery.value) return
    try {
      if (!dirty) { if (clearDraft(localStorage, key, expected)) { expected = null; savedAt.value = null }; return }
      expected = writeDraft(localStorage, key, expected, form, version)
      savedAt.value = JSON.parse(expected).savedAt; warning.value = ''
    } catch (e) { warning.value = e.name === 'Error' ? e.message : '本地存储不可用或空间不足，请保存到服务器或导出正文。' }
  }
  function discard() {
    try {
      if (!clearDraft(localStorage, key, expected)) { warning.value = '另一标签页更新了草稿，未清除它的内容。'; return false }
      expected = null; recovery.value = null; savedAt.value = null; warning.value = ''; return true
    } catch { warning.value = '无法清除本地草稿，请检查浏览器存储权限。'; return false }
  }
  function take() { const value = recovery.value; recovery.value = null; return value }
  function saved() {
    try { if (key && clearDraft(localStorage, key, expected)) { expected = null; recovery.value = null; savedAt.value = null; warning.value = '' } }
    catch { warning.value = '服务器已保存，本地旧备份未清除。' }
  }
  function download(form) {
    const blob = new Blob(['\uFEFF', chapterText(form)], { type: 'text/plain;charset=utf-8' })
    const url = URL.createObjectURL(blob), anchor = document.createElement('a')
    anchor.href = url; anchor.download = chapterFileName(form); anchor.hidden = true
    document.body.appendChild(anchor); anchor.click(); anchor.remove()
    setTimeout(() => URL.revokeObjectURL(url), 10000)
  }
  return { recovery, savedAt, warning, initialize, cache, discard, take, saved, download }
}
