<script setup>
import { ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { api, session } from '../session'
const props = defineProps({ novelId: { type: Number, required: true } })
const route = useRoute(), state = ref(null), busy = ref(false), error = ref('')
let sequence = 0
async function load() {
  const current = ++sequence
  state.value = null; error.value = ''
  if (!session.user) return
  busy.value = true
  try {
    const data = await api(`/me/novels/${props.novelId}/reading`)
    if (current === sequence) state.value = data
  } catch (e) { if (current === sequence) error.value = e.message }
  finally { if (current === sequence) busy.value = false }
}
async function toggle() {
  if (busy.value || !state.value) return
  const remove = state.value.shelved
  if (remove) {
    try { await ElMessageBox.confirm('移出书架后仍保留阅读进度。确认移出？', '移出书架', { confirmButtonText: '移出', cancelButtonText: '保留' }) }
    catch { return }
  }
  busy.value = true; error.value = ''
  try {
    await api(`/me/bookshelf/${props.novelId}`, { method: remove ? 'DELETE' : 'PUT' })
    state.value.shelved = !remove
  } catch (e) { error.value = e.message }
  finally { busy.value = false }
}
watch(() => [props.novelId, session.user?.id], load, { immediate: true })
</script>

<template>
  <div class="book-reading-actions">
    <template v-if="session.user">
      <div class="editor-actions">
        <el-button :disabled="busy || !state" @click="toggle">{{ state?.shelved ? '已在书架 · 移出' : '加入书架' }}</el-button>
        <router-link v-if="state?.resumeChapterId && state?.chapterId" class="quiet-link"
          :to="`/read/${state.resumeChapterId}?resume=1`">继续上次阅读 →</router-link>
      </div>
      <p v-if="state?.chapterId" class="scope-note">{{ state.message }}</p>
      <p v-if="error" role="alert" class="progress-error">{{ error }} <el-button text @click="load">重试</el-button></p>
    </template>
    <router-link v-else class="quiet-link" :to="{ path: '/login', query: { next: route.fullPath } }">登录后加入书架、保存阅读进度 →</router-link>
  </div>
</template>
