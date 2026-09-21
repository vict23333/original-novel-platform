import { test } from 'node:test'
import assert from 'node:assert/strict'
import { authLocation, contextualDestination, loginDestination } from '../src/navigation.js'

const reader = { role: 'READER' }, author = { role: 'AUTHOR' }, admin = { role: 'ADMIN' }

test('直接登录按身份进入日常使用页面', () => {
  assert.equal(loginDestination(reader), '/')
  assert.equal(loginDestination(author), '/author')
  assert.equal(loginDestination(admin), '/admin')
})

test('三种身份均能回到原作品、阅读进度和书架', () => {
  for (const user of [reader, author, admin]) {
    for (const target of ['/books/12', '/read/36?resume=1', '/bookshelf', '/profile', '/#library']) {
      assert.equal(loginDestination(user, target), target)
    }
  }
})

test('作者登录后回到具体作品或章节编辑页', () => {
  for (const target of ['/author/novels/new', '/author/novels/12', '/author/novels/12/chapters/new', '/author/chapters/36']) {
    assert.equal(loginDestination(author, target), target)
  }
})

test('不具备页面角色时使用自己的默认入口', () => {
  assert.equal(loginDestination(reader, '/author/novels/12'), '/')
  assert.equal(loginDestination(author, '/admin'), '/author')
  assert.equal(loginDestination(admin, '/author/chapters/36'), '/admin')
  assert.equal(loginDestination(reader, '/admin/comments'), '/')
  assert.equal(loginDestination(admin, '/admin/comments'), '/admin/comments')
})

test('拒绝外部地址、编码绕过、未知页面和异常参数', () => {
  for (const target of ['https://example.com', '//example.com', '/\\example.com', '/%2fexample.com', '/books/1\n', '/read/1?next=https://example.com', '/missing', '/books/0', ['/books/1'], {}, null]) {
    assert.equal(loginDestination(author, target), '/author')
    assert.deepEqual(authLocation('/login', target), { path: '/login', query: {} })
  }
})

test('回跳目标不能是登录注册页，避免循环', () => {
  for (const target of ['/login', '/register', '/login?next=/admin']) {
    assert.equal(loginDestination(reader, target), '/')
  }
})

test('登录切换注册再回登录保留原章节和恢复标记', () => {
  const register = authLocation('/register', '/read/36?resume=1')
  const login = authLocation('/login', register.query.next)
  assert.equal(loginDestination(reader, login.query.next), '/read/36?resume=1')
})

test('从书页的顶部登录保留上下文，从首页登录使用身份默认入口', () => {
  assert.equal(contextualDestination({ path: '/books/12', fullPath: '/books/12' }), '/books/12')
  assert.equal(contextualDestination({ path: '/', fullPath: '/#library' }), null)
  assert.equal(contextualDestination({ path: '/register', query: { next: '/bookshelf' } }), '/bookshelf')
})
