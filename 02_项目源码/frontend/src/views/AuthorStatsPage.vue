<script setup>
import { useRoute } from 'vue-router'
import AuthorStatistics from '../components/AuthorStatistics.vue'
const route = useRoute()
</script>
<template><section class="page-wrap"><router-link class="quiet-link" to="/author">← 返回作者工作台</router-link><h1 class="page-title stats-title">作品数据</h1><AuthorStatistics :novel-id="route.params.id" /><router-link class="quiet-link" :to="`/author/novels/${route.params.id}`">管理作品与章节 →</router-link></section></template>
