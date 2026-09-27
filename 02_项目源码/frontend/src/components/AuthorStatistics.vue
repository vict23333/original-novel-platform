<script setup>
import { computed, ref, watch } from 'vue'
import { api } from '../session'
import { countText } from '../discovery'
const props = defineProps({ novelId: { type: [Number, String], default: null } })
const stats = ref(null), busy = ref(false), error = ref(''); let generation = 0
const max = computed(() => Math.max(1, ...(stats.value?.trend.map(day => day.reads) || [])))
const cards = computed(() => stats.value ? [
  ['作品数', stats.value.totals.works], ['累计阅读', stats.value.totals.totalReads],
  ['近七日阅读', stats.value.totals.weekReads], ['当前收藏', stats.value.totals.collections], ['有效评论', stats.value.totals.comments],
] : [])
async function load() {
  const id = ++generation; busy.value = true; error.value = ''; stats.value = null
  try { const data = await api(props.novelId ? `/author/novels/${props.novelId}/stats` : '/author/stats'); if (id === generation) stats.value = data }
  catch (e) { if (id === generation) error.value = e.message }
  finally { if (id === generation) busy.value = false }
}
watch(() => props.novelId, load, { immediate: true })
</script>
<template>
  <section class="author-statistics" v-loading="busy" aria-label="作品数据">
    <div class="section-heading"><div><p class="eyebrow">每一次阅读，都值得记录</p><h2>{{ stats?.title || '作品数据' }}</h2></div><el-button :loading="busy" @click="load">刷新数据</el-button></div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <template v-if="stats">
      <div class="stat-cards"><div v-for="[label, value] in cards" :key="label"><span>{{ label }}</span><strong>{{ countText(value) }}</strong></div></div>
      <div class="reading-trend"><h3>近七日阅读趋势</h3><div class="trend-columns" role="img" :aria-label="stats.trend.map(d => `${d.date}：${d.reads}次阅读`).join('；')"><div v-for="day in stats.trend" :key="day.date" class="trend-column"><strong>{{ countText(day.reads) }}</strong><div class="trend-track"><div class="trend-bar" :style="{ height: `${day.reads / max * 100}%` }"></div></div><small>{{ day.date.slice(5) }}</small></div></div>
      <p v-if="!stats.totals.weekReads" class="muted">近七日还没有阅读记录。作品公开后，读者打开章节即可开始记录。</p></div>
      <p class="scope-note">累计阅读从本次统计功能启用后记录，不补算历史访问。同一身份每天阅读同一作品只记一次；有效评论包含未删除的根评论与回复。下架作品的历史数据仍保留在本人统计中。</p>
    </template>
  </section>
</template>
