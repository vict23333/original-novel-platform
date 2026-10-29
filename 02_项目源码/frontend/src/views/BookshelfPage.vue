<script setup>
import BookCover from '../components/BookCover.vue'
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessageBox } from 'element-plus'
import { api } from '../session'
import PageControls from '../components/PageControls.vue'
const result = ref({ items: [], page: 1, size: 12, total: 0 }), busy = ref(false), error = ref('')
const filters = reactive({ keyword: '', status: 'ALL', sort: 'ADDED' })
const applied = ref({ ...filters })
const filtered = computed(() => Boolean(applied.value.keyword || applied.value.status !== 'ALL'))
function search() { if (busy.value) return; applied.value = { ...filters, keyword: filters.keyword.trim() }; load(1) }
function reset() { if (busy.value) return; Object.assign(filters, { keyword: '', status: 'ALL', sort: 'ADDED' }); search() }
async function load(page = 1) {
  if (busy.value) return
  busy.value = true; error.value = ''
  try {
    const query = new URLSearchParams({ ...applied.value, page, size: 12 })
    let data = await api(`/me/bookshelf?${query}`)
    if (page > 1 && !data.items.length) { query.set('page', Math.max(1, Math.ceil(data.total / 12))); data = await api(`/me/bookshelf?${query}`) }
    result.value = data
  } catch (e) { error.value = e.message }
  finally { busy.value = false }
}
async function remove(item) {
  if (busy.value) return
  try { await ElMessageBox.confirm(`将《${item.title}》移出书架？阅读进度仍会保留。`, '移出书架', { confirmButtonText: '移出', cancelButtonText: '保留' }) }
  catch { return }
  busy.value = true; error.value = ''
  try { await api(`/me/bookshelf/${item.novelId}`, { method: 'DELETE' }) }
  catch (e) { error.value = e.message; busy.value = false; return }
  busy.value = false
  await load(result.value.page)
}
onMounted(() => load())
</script>

<template>
  <section class="page-wrap" v-loading="busy">
    <div class="section-heading"><div><p class="eyebrow">故事停在哪里，都能再找到</p><h1 class="page-title">我的书架</h1></div><router-link to="/library" class="quiet-link">去书库找书</router-link></div>
    <p class="page-intro">收藏喜欢的作品，从上次读到的段落继续。</p>
    <form class="admin-filter" @submit.prevent="search"><label>书架内搜索<input v-model="filters.keyword" maxlength="100" :disabled="busy" placeholder="输入书名" /></label><label>阅读状态<select v-model="filters.status" :disabled="busy"><option value="ALL">全部作品</option><option value="READ">已读</option><option value="UNREAD">未读</option></select></label><label>排列方式<select v-model="filters.sort" :disabled="busy"><option value="ADDED">最近收藏</option><option value="RECENT_READ">最近阅读</option></select></label><el-button native-type="submit" type="primary" :disabled="busy">查询书架</el-button><el-button :disabled="busy" @click="reset">重置筛选</el-button></form>
    <el-alert v-if="error" :title="error" type="error" :closable="false" /><el-button v-if="error" @click="load(result.page)">重试</el-button>
    <p v-if="error" class="muted">加载未完成，下方保留上次结果，请重试。</p>
    <div class="content-list">
      <article v-for="item in result.items" :key="item.novelId" class="manuscript-row shelf-item">
        <BookCover :title="item.title" />
        <div class="manuscript-copy">
          <h2><router-link v-if="item.available" :to="`/books/${item.novelId}`">{{ item.title }}</router-link><span v-else>{{ item.title }}</span></h2>
          <p>{{ item.reading.message }}<template v-if="item.reading.chapterTitle"> · {{ item.reading.chapterTitle }}</template></p>
          <p v-if="item.reading.chapterId && item.reading.resumeChapterId">将定位到第 {{ item.reading.resumeParagraphIndex + 1 }} 段</p>
        </div>
        <div class="shelf-actions">
          <router-link v-if="item.reading.resumeChapterId" class="solid-link" :to="`/read/${item.reading.resumeChapterId}?resume=1`">{{ item.reading.chapterId ? '继续阅读' : '开始阅读' }}</router-link>
          <span v-else class="muted">{{ item.available ? '等待更新' : '已下架' }}</span>
          <el-button text :disabled="busy" @click="remove(item)">移出书架</el-button>
        </div>
      </article>
      <div v-if="!busy && !error && !result.items.length" class="empty-state"><h2>{{ filtered ? '没有符合条件的作品' : '书架还没有故事' }}</h2><p>{{ filtered ? '换个书名，或重置筛选查看全部收藏。' : '在作品详情页点击“加入书架”，让下一次阅读更方便。' }}</p><router-link v-if="!filtered" class="solid-link" to="/library">浏览书库</router-link></div>
    </div>
    <PageControls v-bind="result" :busy="busy" @change="load" />
    <p class="scope-note">已读指已保存过阅读进度，不代表读完。最近阅读按进度保存时间排序，未读排在后面。筛选修改后点击查询生效。移出书架不会删除阅读记录。下架作品无法阅读；原章节不可读时，会提示并从首个可读章节开始。</p>
  </section>
</template>
