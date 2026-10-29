import { readonly, shallowRef, watch } from 'vue'
import { libraryRequest, normalizeLibraryQuery } from '../libraryQuery.js'

// 只负责书库请求；表单草稿和 URL 导航留在页面中。
export function usePublicLibrary(query, search) {
  const result = shallowRef({ items: [], page: 1, size: 12, total: 0 })
  const busy = shallowRef(false), error = shallowRef(''), retryVersion = shallowRef(0)
  watch([() => libraryRequest(query.value), () => retryVersion.value], async ([path], _previous, onCleanup) => {
    const controller = new AbortController()
    let current = true
    onCleanup(() => { current = false; controller.abort() })
    busy.value = true; error.value = ''
    result.value = { items: [], page: normalizeLibraryQuery(query.value).page, size: 12, total: 0 }
    try {
      const data = await search(path, { publicRequest: true, signal: controller.signal })
      if (current) result.value = data
    } catch (cause) {
      if (current && cause.name !== 'AbortError') error.value = cause.message || '书库加载失败，请重试。'
    } finally {
      if (current) busy.value = false
    }
  }, { immediate: true })
  return { result: readonly(result), busy: readonly(busy), error: readonly(error), retry: () => retryVersion.value++ }
}
