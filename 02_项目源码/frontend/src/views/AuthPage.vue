<script setup>
import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { api, acceptLogin, session } from '../session'
import { authLocation, loginDestination } from '../navigation'
const route = useRoute(), router = useRouter()
const registering = computed(() => route.path === '/register')
const busy = ref(false), error = ref('')
const form = reactive({ username: '', password: '', confirm: '', nickname: '', role: 'READER' })
async function submit() {
  if (busy.value) return
  error.value = ''
  if (registering.value && form.password !== form.confirm) { error.value = '两次输入的密码不一致'; return }
  busy.value = true
  try {
    if (registering.value) {
      await api('/auth/register', { method: 'POST', publicRequest: true, body: { username: form.username, password: form.password, nickname: form.nickname, role: form.role } })
      ElMessage.success('注册成功，请登录'); form.password = ''; form.confirm = ''; await router.replace(authLocation('/login', route.query.next))
    } else {
      const result = await api('/auth/login', { method: 'POST', publicRequest: true, body: { username: form.username, password: form.password } })
      acceptLogin(result); form.password = ''
      await router.replace(loginDestination(session.user, route.query.next))
    }
  } catch (e) { error.value = e.message }
  finally { busy.value = false }
}
</script>
<template>
  <section class="auth-layout">
    <aside class="auth-story"><p class="eyebrow">WELCOME TO MOJIAN</p><h1>{{ registering ? '你的故事，\n值得被看见。' : '欢迎回来，\n故事仍在继续。' }}</h1><p>读一段文字，写一场远行。<br>从这里，连接阅读与创作。</p><div class="story-lines" aria-hidden="true"><span></span><span></span><span></span></div><small>原创 · 阅读 · 创作</small></aside>
    <div class="auth-card"><div class="form-heading"><p class="eyebrow">{{ registering ? '开启旅程' : '再次相逢' }}</p><h2>{{ registering ? '创建账号' : '登录墨间' }}</h2><p>{{ registering ? '选择身份，开启阅读与创作。' : '登录后继续阅读，或进入你的工作台。' }}</p></div>
      <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon class="form-alert" role="alert" />
      <el-alert v-else-if="route.query.service" title="暂时无法连接服务，请稍后重新登录。" type="warning" :closable="false" class="form-alert" />
      <form @submit.prevent="submit">
        <label for="username">账号</label><el-input id="username" v-model="form.username" autocomplete="username" placeholder="4—20 位字母、数字或下划线" :maxlength="20" :disabled="busy" required />
        <template v-if="registering"><label for="nickname">昵称</label><el-input id="nickname" v-model="form.nickname" autocomplete="nickname" placeholder="你希望大家如何称呼你" :maxlength="30" :disabled="busy" required /></template>
        <label for="password">密码</label><el-input id="password" v-model="form.password" type="password" show-password :autocomplete="registering ? 'new-password' : 'current-password'" placeholder="8—64 位密码" :maxlength="64" :disabled="busy" required />
        <template v-if="registering"><label for="confirm">确认密码</label><el-input id="confirm" v-model="form.confirm" type="password" show-password autocomplete="new-password" placeholder="再次输入密码" :maxlength="64" :disabled="busy" required /><label id="role-label">账号身份</label><el-radio-group v-model="form.role" aria-labelledby="role-label" :disabled="busy"><el-radio-button value="READER">读者 · 探索故事</el-radio-button><el-radio-button value="AUTHOR">作者 · 书写故事</el-radio-button></el-radio-group><p class="field-hint">作者也可以使用读者功能，管理员账号不开放注册。</p></template>
        <el-button class="submit-button" type="primary" native-type="submit" :loading="busy">{{ registering ? '创建账号' : '登录' }}</el-button>
      </form>
      <p class="auth-switch">{{ registering ? '已有账号？' : '还没有账号？' }}<router-link :to="authLocation(registering ? '/login' : '/register', route.query.next)" @click="error = ''">{{ registering ? '去登录' : '创建账号' }}</router-link></p>
    </div>
  </section>
</template>
