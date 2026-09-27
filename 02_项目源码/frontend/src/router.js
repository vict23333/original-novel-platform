import { createRouter, createWebHistory } from 'vue-router'
import { api, session } from './session'
import { authLocation, defaultDestination, loginDestination } from './navigation'
import RankingsPage from './views/RankingsPage.vue'
import AuthorStatsPage from './views/AuthorStatsPage.vue'
import HomePage from './views/HomePage.vue'
import AuthPage from './views/AuthPage.vue'
import ProfilePage from './views/ProfilePage.vue'
import AuthorPage from './views/AuthorPage.vue'
import NovelEditorPage from './views/NovelEditorPage.vue'
import ChapterEditorPage from './views/ChapterEditorPage.vue'
import AdminReviewPage from './views/AdminReviewPage.vue'
import AdminCommentsPage from './views/AdminCommentsPage.vue'
import AdminUsersPage from './views/AdminUsersPage.vue'
import AdminTaxonomyPage from './views/AdminTaxonomyPage.vue'
import AdminAuditsPage from './views/AdminAuditsPage.vue'
import BookDetailPage from './views/BookDetailPage.vue'
import ReadingPage from './views/ReadingPage.vue'
import BookshelfPage from './views/BookshelfPage.vue'

const router = createRouter({ history: createWebHistory(), routes: [
  { path: '/', component: HomePage },
  { path: '/rankings', component: RankingsPage },
  { path: '/author/novels/:id/stats', component: AuthorStatsPage, meta: { auth: true, role: 'AUTHOR' } },
  { path: '/login', component: AuthPage, meta: { guestOnly: true } },
  { path: '/register', component: AuthPage, meta: { guestOnly: true } },
  { path: '/bookshelf', component: BookshelfPage, meta: { auth: true } },
  { path: '/profile', component: ProfilePage, meta: { auth: true } },
  { path: '/author', component: AuthorPage, meta: { auth: true, role: 'AUTHOR' } },
  { path: '/author/novels/:id', component: NovelEditorPage, meta: { auth: true, role: 'AUTHOR' } },
  { path: '/author/novels/:novelId/chapters/new', component: ChapterEditorPage, meta: { auth: true, role: 'AUTHOR' } },
  { path: '/author/chapters/:id', component: ChapterEditorPage, meta: { auth: true, role: 'AUTHOR' } },
  { path: '/admin', component: AdminReviewPage, meta: { auth: true, role: 'ADMIN' } },
  { path: '/admin/comments', component: AdminCommentsPage, meta: { auth: true, role: 'ADMIN' } },
  { path: '/admin/users', component: AdminUsersPage, meta: { auth: true, role: 'ADMIN' } },
  { path: '/admin/taxonomy', component: AdminTaxonomyPage, meta: { auth: true, role: 'ADMIN' } },
  { path: '/admin/audits', component: AdminAuditsPage, meta: { auth: true, role: 'ADMIN' } },
  { path: '/books/:id', component: BookDetailPage },
  { path: '/read/:id', component: ReadingPage },
  { path: '/:pathMatch(.*)*', redirect: '/' },
], scrollBehavior: () => ({ top: 0 }) })

router.beforeEach(async to => {
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
