import { reactive, readonly, shallowRef } from 'vue'
import { defaultReadingSettings, loadReadingSettings, normalizeReadingSettings } from '../readingSettings.js'

let storage
try { storage = globalThis.localStorage } catch { /* 禁用存储时仍可临时调整。 */ }
const preferences = reactive(loadReadingSettings(storage))
const storageWarning = shallowRef('')
export function useReadingPreferences() {
  function update(patch) {
    Object.assign(preferences, normalizeReadingSettings({ ...preferences, ...patch }))
    try { storage.setItem('mojian_reading_settings', JSON.stringify(preferences)); storageWarning.value = '' }
    catch { storageWarning.value = '浏览器未能保存设置，本次阅读仍可使用。' }
  }
  return { preferences: readonly(preferences), storageWarning: readonly(storageWarning), update, reset: () => update(defaultReadingSettings) }
}
