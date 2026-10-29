<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api, roleNames } from '../session'
import PageControls from '../components/PageControls.vue'
const keyword = ref(''), role = ref(''), enabled = ref(''), busy = ref(false), error = ref('')
const result = ref({ items: [], page: 1, size: 12, total: 0 }), selected = ref(null), reason = ref('')
let applied = { keyword: '', role: '', enabled: '' }
function search() { applied = { keyword: keyword.value.trim(), role: role.value, enabled: enabled.value }; load(1) }
async function load(page = 1) {
  keyword.value = applied.keyword; role.value = applied.role; enabled.value = applied.enabled
  busy.value = true; error.value = ''; selected.value = null
  const query = new URLSearchParams({ keyword: applied.keyword, role: applied.role, page, size: 12 })
  if (applied.enabled) query.set('enabled', applied.enabled)
  try { result.value = await api(`/admin/users?${query}`) } catch (e) { error.value = e.message } finally { busy.value = false }
}
function choose(item) { selected.value = { ...item }; reason.value = ''; error.value = '' }
async function save(statusOnly = false) {
  if (busy.value || !selected.value) return
  if (!reason.value.trim()) { error.value = '请填写处理原因'; return }
  if (!statusOnly && !selected.value.nickname.trim()) { error.value = '昵称不能为空'; return }
  const current = selected.value
  if (statusOnly) {
    try { await ElMessageBox.confirm(current.enabled ? '停用后该账号不能登录，现有登录凭证也会失效。作品与评论保留，需要违规处理时请到对应管理页操作。' : '启用后用户需重新登录，旧登录凭证不会恢复。', current.enabled ? '停用账号' : '启用账号', { confirmButtonText: '确认', cancelButtonText: '取消', type: 'warning' }) } catch { return }
  }
  busy.value = true; error.value = ''
  try {
    const body = statusOnly ? { enabled: !current.enabled, version: current.version, reason: reason.value } : { nickname: current.nickname, bio: current.bio, version: current.version, reason: reason.value }
    await api(`/admin/users/${current.id}/${statusOnly ? 'status' : 'profile'}`, { method: 'PUT', body })
    ElMessage.success('已保存并记录处理原因'); await load(result.value.page)
  } catch (e) { error.value = e.message } finally { busy.value = false }
}
onMounted(() => load())
</script>
<template>
  <section class="page-wrap"><p class="eyebrow">账号维护</p><h1 class="page-title">用户管理</h1><p class="page-intro">查询读者、作者账号，维护公开资料和账号状态。管理员账号仅供查看。</p>
    <form class="admin-filter" @submit.prevent="search"><label>账号或昵称<input v-model="keyword" maxlength="50" :disabled="busy" placeholder="输入关键词" /></label><label>角色<select v-model="role" :disabled="busy"><option value="">全部角色</option><option value="READER">读者</option><option value="AUTHOR">作者</option><option value="ADMIN">管理员</option></select></label><label>状态<select v-model="enabled" :disabled="busy"><option value="">全部状态</option><option value="true">正常</option><option value="false">已停用</option></select></label><el-button native-type="submit" :disabled="busy">查询用户</el-button></form>
    <el-alert v-if="error" :title="error" type="error" :closable="false" class="form-alert" />
    <div class="admin-columns" v-loading="busy"><div><p class="muted">共 {{ result.total }} 个账号</p><div class="admin-list"><button v-for="item in result.items" :key="item.id" class="admin-row" :class="{ selected: selected?.id === item.id }" :disabled="busy" @click="choose(item)"><span><strong>{{ item.nickname }}</strong><small>{{ item.username }} · #{{ item.id }}</small></span><span class="admin-row-meta">{{ roleNames[item.role] }}<small :class="{ 'status-disabled': !item.enabled }">{{ item.enabled ? '正常' : '已停用' }}</small></span></button></div><p v-if="!result.items.length && !busy" class="empty-state">没有符合条件的账号</p><PageControls v-bind="result" :busy="busy" @change="load" /></div>
      <form v-if="selected" class="admin-panel" @submit.prevent="save(false)"><h2>账号资料</h2><p class="muted">{{ selected.username }} · {{ roleNames[selected.role] }}</p><p class="muted">注册时间：{{ selected.createdAt?.replace('T', ' ') }} UTC</p><label>昵称<input v-model="selected.nickname" maxlength="30" :disabled="busy || selected.role === 'ADMIN'" required /></label><label>个人简介<textarea v-model="selected.bio" rows="4" maxlength="500" :disabled="busy || selected.role === 'ADMIN'" /></label><template v-if="selected.role !== 'ADMIN'"><label>处理原因<textarea v-model="reason" rows="2" maxlength="500" :disabled="busy" placeholder="说明本次修改或停用原因" /></label><div class="editor-actions"><el-button native-type="submit" type="primary" :disabled="busy">保存资料</el-button><el-button :type="selected.enabled ? 'danger' : 'primary'" plain :disabled="busy" @click="save(true)">{{ selected.enabled ? '停用账号' : '启用账号' }}</el-button></div></template><p v-else class="muted">为避免误操作锁定后台，此处不修改管理员账号。</p></form>
      <div v-else class="admin-panel empty-state">选择一个账号查看和维护资料</div>
    </div>
  </section>
</template>
