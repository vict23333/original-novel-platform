import { onMounted, onBeforeUnmount } from 'vue'
import { onBeforeRouteLeave, onBeforeRouteUpdate } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import 'element-plus/es/components/message-box/style/css'

export function useUnsavedChanges(dirty) {
  async function confirmLeave() {
    if (!dirty.value) return true
    try { await ElMessageBox.confirm('有尚未保存的内容，离开会丢失这些修改。', '离开编辑页', { confirmButtonText: '放弃并离开', cancelButtonText: '继续编辑', type: 'warning' }); return true }
    catch { return false }
  }
  const beforeUnload = e => { if (dirty.value) { e.preventDefault(); e.returnValue = '' } }
  onBeforeRouteLeave(confirmLeave)
  onBeforeRouteUpdate(confirmLeave)
  onMounted(() => window.addEventListener('beforeunload', beforeUnload))
  onBeforeUnmount(() => window.removeEventListener('beforeunload', beforeUnload))
}
