<script setup>
import { computed, reactive, shallowRef, watch } from 'vue'
import { api } from '../session'
const props=defineProps({project:Object})
const emit=defineEmits(['saved','dirty','busy'])
const form=reactive({outline:'',worldSetting:'',writingStyle:'',version:0}),baseline=shallowRef(''),busy=shallowRef(false),error=shallowRef(''),saved=shallowRef(false)
watch(()=>props.project,value=>{Object.assign(form,value);baseline.value=JSON.stringify(form)},{immediate:true})
const dirty=computed(()=>JSON.stringify(form)!==baseline.value)
watch(dirty,value=>emit('dirty',value),{immediate:true})
async function save(){
  if(busy.value)return
  busy.value=true;emit('busy',true);error.value='';saved.value=false
  try{const data=await api(`/author/novels/${props.project.novelId}/writing`,{method:'PUT',body:{outline:form.outline,worldSetting:form.worldSetting,writingStyle:form.writingStyle,version:form.version}});emit('saved',data);Object.assign(form,data);baseline.value=JSON.stringify(form);saved.value=true}
  catch(e){error.value=e.message}finally{busy.value=false;emit('busy',false)}
}
</script>
<template>
  <form class="project-planning" @submit.prevent="save"><div class="planning-main"><label for="story-outline">全书大纲</label><p class="muted">故事的主线、冲突、阶段安排和结局方向。写章时会结合本章目标使用。</p><textarea id="story-outline" v-model="form.outline" maxlength="4000" rows="14" :disabled="busy" placeholder="从一个核心冲突开始，再安排人物如何一步步面对它…" /></div>
    <div class="planning-settings"><label for="story-world">世界与规则</label><textarea id="story-world" v-model="form.worldSetting" maxlength="3000" rows="7" :disabled="busy" placeholder="时代背景、地点、能力边界、不可改变的事实…" /><label for="story-style">文风与叙述视角</label><textarea id="story-style" v-model="form.writingStyle" maxlength="1000" rows="5" :disabled="busy" placeholder="例如：第三人称，节奏舒缓，以动作和对话推进，避免过多解释。" /></div>
    <div class="planning-actions"><el-button type="primary" native-type="submit" :loading="busy">保存创作设定</el-button><span class="muted" role="status">{{ dirty?'有未保存的修改':saved?'创作设定已保存':'创作资料仅自己可见' }}</span></div><el-alert v-if="error" :title="error" type="error" :closable="false" />
  </form>
</template>
<style scoped>
.project-planning{display:grid;grid-template-columns:minmax(0,1.4fr) minmax(0,1fr);gap:24px}.planning-main,.planning-settings{padding:22px;background:white;border:1px solid var(--line);border-radius:10px;min-width:0}.planning-main>label{font-size:20px;font-weight:600}.planning-actions{grid-column:1/-1;display:flex;gap:16px;align-items:center;flex-wrap:wrap}.planning-settings label:not(:first-child){margin-top:24px}.project-planning textarea{display:block;width:100%;padding:12px 14px;border:1px solid #bdcbc3;border-radius:7px;line-height:1.9;resize:vertical;background:#fff;color:var(--ink)}@media(max-width:850px){.project-planning{grid-template-columns:1fr}}
</style>
