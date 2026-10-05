import { ref } from 'vue'
import { api } from '../api'

export function usePage(endpoint, filters = {}, size = 15) {
  const rows = ref([]), total = ref(0), page = ref(1), loading = ref(true), error = ref('')
  let latest = 0
  async function load() {
    const request = ++latest
    loading.value = true; error.value = ''
    try { const params = { page: page.value, size }; for (const [key, value] of Object.entries(filters)) if (value !== '' && value !== null && value !== undefined) params[key] = typeof value === 'string' ? value.trim() : value; const data = await api.get(endpoint, { params }); if (request === latest) { rows.value = data.records; total.value = data.total } }
    catch (e) { if (request === latest) error.value = e.message }
    finally { if (request === latest) loading.value = false }
  }
  function search() { page.value = 1; load() }
  return { rows, total, page, loading, error, load, search }
}
