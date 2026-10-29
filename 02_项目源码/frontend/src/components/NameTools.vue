<script setup>
import { shallowRef } from 'vue'
import { generateNames } from '../nameTools'
const props=defineProps({existing:{type:Array,default:()=>[]},disabled:Boolean})
const emit=defineEmits(['choose'])
const kind=shallowRef('person'),style=shallowRef('ancient'),names=shallowRef([]),message=shallowRef('')
function create(){names.value=generateNames({kind:kind.value,style:style.value,existing:props.existing});message.value=''}
async function choose(name){
  if(kind.value==='person')emit('choose',name)
  else try {await navigator.clipboard.writeText(name);message.value=`已复制：${name}`} catch {message.value=`请手动复制：${name}`}
}
</script>
<template>
  <details class="name-tools"><summary>取名工具 · 本地生成</summary><p class="muted">规则组合，不调用 AI。人名候选避开本书已保存的角色名，可继续修改。</p>
    <div class="name-options"><label>名称类型<select v-model="kind" :disabled="disabled" @change="names=[]"><option value="person">人名</option><option value="place">地名</option><option value="faction">组织名</option></select></label><label v-if="kind==='person'">人名风格<select v-model="style" :disabled="disabled" @change="names=[]"><option value="modern">现代</option><option value="ancient">古风</option><option value="fantasy">幻想</option></select></label><el-button :disabled="disabled" @click="create">生成候选</el-button></div>
    <div class="name-results"><button v-for="name in names" :key="name" type="button" :disabled="disabled" @click="choose(name)">{{ name }} <small>{{ kind==='person'?'填入姓名':'复制' }}</small></button></div><p v-if="message" role="status">{{ message }}</p>
  </details>
</template>
<style scoped>
.name-tools{margin:20px 0;padding:16px;background:#f3f8f5;border:1px solid var(--line);border-radius:8px}.name-tools summary{cursor:pointer;font-weight:600}.name-options{display:flex;align-items:end;flex-wrap:wrap;gap:12px}.name-options label{flex:1;min-width:100px;margin:0}.name-options select{margin-top:8px}.name-results{display:flex;flex-wrap:wrap;gap:8px;margin-top:14px}.name-results button{padding:10px;border:1px solid #b8cfc2;background:white;border-radius:6px;color:var(--ink)}.name-results small{color:var(--muted)}
</style>
