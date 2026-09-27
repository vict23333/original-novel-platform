<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '../session'
import PageControls from '../components/PageControls.vue'
const categories = ref([]), tags = ref([]), kind = ref('categories'), editing = ref(null), name = ref('')
const busy = ref(false), error = ref(''), keyword = ref(''), selected = ref(null), reason = ref('')
const books = ref({ items: [], page: 1, size: 12, total: 0 })
async function terms() { [categories.value, tags.value] = await Promise.all([api('/admin/taxonomy/categories'), api('/admin/taxonomy/tags')]) }
async function loadBooks(page = 1) {
  busy.value = true; error.value = ''; selected.value = null
  try { books.value = await api(`/admin/classification-books?${new URLSearchParams({ keyword: keyword.value.trim(), page })}`) } catch (e) { error.value = e.message } finally { busy.value = false }
}
function edit(item = null) { editing.value = item; name.value = item?.name || '' }
async function saveTerm() {
  if (busy.value || !name.value.trim()) return
  busy.value = true; error.value = ''
  try {
    await api(`/admin/taxonomy/${kind.value}${editing.value ? `/${editing.value.id}` : ''}`, { method: editing.value ? 'PUT' : 'POST', body: { name: name.value.trim(), version: editing.value?.version || 0 } })
    edit(); await terms(); ElMessage.success('已保存'); await loadBooks(books.value.page)
  } catch (e) { error.value = e.message } finally { busy.value = false }
}
async function remove(item) {
  if (busy.value) return
  try { await ElMessageBox.confirm(`删除“${item.name}”？仍被作品使用的项目不能删除。`, '删除确认', { confirmButtonText: '确认删除', cancelButtonText: '取消', type: 'warning' }) } catch { return }
  busy.value = true; error.value = ''
  try { await api(`/admin/taxonomy/${kind.value}/${item.id}?version=${item.version}`, { method: 'DELETE' }); edit(); await terms(); ElMessage.success('已删除') } catch (e) { error.value = e.message } finally { busy.value = false }
}
async function choose(item) {
  busy.value = true; error.value = ''; selected.value = null; reason.value = ''
  try { selected.value = await api(`/admin/novels/${item.id}/classification`) } catch (e) { error.value = e.message } finally { busy.value = false }
}
async function classify() {
  if (busy.value || !selected.value) return
  if (!reason.value.trim()) { error.value = '请填写调整原因'; return }
  if (selected.value.tagIds.length > 5) { error.value = '每部作品最多选择 5 个标签'; return }
  try { await ElMessageBox.confirm('分类与标签会立即应用到已有公开作品；正文与审核状态保持不变。确认调整？', '调整作品归类', { confirmButtonText: '确认调整', cancelButtonText: '取消' }) } catch { return }
  busy.value = true; error.value = ''
  try {
    const item = selected.value
    await api(`/admin/novels/${item.novelId}/classification`, { method: 'PUT', body: { categoryId: item.categoryId, tagIds: item.tagIds, version: item.version, reason: reason.value } })
    await terms(); await loadBooks(books.value.page); ElMessage.success('作品归类已更新')
  } catch (e) { error.value = e.message } finally { busy.value = false }
}
onMounted(async () => { busy.value = true; try { await terms(); await loadBooks() } catch (e) { error.value = e.message } finally { busy.value = false } })
</script>
<template>
  <section class="page-wrap"><p class="eyebrow">书库组织</p><h1 class="page-title">分类与标签</h1><p class="page-intro">维护小说题材和故事标签，并为作品调整归类。每部作品一个题材、最多五个标签。</p>
    <el-alert v-if="error" :title="error" type="error" :closable="false" class="form-alert" />
    <section class="admin-panel"><div class="editor-actions"><el-button :type="kind === 'categories' ? 'primary' : 'default'" :disabled="busy" @click="kind = 'categories'; edit()">题材分类</el-button><el-button :type="kind === 'tags' ? 'primary' : 'default'" :disabled="busy" @click="kind = 'tags'; edit()">作品标签</el-button><el-button :disabled="busy" @click="terms().catch(e => error = e.message)">刷新分类与标签</el-button></div>
      <form class="admin-filter" @submit.prevent="saveTerm"><label>{{ editing ? '修改名称' : '新增名称' }}<input v-model="name" maxlength="20" required :disabled="busy" placeholder="最多 20 个字" /></label><el-button native-type="submit" :disabled="busy || !name.trim()" type="primary">{{ editing ? '保存名称' : '添加' }}</el-button><el-button v-if="editing" :disabled="busy" @click="edit()">取消编辑</el-button></form>
      <div class="term-grid"><article v-for="item in (kind === 'categories' ? categories : tags)" :key="item.id" class="term-card"><strong>{{ item.name }}</strong><small>{{ item.usageCount }} 部作品使用</small><div><el-button size="small" :disabled="busy" @click="edit(item)">重命名</el-button><el-button size="small" type="danger" plain :disabled="busy || item.usageCount > 0" @click="remove(item)">删除</el-button></div></article></div><p v-if="kind === 'tags' && !tags.length" class="muted">还没有标签，可添加“成长”“冒险”等标签。</p>
    </section>
    <h2 class="admin-section-title">作品归类</h2><form class="admin-filter" @submit.prevent="loadBooks()"><label>作品名称<input v-model="keyword" maxlength="50" :disabled="busy" placeholder="按书名搜索" /></label><el-button native-type="submit" :disabled="busy">查询作品</el-button></form>
    <div class="admin-columns" v-loading="busy"><div><div class="admin-list"><button v-for="item in books.items" :key="item.id" class="admin-row" :disabled="busy" :class="{ selected: selected?.novelId === item.id }" @click="choose(item)"><span><strong>{{ item.title }}</strong><small>#{{ item.id }} · {{ item.categoryName }}{{ item.reviewStatus === 'PENDING' ? ' · 待审核' : '' }}</small></span><span>调整 →</span></button></div><p v-if="!books.items.length && !busy" class="empty-state">没有符合条件的作品</p><PageControls v-bind="books" :busy="busy" @change="loadBooks" /></div>
      <form v-if="selected" class="admin-panel" @submit.prevent="classify"><h2>{{ selected.title }}</h2><p v-if="selected.reviewStatus === 'PENDING'" class="status-disabled">请先完成作品审核，再调整归类。</p><label>题材分类<select v-model="selected.categoryId" :disabled="busy"><option v-for="item in categories" :key="item.id" :value="item.id">{{ item.name }}</option></select></label><fieldset class="tag-options" :disabled="busy"><legend>作品标签（最多五个）</legend><label v-for="item in tags" :key="item.id"><input v-model="selected.tagIds" type="checkbox" :value="item.id" />{{ item.name }}</label><p v-if="!tags.length" class="muted">先在上方新增标签</p></fieldset><label>调整原因<textarea v-model="reason" maxlength="500" rows="3" :disabled="busy" /></label><el-button native-type="submit" type="primary" :disabled="busy || selected.reviewStatus === 'PENDING'">保存作品归类</el-button></form><div v-else class="admin-panel empty-state">选择作品，设置分类与标签</div>
    </div>
  </section>
</template>
