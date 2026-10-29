import { test } from 'node:test'
import assert from 'node:assert/strict'
import { effectScope, nextTick, shallowRef } from 'vue'
import { usePublicLibrary } from '../src/composables/usePublicLibrary.js'

function setup(t, initial = {}) {
  const scope = effectScope(), query = shallowRef(initial), requests = []
  const state = scope.run(() => usePublicLibrary(query, (path, options) => new Promise((resolve, reject) => {
    requests.push({ path, ...options, resolve, reject })
  })))
  t.after(() => scope.stop())
  return { scope, query, requests, ...state }
}
const data = (title, page = 1) => ({ items: [{ title }], page, size: 12, total: 24 })

test('从 URL 条件首次加载；切换查询取消旧请求且迟到响应不覆盖', async t => {
  const state = setup(t, { keyword: '星', page: 2 })
  assert.match(state.requests[0].path, /page=2/)
  assert.equal(state.requests[0].publicRequest, true)
  state.query.value = { keyword: '海' }
  await nextTick()
  assert.equal(state.requests[0].signal.aborted, true)
  state.requests[1].resolve(data('海'))
  await nextTick()
  state.requests[0].resolve(data('星', 2))
  await nextTick()
  assert.equal(state.result.value.items[0].title, '海')
  assert.equal(state.busy.value, false)
})

test('迟到的失败不能清除新请求的加载状态或显示旧错误', async t => {
  const state = setup(t)
  state.query.value = { page: 2 }
  await nextTick()
  state.requests[0].reject(new Error('旧请求失败'))
  await nextTick()
  assert.equal(state.busy.value, true)
  assert.equal(state.error.value, '')
  assert.equal(state.result.value.page, 2)
  state.requests[1].resolve(data('新结果', 2))
  await nextTick()
  assert.equal(state.busy.value, false)
})

test('查询失败可按相同条件重试，失败期间无可误点的旧结果', async t => {
  const state = setup(t, { keyword: '雾' })
  state.requests[0].reject(new Error('连接失败'))
  await nextTick()
  assert.equal(state.error.value, '连接失败')
  state.retry()
  await nextTick()
  assert.equal(state.requests[1].path, state.requests[0].path)
  assert.equal(state.error.value, '')
  assert.deepEqual(state.result.value.items, [])
  state.requests[1].resolve(data('雾'))
  await nextTick()
  assert.equal(state.result.value.items[0].title, '雾')
})

test('无关 URL 参数变化不重复查询，离开页面取消未完成请求', async t => {
  const state = setup(t)
  state.query.value = { unrelated: 'ignored' }
  await nextTick()
  assert.equal(state.requests.length, 1)
  state.scope.stop()
  assert.equal(state.requests[0].signal.aborted, true)
  state.requests[0].resolve(data('离开后的结果'))
  await nextTick()
  assert.deepEqual(state.result.value.items, [])
})
