<script setup>
import CommentSection from '../components/CommentSection.vue'
import BookCover from '../components/BookCover.vue'
import { ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { api } from '../session'
import PageControls from '../components/PageControls.vue'
import BookReadingActions from '../components/BookReadingActions.vue'
const route = useRoute(), book = ref(null), busy = ref(false), error = ref('')
const chapters = ref({ items: [], page: 1, size: 20, total: 0 })
let loadId = 0
async function load(page = 1) {
  const current = ++loadId, target = route.params.id
  busy.value = true; error.value = ''; book.value = null
  try {
    const [data, directory] = await Promise.all([api(`/public/novels/${target}`, { publicRequest: true }), api(`/public/novels/${target}/chapters?page=${page}&size=20`, { publicRequest: true })])
    if (current !== loadId) return
    book.value = data; chapters.value = directory
  } catch (e) { if (current === loadId) { error.value = e.message; chapters.value = { items: [], page: 1, size: 20, total: 0 } } }
  finally { if (current === loadId) busy.value = false }
}
watch(() => route.params.id, () => load(), { immediate: true })
</script>
<template><section class="page-wrap" v-loading="busy"><router-link class="quiet-link" to="/">← 返回书库</router-link><el-alert v-if="error" :title="error" type="error" :closable="false" class="form-alert" /><el-button v-if="error" @click="load()">重新加载</el-button><template v-if="book"><div class="book-detail-hero"><BookCover :title="book.title" :category-id="book.categoryId" large /><div><div class="state-line"><span class="role-pill">{{ book.categoryName }}</span><span class="outline-label">{{ book.serialStatus === 'COMPLETED' ? '已完结' : '连载中' }}</span></div><h1 class="page-title">{{ book.title }}</h1><p class="muted">{{ book.authorName }} 著</p><p class="book-introduction">{{ book.introduction }}</p><router-link v-if="chapters.items.length" class="solid-link" :to="`/read/${chapters.items[0].id}`">阅读本页首章 →</router-link><BookReadingActions :novel-id="book.id" /></div></div><div class="section-heading"><h2>章节目录</h2><span class="muted">{{ chapters.total }} 章已公开</span></div><div class="directory-grid"><router-link v-for="chapter in chapters.items" :key="chapter.id" :to="`/read/${chapter.id}`">第 {{ chapter.chapterNumber }} 章 · {{ chapter.title }} <span aria-hidden="true">→</span></router-link></div><div v-if="!chapters.items.length" class="empty-state">作者正在准备新章节，请稍后再来。</div><PageControls v-bind="chapters" :busy="busy" @change="load" /><CommentSection :novel-id="book.id" /></template></section></template>
