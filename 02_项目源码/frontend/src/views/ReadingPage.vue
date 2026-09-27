<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { onBeforeRouteLeave, onBeforeRouteUpdate, useRoute } from 'vue-router'
import { api, session } from '../session'
import { guestIdentity } from '../discovery'
import CommentSection from '../components/CommentSection.vue'
import BookReadingActions from '../components/BookReadingActions.vue'
const route = useRoute(), chapter = ref(null), busy = ref(false), error = ref('')
const paragraphElements = ref([]), index = ref(0), status = ref(''), progressError = ref(''), textChanged = ref(false)
let settings = {}
try { settings = JSON.parse(localStorage.getItem('mojian_reading_settings') || '{}') || {} } catch { /* 使用默认设置。 */ }
const fontSize = ref([16,18,20,22,24,26,28].includes(settings.fontSize) ? settings.fontSize : 20)
const theme = ref(['paper','green','dark'].includes(settings.theme) ? settings.theme : 'paper')
watch([fontSize, theme], () => {
  try { localStorage.setItem('mojian_reading_settings', JSON.stringify({ fontSize: fontSize.value, theme: theme.value })) } catch { /* 禁用存储时临时调整仍可用。 */ }
})
const paragraphs = computed(() => chapter.value?.content.replace(/\r\n?/g, '\n').split(/\n[ \t]*\n/).filter(p => p.trim()) || [])
let requestId = 0, version = 0, lastSaved = -1, ready = false, blocked = false, timer = null, saving = null
function measure() {
  if (!ready) return
  const threshold = window.innerHeight * 0.35
  let position = 0
  for (let i = 0; i < paragraphElements.value.length; i++) {
    if (paragraphElements.value[i]?.getBoundingClientRect().top <= threshold) position = i
  }
  index.value = position
}
function schedule() {
  clearTimeout(timer)
  if (!ready || !session.user || blocked || index.value === lastSaved) return
  status.value = '阅读中，稍后自动保存'
  timer = setTimeout(() => save(), 800)
}
function onScroll() { measure(); schedule() }
async function save(keepalive = false) {
  if (!ready || !session.user || blocked || index.value === lastSaved) return
  if (saving) return saving
  const target = chapter.value, position = index.value, generation = requestId
  status.value = '正在保存进度…'
  saving = (async () => {
    try {
      const data = await api(`/me/novels/${target.novelId}/reading`, { method: 'PUT', keepalive,
        body: { chapterId: target.id, paragraphIndex: position, contentRevision: target.contentRevision, version } })
      if (generation !== requestId) return
      version = data.version; lastSaved = position; progressError.value = ''
      status.value = `已保存到第 ${position + 1} 段`
    } catch (e) {
      if (generation !== requestId) return
      blocked = true; progressError.value = e.message; textChanged.value = e.code === 'TEXT_CHANGED'
      status.value = '进度尚未保存'
    }
  })()
  await saving
  saving = null
  if (generation === requestId && index.value !== lastSaved && !blocked) schedule()
}
async function flush() {
  clearTimeout(timer); measure()
  if (saving) await saving
  await save()
  clearTimeout(timer)
}
async function retry() {
  if (saving) return
  if (textChanged.value) { await load(); return }
  if (!chapter.value || !session.user) return
  try {
    const latest = await api(`/me/novels/${chapter.value.novelId}/reading`)
    version = latest.version; blocked = false; lastSaved = -1
    await save()
  } catch (e) { progressError.value = e.message }
}
async function load() {
  const generation = ++requestId, target = route.params.id
  clearTimeout(timer); ready = false; blocked = false; lastSaved = -1
  busy.value = true; error.value = ''; progressError.value = ''; textChanged.value = false
  chapter.value = null; paragraphElements.value = []; index.value = 0; status.value = ''
  try {
    const data = await api(`/public/chapters/${target}`, { publicRequest: true })
    if (generation !== requestId) return
    chapter.value = data
    let state = null
    if (session.user) {
      try { state = await api(`/me/novels/${data.novelId}/reading`) }
      catch (e) { if (generation === requestId) { blocked = true; progressError.value = e.message } }
    }
    if (generation !== requestId) return
    version = state?.version ?? 0
    const restore = state?.resumeChapterId === data.id && (route.query.resume === '1' || state?.chapterId === data.id)
    if (restore) index.value = Math.min(state.resumeParagraphIndex, Math.max(0, paragraphs.value.length - 1))
    await nextTick()
    // 等待路由滚动复位完成，再恢复段落；恢复期间不监听滚动写入。
    await new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)))
    if (generation !== requestId) return
    if (restore) paragraphElements.value[index.value]?.scrollIntoView({ block: 'start' })
    await new Promise(resolve => requestAnimationFrame(resolve))
    if (generation !== requestId) return
    ready = true
    // 正文完成展示才记数；浏览器存储、身份生成或统计接口失败均不阻断阅读。
    try { void api(`/public/chapters/${data.id}/read-event`, { method: 'POST', body: session.user ? {} : { anonymousId: guestIdentity() } }).catch(() => {}) }
    catch { /* 阅读本身仍然可用。 */ }
    status.value = session.user ? (restore ? state.message : '已打开章节，即将保存进度') : '游客阅读不会保存账号进度'
    schedule()
  } catch (e) { if (generation === requestId) error.value = e.message }
  finally { if (generation === requestId) busy.value = false }
}
function onHidden() { if (document.visibilityState === 'hidden') { measure(); save(true) } }
function onPageHide() { measure(); save(true) }
onMounted(() => {
  window.addEventListener('scroll', onScroll, { passive: true })
  window.addEventListener('pagehide', onPageHide)
  document.addEventListener('visibilitychange', onHidden)
})
onBeforeUnmount(() => {
  ++requestId; ready = false; clearTimeout(timer)
  window.removeEventListener('scroll', onScroll)
  window.removeEventListener('pagehide', onPageHide)
  document.removeEventListener('visibilitychange', onHidden)
})
onBeforeRouteLeave(flush)
onBeforeRouteUpdate(flush)
watch(() => [route.fullPath, session.user?.id], load, { immediate: true })
</script>

