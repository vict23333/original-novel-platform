<script setup>
import DiscoveryBook from '../components/DiscoveryBook.vue'
import { rankingKinds } from '../discovery'
import { computed, onBeforeUnmount, onMounted, reactive, shallowRef, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '../session'
import { libraryLocation, libraryRequest, normalizeLibraryQuery } from '../libraryQuery'
import { usePublicLibrary } from '../composables/usePublicLibrary'
import PageControls from '../components/PageControls.vue'
const route = useRoute(), router = useRouter()
const applied = computed(() => normalizeLibraryQuery(route.query))
const form = reactive({ ...applied.value })
const categories = shallowRef([]), categoryError = shallowRef('')
const { result, busy, error, retry } = usePublicLibrary(applied, api)
const filtered = computed(() => Boolean(applied.value.keyword || applied.value.categoryId || applied.value.serialStatus))
watch(applied, value => Object.assign(form, value))

function navigate(value) {
  const target = libraryLocation(value)
  Object.assign(form, normalizeLibraryQuery(value))
  if (libraryRequest(value) === libraryRequest(applied.value)) retry()
  if (router.resolve(target).fullPath !== route.fullPath) router.push(target)
}
function search() { navigate({ ...form, page: 1 }) }
function changePage(page) { navigate({ ...applied.value, page }) }
function reset() { Object.assign(form, normalizeLibraryQuery()); navigate(form) }
// 旧收藏链接可能指向已经不存在的尾页，回到仍有数据的最后一页。
watch([result, busy], () => {
  if (busy.value || error.value) return
  const lastPage = Math.max(1, Math.ceil(result.value.total / result.value.size))
  if (applied.value.page > lastPage) router.replace(libraryLocation({ ...applied.value, page: lastPage }))
})

let categoryController
async function loadCategories() {
  categoryController?.abort()
  const controller = new AbortController()
  categoryController = controller; categoryError.value = ''
  try {
    const data = await api('/public/categories', { publicRequest: true, signal: controller.signal })
    if (!controller.signal.aborted) categories.value = data
  } catch (e) { if (!controller.signal.aborted) categoryError.value = e.message }
}
onMounted(loadCategories)
onBeforeUnmount(() => categoryController?.abort())
</script>
<template>
  <section id="library" class="page-wrap library-page">
    <div class="section-heading"><div><h1 class="page-title">全部书库</h1><p class="page-intro">按书名、作者或题材，找到想读的故事。</p></div><span class="outline-label" role="status">{{ busy ? '正在查找…' : `共 ${result.total} 部作品` }}</span></div>
    <form class="library-search" @submit.prevent="search">
      <div><label for="book-search">关键词</label><el-input id="book-search" v-model="form.keyword" :maxlength="100" name="keyword" autocomplete="off" placeholder="搜索书名或作者…" /></div>
      <div><label for="book-category">题材</label><select id="book-category" v-model="form.categoryId"><option value="">全部题材</option><option v-for="category in categories" :key="category.id" :value="String(category.id)">{{ category.name }}</option></select></div>
      <div><label for="book-serial">状态</label><select id="book-serial" v-model="form.serialStatus"><option value="">全部状态</option><option value="SERIALIZING">连载中</option><option value="COMPLETED">已完结</option></select></div>
      <div><label for="book-sort">排序</label><select id="book-sort" v-model="form.sort"><option v-for="kind in rankingKinds" :key="kind.value" :value="kind.value">{{ kind.label }}</option></select></div>
      <el-button type="primary" native-type="submit" :loading="busy">搜索作品</el-button>
    </form>
    <div class="library-tools"><p class="muted">筛选后点击搜索，刷新或返回会保留查询条件。</p><el-button :disabled="busy" @click="reset">重置筛选</el-button></div>
    <div v-if="categoryError" class="library-tools" role="alert"><span>题材列表加载失败，仍可搜索作品。</span><el-button @click="loadCategories">重试题材</el-button></div>
    <div v-if="error" class="library-error" role="alert"><el-alert :title="error" type="error" :closable="false" /><el-button @click="retry">重试查询</el-button></div>
    <div class="library-grid" v-loading="busy" :aria-busy="busy"><DiscoveryBook v-for="book in result.items" :key="book.id" :book="book" /></div>
    <div v-if="!busy && !error && !result.items.length" class="empty-state"><h3>{{ filtered ? '没有找到匹配作品' : '书库正在等待新的故事' }}</h3><p>{{ filtered ? '试试其他关键词、题材或作品状态。' : '作者提交作品与章节，管理员审核通过后，读者即可开始阅读。' }}</p></div>
    <PageControls v-if="!error" v-bind="result" :busy="busy" @change="changePage" />
  </section>
</template>
