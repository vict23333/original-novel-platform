<script setup>
import { ref, reactive, computed, watch, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter, onBeforeRouteLeave, onBeforeRouteUpdate } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api, session } from '../session'
import { useChapterDraft } from '../useChapterDraft'
import { useUnsavedChanges } from '../useUnsavedChanges'
import ContentState from '../components/ContentState.vue'
import AiAssistant from '../components/AiAssistant.vue'
const route = useRoute(), router = useRouter()
const chapter = ref(null), book = ref(null), loading = ref(true), busy = ref(false), error = ref(''), baseline = ref('')
const form = reactive({ chapterNumber: 1, title: '', content: '' })
const aiBusy = ref(false), aiDirty = ref(false), contentInput = ref(null)
const draft = useChapterDraft(), editorForm = ref(null), focusMode = ref(false)
const recovered = ref(false), removed = ref(false)
const hasRecovery = computed(() => Boolean(draft.recovery.value))
const characters = computed(() => Array.from(form.content).filter(char => !/\s/u.test(char)).length)
const editorBusy = computed(() => busy.value || aiBusy.value)
function readSelection() {
  const textarea = contentInput.value?.textarea
  return { start: textarea?.selectionStart ?? 0, end: textarea?.selectionEnd ?? 0 }
}
const isNew = computed(() => !route.params.id), pending = computed(() => chapter.value?.reviewStatus === 'PENDING')
const dirty = computed(() => !loading.value && JSON.stringify(form) !== baseline.value)
useUnsavedChanges(computed(() => !removed.value && (dirty.value || aiDirty.value || aiBusy.value)), busy)
function accept(data) { chapter.value = data; Object.assign(form, { chapterNumber: data.chapterNumber, title: data.title, content: data.content }); baseline.value = JSON.stringify(form) }
let loadId = 0
async function load() {
  const current = ++loadId, target = route.params.id, novelId = route.params.novelId
  clearTimeout(cacheTimer); loading.value = true; error.value = ''; chapter.value = null; book.value = null; recovered.value = false; removed.value = false
  try {
    if (!target) {
      const data = await api(`/author/novels/${novelId}`)
      if (current !== loadId) return
      let number = 1
      try { number = (await api(`/author/novels/${novelId}/next-chapter-number`)).chapterNumber }
      catch (e) { if (current === loadId) error.value = e.message }
      if (current !== loadId) return
      book.value = data; Object.assign(form, { chapterNumber: number, title: '', content: '' }); baseline.value = JSON.stringify(form)
    } else {
      const data = await api(`/author/chapters/${target}`), parent = await api(`/author/novels/${data.novelId}`)
      if (current !== loadId) return
      book.value = parent; accept(data)
    }
    draft.initialize(session.user.id, target ? `chapter:${target}` : `novel-new:${novelId}`, { ...form })
  } catch (e) { if (current === loadId) error.value = e.message }
  finally { if (current === loadId) loading.value = false }
}
async function save() {
  if (editorBusy.value || pending.value || loading.value || !book.value || hasRecovery.value) return
  if (!editorForm.value?.reportValidity() || !form.title.trim()) { error.value = '请填写有效的章节序号和标题'; return }
  busy.value = true; error.value = ''
  try {
    const created = isNew.value
    const data = await api(created ? `/author/novels/${book.value.id}/chapters` : `/author/chapters/${chapter.value.id}`, { method: created ? 'POST' : 'PUT', body: { ...form, chapterNumber: Number(form.chapterNumber), version: chapter.value?.version ?? 0 } })
    accept(data); clearTimeout(cacheTimer); draft.saved(); recovered.value = false; ElMessage.success('章节草稿已保存')
    if (created) { busy.value = false; await router.replace(`/author/chapters/${data.id}`) }
  } catch (e) { error.value = e.message }
  finally { busy.value = false }
}
async function submit() {
  if (dirty.value || editorBusy.value || hasRecovery.value || !chapter.value || pending.value) return
  try { await ElMessageBox.confirm('审核期间章节不能修改或删除，通过后公开此版本。', '提交章节审核', { confirmButtonText: '提交审核', cancelButtonText: '继续编辑' }) } catch { return }
  busy.value = true; error.value = ''
  try { accept(await api(`/author/chapters/${chapter.value.id}/submit`, { method: 'POST', body: { version: chapter.value.version } })); ElMessage.success('章节已提交审核') }
  catch (e) { error.value = e.message } finally { busy.value = false }
}
async function remove() {
  if (editorBusy.value) return
  try { await ElMessageBox.confirm('删除后读者将无法阅读本章节，现有内容不会自动恢复。确认删除？', '删除章节', { confirmButtonText: '确认删除', cancelButtonText: '保留章节', type: 'warning' }) } catch { return }
  busy.value = true; error.value = ''
  try { await api(`/author/chapters/${chapter.value.id}?version=${chapter.value.version}`, { method: 'DELETE' }); removed.value = true; clearTimeout(cacheTimer); draft.saved(); baseline.value = JSON.stringify(form); ElMessage.success('章节已删除'); busy.value = false; await router.push(`/author/novels/${book.value.id}`) }
  catch (e) { error.value = e.message } finally { busy.value = false }
}
let cacheTimer = null
function cacheNow() { if (!loading.value && book.value && !pending.value && !removed.value) draft.cache({ ...form }, chapter.value?.version ?? 0, dirty.value) }
watch(form, () => { clearTimeout(cacheTimer); if (!loading.value) cacheTimer = setTimeout(cacheNow, 600) }, { deep: true, flush: 'sync' })
async function restore() {
  if (!hasRecovery.value || pending.value || editorBusy.value) return
  const stale = draft.recovery.value.version !== (chapter.value?.version ?? 0)
  try { await ElMessageBox.confirm(stale ? '服务器草稿已有更新。恢复会替换当前编辑区，请核对内容后再保存；也可先导出本地备份。' : '将本地备份恢复到编辑区，不会自动保存到服务器或提交审核。', '恢复本地草稿', { confirmButtonText: '恢复到编辑区', cancelButtonText: '暂不恢复', type: stale ? 'warning' : 'info' }) } catch { return }
  Object.assign(form, draft.take().form); recovered.value = true; cacheNow()
}
async function discardRecovery() {
  try { await ElMessageBox.confirm('确认清除这份本地备份？服务器内容不受影响。', '清除本地备份', { confirmButtonText: '确认清除', cancelButtonText: '保留备份', type: 'warning' }) } catch { return }
  draft.discard()
}
function shortcut(e) { if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 's') { e.preventDefault(); save() } }
function pageHidden() { if (document.visibilityState === 'hidden') cacheNow() }
onMounted(() => { window.addEventListener('keydown', shortcut); window.addEventListener('pagehide', cacheNow); document.addEventListener('visibilitychange', pageHidden) })
onBeforeRouteLeave(() => { cacheNow() })
onBeforeRouteUpdate(() => { cacheNow() })
onBeforeUnmount(() => { cacheNow(); ++loadId; clearTimeout(cacheTimer); window.removeEventListener('keydown', shortcut); window.removeEventListener('pagehide', cacheNow); document.removeEventListener('visibilitychange', pageHidden) })
watch(() => route.fullPath, load, { immediate: true })
</script>
<template><section class="page-wrap writing-page" :class="{ 'writing-focus': focusMode }"><router-link class="quiet-link" :to="book ? `/author/novels/${book.id}` : '/author'">← 返回作品管理</router-link><div class="editor-heading"><div><p v-if="book" class="eyebrow">{{ book.title }}</p><h1 class="page-title">{{ isNew ? '新建章节' : '章节编辑' }}</h1></div><div v-if="chapter" class="state-line"><ContentState :item="chapter" /></div></div><el-alert v-if="error" :title="error" type="error" :closable="false" class="form-alert" /><el-button v-if="error && !dirty" :disabled="editorBusy" @click="load">重新加载</el-button><div v-if="book && !loading" class="editor-card">
  <div v-if="hasRecovery" class="draft-recovery" role="status"><strong>发现尚未保存到服务器的本地草稿</strong><p>备份时间：{{ new Date(draft.recovery.value.savedAt).toLocaleString() }}。请选择恢复、导出或清除，再继续编辑。</p><p v-if="pending">本章正在审核，可以导出备份，审核结束后再恢复。</p><div class="editor-actions"><el-button :disabled="pending || editorBusy" @click="restore">恢复本地草稿</el-button><el-button @click="draft.download(draft.recovery.value.form)">导出本地备份</el-button><el-button :disabled="editorBusy" @click="discardRecovery">清除本地备份</el-button></div></div>
  <div class="writing-tools"><span>{{ characters }} 字符（不含空白）</span><div><el-button :disabled="loading" @click="draft.download(form)">导出正文 TXT</el-button><el-button @click="focusMode = !focusMode">{{ focusMode ? '退出专注模式' : '专注写作' }}</el-button></div></div>
  <p v-if="draft.warning.value" class="draft-warning" role="alert">{{ draft.warning.value }}</p>
  <p v-else-if="draft.savedAt.value" class="draft-status" role="status">本地备份已更新 {{ new Date(draft.savedAt.value).toLocaleTimeString() }} · 尚未保存到服务器</p>
  <p v-if="recovered" class="draft-status">本地内容已恢复，请核对后保存到服务器。</p><form ref="editorForm" @submit.prevent="save"><div class="chapter-fields"><div><label for="chapter-number">章节序号</label><input id="chapter-number" v-model="form.chapterNumber" class="native-input" type="number" min="1" max="100000" required :disabled="editorBusy || pending || hasRecovery" /></div><div><label for="chapter-title">章节标题</label><el-input id="chapter-title" v-model="form.title" :maxlength="100" required :disabled="editorBusy || pending || hasRecovery" placeholder="这一章的故事，从这里开始" /></div></div><p v-if="isNew" class="field-hint">已根据现有章节建议序号，可自行调整；保存时会再次检查是否重复。</p><label for="chapter-content">章节正文</label><el-input id="chapter-content" ref="contentInput" v-model="form.content" type="textarea" :rows="20" :maxlength="20000" show-word-limit :disabled="editorBusy || pending || hasRecovery" placeholder="写下你的正文。空白内容可以暂存，但不能提交审核。" /><p v-if="chapter?.reviewReason" class="review-reason">最近审核反馈：{{ chapter.reviewReason }}</p><div class="editor-actions"><el-button type="primary" native-type="submit" :loading="busy" :disabled="pending || aiBusy || hasRecovery">保存草稿</el-button><el-button v-if="chapter" :disabled="editorBusy || hasRecovery || dirty || pending || chapter.reviewStatus === 'APPROVED'" @click="submit">提交审核</el-button><el-button v-if="chapter" type="danger" plain :disabled="editorBusy || pending || hasRecovery" @click="remove">删除章节</el-button><span role="status" class="muted">{{ dirty ? '有未保存修改' : pending ? '等待审核，暂不可编辑' : isNew ? '填写内容后保存草稿' : '当前内容已保存' }}</span></div></form><AiAssistant v-if="chapter" :key="chapter.id" :chapter="chapter" :content="form.content" :disabled="busy || pending || hasRecovery" :selection-reader="readSelection" @apply="form.content = $event" @busy="aiBusy = $event" @dirty="aiDirty = $event" /><p v-else class="muted">先保存章节草稿，即可使用 AI 续写与润色。</p><p class="scope-note">Ctrl / ⌘ + S 保存到服务器。本地备份仅保留在当前浏览器，按账号和章节区分，有效期七天；公共电脑使用后请清除备份。AI 未采用的预览不在备份中。</p></div></section></template>
