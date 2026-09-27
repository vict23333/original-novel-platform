<script setup>
const links = [['/admin', '作品与章节审核', '01'], ['/admin/users', '用户管理', '02'], ['/admin/comments', '评论管理', '03'], ['/admin/taxonomy', '分类与标签', '04'], ['/admin/audits', '操作记录', '05']]
</script>
<template>
  <div class="admin-shell">
    <aside class="admin-sidebar"><p class="eyebrow">墨间 · 管理中心</p><h2>平台管理</h2><p class="muted">内容、账号与书库维护</p>
      <nav aria-label="后台导航"><router-link v-for="[path, name, number] in links" :key="path" :to="path" :class="{ active: $route.path === path }"><small>{{ number }}</small>{{ name }}</router-link></nav>
      <router-link class="quiet-link" to="/">查看读者首页 →</router-link>
    </aside>
    <div class="admin-main"><slot /></div>
  </div>
</template>
