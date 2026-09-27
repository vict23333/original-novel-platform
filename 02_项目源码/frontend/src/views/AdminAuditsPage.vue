<script setup>
import { ref, reactive, onMounted, onBeforeUnmount } from 'vue'
import { api } from '../session'
import PageControls from '../components/PageControls.vue'
const filters = reactive({ keyword: '', type: '', action: '', targetId: '' })
const result = ref({ items: [], page: 1, size: 12, total: 0 }), busy = ref(false), error = ref('')
const actions = { PROFILE: '修改资料', ENABLE: '启用账号', DISABLE: '停用账号', CREATE: '新增', RENAME: '重命名', DELETE: '删除', CLASSIFY: '调整归类' }
const types = { USER: '用户', NOVEL: '作品', categories: '分类', tags: '标签' }
let applied = { ...filters }, controller, requestId = 0
async function load(page = 1) {
  const current = ++requestId
  controller?.abort(); controller = new AbortController()
  busy.value = true; error.value = ''
  const query = new URLSearchParams({ ...applied, page, size: 12 })
  if (!applied.targetId) query.delete('targetId')
  try { const data = await api(`/admin/audits/search?${query}`, { signal: controller.signal }); if (current === requestId) result.value = data }
  catch (e) { if (current === requestId && e.name !== 'AbortError') error.value = e.message }
  finally { if (current === requestId) busy.value = false }
}
function search() { applied = { ...filters, keyword: filters.keyword.trim() }; load(1) }
function reset() { Object.assign(filters, { keyword: '', type: '', action: '', targetId: '' }); search() }
onMounted(() => load())
onBeforeUnmount(() => { ++requestId; controller?.abort() })
</script>
<template>
  <section class="page-wrap">
    <p class="eyebrow">管理留痕</p><h1 class="page-title">操作记录</h1>
    <p class="page-intro">查询账号与分类标签的历史管理记录，按最新操作排序。作品审核及评论处理记录在对应管理页面查看。</p>
    <form class="admin-filter" @submit.prevent="search">
      <label>记录关键词<input v-model="filters.keyword" maxlength="100" :disabled="busy" placeholder="处理原因或分类标签名称" /></label>
      <label>对象类型<select v-model="filters.type" :disabled="busy"><option value="">全部对象</option><option v-for="(label, value) in types" :key="value" :value="value">{{ label }}</option></select></label>
      <label>操作类型<select v-model="filters.action" :disabled="busy"><option value="">全部操作</option><option v-for="(label, value) in actions" :key="value" :value="value">{{ label }}</option></select></label>
      <label>对象编号<input v-model="filters.targetId" type="number" min="1" step="1" :disabled="busy" placeholder="可选，建议同时选择对象类型" /></label>
      <el-button native-type="submit" type="primary" :disabled="busy">查询记录</el-button><el-button :disabled="busy" @click="reset">重置筛选</el-button>
    </form>
    <div class="editor-actions"><el-button :disabled="busy" @click="load(result.page)">刷新记录</el-button><span class="muted">筛选修改后点击查询生效；时间为 UTC。</span></div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" class="form-alert" />
    <p v-if="error" class="muted">查询未完成，下方保留上次成功结果，请重试。</p>
    <div class="admin-list" v-loading="busy">
      <article v-for="row in result.items" :key="row.id" class="audit-row"><strong>{{ actions[row.action] || row.action }} · {{ types[row.targetType] || row.targetType }} #{{ row.targetId }}</strong><p>{{ row.reason }}</p><small>记录 #{{ row.id }} · 管理员 #{{ row.actorId }} · {{ row.createdAt.replace('T', ' ') }} UTC</small></article>
      <p v-if="!busy && !error && !result.items.length" class="empty-state">没有符合条件的管理记录，可重置筛选后再试。</p>
    </div>
    <PageControls v-bind="result" :busy="busy" @change="load" />
  </section>
</template>
