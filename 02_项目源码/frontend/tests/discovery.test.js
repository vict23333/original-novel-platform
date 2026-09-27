import test from 'node:test'
import assert from 'node:assert/strict'
import { guestIdentity, dateText } from '../src/discovery.js'
test('anonymous identity survives refresh within the same storage session', () => {
  const data = new Map(); const storage = { getItem: key => data.get(key), setItem: (key, value) => data.set(key, value) }
  const first = guestIdentity(storage, () => '12345678-1234-4123-8123-123456789abc')
  assert.equal(guestIdentity(storage, () => { throw Error('must reuse identity') }), first)
})
test('unavailable browser storage does not block reading identity', () => {
  const broken = { getItem() { throw Error('blocked') } }
  assert.match(guestIdentity(broken), /^[0-9a-f-]{36}$/i)
})
test('public update time is explicitly formatted in platform timezone', () => {
  assert.match(dateText('2026-09-20T16:05:00'), /09.?21/)
  assert.match(dateText('2026-09-20T16:05:00'), /00:05/)
  assert.equal(dateText(null), '暂无公开更新')
})
