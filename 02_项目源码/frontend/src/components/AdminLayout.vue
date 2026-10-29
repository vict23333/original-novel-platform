<script setup>
const links = [['/admin', '作品与章节审核'], ['/admin/users', '用户管理'], ['/admin/comments', '评论管理'], ['/admin/taxonomy', '分类与标签'], ['/admin/audits', '操作记录']]
</script>
<template>
  <div class="workspace-shell admin-shell"><aside class="workspace-sidebar admin-sidebar"><router-link class="workspace-brand" to="/admin">管理中心</router-link><p>维护内容与平台秩序</p>
    <nav aria-label="后台导航"><router-link v-for="[path, name] in links" :key="path" :to="path" :class="{ active: $route.path === path }">{{ name }}</router-link></nav><router-link class="workspace-back" to="/library">查看读者书库</router-link>
  </aside><div class="workspace-main admin-main"><slot /></div></div>
</template>
