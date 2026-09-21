<script setup>
import { nextTick, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api, session } from '../session'
import { authLocation } from '../navigation'
import CommentItem from './CommentItem.vue'
import PageControls from './PageControls.vue'
const props = defineProps({ novelId: { type: Number, required: true }, chapterId: Number })
const route = useRoute(), result = ref({ items: [], page: 1, size: 10, total: 0 }), replies = reactive({})
const text = ref(''), target = ref(null), editor = ref(null), error = ref(''), busy = ref(false)
let generation = 0
function endpoint(page) { return `/public/novels/${props.novelId}/comments?page=${page}&size=10${props.chapterId ? `&chapterId=${props.chapterId}` : ''}` }
async function load(page = 1) {
  const g = generation
  busy.value = true; error.value = ''
  try { const data = await api(endpoint(page)); if (g === generation) result.value = data }
  catch (e) { if (g === generation) error.value = e.message }
  finally { if (g === generation) busy.value = false }
}
async function loadReplies(id, page = 1) {
  const g = generation; busy.value = true; error.value = ''
  try { const data = await api(`/public/comments/${id}/replies?page=${page}&size=10`); if (g === generation) replies[id] = data }
  catch (e) { if (g === generation) error.value = e.message }
  finally { if (g === generation) busy.value = false }
}
async function reply(item) {
  target.value = item
  await nextTick(); editor.value?.focus(); editor.value?.scrollIntoView({ block: 'center', behavior: 'smooth' })
}
async function send() {
  if (busy.value || !text.value.trim()) return
  const g = generation, replyTarget = target.value
  busy.value = true; error.value = ''
  try {
    await api('/me/comments', { method: 'POST', body: { novelId: props.novelId, chapterId: props.chapterId ?? null, replyToId: replyTarget?.id ?? null, body: text.value.trim() } })
    if (g !== generation) return
    text.value = ''; target.value = null; ElMessage.success(replyTarget ? '回复已发表' : '评论已发表')
    // 发布与列表刷新分开：刷新失败时保留已成功发布的事实，避免重复提交。
    await load(replyTarget ? result.value.page : 1)
    if (replyTarget && g === generation) {
      const rootId = replyTarget.rootId || replyTarget.id
      const root = result.value.items.find(row => row.id === rootId)
      await loadReplies(rootId, Math.max(1, Math.ceil((root?.replyCount || 1) / 10)))
    }
  } catch (e) { if (g === generation) error.value = e.message }
  finally { if (g === generation) busy.value = false }
}
async function like(item) {
  if (busy.value) return
  const g = generation; busy.value = true; error.value = ''
  try {
    const data = await api(`/me/comments/${item.id}/like`, { method: item.liked ? 'DELETE' : 'PUT' })
    if (g === generation) Object.assign(item, data)
  } catch (e) { if (g === generation) error.value = e.message }
  finally { if (g === generation) busy.value = false }
}
async function remove(item) {
  if (busy.value) return
  const g = generation
  try { await ElMessageBox.confirm('删除后正文不可恢复，已有回复仍会保留。确认删除？', '删除评论', { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }) } catch { return }
  if (g !== generation) return
  busy.value = true; error.value = ''
  try {
    await api(`/me/comments/${item.id}`, { method: 'DELETE' })
    if (g !== generation) return
    if (target.value?.id === item.id || (!item.rootId && target.value?.rootId === item.id)) target.value = null
    await load(result.value.page)
    const rootId = item.rootId || item.id
    if (replies[rootId] && g === generation) await loadReplies(rootId, replies[rootId].page)
  } catch (e) { if (g === generation) error.value = e.message }
  finally { if (g === generation) busy.value = false }
}
watch(() => [props.novelId, props.chapterId, session.user?.id], () => {
  generation++; result.value = { items: [], page: 1, size: 10, total: 0 }; target.value = null; text.value = ''
  for (const key of Object.keys(replies)) delete replies[key]
  load()
}, { immediate: true })
onBeforeUnmount(() => { generation++ })
</script>
<template>
  <section class="comment-section" aria-label="评论区">
    <div class="section-heading"><h2>{{ chapterId ? '本章讨论' : '作品讨论' }}</h2><el-button text :disabled="busy" @click="load(result.page)">刷新评论</el-button></div>
    <p class="muted">分享阅读感受，尊重不同观点。正文最多 500 字。</p>
    <form v-if="session.user" class="comment-form" @submit.prevent="send">
      <div v-if="target" class="comment-reply-hint">回复 {{ target.nickname }} <el-button text :disabled="busy" @click="target = null">取消回复</el-button></div>
      <label :for="`comment-${novelId}-${chapterId || 'book'}`">{{ target ? '回复内容' : '写下你的评论' }}</label>
      <textarea :id="`comment-${novelId}-${chapterId || 'book'}`" ref="editor" v-model="text" maxlength="500" rows="3" :disabled="busy" required placeholder="聊聊人物、剧情，或最打动你的文字……"></textarea>
      <div class="comment-form-footer"><small>{{ text.length }} / 500</small><el-button type="primary" native-type="submit" :disabled="busy || !text.trim()">{{ target ? '发表回复' : '发表评论' }}</el-button></div>
    </form>
    <p v-else><router-link class="quiet-link" :to="authLocation('/login', route.fullPath)">登录后发表评论、回复或点赞 →</router-link></p>
    <el-alert v-if="error" :title="error" type="error" :closable="false" class="form-alert" />
    <div v-loading="busy">
      <div v-for="item in result.items" :key="item.id" class="comment-thread">
        <CommentItem :item="item" :busy="busy" @like="like" @reply="reply" @remove="remove" />
        <el-button v-if="item.replyCount" text :disabled="busy" @click="replies[item.id] ? delete replies[item.id] : loadReplies(item.id)">{{ replies[item.id] ? '收起回复' : `查看回复（${item.replyCount}）` }}</el-button>
        <div v-if="replies[item.id]" class="comment-replies">
          <CommentItem v-for="child in replies[item.id].items" :key="child.id" :item="child" :busy="busy" :thread-deleted="item.deleted" @like="like" @reply="reply" @remove="remove" />
          <PageControls v-bind="replies[item.id]" :busy="busy" @change="page => loadReplies(item.id, page)" />
        </div>
      </div>
      <p v-if="!busy && !error && !result.items.length" class="empty-state">还没有评论，来聊聊你的阅读感受吧。</p>
    </div>
    <PageControls v-bind="result" :busy="busy" @change="load" />
  </section>
</template>
