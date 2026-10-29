<script setup>
import { useRoute } from 'vue-router'
const route = useRoute()
</script>
<template>
  <div class="workspace-shell author-shell">
    <aside class="workspace-sidebar"><router-link class="workspace-brand" to="/author">创作中心</router-link><p>专心写好每一个故事</p>
      <nav aria-label="创作导航"><router-link to="/author" :class="{ active: (route.path === '/author' && route.query.view !== 'stats') || (route.path.startsWith('/author/novels/') && route.path !== '/author/novels/new') }">我的作品</router-link><router-link to="/author?view=stats" :class="{ active: route.path === '/author' && route.query.view === 'stats' }">数据概览</router-link><router-link to="/author/novels/new" :class="{ active: route.path === '/author/novels/new' }">新建作品</router-link></nav>
      <router-link class="workspace-back" to="/library">浏览书库</router-link>
    </aside><div class="workspace-main"><slot /></div>
  </div>
</template>
