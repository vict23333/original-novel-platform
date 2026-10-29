<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '../session'
import { useUnsavedChanges } from '../useUnsavedChanges'
import ContentState from '../components/ContentState.vue'
import PageControls from '../components/PageControls.vue'
const route = useRoute(), router = useRouter()
const book = ref(null), categories = ref([]), busy = ref(false), loading = ref(true), error = ref(''), history = ref([])
const chapters = ref({ items: [], total: 0, page: 1, size: 20 }), baseline = ref('')
const form = reactive({ title: '', introduction: '', categoryId: '', serialStatus: 'SERIALIZING' })
const isNew = computed(() => route.params.id === 'new')
const pending = computed(() => book.value?.reviewStatus === 'PENDING')
const dirty = computed(() => !loading.value && JSON.stringify(form) !== baseline.value)
useUnsavedChanges(dirty)
function accept(data) {
  book.value = data
  Object.assign(form, { title: data.title, introduction: data.introduction, categoryId: data.categoryId, serialStatus: data.serialStatus })
  baseline.value = JSON.stringify(form)
}
async function loadChapters(page = 1) { chapters.value = await api(`/author/novels/${route.params.id}/chapters?page=${page}&size=20`) }
async function chapterPage(page) { try { await loadChapters(page) } catch (e) { error.value = e.message } }
let loadId = 0
async function load() {
  const current = ++loadId, target = route.params.id
  loading.value = true; error.value = ''; book.value = null
  try {
    const options = await api('/public/categories', { publicRequest: true })
    if (current !== loadId) return
    categories.value = options
    if (target === 'new') { Object.assign(form, { title: '', introduction: '', categoryId: '', serialStatus: 'SERIALIZING' }); baseline.value = JSON.stringify(form) }
    else {
      const [data, directory, events] = await Promise.all([api(`/author/novels/${target}`), api(`/author/novels/${target}/chapters?page=1&size=20`), api(`/author/novels/${target}/reviews`)])
      if (current !== loadId) return
      accept(data); chapters.value = directory; history.value = events
    }
  } catch (e) { if (current === loadId) error.value = e.message }
  finally { if (current === loadId) loading.value = false }
}
async function save() {
  if (busy.value) return
  busy.value = true; error.value = ''
  try {
    const created = isNew.value
    const data = await api(created ? '/author/novels' : `/author/novels/${book.value.id}`, { method: created ? 'POST' : 'PUT', body: { ...form, categoryId: Number(form.categoryId), version: book.value?.version ?? 0 } })
    accept(data); ElMessage.success('作品草稿已保存')
    if (created) await router.replace(`/author/novels/${data.id}`)
  } catch (e) { error.value = e.message }
  finally { busy.value = false }
}
async function submit() {
  if (dirty.value || busy.value) return
  try { await ElMessageBox.confirm('审核期间不能修改本次提交。通过后，读者会看到这个版本。', '提交作品审核', { confirmButtonText: '提交审核', cancelButtonText: '返回编辑' }) }
  catch { return }
  busy.value = true; error.value = ''
  try { accept(await api(`/author/novels/${book.value.id}/submit`, { method: 'POST', body: { version: book.value.version } })); ElMessage.success('作品已提交审核') }
  catch (e) { error.value = e.message }
  finally { busy.value = false }
}
watch(() => route.params.id, load, { immediate: true })
</script>
<template><section class="page-wrap"><router-link class="quiet-link" to="/author">← 返回我的作品</router-link><div class="editor-heading"><h1 class="page-title">{{ isNew ? '新建作品' : '作品管理' }}</h1><div v-if="book" class="state-line"><ContentState :item="book" /></div></div><el-alert v-if="error" :title="error" type="error" :closable="false" class="form-alert" /><el-button v-if="error && !dirty" :disabled="busy" @click="load">重新加载</el-button><div v-if="!loading && (isNew || book)" class="editor-card"><form @submit.prevent="save"><div class="form-grid"><div><label for="novel-title">作品名称</label><el-input id="novel-title" v-model="form.title" :maxlength="50" :disabled="busy || pending" required placeholder="给故事一个名字" /></div><div><label for="category">题材分类</label><select id="category" v-model="form.categoryId" :disabled="busy || pending" required><option disabled value="">请选择题材</option><option v-for="category in categories" :key="category.id" :value="category.id">{{ category.name }}</option></select></div></div><label for="novel-intro">作品简介</label><el-input id="novel-intro" v-model="form.introduction" type="textarea" :rows="5" :maxlength="1000" show-word-limit :disabled="busy || pending" placeholder="作品讲述什么故事？提交审核前需填写简介。" /><label for="serial-status">创作状态</label><select id="serial-status" v-model="form.serialStatus" :disabled="busy || pending"><option value="SERIALIZING">连载中</option><option value="COMPLETED">已完结</option></select><p v-if="book?.reviewReason" class="review-reason">最近审核反馈：{{ book.reviewReason }}</p><div class="editor-actions"><el-button type="primary" native-type="submit" :loading="busy" :disabled="pending">保存草稿</el-button><el-button v-if="book" :disabled="busy || dirty || pending || book.reviewStatus === 'APPROVED'" @click="submit">提交审核</el-button><span role="status" class="muted">{{ dirty ? '有未保存修改，请先保存' : pending ? '正在等待审核' : isNew ? '填写作品信息后保存草稿' : book?.reviewStatus === 'APPROVED' ? '已通过审核，修改后需重新送审' : '草稿已保存，可以提交审核' }}</span></div></form></div><div v-if="book" class="chapter-section"><p><router-link class="solid-link" :to="`/author/novels/${book.id}/studio`">大纲、角色与创作工具 →</router-link></p><div class="section-heading"><h2>章节管理</h2><router-link class="solid-link" :to="`/author/novels/${book.id}/chapters/new`">＋ 新建章节</router-link></div><div class="content-list"><article v-for="chapter in chapters.items" :key="chapter.id" class="chapter-row"><span>第 {{ chapter.chapterNumber }} 章 · {{ chapter.title }}</span><div class="state-line"><ContentState :item="chapter" /><router-link class="quiet-link" :to="`/author/chapters/${chapter.id}`">编辑 →</router-link></div></article><p v-if="!chapters.items.length" class="empty-state">还没有章节，从第一章开始写作吧。</p></div><PageControls v-bind="chapters" :busy="busy" @change="chapterPage" /><details class="review-history"><summary>最近审核记录（最多 50 条）</summary><p v-if="!history.length">暂无审核记录</p><p v-for="event in history" :key="event.id">{{ event.chapterId ? `章节 #${event.chapterId}` : '作品信息' }} · {{ { APPROVE: '通过', REJECT: '驳回', TAKEDOWN: '下架' }[event.action] }} · {{ event.reason || '审核通过' }} <small>{{ event.createdAt }} UTC</small></p></details></div></section></template>
