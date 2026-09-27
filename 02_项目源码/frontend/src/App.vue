<script setup>
import { computed, ref } from 'vue'
import AdminLayout from './components/AdminLayout.vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { api, session, roleNames, clearSession } from './session'
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
  <div class="site-shell">
    <header class="site-header">
      <router-link to="/" class="brand" aria-label="墨间首页"><span class="brand-mark">墨</span><span>墨间<small>原创小说读写平台</small></span></router-link>
      <nav aria-label="主导航"><router-link to="/">首页</router-link><router-link to="/rankings">排行榜</router-link><router-link v-if="session.user" to="/bookshelf">我的书架</router-link><router-link v-if="session.user" to="/profile">个人中心</router-link><router-link v-if="session.user?.role === 'AUTHOR'" to="/author">作者工作台</router-link><router-link v-if="session.user?.role === 'ADMIN'" to="/admin">管理后台</router-link></nav>
      <div class="account-nav" v-if="session.user"><span class="role-pill">{{ roleNames[session.user.role] }}</span><el-button text :loading="leaving" @click="logout">退出登录</el-button></div>
      <div class="account-nav" v-else><router-link class="quiet-link" :to="authLocation('/login', authNext)">登录</router-link><router-link class="solid-link" :to="authLocation('/register', authNext)">创建账号</router-link></div>
    </header>
    <main id="main-content"><AdminLayout v-if="route.meta.role === 'ADMIN'"><router-view /></AdminLayout><router-view v-else /></main>
    <footer class="site-footer"><span>墨间 · 让故事从这里开始</span><span>当前版本 0.9 · 写作与阅读</span></footer>
  </div>
</template>
