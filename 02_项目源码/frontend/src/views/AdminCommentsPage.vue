<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '../session'
import PageControls from '../components/PageControls.vue'
const keyword = ref(''), state = ref('false'), novelId = ref(''), busy = ref(false), error = ref('')
const result = ref({ items: [], page: 1, size: 12, total: 0 })
async function load(page = 1) {
  busy.value = true; error.value = ''
  const query = new URLSearchParams({ keyword: keyword.value.trim(), page, size: 12 })
  if (state.value) query.set('deleted', state.value)
  if (novelId.value) query.set('novelId', novelId.value)
  try { result.value = await api(`/admin/comments?${query}`) }
  catch (e) { error.value = e.message }
  finally { busy.value = false }
}
async function remove(item) {
  if (busy.value) return
  let reason
  try {
    const answer = await ElMessageBox.prompt('填写违规原因。删除后正文不可恢复，已有回复保留。', '删除违规评论', {
      confirmButtonText: '确认删除', cancelButtonText: '取消', inputType: 'textarea',
      inputValidator: value => Boolean(value?.trim() && value.length <= 500) || '请填写 1—500 字的处理原因',
    })
    reason = answer.value.trim()
  } catch { return }
  busy.value = true; error.value = ''
  try {
    await api(`/admin/comments/${item.id}`, { method: 'DELETE', body: { reason } })
    ElMessage.success('已删除违规评论')
    await load(result.value.items.length === 1 ? Math.max(1, result.value.page - 1) : result.value.page)
  } catch (e) { error.value = e.message }
  finally { busy.value = false }
}
onMounted(() => load())
</script>
<template>
  <section class="page-wrap">
    <router-link class="quiet-link" to="/admin">← 作品与章节审核</router-link>
    <p class="eyebrow">评论内容管理</p><h1 class="page-title">维护友善、有序的讨论。</h1>
    <p class="page-intro">检索小说、昵称或评论内容，处理小说与章节评论。删除操作保留原因与时间。</p>
    <form class="filter-bar" @submit.prevent="load()">
      <label for="comment-search">关键词</label><input id="comment-search" v-model="keyword" maxlength="100" :disabled="busy" placeholder="作品、昵称或正文" />
      <label for="comment-book">作品编号</label><input id="comment-book" v-model="novelId" type="number" min="1" :disabled="busy" placeholder="可选" />
      <label for="comment-state">状态</label><select id="comment-state" v-model="state" :disabled="busy"><option value="false">未删除</option><option value="true">已删除</option><option value="">全部</option></select>
      <el-button native-type="submit" :disabled="busy">查询</el-button>
    </form>
    <el-alert v-if="error" :title="error" type="error" :closable="false" class="form-alert" />
    <div v-loading="busy">
      <article v-for="item in result.items" :key="item.id" class="comment-thread admin-comment">
        <div class="comment-byline"><strong>{{ item.nickname }}</strong><small>#{{ item.id }} · {{ item.createdAt.replace('T', ' ') }} UTC</small></div>
        <p class="muted">《{{ item.novelTitle }}》#{{ item.novelId }} · {{ item.chapterId ? `章节 #${item.chapterId}` : '作品评论' }}{{ item.rootId ? ' · 回复' : '' }}</p>
        <p class="comment-body">{{ item.deleted ? '评论已删除' : item.body }}</p>
        <p v-if="item.deleted" class="muted">处理原因：{{ item.deleteReason }} · {{ item.deletedAt?.replace('T', ' ') }} UTC</p>
        <el-button v-else type="danger" plain :disabled="busy" @click="remove(item)">删除违规评论</el-button>
      </article>
      <p v-if="!busy && !error && !result.items.length" class="empty-state">当前条件下没有评论。</p>
    </div>
    <PageControls v-bind="result" :busy="busy" @change="load" />
  </section>
</template>
