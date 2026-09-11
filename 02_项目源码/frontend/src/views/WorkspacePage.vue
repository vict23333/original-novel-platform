<script setup>
import { onMounted, ref, computed } from 'vue'
import { useRoute } from 'vue-router'
import { api } from '../session'
const route = useRoute(), status = ref(''), error = ref(''), loading = ref(true)
const author = computed(() => route.meta.role === 'AUTHOR')
async function load() {
  loading.value = true; error.value = ''
  try { const result = await api(author.value ? '/author/workspace' : '/admin/workspace'); status.value = result.message }
  catch (e) { error.value = e.message }
  finally { loading.value = false }
}
onMounted(load)
</script>
<template><section class="page-wrap"><p class="eyebrow">{{ author ? '作者专属空间' : '平台管理空间' }}</p><h1 class="page-title">{{ author ? '作者工作台' : '管理后台' }}</h1><div class="workspace-card" v-loading="loading"><span class="outline-label">身份与权限验证</span><h2>{{ error ? '暂时无法进入' : '你的专属入口已准备好' }}</h2><p role="status">{{ error || status }}</p><el-button v-if="error" @click="load">重新验证</el-button><div class="planned-modules"><span v-for="item in (author ? ['作品管理', '章节编辑', 'AI 辅助创作', '作品数据'] : ['用户管理', '作品审核', '评论管理', '分类标签'])" :key="item">{{ item }}<small>待实现</small></span></div><router-link class="quiet-link" to="/profile">← 返回个人中心</router-link></div></section></template>
