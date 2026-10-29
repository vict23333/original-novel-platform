<script setup>
import {computed,onBeforeUnmount,reactive,shallowRef,watch} from 'vue'
import { useRoute } from 'vue-router'
import { api } from '../session'
import { useUnsavedChanges } from '../useUnsavedChanges'
import WritingProjectForm from '../components/WritingProjectForm.vue'
import WritingCharacters from '../components/WritingCharacters.vue'
const route=useRoute(),book=shallowRef(null),workspace=shallowRef(null),error=shallowRef(''),loading=shallowRef(false),tab=shallowRef('project')
const dirty=reactive({project:false,characters:false}),busy=reactive({project:false,characters:false})
useUnsavedChanges(computed(()=>dirty.project||dirty.characters),computed(()=>busy.project||busy.characters))
let generation=0
onBeforeUnmount(()=>generation++)
async function load(){
  const id=route.params.id
  const current=++generation;loading.value=true;error.value='';book.value=null;workspace.value=null;dirty.project=false;dirty.characters=false
  try{const [novel,data]=await Promise.all([api(`/author/novels/${id}`),api(`/author/novels/${id}/writing`)]);if(current===generation){book.value=novel;workspace.value=data}}
  catch(e){if(current===generation)error.value=e.message}finally{if(current===generation)loading.value=false}
}
watch(()=>route.params.id,load,{immediate:true})
</script>
<template>
  <section class="page-wrap studio-page" v-loading="loading"><router-link class="quiet-link" :to="`/author/novels/${route.params.id}`">返回正文与章节目录</router-link><div class="section-heading"><div><h1 class="page-title">{{ book?.title||'作品' }} · 创作资料</h1><p class="page-intro">先整理故事和人物，写下一章时选择引用。所有资料仅自己可见。</p></div><router-link v-if="book" class="solid-link" :to="`/author/novels/${book.id}/chapters/new`">写下一章</router-link></div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" /><el-button v-if="error" @click="load">重新加载创作资料</el-button><nav v-if="workspace" class="studio-tabs" aria-label="创作资料分区"><button :aria-pressed="tab==='project'" @click="tab='project'">大纲与故事设定</button><button :aria-pressed="tab==='characters'" @click="tab='characters'">角色与取名</button></nav>
    <template v-if="workspace"><WritingProjectForm v-show="tab==='project'" :project="workspace.project" @saved="workspace={...workspace,project:$event}" @dirty="dirty.project=$event" @busy="busy.project=$event" /><WritingCharacters v-show="tab==='characters'" :novel-id="book.id" :characters="workspace.characters" @changed="workspace={...workspace,characters:$event}" @dirty="dirty.characters=$event" @busy="busy.characters=$event" /></template>
  </section>
</template>
<style scoped>
.studio-tabs{display:flex;flex-wrap:wrap;gap:12px;margin:24px 0}.studio-tabs button{padding:12px 20px;background:white;border:1px solid var(--line);border-radius:8px;color:var(--ink)}.studio-tabs button[aria-pressed=true]{background:var(--brand);color:white}.studio-page>.section-heading{margin-top:22px}
</style>