<template>
  <section class="reading-page" :class="`theme-${theme}`" v-loading="busy">
    <div class="reading-toolbar">
      <router-link class="quiet-link" :to="chapter ? `/books/${chapter.novelId}` : '/'">← 返回目录</router-link>
      <div><label for="reading-size">字号</label><select id="reading-size" v-model="fontSize"><option v-for="size in [16,18,20,22,24,26,28]" :key="size" :value="size">{{ size }} px</option></select><label for="reading-theme">背景</label><select id="reading-theme" v-model="theme"><option value="paper">浅色</option><option value="green">护眼</option><option value="dark">深色</option></select></div>
    </div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" class="form-alert" /><el-button v-if="error" @click="load">重新加载</el-button>
    <article v-if="chapter" class="reading-sheet">
      <p class="eyebrow">第 {{ chapter.chapterNumber }} 章</p><h1>{{ chapter.title }}</h1>
      <div class="reading-progress" aria-live="polite"><span>{{ status }}</span><span v-if="progressError" class="progress-error">{{ progressError }} <el-button v-if="session.user" text @click="retry">{{ textChanged ? '重新加载正文' : '保存当前位置' }}</el-button></span></div>
      <div class="chapter-body" :style="{ fontSize: `${fontSize}px` }"><p v-for="(paragraph, i) in paragraphs" :key="i" :ref="el => paragraphElements[i] = el" :data-paragraph="i">{{ paragraph }}</p></div>
      <nav class="reading-nav" aria-label="章节切换"><router-link v-if="chapter.previousChapterId" :to="`/read/${chapter.previousChapterId}`">← 上一章</router-link><span v-else>已是首章</span><router-link :to="`/books/${chapter.novelId}`">目录</router-link><router-link v-if="chapter.nextChapterId" :to="`/read/${chapter.nextChapterId}`">下一章 →</router-link><span v-else>已是最新章</span></nav>
      <BookReadingActions :novel-id="chapter.novelId" />
      <p class="scope-note">字号和背景保存在当前浏览器。登录后自动保存章节与段落，书架中可继续阅读；网络中断或直接结束进程时，最后一次保存可能未完成。</p>
      <CommentSection :novel-id="chapter.novelId" :chapter-id="chapter.id" />
    </article>
  </section>
</template>
