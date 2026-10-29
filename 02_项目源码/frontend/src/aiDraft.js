export function prepareAiDraft(content, mode, selection = { start: 0, end: 0 }, goal = '') {
  if (!['DRAFT', 'CONTINUE', 'POLISH', 'EXPAND'].includes(mode)) throw new Error('请选择起草、续写、润色或扩写')
  if(mode==='DRAFT' && (content.trim() || !goal.trim())) throw new Error('空白章请填写本章目标，已有正文请使用续写')
  let start = mode === 'CONTINUE' ? Math.max(0, content.length - 6000) : 0
  let end = content.length
  if (['POLISH','EXPAND'].includes(mode) && selection.end > selection.start) {
    start = Math.max(0, selection.start); end = Math.min(content.length, selection.end)
  }
  const text = content.slice(start, end)
  if (!text.trim() && mode!=='DRAFT') throw new Error('请先填写正文或选择需要处理的文字')
  if (text.length > 6000) throw new Error('一次最多润色 6000 字符，请先在正文中选中较短的段落')
  return { base: content, mode, start, end, text }
}

export function applyAiDraft(current, draft, result) {
  if (current !== draft.base) throw new Error('正文已在生成后修改，请重新生成，避免覆盖你的新内容')
  if (!result.trim()) throw new Error('预览内容不能为空')
  const content = draft.mode === 'DRAFT' ? result
    : draft.mode === 'CONTINUE'
    ? `${current}\n\n${result}`
    : current.slice(0, draft.start) + result + current.slice(draft.end)
  if (content.length > 20000) throw new Error('采用后正文超过 20000 字符，请缩短预览内容再试')
  return content
}

export function undoAiDraft(current, change) {
  if (!change || current !== change.after) throw new Error('采用后正文已有新修改，无法直接撤销，请手动编辑')
  return change.before
}
