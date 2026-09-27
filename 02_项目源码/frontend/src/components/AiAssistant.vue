<script setup>
import { ref, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { api } from '../session'
import { prepareAiDraft, applyAiDraft, undoAiDraft } from '../aiDraft'

const props = defineProps({ chapter: Object, content: String, disabled: Boolean, selectionReader: Function })
const emit = defineEmits(['apply', 'busy', 'dirty'])
const status = ref(null), checking = ref(false), generating = ref(false), error = ref('')
const instructions = ref(''), preview = ref(''), draft = ref(null), truncated = ref(false), lastChange = ref(null)
const hasPreview = ref(false)
const stale = computed(() => draft.value && props.content !== draft.value.base)
const locked = computed(() => props.disabled || generating.value || !status.value?.available)
let generation = 0, controller = null, alive = true
watch(hasPreview, value => emit('dirty', value), { immediate: true })
async function checkStatus() {
  checking.value = true; error.value = ''
  try { const result = await api('/author/ai/status'); if (alive) status.value = result }
  catch (e) { if (alive) error.value = e.message }
  finally { if (alive) checking.value = false }
}
async function generate(mode) {
  if (locked.value || hasPreview.value) return
  error.value = ''
  try { draft.value = prepareAiDraft(props.content, mode, props.selectionReader?.()) }
  catch (e) { error.value = e.message; return }
  const current = ++generation
  controller = new AbortController(); generating.value = true; emit('busy', true)
  try {
    const result = await api(`/author/chapters/${props.chapter.id}/ai`, {
      method: 'POST', signal: controller.signal,
      body: { version: props.chapter.version, mode, text: draft.value.text, instructions: instructions.value },
    })
    if (!alive || current !== generation) return
    preview.value = result.content; truncated.value = result.truncated; hasPreview.value = true
  } catch (e) { if (alive && current === generation && e.name !== 'AbortError') error.value = e.message }
  finally { if (alive && current === generation) { generating.value = false; emit('busy', false) } }
}
function discard() { preview.value = ''; hasPreview.value = false; draft.value = null; truncated.value = false; error.value = '' }
function apply() {
  if (props.disabled || generating.value) return
  try {
    const after = applyAiDraft(props.content, draft.value, preview.value)
    lastChange.value = { before: props.content, after }; emit('apply', after); discard()
  } catch (e) { error.value = e.message }
}
function undo() {
  if (props.disabled || generating.value) return
  try { emit('apply', undoAiDraft(props.content, lastChange.value)); lastChange.value = null; error.value = '' }
  catch (e) { error.value = e.message }
}
onMounted(checkStatus)
onBeforeUnmount(() => { alive = false; generation++; controller?.abort(); emit('busy', false); emit('dirty', false) })
</script>

<template>
  <section class="ai-assistant" aria-labelledby="ai-title" :aria-busy="generating">
    <div class="ai-heading"><div><p class="eyebrow">写作搭档</p><h2 id="ai-title">AI 辅助创作</h2></div><span class="ai-badge">预览后采用</span></div>
    <p class="muted">续写以正文末尾最多 6000 字符为参考；润色可先在正文中选中一段，不选则处理全文（最多 6000 字符）。</p>
    <p v-if="checking" role="status">正在检查 AI 服务…</p>
    <div v-else-if="!status?.available" class="ai-notice"><p>AI 服务尚未连接，配置完成后即可生成。你仍可正常编辑、保存和提交章节。</p><el-button :disabled="checking" @click="checkStatus">重新检查连接状态</el-button></div>
    <p v-else class="muted">当前模型：{{ status.model }} · 请求至少间隔 {{ status.cooldownSeconds }} 秒。点击生成将把本次正文片段和创作要求发送给已配置的 AI 服务。</p>
    <label for="ai-instructions">创作要求（可选）</label>
    <el-input id="ai-instructions" v-model="instructions" :maxlength="500" type="textarea" :rows="2" :disabled="locked || hasPreview" placeholder="例如：保持第一人称，增加环境描写，不改变人物关系" />
    <div class="ai-actions"><el-button :disabled="locked || hasPreview" :loading="generating && draft?.mode === 'CONTINUE'" @click="generate('CONTINUE')">剧情续写</el-button><el-button :disabled="locked || hasPreview" :loading="generating && draft?.mode === 'POLISH'" @click="generate('POLISH')">润色选段 / 全文</el-button></div>
    <p v-if="generating" role="status" class="muted">正在生成预览，原稿尚未改变。请等待完成后再保存或提交。</p>
    <el-alert v-if="error" :title="error" type="error" :closable="false" class="form-alert" />
    <div v-if="draft && hasPreview" class="ai-preview">
      <details><summary>查看本次{{ draft.mode === 'CONTINUE' ? '续写参考' : '润色原文' }}（{{ draft.text.length }} 字符）</summary><p class="ai-source">{{ draft.text }}</p></details>
      <label for="ai-preview">生成预览 · 可继续修改</label>
      <el-input id="ai-preview" v-model="preview" type="textarea" :rows="8" :maxlength="6000" show-word-limit :disabled="disabled" />
      <p v-if="truncated" class="ai-notice">本次生成达到长度限制，内容可能未完结，请检查和编辑后再采用。</p>
      <p v-if="stale" role="status" class="ai-notice">正文已变化，此预览不能直接采用。可保留需要的文字，或放弃后重新生成。</p>
      <div class="ai-actions"><el-button type="primary" :disabled="disabled || stale || !preview.trim()" @click="apply">{{ draft.mode === 'CONTINUE' ? '追加到正文' : '替换原选段 / 全文' }}</el-button><el-button :disabled="disabled" @click="discard">放弃本次预览</el-button></div>
      <p class="muted">采用只修改编辑区；请自行检查内容，再保存草稿并提交审核。</p>
    </div>
    <el-button v-if="lastChange" :disabled="disabled || generating || hasPreview || content !== lastChange.after" @click="undo">撤销上次采用</el-button>
  </section>
</template>

<style scoped>
.ai-assistant{margin-top:28px;padding:26px;border:1px solid #d2dfcc;border-radius:12px;background:#f4f7ef;min-width:0}
.ai-heading{display:flex;align-items:center;justify-content:space-between;gap:16px}.ai-heading h2{margin:6px 0;font-size:23px}.ai-heading .eyebrow{margin:0}.ai-badge{font-size:12px;color:#426340;border:1px solid #b7c9ac;padding:7px 12px;border-radius:20px;white-space:nowrap}
.ai-assistant>.muted,.ai-preview>.muted{font-size:13px;line-height:1.9;overflow-wrap:anywhere}.ai-assistant label{display:block;margin:18px 0 10px}.ai-actions{display:flex;gap:10px;flex-wrap:wrap;margin:18px 0}.ai-actions .el-button+.el-button{margin-left:0}.ai-notice{font-size:13px;line-height:1.9;background:#fffaf0;border-radius:6px;padding:12px 16px}.ai-notice p{margin-top:0}.ai-preview{margin-top:20px;border-top:1px solid #cddac5;padding-top:18px}.ai-source{white-space:pre-wrap;overflow-wrap:anywhere;max-height:230px;overflow:auto;padding:14px;background:#fffefa;line-height:1.8}.ai-preview summary{cursor:pointer;font-size:13px;color:#426340}
@media(max-width:600px){.ai-assistant{padding:16px}.ai-heading{align-items:flex-start;flex-wrap:wrap}.ai-heading h2{font-size:21px}}
</style>
