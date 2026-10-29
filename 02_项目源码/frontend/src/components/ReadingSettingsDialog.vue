<script setup>
import { useTemplateRef } from 'vue'
import ReadingPreferences from './ReadingPreferences.vue'
const dialog = useTemplateRef('dialog')
defineExpose({ open: () => dialog.value?.showModal() })
</script>
<template>
  <dialog ref="dialog" class="settings-dialog" aria-labelledby="reading-settings-title" @click="event => { if (event.target === dialog) dialog.close() }">
    <div class="dialog-heading"><h2 id="reading-settings-title">阅读设置</h2><form method="dialog"><button class="icon-button" aria-label="关闭阅读设置">×</button></form></div>
    <ReadingPreferences />
  </dialog>
</template>
