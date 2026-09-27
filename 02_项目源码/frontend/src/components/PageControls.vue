<script setup>
import { ref, computed, watch } from 'vue'
import { pageTarget } from '../pagination'
const props = defineProps({ page: Number, size: Number, total: Number, busy: Boolean })
const emit = defineEmits(['change'])
const target = ref(props.page), error = ref('')
const pages = computed(() => Math.max(1, Math.ceil(props.total / props.size)))
watch(() => props.page, value => { target.value = value; error.value = '' })
function jump() {
  if (props.busy) return
  const value = pageTarget(target.value, props.total, props.size)
  if (value === null) { error.value = `请输入 1—${pages.value} 之间的整数页码`; return }
  error.value = ''; if (value !== props.page) emit('change', value)
}
</script>
<template><div class="page-controls" aria-label="分页"><span>共 {{ total }} 条 · 第 {{ page }} / {{ pages }} 页</span><el-button :disabled="busy || page <= 1" @click="$emit('change', page - 1)">上一页</el-button><el-button :disabled="busy || page * size >= total" @click="$emit('change', page + 1)">下一页</el-button><template v-if="pages > 1"><label class="page-jump">跳至<input v-model="target" aria-label="跳转页码" type="number" min="1" :max="pages" :disabled="busy" @keydown.enter.prevent="jump" />页</label><el-button :disabled="busy" @click="jump">跳转</el-button></template><span v-if="error" class="pagination-error" role="alert">{{ error }}</span></div></template>
