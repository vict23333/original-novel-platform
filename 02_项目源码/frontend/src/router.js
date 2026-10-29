import { createRouter, createWebHistory } from 'vue-router'
import { api, session } from './session'
import { authLocation, defaultDestination, loginDestination } from './navigation'
import HomePage from './views/HomePage.vue'
const RankingsPage = () => import('./views/RankingsPage.vue')
const AuthorStatsPage = () => import('./views/AuthorStatsPage.vue')
const AuthPage = () => import('./views/AuthPage.vue')
const ProfilePage = () => import('./views/ProfilePage.vue')
const AuthorPage = () => import('./views/AuthorPage.vue')
const NovelEditorPage = () => import('./views/NovelEditorPage.vue')
const ChapterEditorPage = () => import('./views/ChapterEditorPage.vue')
const AdminReviewPage = () => import('./views/AdminReviewPage.vue')
const AdminCommentsPage = () => import('./views/AdminCommentsPage.vue')
const AdminUsersPage = () => import('./views/AdminUsersPage.vue')
const AdminTaxonomyPage = () => import('./views/AdminTaxonomyPage.vue')
const AdminAuditsPage = () => import('./views/AdminAuditsPage.vue')
const BookDetailPage = () => import('./views/BookDetailPage.vue')
const ReadingPage = () => import('./views/ReadingPage.vue')
const BookshelfPage = () => import('./views/BookshelfPage.vue')
const LibraryPage = () => import('./views/LibraryPage.vue')
const SettingsPage = () => import('./views/SettingsPage.vue')
const WritingStudioPage = () => import('./views/WritingStudioPage.vue')

const router = createRouter({ history: createWebHistory(), routes: [
  { path: '/', component: HomePage },
  { path: '/library', component: LibraryPage },
  { path: '/settings', component: SettingsPage },
  { path: '/rankings', component: RankingsPage },
  { path: '/author/novels/:id/stats', component: AuthorStatsPage, meta: { auth: true, role: 'AUTHOR' } },
  { path: '/author/novels/:id/studio', component: WritingStudioPage, meta: { auth: true, role: 'AUTHOR' } },
  { path: '/login', component: AuthPage, meta: { guestOnly: true } },
  { path: '/register', component: AuthPage, meta: { guestOnly: true } },
  { path: '/bookshelf', component: BookshelfPage, meta: { auth: true } },
  { path: '/profile', component: ProfilePage, meta: { auth: true } },
  { path: '/author', component: AuthorPage, meta: { auth: true, role: 'AUTHOR' } },
  { path: '/author/novels/:id', component: NovelEditorPage, meta: { auth: true, role: 'AUTHOR' } },
  { path: '/author/novels/:novelId/chapters/new', component: ChapterEditorPage, meta: { auth: true, role: 'AUTHOR', editor: true } },
  { path: '/author/chapters/:id', component: ChapterEditorPage, meta: { auth: true, role: 'AUTHOR', editor: true } },
  { path: '/admin', component: AdminReviewPage, meta: { auth: true, role: 'ADMIN' } },
  { path: '/admin/comments', component: AdminCommentsPage, meta: { auth: true, role: 'ADMIN' } },
  { path: '/admin/users', component: AdminUsersPage, meta: { auth: true, role: 'ADMIN' } },
  { path: '/admin/taxonomy', component: AdminTaxonomyPage, meta: { auth: true, role: 'ADMIN' } },
  { path: '/admin/audits', component: AdminAuditsPage, meta: { auth: true, role: 'ADMIN' } },
  { path: '/books/:id', component: BookDetailPage },
  { path: '/read/:id', component: ReadingPage },
  { path: '/:pathMatch(.*)*', redirect: '/' },
], scrollBehavior: (to, _from, savedPosition) => {
  if (savedPosition) return savedPosition
  if (to.path === '/' && to.hash === '#library') return { el: '#library' }
  return { top: 0 }
} })

router.beforeEach(async to => {
  if (to.path === '/' && (to.hash === '#library' || ['keyword','categoryId','serialStatus','sort','page'].some(key => key in to.query))) return { path: '/library', query: to.query, replace: true }
  if (session.token && !session.user) {
    try { session.user = await api('/users/me') }
    catch (e) { if (e.status !== 401) return to.meta.auth ? { path: '/login', query: { ...authLocation('/login', to.fullPath).query, service: 'unavailable' } } : true }
  }
  if (to.meta.auth && !session.user) return authLocation('/login', to.fullPath)
  if (to.meta.guestOnly && session.user) return loginDestination(session.user, to.query.next)
  if (to.meta.role && session.user?.role !== to.meta.role) return defaultDestination(session.user)
  return true
})
export default router
