import { test } from 'node:test'
import assert from 'node:assert/strict'
import { draftKey, parseDraft, writeDraft, clearDraft, chapterText, chapterFileName, draftLifetime } from '../src/chapterDraft.js'
const form = { chapterNumber: 2, title: '第二章', content: '未保存的正文\n第二段' }
function storage() { const values = new Map(); return { getItem: key => values.get(key) ?? null, setItem: (key, value) => values.set(key, value), removeItem: key => values.delete(key) } }
test('草稿按账号、章节、新建作品隔离', () => {
  const keys = [draftKey(1, 'chapter:2'), draftKey(2, 'chapter:2'), draftKey(1, 'novel-new:2')]
  assert.equal(new Set(keys).size, 3)
  assert.throws(() => draftKey(null, 'chapter:2'))
  assert.throws(() => draftKey(1, '../chapter:2'))
})
test('本地备份能还原标题、序号、正文与服务器版本', () => {
  const s = storage(), key = draftKey(1, 'chapter:2')
  const raw = writeDraft(s, key, null, form, 3, 100000)
  assert.deepEqual(parseDraft(raw, 100001).form, form)
  assert.equal(parseDraft(raw, 100001).version, 3)
})
test('七天过期和损坏数据不会作为可恢复正文', () => {
  const raw = writeDraft(storage(), 'key', null, form, 0, 100000)
  assert.equal(parseDraft(raw, 100001 + draftLifetime), null)
  assert.throws(() => parseDraft('{bad'))
  assert.throws(() => parseDraft(JSON.stringify({ schema: 1, savedAt: 100000, version: 0, form: { ...form, content: 123 } }), 100000))
})
test('发现其他标签页写入后不覆盖，也不删除它的备份', () => {
  const s = storage(), raw = writeDraft(s, 'key', null, form, 0)
  const newer = writeDraft(s, 'key', raw, { ...form, content: '另一页的新内容' }, 0)
  assert.throws(() => writeDraft(s, 'key', raw, form, 0), /另一标签页/)
  assert.equal(clearDraft(s, 'key', raw), false)
  assert.equal(s.getItem('key'), newer)
})
test('服务器保存后只清理自己的备份', () => {
  const s = storage(), raw = writeDraft(s, 'key', null, form, 0)
  assert.equal(clearDraft(s, 'key', raw), true)
  assert.equal(s.getItem('key'), null)
})
test('导出纯文本保留段落且文件名不能包含路径字符', () => {
  assert.equal(chapterText(form), '第 2 章 第二章\r\n\r\n未保存的正文\r\n第二段')
  assert.equal(/[<>:"/\\|?*]/.test(chapterFileName({ ...form, title: '../带/路径:*标题' })), false)
})
test('存储被禁用或空间不足时向编辑页面报告失败', () => {
  const s = storage();s.setItem = () => { throw new Error('quota') }
  assert.throws(() => writeDraft(s, 'key', null, form, 0), /quota/)
})
