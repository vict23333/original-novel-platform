import { libraryLocation } from './libraryQuery.js'
// Only known local routes may be used as a post-login destination.
export function safeDestination(value) {
  if (typeof value !== 'string') return null
  if (value === '/settings' || value === '/author?view=stats') return value
  if (/^\/library(?:\?[^#]*)?$/.test(value) && !/[\r\n]/.test(value)) {
    const parsed = new URL(value, 'https://local.invalid')
    const keys = [...parsed.searchParams.keys()]
    if (keys.some(key => !['keyword','categoryId','serialStatus','sort','page'].includes(key)) || new Set(keys).size !== keys.length || [...parsed.searchParams.values()].some(v => /[\r\n]/.test(v))) return null
    const target = libraryLocation(Object.fromEntries(parsed.searchParams))
    const query = new URLSearchParams(target.query).toString()
    return target.path + (query ? `?${query}` : '')
  }
  if (/^\/rankings(?:\?kind=(?:POPULAR|COLLECTED|NEWEST|UPDATED))?$/.test(value) || /^\/author\/novels\/[1-9]\d*\/(?:stats|studio)$/.test(value)) return value
  return /^(?:\/(?:#library)?|\/(?:profile|bookshelf|admin(?:\/(?:comments|users|taxonomy|audits))?|author)|\/books\/[1-9]\d*|\/read\/[1-9]\d*(?:\?resume=1)?|\/author\/novels\/(?:new|[1-9]\d*)|\/author\/novels\/[1-9]\d*\/chapters\/new|\/author\/chapters\/[1-9]\d*)$/.test(value) && !/[\r\n]/.test(value) ? value : null
}

export function defaultDestination(user) {
  return user?.role === 'AUTHOR' ? '/author' : user?.role === 'ADMIN' ? '/admin' : '/'
}

export function loginDestination(user, requested) {
  const target = safeDestination(requested)
  if (!target || (target.startsWith('/author') && user?.role !== 'AUTHOR') || (target.startsWith('/admin') && user?.role !== 'ADMIN')) return defaultDestination(user)
  return target
}

export function authLocation(path, requested) {
  const next = safeDestination(requested)
  return { path, query: next ? { next } : {} }
}

export function contextualDestination(route) {
  if (route.path === '/login' || route.path === '/register') return safeDestination(route.query.next)
  // A login from the home page uses the role's default landing page.
  return route.path === '/' ? null : safeDestination(route.fullPath)
}
