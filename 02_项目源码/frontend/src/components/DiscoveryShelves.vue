<script setup>
import { onMounted, ref } from 'vue'
import { api } from '../session'
const shelves = ref([
  { kind: 'POPULAR', title: '本周热门', note: '读者正在翻开的故事', items: [], error: '' },
  { kind: 'NEWEST', title: '新书上架', note: '从故事的第一章相遇', items: [], error: '' },
  { kind: 'UPDATED', title: '最近更新', note: '看看故事又走到了哪里', items: [], error: '' },
])
const busy = ref(false)
async function load() {
  busy.value = true
  await Promise.allSettled(shelves.value.map(async shelf => {
    shelf.error = ''
    try { shelf.items = (await api(`/public/rankings?kind=${shelf.kind}&size=3`, { publicRequest: true })).items }
    catch (e) { shelf.items = []; shelf.error = e.message }
  }))
  busy.value = false
}
onMounted(load)
</script>
<template>
  <section class="feature-section discovery-section">
    <div class="section-heading"><div><p class="eyebrow">让好故事被看见</p><h2>发现与追更</h2></div><router-link class="quiet-link" to="/rankings">全部榜单 →</router-link></div>
    <div class="discovery-shelves" v-loading="busy">
      <article v-for="shelf in shelves" :key="shelf.kind" class="discovery-shelf">
        <h3>{{ shelf.title }}</h3><p class="muted">{{ shelf.note }}</p>
        <ol v-if="shelf.items.length"><li v-for="(book, index) in shelf.items" :key="book.id"><span>{{ index + 1 }}</span><router-link :to="`/books/${book.id}`"><strong>{{ book.title }}</strong><small>{{ book.authorName }} · {{ book.categoryName }}</small></router-link></li></ol>
        <p v-else-if="!busy" class="muted">{{ shelf.error || '还没有可阅读作品，等待新故事上架。' }}</p>
        <el-button v-if="shelf.error" :disabled="busy" @click="load">重试</el-button>
        <router-link v-else class="quiet-link" :to="{ path: '/rankings', query: { kind: shelf.kind } }">查看完整榜单 →</router-link>
      </article>
    </div>
  </section>
</template>
