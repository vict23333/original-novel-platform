<script setup>
import { computed, ref } from 'vue'
import AdminLayout from './components/AdminLayout.vue'
import AuthorLayout from './components/AuthorLayout.vue'
import HeaderAccount from './components/HeaderAccount.vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { api, session, clearSession } from './session'
import { authLocation, contextualDestination } from './navigation'
const router = useRouter()
const route = useRoute()
const authNext = computed(() => contextualDestination(route))
const leaving = ref(false)
async function logout() {
  leaving.value = true
  try { await api('/auth/logout', { method: 'POST' }); clearSession(); await router.push('/'); ElMessage.success('已退出登录') }
  catch (e) { ElMessage.error(e.message); if (!session.token) router.push('/login') }
  finally { leaving.value = false }
}
</script>
<template>
  <div class="site-shell" :class="{ 'is-writing': route.meta.editor, 'is-reading': route.path.startsWith('/read/') }">
    <a class="skip-link" href="#main-content">跳到主要内容</a>
    <header class="site-header"><div class="header-inner">
      <router-link to="/" class="brand" aria-label="墨间首页"><span class="brand-mark">墨</span><span>墨间<small>读故事 · 写故事</small></span></router-link>
      <nav class="primary-nav" aria-label="主导航"><router-link to="/">发现</router-link><router-link to="/library">书库</router-link><router-link to="/rankings">排行榜</router-link><router-link v-if="session.user" to="/bookshelf">我的书架</router-link></nav>
      <div class="account-nav"><router-link v-if="session.user?.role === 'AUTHOR'" class="workspace-entry" to="/author">创作中心</router-link><router-link v-if="session.user?.role === 'ADMIN'" class="workspace-entry" to="/admin">管理中心</router-link>
        <HeaderAccount v-if="session.user" :leaving="leaving" @logout="logout" />
        <template v-else><router-link class="settings-entry" to="/settings" aria-label="阅读设置" title="阅读设置">⚙</router-link><router-link class="quiet-link" :to="authLocation('/login', authNext)">登录</router-link><router-link class="solid-link" :to="authLocation('/register', authNext)">注册</router-link></template>
      </div>
    </div></header>
    <main id="main-content" tabindex="-1"><AdminLayout v-if="route.meta.role === 'ADMIN'"><router-view /></AdminLayout><AuthorLayout v-else-if="route.meta.role === 'AUTHOR' && !route.meta.editor"><router-view /></AuthorLayout><router-view v-else /></main>
    <footer class="site-footer"><span>墨间，让每个故事找到读者</span><div><router-link to="/library">全部书库</router-link><router-link to="/settings">阅读设置</router-link><span>v0.13</span></div></footer>
  </div>
</template>
