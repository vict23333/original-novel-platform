import { test } from 'node:test'
import assert from 'node:assert/strict'
import { pageTarget } from '../src/pagination.js'
test('可直接跳转末页，不接受越界、小数或无效页码', () => {
  assert.equal(pageTarget('7', 128, 20), 7)
  for (const value of [0, 8, -1, '2.1', '', 'abc', Infinity]) assert.equal(pageTarget(value, 128, 20), null)
})
test('空列表只有第一页，整页数量不多算一页', () => {
  assert.equal(pageTarget(1, 0, 20), 1)
  assert.equal(pageTarget(2, 20, 20), null)
})
