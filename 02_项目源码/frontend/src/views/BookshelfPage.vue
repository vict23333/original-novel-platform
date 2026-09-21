<script setup>
import BookCover from '../components/BookCover.vue'
import { ref, onMounted } from 'vue'
import { ElMessageBox } from 'element-plus'
import { api } from '../session'
import PageControls from '../components/PageControls.vue'
const result = ref({ items: [], page: 1, size: 12, total: 0 }), busy = ref(false), error = ref('')
async function load(page = 1) {
  if (busy.value) return
  busy.value = true; error.value = ''
  try {
    let data = await api(`/me/bookshelf?page=${page}&size=12`)
    if (page > 1 && !data.items.length) data = await api(`/me/bookshelf?page=${Math.max(1, Math.ceil(data.total / 12))}&size=12`)
    result.value = data
  } catch (e) { error.value = e.message }
  finally { busy.value = false }
}
async function remove(item) {
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
    <div class="section-heading"><div><p class="eyebrow">故事停在哪里，都能再找到</p><h1 class="page-title">我的书架</h1></div><router-link to="/" class="quiet-link">去书库找书 →</router-link></div>
    <p class="page-intro">收藏喜欢的作品，从上次读到的段落继续。</p>
    <el-alert v-if="error" :title="error" type="error" :closable="false" /><el-button v-if="error" @click="load(result.page)">重试</el-button>
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
      <div v-if="!busy && !error && !result.items.length" class="empty-state"><h2>书架还没有故事</h2><p>在作品详情页点击“加入书架”，让下一次阅读更方便。</p><router-link class="solid-link" to="/">浏览书库</router-link></div>
    </div>
    <PageControls v-bind="result" :busy="busy" @change="load" />
    <p class="scope-note">移出书架不会删除阅读记录。下架作品无法阅读；原章节不可读时，会提示并从首个可读章节开始。</p>
  </section>
</template>
