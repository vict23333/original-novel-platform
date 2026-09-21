<script setup>
import { ref, reactive, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '../session'
import { useUnsavedChanges } from '../useUnsavedChanges'
import ContentState from '../components/ContentState.vue'
const route = useRoute(), router = useRouter()
const chapter = ref(null), book = ref(null), loading = ref(true), busy = ref(false), error = ref(''), baseline = ref('')
const form = reactive({ chapterNumber: 1, title: '', content: '' })
const isNew = computed(() => !route.params.id), pending = computed(() => chapter.value?.reviewStatus === 'PENDING')
const dirty = computed(() => !loading.value && JSON.stringify(form) !== baseline.value)
useUnsavedChanges(dirty)
function accept(data) { chapter.value = data; Object.assign(form, { chapterNumber: data.chapterNumber, title: data.title, content: data.content }); baseline.value = JSON.stringify(form) }
let loadId = 0
async function load() {
  const current = ++loadId, target = route.params.id, novelId = route.params.novelId
  loading.value = true; error.value = ''; chapter.value = null; book.value = null
  try {
    if (!target) {
      const data = await api(`/author/novels/${novelId}`)
      if (current !== loadId) return
      book.value = data; Object.assign(form, { chapterNumber: 1, title: '', content: '' }); baseline.value = JSON.stringify(form)
    } else {
      const data = await api(`/author/chapters/${target}`), parent = await api(`/author/novels/${data.novelId}`)
      if (current !== loadId) return
      book.value = parent; accept(data)
    }
  } catch (e) { if (current === loadId) error.value = e.message }
  finally { if (current === loadId) loading.value = false }
}
async function save() {
  if (busy.value) return
  busy.value = true; error.value = ''
  try {
    const created = isNew.value
    const data = await api(created ? `/author/novels/${book.value.id}/chapters` : `/author/chapters/${chapter.value.id}`, { method: created ? 'POST' : 'PUT', body: { ...form, chapterNumber: Number(form.chapterNumber), version: chapter.value?.version ?? 0 } })
    accept(data); ElMessage.success('章节草稿已保存')
    if (created) await router.replace(`/author/chapters/${data.id}`)
  } catch (e) { error.value = e.message }
  finally { busy.value = false }
}
async function submit() {
  if (dirty.value || busy.value) return
  try { await ElMessageBox.confirm('审核期间章节不能修改或删除，通过后公开此版本。', '提交章节审核', { confirmButtonText: '提交审核', cancelButtonText: '继续编辑' }) } catch { return }
  busy.value = true; error.value = ''
  try { accept(await api(`/author/chapters/${chapter.value.id}/submit`, { method: 'POST', body: { version: chapter.value.version } })); ElMessage.success('章节已提交审核') }
  catch (e) { error.value = e.message } finally { busy.value = false }
}
async function remove() {
  try { await ElMessageBox.confirm('删除后读者将无法阅读本章节，现有内容不会自动恢复。确认删除？', '删除章节', { confirmButtonText: '确认删除', cancelButtonText: '保留章节', type: 'warning' }) } catch { return }
  busy.value = true; error.value = ''
  try { await api(`/author/chapters/${chapter.value.id}?version=${chapter.value.version}`, { method: 'DELETE' }); baseline.value = JSON.stringify(form); ElMessage.success('章节已删除'); await router.push(`/author/novels/${book.value.id}`) }
  catch (e) { error.value = e.message } finally { busy.value = false }
}
watch(() => route.fullPath, load, { immediate: true })
</script>
<template><section class="page-wrap"><router-link class="quiet-link" :to="book ? `/author/novels/${book.id}` : '/author'">← 返回作品管理</router-link><div class="editor-heading"><div><p v-if="book" class="eyebrow">{{ book.title }}</p><h1 class="page-title">{{ isNew ? '新建章节' : '章节编辑' }}</h1></div><div v-if="chapter" class="state-line"><ContentState :item="chapter" /></div></div><el-alert v-if="error" :title="error" type="error" :closable="false" class="form-alert" /><el-button v-if="error && !dirty" :disabled="busy" @click="load">重新加载</el-button><div v-if="book && !loading" class="editor-card"><form @submit.prevent="save"><div class="chapter-fields"><div><label for="chapter-number">章节序号</label><input id="chapter-number" v-model="form.chapterNumber" class="native-input" type="number" min="1" max="100000" required :disabled="busy || pending" /></div><div><label for="chapter-title">章节标题</label><el-input id="chapter-title" v-model="form.title" :maxlength="100" required :disabled="busy || pending" placeholder="这一章的故事，从这里开始" /></div></div><label for="chapter-content">章节正文</label><el-input id="chapter-content" v-model="form.content" type="textarea" :rows="20" :maxlength="20000" show-word-limit :disabled="busy || pending" placeholder="写下你的正文。空白内容可以暂存，但不能提交审核。" /><p v-if="chapter?.reviewReason" class="review-reason">最近审核反馈：{{ chapter.reviewReason }}</p><div class="editor-actions"><el-button type="primary" native-type="submit" :loading="busy" :disabled="pending">保存草稿</el-button><el-button v-if="chapter" :disabled="busy || dirty || pending || chapter.reviewStatus === 'APPROVED'" @click="submit">提交审核</el-button><el-button v-if="chapter" type="danger" plain :disabled="busy || pending" @click="remove">删除章节</el-button><span role="status" class="muted">{{ dirty ? '有未保存修改' : pending ? '等待审核，暂不可编辑' : isNew ? '填写内容后保存草稿' : '当前内容已保存' }}</span></div></form></div></section></template>
