import { test } from 'node:test'
import assert from 'node:assert/strict'
import { libraryLocation, libraryRequest, normalizeLibraryQuery } from '../src/libraryQuery.js'

test('书库分享链接保留中文、组合条件及页码，往返不丢失', () => {
  const state = { keyword: ' 雾 & 山? ', categoryId: '3', serialStatus: 'COMPLETED', sort: 'UPDATED', page: 2 }
  const location = libraryLocation(state)
  assert.equal(location.path, '/library')
  assert.deepEqual(normalizeLibraryQuery(location.query), { ...state, keyword: '雾 & 山?' })
  const params = new URLSearchParams(libraryRequest(state).split('?')[1])
  assert.equal(params.get('keyword'), '雾 & 山?')
  assert.equal(params.get('size'), '12')
  assert.equal(params.get('page'), '2')
})

test('默认查询使用短链接，重置清除所有筛选', () => {
  assert.deepEqual(libraryLocation({}).query, {})
  const params = new URLSearchParams(libraryRequest({}).split('?')[1])
  assert.equal(params.has('categoryId'), false)
  assert.equal(params.has('serialStatus'), false)
  assert.equal(params.get('sort'), 'NEWEST')
})

test('地址栏的非法页码、分类和重复参数安全回退', () => {
  for (const value of [-1, 0, '1.2', '1e3', ['2', '3'], null, {}, Infinity, 'abc']) {
    const actual = normalizeLibraryQuery({ page: value, categoryId: value })
    assert.equal(actual.page, 1)
    assert.equal(actual.categoryId, '')
  }
  assert.equal(normalizeLibraryQuery({ page: '100001' }).page, 1)
  assert.equal(normalizeLibraryQuery({ page: '100000' }).page, 100000)
  assert.equal(normalizeLibraryQuery({ categoryId: '9007199254740992' }).categoryId, '')
})

test('只接受支持的排序和状态，关键词限制与后端一致', () => {
  assert.equal(normalizeLibraryQuery({ keyword: ['x', 'y'] }).keyword, '')
  assert.equal(normalizeLibraryQuery({ keyword: '中'.repeat(101) }).keyword.length, 100)
  for (const sort of ['POPULAR', 'COLLECTED', 'NEWEST', 'UPDATED']) assert.equal(normalizeLibraryQuery({ sort }).sort, sort)
  assert.equal(normalizeLibraryQuery({ sort: 'other', serialStatus: 'DRAFT' }).sort, 'NEWEST')
  assert.equal(normalizeLibraryQuery({ serialStatus: 'DRAFT' }).serialStatus, '')
})
