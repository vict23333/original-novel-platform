<script setup>
import { onBeforeUnmount, onMounted, shallowRef } from 'vue'
import { useRouter } from 'vue-router'
import { api, session } from '../session'
import { authLocation } from '../navigation'
import { libraryLocation } from '../libraryQuery'
import DiscoveryBook from '../components/DiscoveryBook.vue'
import DiscoveryShelves from '../components/DiscoveryShelves.vue'
const router = useRouter(), keyword = shallowRef(''), categories = shallowRef([]), books = shallowRef([])
const busy = shallowRef(false), error = shallowRef('')
let controller
function search() { router.push(libraryLocation({ keyword: keyword.value })) }
async function load() {
  controller?.abort(); controller = new AbortController()
  const signal = controller.signal
  busy.value = true; error.value = ''
  await Promise.allSettled([
    api('/public/categories', { publicRequest: true, signal }).then(data => { if (!signal.aborted) categories.value = data }),
    api('/public/novels?size=6&sort=NEWEST', { publicRequest: true, signal }).then(data => { if (!signal.aborted) books.value = data.items }).catch(e => { if (!signal.aborted) error.value = e.message }),
  ])
  if (!signal.aborted) busy.value = false
}
onMounted(load)
onBeforeUnmount(() => controller?.abort())
</script>
<template>
  <div class="home-page">
    <section class="home-welcome">
      <div class="welcome-main"><h1>今天，读点什么？</h1><p>发现喜欢的故事，也写下自己的下一章。</p>
        <form class="home-search" role="search" @submit.prevent="search"><label class="sr-only" for="home-keyword">搜索书名或作者</label><input id="home-keyword" v-model="keyword" name="keyword" type="search" maxlength="100" autocomplete="off" placeholder="搜索书名或作者…" /><button type="submit" class="solid-link">搜索作品</button></form>
        <nav class="category-shortcuts" aria-label="按题材找书"><router-link to="/library">全部题材</router-link><router-link v-for="category in categories" :key="category.id" :to="libraryLocation({ categoryId: category.id })">{{ category.name }}</router-link></nav>
      </div>
      <aside class="welcome-shortcuts"><strong>{{ session.user ? '故事，在等你继续' : '把喜欢的故事放进书架' }}</strong><p>{{ session.user ? '回到书架，从上次读到的地方接着读。' : '登录后收藏作品，自动保存阅读进度。' }}</p><router-link class="solid-link" :to="session.user ? '/bookshelf' : authLocation('/login','/bookshelf')">{{ session.user ? '打开我的书架' : '登录并开启书架' }}</router-link></aside>
    </section>
    <DiscoveryShelves />
    <section class="feature-section home-latest"><div class="section-heading"><div><h2>新书上架</h2><p class="page-intro">从第一章开始，认识一个新世界。</p></div><router-link class="quiet-link" to="/library">查看全部作品</router-link></div>
      <div v-if="error" class="library-error" role="alert"><span>{{ error }}</span><button class="text-button" @click="load">重新加载</button></div>
      <div class="library-grid" v-loading="busy"><DiscoveryBook v-for="book in books" :key="book.id" :book="book" /></div>
      <div v-if="!busy && !error && !books.length" class="empty-state">新故事正在准备中，稍后再来看看。</div>
    </section>
  </div>
</template>
