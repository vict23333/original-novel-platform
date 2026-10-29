<script setup>
import { computed,onBeforeUnmount,shallowRef,watch } from 'vue'
import { api } from '../session'
const props=defineProps({novelId:Number,chapterNumber:Number,request:Object,disabled:Boolean})
const emit=defineEmits(['prepared','ready'])
const characters=shallowRef([]),previous=shallowRef([]),characterIds=shallowRef([]),previousIds=shallowRef([])
const ready=shallowRef(false),loading=shallowRef(false),preparing=shallowRef(false),error=shallowRef(''),preview=shallowRef(null)
let assetsGeneration=0,contextGeneration=0
function invalidate(){contextGeneration++;preparing.value=false;preview.value=null;emit('prepared',null)}
async function load(){
  const current=++assetsGeneration;ready.value=false;emit('ready',false);loading.value=true;error.value='';invalidate()
  try {
    const [workspace,options]=await Promise.all([api(`/author/novels/${props.novelId}/writing`),api(`/author/novels/${props.novelId}/writing/previous?before=${props.chapterNumber}`)])
    if(current!==assetsGeneration)return
    characters.value=workspace.characters;previous.value=options;characterIds.value=[];previousIds.value=options.slice(0,2).map(c=>c.id);ready.value=true;emit('ready',true)
  }catch(e){if(current===assetsGeneration)error.value=e.message}finally{if(current===assetsGeneration)loading.value=false}
}
async function prepare(){
  if(!props.request||!ready.value||props.disabled)return
  const current=++contextGeneration;preview.value=null;emit('prepared',null);preparing.value=true;error.value=''
  const request={...props.request,characterIds:[...characterIds.value],previousChapterIds:[...previousIds.value]}
  try {const data=await api(`/author/novels/${props.novelId}/writing/context`,{method:'POST',body:request});if(current===contextGeneration){preview.value=data;emit('prepared',{view:data,request:{...request,contextDigest:data.digest}})}}
  catch(e){if(current===contextGeneration)error.value=e.message}finally{if(current===contextGeneration)preparing.value=false}
}
watch([()=>props.novelId,()=>props.chapterNumber],load,{immediate:true})
watch([()=>props.request,ready],()=>{invalidate();if(props.request&&ready.value)prepare()})
watch([characterIds,previousIds],invalidate)
const visibleSources=computed(()=>preview.value?.payload.sources.filter(s=>s.kind!=='CURRENT')||[])
onBeforeUnmount(()=>{assetsGeneration++;contextGeneration++})
</script>
<template>
  <div class="context-panel"><div class="context-heading"><h3>本次引用资料</h3><router-link :to="`/author/novels/${novelId}/studio`">维护大纲与角色</router-link></div><p class="muted">只引用本书已保存的较早章节。默认选最近两章，每章最多引用末尾 2000 字符。</p>
    <p v-if="loading" role="status">正在读取创作资料…</p><el-button v-if="!ready&&!loading" @click="load">重新加载资料</el-button>
    <fieldset v-if="ready"><legend>出场角色（最多 6 个）</legend><p v-if="!characters.length" class="muted">尚无角色卡，可先填写本章目标。</p><label v-for="item in characters" :key="item.id" class="context-choice"><input v-model="characterIds" type="checkbox" :value="item.id" :disabled="disabled||(!characterIds.includes(item.id)&&characterIds.length>=6)" />{{ item.name }}</label></fieldset>
    <details v-if="ready&&previous.length" class="previous-picker"><summary>选择前文（已选 {{ previousIds.length }} / 2）</summary><label v-for="item in previous" :key="item.id" class="context-choice"><input v-model="previousIds" type="checkbox" :value="item.id" :disabled="disabled||(!previousIds.includes(item.id)&&previousIds.length>=2)" />第 {{ item.chapterNumber }} 章 · {{ item.title }}</label></details>
    <el-alert v-if="error" :title="error" type="error" :closable="false" /><p v-if="preparing" role="status">正在准备引用预览，不调用 AI…</p><el-button v-if="request&&!preparing&&ready" :disabled="disabled" @click="prepare">{{ preview?'更新引用预览':'重新预览引用' }}</el-button>
    <div v-if="preview" class="context-preview"><p role="status">本次资料 {{ preview.inputCharacters }} 字符（含格式信息），尚未调用 AI。</p><details><summary>查看将发送的全部资料</summary><section><strong>任务、目标与要求</strong><p class="source-text">{{ preview.payload.task }} · 第 {{ preview.payload.chapterNumber }} 章
{{ preview.payload.goal }}
{{ preview.payload.authorInstructions }}</p></section><section v-for="(source,index) in visibleSources" :key="index"><strong>{{ source.label }}</strong><small>保存版本 {{ source.version }}{{ source.omittedCharacters?` · 已省略前 ${source.omittedCharacters} 字符`:'' }}</small><p class="source-text">{{ source.text||'（未填写）' }}</p></section><section><strong>当前素材</strong><p class="source-text">{{ preview.payload.currentText||'（空白章，按目标起草）' }}</p></section></details></div>
  </div>
</template>
<style scoped>
.context-panel{margin-top:18px;padding-top:14px;border-top:1px solid var(--line)}.context-heading{display:flex;gap:12px;justify-content:space-between;align-items:center;flex-wrap:wrap}.context-heading h3{font-size:16px;margin:0}.context-heading a{font-size:12px;color:var(--brand)}fieldset{border:1px solid var(--line);padding:12px;border-radius:6px;margin:12px 0}.context-choice{display:flex;align-items:center;gap:9px;padding:7px 0;margin:0;font-size:13px}.context-choice input{width:17px;height:17px;flex-shrink:0}.previous-picker{margin:14px 0;max-height:240px;overflow:auto}.previous-picker summary,.context-preview summary{cursor:pointer}.context-preview{font-size:13px;margin-top:14px;background:#f2f7f4;padding:12px;border-radius:8px}.context-preview section{border-top:1px solid var(--line);padding-top:12px;margin-top:12px}.context-preview small{display:block;color:var(--muted);margin-top:5px}.source-text{white-space:pre-wrap;overflow-wrap:anywhere;max-height:220px;overflow:auto;line-height:1.8}.muted{font-size:13px;line-height:1.8}
</style>
