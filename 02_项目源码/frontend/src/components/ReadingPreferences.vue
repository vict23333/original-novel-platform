<script setup>
import { useReadingPreferences } from '../composables/useReadingPreferences'
const { preferences, storageWarning, update, reset } = useReadingPreferences()
</script>
<template>
  <div class="reading-preferences">
    <fieldset><legend>阅读背景</legend><div class="theme-options"><button v-for="option in [{value:'paper',label:'纸色'},{value:'green',label:'护眼'},{value:'dark',label:'夜间'}]" :key="option.value" type="button" :aria-pressed="preferences.theme === option.value" @click="update({ theme: option.value })"><span :class="`theme-swatch swatch-${option.value}`" aria-hidden="true"></span>{{ option.label }}</button></div></fieldset>
    <fieldset><legend>正文字号</legend><div class="font-adjust"><button type="button" aria-label="减小字号" :disabled="preferences.fontSize <= 16" @click="update({ fontSize: preferences.fontSize - 2 })">A−</button><output aria-live="polite">{{ preferences.fontSize }} px</output><button type="button" aria-label="增大字号" :disabled="preferences.fontSize >= 28" @click="update({ fontSize: preferences.fontSize + 2 })">A＋</button></div></fieldset>
    <label>正文行距<select name="reading-line-height" :value="preferences.lineHeight" @change="update({ lineHeight: Number($event.target.value) })"><option :value="1.8">紧凑</option><option :value="2">适中</option><option :value="2.15">舒适</option><option :value="2.4">宽松</option></select></label>
    <label>阅读宽度<select name="reading-width" :value="preferences.width" @change="update({ width: $event.target.value })"><option value="narrow">窄版</option><option value="comfortable">标准</option><option value="wide">宽版</option></select></label>
    <p class="settings-save-note" role="status">{{ storageWarning || '设置即时生效，保存在当前浏览器。' }}</p>
    <button type="button" class="text-button" @click="reset">恢复默认设置</button>
  </div>
</template>
