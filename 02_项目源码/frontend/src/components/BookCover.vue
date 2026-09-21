<script setup>
import { computed, ref, watch } from 'vue'
const props = defineProps({ title: { type: String, required: true }, categoryId: Number, large: Boolean })
const failed = ref(false)
const categories = { 1: ['mountain', '玄幻'], 2: ['city', '都市'], 3: ['moon', '悬疑'], 4: ['stars', '科幻'], 5: ['ink', '历史'], 6: ['fields', '故事'] }
const titles = {
  '云海铸剑人': 1, '山河守灯录': 1, '雪原来客': 1,
  '巷口面包房': 2, '末班车向南': 2, '旧物修理日记': 2,
  '雨夜第七站': 3, '白纸上的第八人': 3, '钟楼失约者': 3,
  '环轨邮差': 4, '记忆海岸线': 4, '静默星站': 4,
  '墨驿长安': 5, '青瓷入梦': 5, '旧桥春水': 5,
  '小岛杂货铺': 6, '森林信箱': 6, '天气收藏家': 6,
}
const cover = computed(() => categories[props.categoryId || titles[props.title]] || categories[6])
const position = computed(() => `${35 + Array.from(props.title).reduce((sum, ch) => sum + ch.codePointAt(0), 0) % 31}% center`)
watch(() => cover.value[0], () => { failed.value = false })
</script>

<template>
  <div class="novel-cover" :class="[{ 'cover-large': large }, `cover-${cover[0]}`]" aria-hidden="true">
    <img v-if="!failed" :src="`/covers/${cover[0]}.jpg`" alt="" loading="lazy" decoding="async" :style="{ objectPosition: position }" @error="failed = true" />
    <div class="cover-shade"></div>
    <span class="cover-imprint">墨间 · {{ cover[1] }}</span>
    <strong>{{ title }}</strong>
    <span class="cover-bottom">ORIGINAL STORIES</span>
  </div>
</template>
