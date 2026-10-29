<script setup>
import { computed, onBeforeUnmount, onMounted, shallowRef, useTemplateRef, watch } from 'vue'
import { useRoute } from 'vue-router'
import { session, roleNames } from '../session'
defineProps({ leaving: Boolean })
defineEmits(['logout'])
const route = useRoute(), open = shallowRef(false), root = useTemplateRef('root'), trigger = useTemplateRef('trigger')
const initial = computed(() => Array.from(session.user?.nickname || '读')[0])
function outside(event) { if (!root.value?.contains(event.target)) open.value = false }
function escape(event) { if (event.key === 'Escape' && open.value) { open.value = false; trigger.value?.focus() } }
watch(() => route.fullPath, () => { open.value = false })
onMounted(() => { document.addEventListener('pointerdown', outside); document.addEventListener('keydown', escape) })
onBeforeUnmount(() => { document.removeEventListener('pointerdown', outside); document.removeEventListener('keydown', escape) })
</script>
<template>
  <div ref="root" class="account-menu">
    <button ref="trigger" class="account-trigger" :aria-expanded="open" aria-controls="account-links" @click="open = !open"><span class="account-avatar">{{ initial }}</span><span class="account-name">{{ session.user?.nickname }}</span><span aria-hidden="true" class="menu-chevron">⌄</span><span class="sr-only">账号菜单</span></button>
    <div v-if="open" id="account-links" class="account-popover" aria-label="账号操作">
      <div class="account-summary"><strong>{{ session.user?.nickname }}</strong><small>{{ roleNames[session.user?.role] }}账号</small></div>
      <router-link to="/profile">个人资料</router-link><router-link to="/settings">阅读设置</router-link>
      <button :disabled="leaving" @click="$emit('logout')">{{ leaving ? '正在退出…' : '退出登录' }}</button>
    </div>
  </div>
</template>
