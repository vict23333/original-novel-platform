<script setup>
defineProps({ page: Number, size: Number, total: Number, busy: Boolean })
defineEmits(['change'])
</script>
<template><div class="page-controls" aria-label="分页"><span>共 {{ total }} 条 · 第 {{ page }} / {{ Math.max(1, Math.ceil(total / size)) }} 页</span><el-button :disabled="busy || page <= 1" @click="$emit('change', page - 1)">上一页</el-button><el-button :disabled="busy || page * size >= total" @click="$emit('change', page + 1)">下一页</el-button></div></template>
