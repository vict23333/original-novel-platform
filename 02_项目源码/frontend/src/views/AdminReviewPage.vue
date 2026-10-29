<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '../session'
import ContentState from '../components/ContentState.vue'
import PageControls from '../components/PageControls.vue'
const kind = ref('novels'), state = ref('PENDING'), result = ref({ items: [], page: 1, size: 12, total: 0 })
const categories = ref([])
const categoryName = (id) => categories.value.find(item => item.id === id)?.name || '未知题材'
const selected = ref(null), parentBook = ref(null), history = ref([]), reason = ref(''), error = ref(''), busy = ref(false)
async function load(page = 1) {
  busy.value = true; error.value = ''; selected.value = null; reason.value = ''
  try { result.value = await api(`/admin/${kind.value}?state=${state.value}&page=${page}&size=12`) }
  catch (e) { error.value = e.message }
  finally { busy.value = false }
}
async function choose(item) {
  busy.value = true; error.value = ''; selected.value = null; reason.value = ''
  try {
    const data = await api(`/admin/${kind.value}/${item.id}`)
    parentBook.value = kind.value === 'chapters' ? await api(`/admin/novels/${data.novelId}`) : data
    history.value = await api(`/admin/novels/${parentBook.value.id}/reviews`)
    selected.value = data
  } catch (e) { error.value = e.message }
  finally { busy.value = false }
}
async function decide(action) {
  if (busy.value || !selected.value) return
  if (action !== 'APPROVE' && !reason.value.trim()) { error.value = '驳回或下架必须填写原因'; return }
  const actionName = { APPROVE: '审核通过', REJECT: '驳回', TAKEDOWN: '下架' }[action]
  try { await ElMessageBox.confirm(`确认对《${selected.value.title}》执行“${actionName}”？`, actionName, { confirmButtonText: '确认', cancelButtonText: '取消', type: action === 'APPROVE' ? 'info' : 'warning' }) } catch { return }
  busy.value = true; error.value = ''
  try {
    const data = { version: selected.value.version, reason: reason.value }
    if (action !== 'TAKEDOWN') data.action = action
    await api(`/admin/${kind.value}/${selected.value.id}/${action === 'TAKEDOWN' ? 'takedown' : 'review'}`, { method: 'POST', body: data })
    ElMessage.success(`${actionName}成功`)
    await load(result.value.page)
  } catch (e) { error.value = e.message }
  finally { busy.value = false }
}
onMounted(async () => {
  await load()
  try { categories.value = await api('/public/categories', { publicRequest: true }) }
  catch (e) { error.value = e.message }
})
</script>
<template><section class="page-wrap review-page"><h1 class="page-title">作品与章节审核</h1><p class="page-intro">小说与章节独立审核。通过章节后，所属小说也需通过且未下架，读者才能阅读。</p><div class="filter-bar"><label for="review-kind">审核对象</label><select id="review-kind" v-model="kind" :disabled="busy" @change="load()"><option value="novels">小说作品</option><option value="chapters">章节内容</option></select><label for="review-state">筛选状态</label><select id="review-state" v-model="state" :disabled="busy" @change="load()"><option value="PENDING">待审核</option><option value="PUBLISHED">已有公开版本</option><option value="ALL">全部</option></select><el-button :disabled="busy" @click="load(result.page)">刷新列表</el-button></div><el-alert v-if="error" :title="error" type="error" :closable="false" class="form-alert" /><div class="review-layout" v-loading="busy"><div><div class="review-queue"><button v-for="item in result.items" :key="item.id" class="queue-item" :class="{ selected: selected?.id === item.id }" :disabled="busy" @click="choose(item)"><strong>{{ item.title }}</strong><div class="state-line"><ContentState :item="item" /></div><small v-if="kind === 'chapters'">作品 #{{ item.novelId }} · 第 {{ item.chapterNumber }} 章</small></button><p v-if="!busy && !result.items.length" class="empty-state">当前筛选下没有内容。</p></div><PageControls v-bind="result" :busy="busy" @change="load" /></div><article class="review-detail"><template v-if="selected"><div class="state-line"><ContentState :item="selected" /></div><p class="eyebrow">{{ kind === 'chapters' ? `所属作品：${parentBook.title}` : '作品信息' }}</p><h2>{{ selected.title }}</h2><div class="review-text">{{ kind === 'chapters' ? selected.content : selected.introduction }}</div><p v-if="kind === 'novels'" class="muted">创作状态：{{ selected.serialStatus === 'COMPLETED' ? '完结' : '连载中' }} · 题材：{{ categoryName(selected.categoryId) }}</p><details v-if="selected.published" class="public-snapshot"><summary>{{ selected.takenDown ? '最近公开版本（已下架）' : '当前公开版本' }}</summary><h3>{{ selected.publicTitle }}</h3><p class="muted">{{ kind === 'chapters' ? `第 ${selected.publicChapterNumber} 章` : `${categoryName(selected.publicCategoryId)} · ${selected.publicSerialStatus === 'COMPLETED' ? '完结' : '连载中'}` }}</p><div class="review-text">{{ kind === 'chapters' ? selected.publicContent : selected.publicIntroduction }}</div></details><label for="review-reason">处理说明（驳回、下架时必填）</label><el-input id="review-reason" v-model="reason" type="textarea" :rows="3" :maxlength="500" :disabled="busy" placeholder="填写具体原因，帮助作者修订" /><div class="editor-actions"><el-button type="primary" :disabled="busy || selected.reviewStatus !== 'PENDING'" @click="decide('APPROVE')">审核通过</el-button><el-button :disabled="busy || selected.reviewStatus !== 'PENDING'" @click="decide('REJECT')">驳回</el-button><el-button type="danger" plain :disabled="busy || !selected.published || selected.takenDown" @click="decide('TAKEDOWN')">违规下架</el-button><el-button :disabled="busy" @click="choose(selected)">重新读取</el-button></div><details class="review-history"><summary>所属作品最近审核记录</summary><p v-if="!history.length">暂无记录</p><p v-for="event in history" :key="event.id">{{ event.chapterId ? `章节 #${event.chapterId}` : '作品' }} · {{ { APPROVE: '通过', REJECT: '驳回', TAKEDOWN: '下架' }[event.action] }} · {{ event.reason || '审核通过' }}<small>{{ event.createdAt }} UTC</small></p></details></template><div v-else class="empty-state"><h2>选择一条内容开始审核</h2><p>请完整阅读提交内容，再作出审核结论。</p></div></article></div></section></template>
