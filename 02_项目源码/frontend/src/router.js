import { createRouter, createWebHistory } from 'vue-router'
import { api, session } from './session'
import HomePage from './views/HomePage.vue'
import AuthPage from './views/AuthPage.vue'
import ProfilePage from './views/ProfilePage.vue'
import WorkspacePage from './views/WorkspacePage.vue'

const router = createRouter({ history: createWebHistory(), routes: [
  { path: '/', component: HomePage },
  { path: '/login', component: AuthPage },
  { path: '/register', component: AuthPage },
  { path: '/profile', component: ProfilePage, meta: { auth: true } },
  { path: '/author', component: WorkspacePage, meta: { auth: true, role: 'AUTHOR' } },
  { path: '/admin', component: WorkspacePage, meta: { auth: true, role: 'ADMIN' } },
  { path: '/:pathMatch(.*)*', redirect: '/' },
], scrollBehavior: () => ({ top: 0 }) })

router.beforeEach(async to => {
  if (session.token && !session.user) {
    try { session.user = await api('/users/me') }
    catch (e) { if (e.status !== 401) return to.meta.auth ? { path: '/login', query: { service: 'unavailable' } } : true }
  }
  if (to.meta.auth && !session.user) return { path: '/login', query: { next: to.path } }
  if (to.meta.role && session.user?.role !== to.meta.role) return '/profile'
  return true
})
export default router
