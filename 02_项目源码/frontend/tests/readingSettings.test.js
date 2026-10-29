import { test } from 'node:test'
import assert from 'node:assert/strict'
import { defaultReadingSettings, loadReadingSettings, normalizeReadingSettings } from '../src/readingSettings.js'

test('升级后保留原来的字号与背景，为新增行距和版心补默认值', () => {
  const storage = { getItem: () => JSON.stringify({ fontSize: 24, theme: 'dark' }) }
  assert.deepEqual(loadReadingSettings(storage), { ...defaultReadingSettings, fontSize: 24, theme: 'dark' })
})
test('关闭浏览器存储、损坏 JSON 和空设置仍能使用默认阅读方式', () => {
  for (const storage of [undefined, { getItem() { throw new Error('disabled') } }, { getItem: () => '{broken' }, { getItem: () => 'null' }]) assert.deepEqual(loadReadingSettings(storage), defaultReadingSettings)
})
test('不信任存储内的异常样式值，只接受阅读选项白名单', () => {
  const actual = normalizeReadingSettings({ fontSize: 99, theme: 'bad', lineHeight: -1, width: '__proto__', key: 'not-a-setting' })
  assert.deepEqual(actual, defaultReadingSettings)
  assert.deepEqual(normalizeReadingSettings({ fontSize: 28, theme: 'green', lineHeight: 2.4, width: 'wide' }), { fontSize: 28, theme: 'green', lineHeight: 2.4, width: 'wide' })
})
