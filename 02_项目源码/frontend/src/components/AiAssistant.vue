<script setup>
import { ref, shallowRef, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { api } from '../session'
import { prepareAiDraft, applyAiDraft, undoAiDraft } from '../aiDraft'
import AiContextPanel from './AiContextPanel.vue'
const props = defineProps({ novelId:Number, chapter:Object, chapterNumber:Number, goal:String, content:String, disabled:Boolean, selectionReader:Function })
const emit=defineEmits(['apply','busy','dirty'])
const status=shallowRef(null),checking=shallowRef(false),generating=shallowRef(false),error=shallowRef(''),contextReady=shallowRef(false)
const instructions=ref(''),preview=ref(''),draft=shallowRef(null),truncated=shallowRef(false),lastChange=shallowRef(null),hasPreview=shallowRef(false)
const stagedTask=shallowRef(null),prepared=shallowRef(null)
const locked=computed(()=>props.disabled||generating.value)
const stale=computed(()=>Boolean(draft.value&&(props.content!==draft.value.base||props.goal!==draft.value.goal||props.chapterNumber!==draft.value.chapterNumber||(props.chapter?.version??null)!==draft.value.version)))
let generation=0,controller=null,alive=true
watch(hasPreview,value=>emit('dirty',value),{immediate:true})
watch([()=>props.content,()=>props.goal,()=>props.chapterNumber,()=>props.chapter?.version,instructions],()=>{
  if(!hasPreview.value&&!generating.value){stagedTask.value=null;prepared.value=null;draft.value=null}
})
async function checkStatus(){checking.value=true;error.value='';try{const result=await api('/author/ai/status');if(alive)status.value=result}catch(e){if(alive)error.value=e.message}finally{if(alive)checking.value=false}}
function stage(mode){
  if(locked.value||hasPreview.value||!contextReady.value)return
  error.value=''
  try{
    const value=prepareAiDraft(props.content,mode,props.selectionReader?.(),props.goal||'')
    draft.value={...value,goal:props.goal,chapterNumber:props.chapterNumber,version:props.chapter?.version??null}
    prepared.value=null
    stagedTask.value={chapterId:props.chapter?.id??null,version:props.chapter?.version??null,chapterNumber:props.chapterNumber,mode,text:value.text,goal:props.goal||'',instructions:instructions.value}
  }catch(e){error.value=e.message}
}
async function generate(){
  if(locked.value||!prepared.value||hasPreview.value||!status.value?.available||stale.value)return
  const snapshot=prepared.value,current=++generation
  controller=new AbortController();generating.value=true;emit('busy',true);error.value=''
  try{const result=await api(`/author/novels/${props.novelId}/writing/generate`,{method:'POST',signal:controller.signal,body:snapshot.request});if(!alive||current!==generation)return;preview.value=result.content;truncated.value=result.truncated;hasPreview.value=true}
  catch(e){if(alive&&current===generation&&e.name!=='AbortError')error.value=e.message}
  finally{if(alive&&current===generation){generating.value=false;emit('busy',false)}}
}
function discard(){preview.value='';hasPreview.value=false;draft.value=null;stagedTask.value=null;prepared.value=null;truncated.value=false;error.value=''}
function apply(){
  if(locked.value||stale.value)return
  try{const after=applyAiDraft(props.content,draft.value,preview.value);lastChange.value={before:props.content,after};emit('apply',after);discard()}catch(e){error.value=e.message}
}
function undo(){if(locked.value)return;try{emit('apply',undoAiDraft(props.content,lastChange.value));lastChange.value=null;error.value=''}catch(e){error.value=e.message}}
onMounted(checkStatus)
onBeforeUnmount(()=>{alive=false;generation++;controller?.abort();emit('busy',false);emit('dirty',false)})
</script>
<template>
  <section class="ai-assistant" aria-labelledby="ai-title" :aria-busy="generating">
    <div class="ai-heading"><h2 id="ai-title">AI 写作助手</h2><span class="ai-badge">作者确认后采用</span></div>
    <p class="muted">填好本章目标，选人物和前文，再预览引用资料。生成候选不会自动保存或送审。</p>
    <p v-if="checking" role="status">正在检查 AI 配置…</p><div v-else-if="!status?.available" class="ai-notice"><p>{{ status?'AI 尚未启用，仍可维护设定和预览引用资料。':'暂时无法读取 AI 配置状态。' }}</p><el-button @click="checkStatus">重新检查配置</el-button></div><p v-else class="muted">{{ status.model }} · 调用间隔 {{ status.cooldownSeconds }} 秒</p>
    <label for="ai-instructions">本次创作要求（可选）</label><el-input id="ai-instructions" v-model="instructions" :maxlength="500" type="textarea" :rows="2" :disabled="locked||hasPreview" placeholder="例如：以对话推进，不提前揭开谜底…" />
    <AiContextPanel :novel-id="novelId" :chapter-number="chapterNumber" :request="stagedTask" :disabled="locked||hasPreview" @prepared="prepared=$event" @ready="contextReady=$event" />
    <div v-if="!hasPreview" class="ai-actions"><el-button :disabled="locked||!contextReady" @click="stage(content.trim()?'CONTINUE':'DRAFT')">{{ content.trim()?'承接前文续写':'按本章目标起草' }}</el-button><el-button :disabled="locked||!contextReady||!content.trim()" @click="stage('POLISH')">润色选段 / 全文</el-button><el-button :disabled="locked||!contextReady||!content.trim()" @click="stage('EXPAND')">扩写选段 / 全文</el-button></div>
    <div v-if="prepared&&!hasPreview" class="ai-notice"><p>点击后将以上资料发送至已配置的 AI 服务，并消耗 API 额度。结果先进入候选区。</p><el-button type="primary" :loading="generating" :disabled="locked||!status?.available||stale" @click="generate">确认资料并生成候选</el-button></div>
    <p v-if="generating" role="status">正在生成候选，原稿尚未改变…</p><el-alert v-if="error" :title="error" type="error" :closable="false" class="form-alert" />
    <div v-if="draft&&hasPreview" class="ai-preview"><label for="ai-preview">生成候选 · 可以继续修改</label><el-input id="ai-preview" v-model="preview" type="textarea" :rows="10" :maxlength="6000" show-word-limit :disabled="disabled" /><p v-if="truncated" class="ai-notice">生成达到长度限制，内容可能未完结，请检查后采用。</p><p v-if="stale" role="status" class="ai-notice">正文、本章目标或保存版本已变化，此候选不能直接采用。可保留文字，或放弃后重新生成。</p><div class="ai-actions"><el-button type="primary" :disabled="disabled||stale||!preview.trim()" @click="apply">{{ draft.mode==='DRAFT'?'填入正文':draft.mode==='CONTINUE'?'追加到正文':'替换原选段 / 全文' }}</el-button><el-button :disabled="disabled" @click="discard">放弃本次候选</el-button></div><p class="muted">采用后请检查正文，再保存草稿并提交审核。</p></div>
    <el-button v-if="lastChange" :disabled="disabled||generating||hasPreview||content!==lastChange.after" @click="undo">撤销上次采用</el-button>
  </section>
</template>
<style scoped>
.ai-assistant{margin:0;padding:22px;border:1px solid #dce4df;border-radius:10px;background:#fff;min-width:0}
.ai-heading{display:flex;align-items:center;justify-content:space-between;gap:16px}.ai-heading h2{margin:6px 0;font-size:23px}.ai-heading .eyebrow{margin:0}.ai-badge{font-size:12px;color:#426340;border:1px solid #b7c9ac;padding:7px 12px;border-radius:20px;white-space:nowrap}
.ai-assistant>.muted,.ai-preview>.muted{font-size:13px;line-height:1.9;overflow-wrap:anywhere}.ai-assistant label{display:block;margin:18px 0 10px}.ai-actions{display:flex;gap:10px;flex-wrap:wrap;margin:18px 0}.ai-actions .el-button+.el-button{margin-left:0}.ai-notice{font-size:13px;line-height:1.9;background:#fffaf0;border-radius:6px;padding:12px 16px}.ai-notice p{margin-top:0}.ai-preview{margin-top:20px;border-top:1px solid #cddac5;padding-top:18px}.ai-source{white-space:pre-wrap;overflow-wrap:anywhere;max-height:230px;overflow:auto;padding:14px;background:#fffefa;line-height:1.8}.ai-preview summary{cursor:pointer;font-size:13px;color:#426340}
@media(max-width:600px){.ai-assistant{padding:16px}.ai-heading{align-items:flex-start;flex-wrap:wrap}.ai-heading h2{font-size:21px}}
</style>
