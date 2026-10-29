import {test} from 'node:test'
import assert from 'node:assert/strict'
import {generateNames} from '../src/nameTools.js'
import {prepareAiDraft,applyAiDraft,undoAiDraft} from '../src/aiDraft.js'
import {draftForm} from '../src/chapterDraft.js'
import {safeDestination,loginDestination} from '../src/navigation.js'

test('空白章有目标可起草，采用无多余空行且支持撤销',()=>{
  assert.throws(()=>prepareAiDraft('','DRAFT'),/目标/)
  const draft=prepareAiDraft('','DRAFT',undefined,'追踪匿名信的来源')
  assert.equal(draft.text,'');assert.equal(applyAiDraft('',draft,'候选正文'),'候选正文')
  assert.equal(undoAiDraft('候选正文',{before:'',after:'候选正文'}),'')
  assert.throws(()=>prepareAiDraft('已有正文','DRAFT',undefined,'目标'),/续写/)
})
test('扩写只替换选段，正文变化后不得覆盖',()=>{
  const draft=prepareAiDraft('前文原句后文','EXPAND',{start:2,end:4})
  assert.equal(applyAiDraft('前文原句后文',draft,'补充后的句子'),'前文补充后的句子后文')
  assert.throws(()=>applyAiDraft('已修改',draft,'候选'),/重新生成/)
})
test('本章目标进入本地草稿，旧备份兼容且异常目标被拒绝',()=>{
  const value={chapterNumber:1,title:'第一章',content:'',goal:'找到入口'}
  assert.deepEqual(draftForm(value),value)
  assert.equal(draftForm({chapterNumber:1,title:'旧草稿',content:''}).goal,undefined)
  assert.throws(()=>draftForm({...value,goal:'字'.repeat(2001)}),/目标/)
})
test('取名候选有限、不重复、排除已有姓名且拒绝无效类型',()=>{
  for(const kind of ['person','place','faction']){
    const names=generateNames({kind,count:12,random:()=>0})
    assert.equal(names.length,12);assert.equal(new Set(names).size,12)
    assert.ok(!generateNames({kind,existing:names,random:()=>0}).some(n=>names.includes(n)))
  }
  assert.deepEqual(generateNames({kind:'invalid'}),[])
})
test('创作资料路由仅作作者安全回跳，不允许附加外部地址',()=>{
  assert.equal(safeDestination('/author/novels/5/studio'),'/author/novels/5/studio')
  assert.equal(safeDestination('/author/novels/5/studio?next=https://evil.invalid'),null)
  assert.equal(loginDestination({role:'READER'},'/author/novels/5/studio'),'/')
})
