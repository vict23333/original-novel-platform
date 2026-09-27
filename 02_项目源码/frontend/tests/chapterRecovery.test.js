import { test } from 'node:test'
import assert from 'node:assert/strict'
import { useChapterDraft } from '../src/useChapterDraft.js'
import { draftKey, writeDraft } from '../src/chapterDraft.js'
function memory() { const m = new Map(); return { getItem: key => m.get(key) ?? null, setItem: (key, val) => m.set(key, val), removeItem: key => m.delete(key) } }
const server = { chapterNumber: 1, title: '服务器章节', content: '服务器内容' }
test('有备份时先要求处理，不被初次加载或空表单覆盖', () => {
  globalThis.localStorage = memory()
  const key = draftKey(1, 'chapter:2'), local = { ...server, content: '未保存内容' }
  const raw = writeDraft(localStorage, key, null, local, 4)
  const state = useChapterDraft(); state.initialize(1, 'chapter:2', server)
  state.cache(server, 5, false)
  assert.equal(localStorage.getItem(key), raw)
  assert.deepEqual(state.take().form, local)
  state.cache(local, 5, true)
  assert.equal(JSON.parse(localStorage.getItem(key)).version, 5)
  state.saved(); assert.equal(localStorage.getItem(key), null)
})
test('切换章节不恢复其他章节或其他账号的内容', () => {
  globalThis.localStorage = memory()
  const state = useChapterDraft();state.initialize(1, 'chapter:2', server)
  state.cache({ ...server, content: '章节二备份' }, 0, true)
  state.initialize(1, 'chapter:3', server);assert.equal(state.recovery.value, null)
  state.initialize(2, 'chapter:2', server);assert.equal(state.recovery.value, null)
  state.initialize(1, 'chapter:2', server);assert.equal(state.recovery.value.form.content, '章节二备份')
})
test('另一页面变更后拒绝清除，保留恢复提示和原数据', () => {
  globalThis.localStorage = memory()
  const key = draftKey(1, 'chapter:2')
  let raw = writeDraft(localStorage, key, null, { ...server, content: '旧本地' }, 0)
  const state = useChapterDraft();state.initialize(1, 'chapter:2', server)
  raw = writeDraft(localStorage, key, raw, { ...server, content: '新本地' }, 0)
  assert.equal(state.discard(), false)
  assert.equal(localStorage.getItem(key), raw)
  assert.match(state.warning.value, /另一标签页/)
})
