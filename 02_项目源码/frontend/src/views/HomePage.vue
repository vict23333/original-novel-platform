<script setup>
import DiscoveryBook from '../components/DiscoveryBook.vue'
import DiscoveryShelves from '../components/DiscoveryShelves.vue'
import { rankingKinds } from '../discovery'
import { onMounted, ref } from 'vue'
import { api, session } from '../session'
import PageControls from '../components/PageControls.vue'
const serialStatus = ref(''), sort = ref('NEWEST')
const keyword = ref(''), categoryId = ref(''), categories = ref([]), busy = ref(false), error = ref('')
const result = ref({ items: [], page: 1, size: 12, total: 0 })
let requestId = 0
async function load(page = 1) {
  const current = ++requestId
  busy.value = true; error.value = ''
  try {
    const query = new URLSearchParams({ keyword: keyword.value, page: String(page), size: '12' })
    if (categoryId.value) query.set('categoryId', categoryId.value)
    if (serialStatus.value) query.set('serialStatus', serialStatus.value)
    query.set('sort', sort.value)
    const data = await api(`/public/novels?${query}`, { publicRequest: true })
    if (current === requestId) result.value = data
  } catch (e) { if (current === requestId) { error.value = e.message; result.value.items = [] } }
  finally { if (current === requestId) busy.value = false }
}
onMounted(async () => {
  try { categories.value = await api('/public/categories', { publicRequest: true }) } catch (e) { error.value = e.message; return }
  await load()
})
</script>
<template>
  <section class="home-hero">
    <div class="hero-copy"><p class="eyebrow">故事的下一页，等你开启</p><h1>在文字之间，<br>遇见新的世界。</h1><p class="hero-description">为热爱故事的读者，也为正在写下故事的你。<br>在墨间，开启属于自己的阅读与创作旅程。</p><div class="hero-actions"><a class="solid-link large" href="#library">探索书库 ↓</a><router-link class="quiet-link" :to="session.user?.role === 'AUTHOR' ? '/author' : '/register'">开始我的创作 →</router-link></div><p class="release-note">作品与章节通过审核后，即可在这里与读者相遇。</p></div>
    <div class="book-scene" aria-hidden="true"><div class="scene-circle"></div><div class="book book-back"><span>故事<br>始于<br>一念</span></div><div class="book book-front"><span class="book-label">墨 间 · 创 作 手 记</span><strong>每一个故事<br>都有自己的<br>回响。</strong><div class="book-rule"></div><small>写下第一行，让想象生长。</small></div><span class="scene-caption">A PLACE FOR YOUR STORIES</span></div>
  </section>
  <DiscoveryShelves />
  <section id="library" class="feature-section"><div class="section-heading"><div><p class="eyebrow">发现下一本好故事</p><h2>原创书库</h2></div><span class="outline-label">已审核作品</span></div><form class="library-search" @submit.prevent="load(1)"><div><label for="book-search">关键词</label><el-input id="book-search" v-model="keyword" :maxlength="100" placeholder="搜索书名或作者昵称" /></div><div><label for="book-category">题材</label><select id="book-category" v-model="categoryId"><option value="">全部题材</option><option v-for="category in categories" :key="category.id" :value="category.id">{{ category.name }}</option></select></div><div><label for="book-serial">状态</label><select id="book-serial" v-model="serialStatus"><option value="">全部状态</option><option value="SERIALIZING">连载中</option><option value="COMPLETED">已完结</option></select></div><div><label for="book-sort">排序</label><select id="book-sort" v-model="sort"><option v-for="kind in rankingKinds" :key="kind.value" :value="kind.value">{{ kind.label }}</option></select></div><el-button type="primary" native-type="submit" :loading="busy">搜索作品</el-button></form><el-alert v-if="error" :title="error" type="error" :closable="false" class="form-alert" /><div class="library-grid" v-loading="busy"><DiscoveryBook v-for="book in result.items" :key="book.id" :book="book" /></div><div v-if="!busy && !error && !result.items.length" class="empty-state"><h3>{{ keyword || categoryId ? '没有找到匹配作品' : '书库正在等待新的故事' }}</h3><p>{{ keyword || categoryId ? '试试其他关键词或题材。' : '作者提交作品与章节，管理员审核通过后，读者即可开始阅读。' }}</p></div><PageControls v-bind="result" :busy="busy" @change="load" /></section>
</template>
