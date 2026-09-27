<script setup>
import { onMounted, ref } from 'vue'
import { api } from '../session'
const props = defineProps({ novelId: { type: Number, required: true } })
const tags = ref([]), failed = ref(false)
async function load() { failed.value = false; try { tags.value = await api(`/public/novels/${props.novelId}/tags`, { publicRequest: true }) } catch { failed.value = true } }
onMounted(load)
</script>
<template><div v-if="tags.length" class="book-tag-list" aria-label="作品标签"><span v-for="tag in tags" :key="tag.id">{{ tag.name }}</span></div><button v-if="failed" class="quiet-link" @click="load">标签加载失败，点击重试</button></template>
