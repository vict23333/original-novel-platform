import test from 'node:test'
import assert from 'node:assert/strict'
import { prepareAiDraft, applyAiDraft, undoAiDraft } from '../src/aiDraft.js'

test('续写只发送末尾上下文，采用后保留完整原稿', () => {
  const source = '甲'.repeat(7000), draft = prepareAiDraft(source, 'CONTINUE')
  assert.equal(draft.text.length, 6000)
  assert.equal(applyAiDraft(source, draft, '新段落'), source + '\n\n新段落')
})
test('润色选段仅替换对应位置，重复词句不会误替换', () => {
  const source = '相同。开头。相同。结尾。', draft = prepareAiDraft(source, 'POLISH', { start: 6, end: 9 })
  assert.equal(draft.text, '相同。')
  assert.equal(applyAiDraft(source, draft, '修改。'), '相同。开头。修改。结尾。')
})
test('没有选区时润色全文，长正文要求选段', () => {
  const draft = prepareAiDraft('原文', 'POLISH')
  assert.equal(applyAiDraft('原文', draft, '润色正文'), '润色正文')
  assert.throws(() => prepareAiDraft('字'.repeat(6001), 'POLISH'), /选中/)
})
test('生成期间原稿改变时拒绝覆盖', () => {
  const draft = prepareAiDraft('原文', 'CONTINUE')
  assert.throws(() => applyAiDraft('新原文', draft, '生成内容'), /正文已/)
})
test('空正文、空预览和采用后超长均有明确错误', () => {
  assert.throws(() => prepareAiDraft('  ', 'CONTINUE'), /先填写/)
  const draft = prepareAiDraft('原文', 'CONTINUE')
  assert.throws(() => applyAiDraft('原文', draft, ' '), /不能为空/)
  const long = '字'.repeat(20000)
  assert.throws(() => applyAiDraft(long, prepareAiDraft(long, 'CONTINUE'), '新'), /超过/)
})
test('只撤销仍与采用结果一致的正文，保护后续手动编辑', () => {
  const change = { before: '原稿', after: '采用结果' }
  assert.equal(undoAiDraft('采用结果', change), '原稿')
  assert.throws(() => undoAiDraft('后续手动改稿', change), /新修改/)
})
