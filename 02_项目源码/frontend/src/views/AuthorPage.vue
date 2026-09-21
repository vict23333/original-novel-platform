<script setup>
import BookCover from '../components/BookCover.vue'
import { ref, onMounted } from 'vue'
import { api } from '../session'
import ContentState from '../components/ContentState.vue'
import PageControls from '../components/PageControls.vue'
const result = ref({ items: [], page: 1, size: 12, total: 0 }), busy = ref(false), error = ref('')
async function load(page = 1) {
  busy.value = true; error.value = ''
  try { result.value = await api(`/author/novels?page=${page}&size=12`) }
  catch (e) { error.value = e.message }
  finally { busy.value = false }
}
onMounted(() => load())
</script>
<template><section class="page-wrap"><div class="section-heading"><div><p class="eyebrow">从第一章开始</p><h1 class="page-title">我的作品</h1></div><router-link class="solid-link" to="/author/novels/new">＋ 新建作品</router-link></div><p class="page-intro">保存灵感，完善章节，再将故事提交审核。</p><el-alert v-if="error" :title="error" type="error" :closable="false" class="form-alert" /><el-button v-if="error" @click="load(result.page)">重新加载</el-button><div v-loading="busy" class="content-list"><article v-for="book in result.items" :key="book.id" class="manuscript-row"><BookCover :title="book.title" :category-id="book.categoryId" /><div class="manuscript-copy"><div class="state-line"><ContentState :item="book" /><span class="muted">{{ book.serialStatus === 'COMPLETED' ? '完结' : '连载中' }}</span></div><h2>{{ book.title }}</h2><p>{{ book.introduction || '还没有填写简介' }}</p><p v-if="book.reviewReason" class="review-reason">审核反馈：{{ book.reviewReason }}</p></div><div class="author-book-links"><router-link class="quiet-link" :to="`/author/novels/${book.id}`">管理作品 →</router-link><router-link v-if="book.published && !book.takenDown" class="quiet-link" :to="`/books/${book.id}`">查看读者讨论 →</router-link></div></article><div v-if="!busy && !error && !result.items.length" class="empty-state"><h2>还没有作品</h2><p>先创建作品，填写书名与题材，就可以开始编写章节。</p></div></div><PageControls v-bind="result" :busy="busy" @change="load" /><p class="scope-note">AI 辅助创作与作品数据统计将在后续版本加入。</p></section></template>
