export const defaultReadingSettings = Object.freeze({ fontSize: 20, theme: 'paper', lineHeight: 2.15, width: 'comfortable' })
export const readingWidths = Object.freeze({ narrow: 680, comfortable: 800, wide: 960 })
export function normalizeReadingSettings(value = {}) {
  if (!value || typeof value !== 'object') value = {}
  return {
    fontSize: [16, 18, 20, 22, 24, 26, 28].includes(value.fontSize) ? value.fontSize : 20,
    theme: ['paper', 'green', 'dark'].includes(value.theme) ? value.theme : 'paper',
    lineHeight: [1.8, 2, 2.15, 2.4].includes(value.lineHeight) ? value.lineHeight : 2.15,
    width: Object.hasOwn(readingWidths, value.width) ? value.width : 'comfortable',
  }
}
export function loadReadingSettings(storage) {
  try { return normalizeReadingSettings(JSON.parse(storage.getItem('mojian_reading_settings') || '{}')) }
  catch { return { ...defaultReadingSettings } }
}
