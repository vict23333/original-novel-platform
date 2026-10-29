<script setup>
import ReadingPreferences from '../components/ReadingPreferences.vue'
import { useReadingPreferences } from '../composables/useReadingPreferences'
const { preferences } = useReadingPreferences()
</script>
<template>
  <section class="page-wrap settings-page"><div class="page-heading"><div><h1 class="page-title">阅读设置</h1><p class="page-intro">选择适合你的阅读方式。</p></div><router-link class="quiet-link" to="/library">返回书库</router-link></div>
    <div class="settings-layout"><section class="settings-card"><h2>显示偏好</h2><ReadingPreferences /></section><section class="settings-preview" :class="`theme-${preferences.theme}`" aria-label="阅读效果预览"><span>效果预览</span><h2>在下一页，遇见故事</h2><p :style="{fontSize: `${preferences.fontSize}px`, lineHeight: preferences.lineHeight}">窗外的雨停了，书页间透进一束柔和的光。你翻开下一页，故事便从这里继续。<br><br>选好喜欢的背景、字号和行距，再回到小说中，按自己的节奏慢慢阅读。</p></section></div>
  </section>
</template>
