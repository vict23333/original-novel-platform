<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { api, session, roleNames } from '../session'
const router = useRouter()
const form = reactive({ nickname: session.user?.nickname || '', bio: session.user?.bio || '' })
const busy = ref(false), error = ref('')
async function save() {
  if (busy.value) return
  busy.value = true; error.value = ''
  try { session.user = await api('/users/me', { method: 'PUT', body: form }); ElMessage.success('个人资料已保存') }
  catch (e) { error.value = e.message; if (e.status === 401) ElMessage.warning('请重新登录，当前输入仍保留在页面中') }
  finally { busy.value = false }
}
</script>
<template>
  <section class="page-wrap"><h1 class="page-title">个人资料</h1><p class="page-intro">管理公开昵称和个人简介。</p><div class="profile-layout"><aside class="identity-card"><div class="avatar">{{ session.user?.nickname?.slice(0, 1) || '墨' }}</div><h2>{{ session.user?.nickname }}</h2><span class="role-pill">{{ roleNames[session.user?.role] }}</span><dl><dt>登录账号</dt><dd>{{ session.user?.username }}</dd><dt>资料可见范围</dt><dd>昵称与简介用于公开展示</dd></dl><p>账号身份由平台校验，个人资料编辑不会改变角色权限。</p></aside><div class="profile-card"><h2>编辑个人资料</h2><p class="muted">保持真实的表达，不必填写私人联系方式。</p><el-alert v-if="error" :title="error" type="error" :closable="false" show-icon class="form-alert" role="alert" /><form @submit.prevent="save"><label for="profile-name">昵称</label><el-input id="profile-name" v-model="form.nickname" :maxlength="30" show-word-limit required :disabled="busy" /><label for="profile-bio">个人简介</label><el-input id="profile-bio" v-model="form.bio" type="textarea" :rows="5" :maxlength="500" show-word-limit placeholder="分享你喜欢的题材，或你的创作方向……" :disabled="busy" /><div class="profile-actions"><el-button type="primary" native-type="submit" :loading="busy">保存修改</el-button><el-button v-if="!session.user" @click="router.push('/login')">重新登录</el-button></div></form></div></div></section>
</template>
