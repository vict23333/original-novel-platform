<script setup>
import DiscoveryBook from '../components/DiscoveryBook.vue'
import BookTags from '../components/BookTags.vue'
import { countText, dateText } from '../discovery'
import CommentSection from '../components/CommentSection.vue'
import BookCover from '../components/BookCover.vue'
import { ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { api } from '../session'
import PageControls from '../components/PageControls.vue'
import BookReadingActions from '../components/BookReadingActions.vue'
const route = useRoute(), book = ref(null), busy = ref(false), error = ref('')
const discovery = ref(null), discoveryError = ref('')
const chapters = ref({ items: [], page: 1, size: 20, total: 0 })
let loadId = 0
async function load(page = 1) {
  const current = ++loadId, target = route.params.id
  busy.value = true; error.value = ''; book.value = null; discovery.value = null; discoveryError.value = ''
  try {
    const [data, directory] = await Promise.all([api(`/public/novels/${target}`, { publicRequest: true }), api(`/public/novels/${target}/chapters?page=${page}&size=20`, { publicRequest: true })])
    if (current !== loadId) return
    book.value = data; chapters.value = directory
    try { const detail = await api(`/public/novels/${target}/discovery`, { publicRequest: true }); if (current === loadId) discovery.value = detail }
    catch (e) { if (current === loadId) discoveryError.value = e.message }
  } catch (e) { if (current === loadId) { error.value = e.message; chapters.value = { items: [], page: 1, size: 20, total: 0 } } }
  finally { if (current === loadId) busy.value = false }
}
watch(() => route.params.id, () => load(), { immediate: true })
</script>
<template><section class="page-wrap" v-loading="busy"><router-link class="quiet-link" to="/">← 返回书库</router-link><el-alert v-if="error" :title="error" type="error" :closable="false" class="form-alert" /><el-button v-if="error" @click="load()">重新加载</el-button><template v-if="book"><div class="book-detail-hero"><BookCover :title="book.title" :category-id="book.categoryId" large /><div><div class="state-line"><span class="role-pill">{{ book.categoryName }}</span><span class="outline-label">{{ book.serialStatus === 'COMPLETED' ? '已完结' : '连载中' }}</span></div><h1 class="page-title">{{ book.title }}</h1><p class="muted">{{ book.authorName }} 著</p><div v-if="discovery" class="detail-metrics"><span>{{ countText(discovery.book.totalReads) }} 阅读</span><span>{{ countText(discovery.book.collections) }} 收藏</span><span>{{ countText(discovery.book.wordCount) }} 字</span><span>{{ countText(discovery.book.comments) }} 评论</span></div><BookTags :key="book.id" :novel-id="book.id" /><p class="book-introduction">{{ book.introduction }}</p><router-link v-if="chapters.items.length" class="solid-link" :to="`/read/${chapters.items[0].id}`">阅读本页首章 →</router-link><BookReadingActions :novel-id="book.id" /></div></div><p v-if="discovery?.book.latestChapterId" class="latest-update">最近公开更新：{{ dateText(discovery.book.lastUpdatedAt) }} · <router-link :to="`/read/${discovery.book.latestChapterId}`">目录末章：{{ discovery.book.latestChapterTitle }} →</router-link></p><div class="section-heading"><h2>章节目录</h2><span class="muted">{{ chapters.total }} 章已公开</span></div><div class="directory-grid"><router-link v-for="chapter in chapters.items" :key="chapter.id" :to="`/read/${chapter.id}`">第 {{ chapter.chapterNumber }} 章 · {{ chapter.title }} <span aria-hidden="true">→</span></router-link></div><div v-if="!chapters.items.length" class="empty-state">作者正在准备新章节，请稍后再来。</div><PageControls v-bind="chapters" :busy="busy" @change="load" /><CommentSection :novel-id="book.id" /><section class="similar-section"><div class="section-heading"><div><p class="eyebrow">沿着喜欢的题材，继续探索</p><h2>同题材推荐</h2></div><router-link class="quiet-link" to="/rankings">发现更多 →</router-link></div><div v-if="discovery?.similar.length" class="library-grid"><DiscoveryBook v-for="item in discovery.similar" :key="item.id" :book="item" /></div><p v-else-if="!discoveryError" class="muted">暂时没有其他可阅读的同题材作品。</p><el-alert v-if="discoveryError" :title="discoveryError" type="error" :closable="false" /><el-button v-if="discoveryError" @click="load(chapters.page)">重新加载推荐与数据</el-button></section></template></section></template>
