<script setup>
defineProps({ item: Object })
const names = { DRAFT: '草稿', PENDING: '待审核', APPROVED: '已通过', REJECTED: '已驳回' }
</script>
<template><span class="content-state" :class="item.reviewStatus.toLowerCase()">{{ names[item.reviewStatus] }}</span><span v-if="item.takenDown" class="content-state rejected">已下架</span><span v-else-if="item.published && item.reviewStatus !== 'APPROVED'" class="content-state approved">旧版仍公开</span></template>
