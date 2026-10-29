<script setup>
import { computed, ref, watch, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '../session'
import { rankingKinds } from '../discovery'
import DiscoveryBook from '../components/DiscoveryBook.vue'
import PageControls from '../components/PageControls.vue'
const route = useRoute(), router = useRouter()
const categories = ref([]), result = ref({ items: [], total: 0, page: 1, size: 12 }), busy = ref(false), error = ref('')
const categoryId = ref(''), serialStatus = ref('')
let applied = { categoryId: '', serialStatus: '' }
function search() { applied = { categoryId: categoryId.value, serialStatus: serialStatus.value }; load(1) }
const current = computed(() => rankingKinds.find(k => k.value === route.query.kind) || rankingKinds[0])
let generation = 0
async function load(page = 1) {
  categoryId.value = applied.categoryId; serialStatus.value = applied.serialStatus
  const id = ++generation; busy.value = true; error.value = ''
  try {
    const query = new URLSearchParams({ kind: current.value.value, page: String(page), size: '12' })
    if (applied.categoryId) query.set('categoryId', applied.categoryId)
    if (applied.serialStatus) query.set('serialStatus', applied.serialStatus)
    const data = await api(`/public/rankings?${query}`, { publicRequest: true })
    if (id === generation) result.value = data
  } catch (e) { if (id === generation) { error.value = e.message; result.value.items = [] } }
  finally { if (id === generation) busy.value = false }
}
function choose(kind) { categoryId.value = applied.categoryId; serialStatus.value = applied.serialStatus; router.push({ path: '/rankings', query: { kind } }) }
watch(() => current.value.value, () => load())
onMounted(async () => {
  try { categories.value = await api('/public/categories', { publicRequest: true }) } catch { /* 榜单仍可按全部题材浏览。 */ }
  await load()
})
</script>
<template>
  <section class="page-wrap rankings-page">
    <div class="ranking-heading"><div><p class="eyebrow">墨间 · 故事风向</p><h1 class="page-title">小说排行榜</h1></div><router-link class="quiet-link" to="/library">全部书库</router-link></div>
    <div class="rank-tabs" role="group" aria-label="榜单类型"><button v-for="kind in rankingKinds" :key="kind.value" :aria-pressed="current.value === kind.value" @click="choose(kind.value)">{{ kind.label }}</button></div>
    <p class="page-intro">{{ current.description }} 修改条件后点击“筛选榜单”生效。</p>
    <form class="filter-bar ranking-filters" @submit.prevent="search"><div><label for="rank-category">题材</label><select id="rank-category" v-model="categoryId"><option value="">全部题材</option><option v-for="c in categories" :key="c.id" :value="c.id">{{ c.name }}</option></select></div><div><label for="rank-serial">状态</label><select id="rank-serial" v-model="serialStatus"><option value="">全部状态</option><option value="SERIALIZING">连载中</option><option value="COMPLETED">已完结</option></select></div><el-button type="primary" native-type="submit" :loading="busy">筛选榜单</el-button><span class="ranking-total" role="status">共 {{ result.total }} 部作品</span></form>
    <el-alert v-if="error" :title="error" type="error" :closable="false" /><el-button v-if="error" @click="load()">重新加载</el-button>
    <div class="ranking-list" v-loading="busy"><DiscoveryBook v-for="(book, index) in result.items" :key="book.id" :book="book" :rank="(result.page - 1) * result.size + index + 1" /></div>
    <div v-if="!busy && !error && !result.items.length" class="empty-state">当前条件下还没有可阅读作品，试试其他题材或状态。</div>
    <PageControls v-bind="result" :busy="busy" @change="load" />
    <p class="scope-note">阅读按同一账号或匿名会话、同一作品、同一天去重。近七日包含今天；收藏为当前书架收藏数。榜单只展示含可读章节的公开作品。</p>
  </section>
</template>
