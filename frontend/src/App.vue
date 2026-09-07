<script setup>
import { computed, onBeforeUnmount, reactive, ref } from 'vue'

const credentials = reactive({
  username: '',
  password: ''
})

const filters = reactive({
  q: '',
  topic: '',
  area: ''
})

const page = ref(0)
const size = ref(20)
const total = ref(0)
const items = ref([])
const loading = ref(false)
const error = ref('')
const hasLoaded = ref(false)

let activeController = null
let requestSeq = 0

const totalPages = computed(() => {
  if (total.value <= 0) return 1
  return Math.max(1, Math.ceil(total.value / size.value))
})

const pageLabel = computed(() => `${page.value + 1} / ${totalPages.value}`)

const canQuery = computed(() => credentials.username.trim() !== '' && credentials.password !== '')

function parseNonNegativeInteger(value, fieldName) {
  const parsed = Number(value)
  if (!Number.isInteger(parsed) || parsed < 0) {
    throw new Error(`接口字段 ${fieldName} 格式不符合预期。`)
  }
  return parsed
}

function parsePositiveInteger(value, fieldName) {
  const parsed = Number(value)
  if (!Number.isInteger(parsed) || parsed <= 0) {
    throw new Error(`接口字段 ${fieldName} 格式不符合预期。`)
  }
  return parsed
}

function authHeader() {
  const bytes = new TextEncoder().encode(`${credentials.username}:${credentials.password}`)
  let binary = ''
  for (const byte of bytes) {
    binary += String.fromCharCode(byte)
  }
  return `Basic ${btoa(binary)}`
}

function requestUrl() {
  const params = new URLSearchParams({
    page: String(page.value),
    size: String(size.value),
    q: filters.q.trim(),
    topic: filters.topic.trim(),
    area: filters.area.trim()
  })
  return `/api/v1/research/exercises?${params.toString()}`
}

async function fetchCatalog() {
  if (!canQuery.value) {
    error.value = '请输入后端 Basic Auth 用户名和密码。'
    return
  }

  if (activeController) {
    activeController.abort()
  }

  const controller = new AbortController()
  activeController = controller
  const seq = ++requestSeq
  loading.value = true
  error.value = ''

  try {
    const response = await fetch(requestUrl(), {
      signal: controller.signal,
      headers: {
        Authorization: authHeader()
      }
    })

    if (seq !== requestSeq) return

    if (!response.ok) {
      if (response.status === 401) {
        throw new Error('认证失败，请检查用户名和密码。')
      }
      throw new Error(`请求失败：HTTP ${response.status}`)
    }

    const payload = await response.json()
    if (seq !== requestSeq) return

    if (payload.code !== 'OK' || !payload.data) {
      throw new Error(payload.message || '接口返回格式不符合预期。')
    }
    if (!Array.isArray(payload.data.items)) {
      throw new Error('接口字段 items 格式不符合预期。')
    }

    const nextTotal = parseNonNegativeInteger(payload.data.total, 'total')
    const nextPage = parseNonNegativeInteger(payload.data.page, 'page')
    const nextSize = parsePositiveInteger(payload.data.size, 'size')

    items.value = payload.data.items
    total.value = nextTotal
    page.value = nextPage
    size.value = nextSize
    hasLoaded.value = true
  } catch (err) {
    if (err?.name === 'AbortError') return
    if (seq !== requestSeq) return
    items.value = []
    total.value = 0
    error.value = err instanceof Error ? err.message : '请求失败。'
  } finally {
    if (seq === requestSeq) {
      loading.value = false
      if (activeController === controller) {
        activeController = null
      }
    }
  }
}

function applyFilters() {
  page.value = 0
  fetchCatalog()
}

function resetFilters() {
  filters.q = ''
  filters.topic = ''
  filters.area = ''
  page.value = 0
  fetchCatalog()
}

function previousPage() {
  if (page.value <= 0) return
  page.value -= 1
  fetchCatalog()
}

function nextPage() {
  if (page.value + 1 >= totalPages.value) return
  page.value += 1
  fetchCatalog()
}

function onSizeChange() {
  page.value = 0
  fetchCatalog()
}

function prerequisiteCount(item) {
  return Array.isArray(item.prerequisites) ? item.prerequisites.length : 0
}

onBeforeUnmount(() => {
  if (activeController) {
    activeController.abort()
  }
})
</script>

<template>
  <main class="page-shell">
    <header class="page-header">
      <div>
        <p class="eyebrow">研究数据目录</p>
        <h1>Junyi 研究练习目录</h1>
        <p class="subtitle">
          浏览 Junyi 练习的分类、状态与前置关系，辅助本地教学系统接入真实研究数据。
        </p>
      </div>
      <div class="summary-box" aria-live="polite">
        <span class="summary-number">{{ total }}</span>
        <span class="summary-label">匹配记录</span>
      </div>
    </header>

    <section class="toolbar" aria-label="连接和筛选">
      <form class="auth-form" @submit.prevent="applyFilters">
        <label>
          <span>用户名</span>
          <input v-model="credentials.username" autocomplete="username" placeholder="Basic Auth username" />
        </label>
        <label>
          <span>密码</span>
          <input
            v-model="credentials.password"
            autocomplete="current-password"
            placeholder="Basic Auth password"
            type="password"
          />
        </label>
        <button class="primary" type="submit" :disabled="loading || !canQuery">
          {{ loading ? '加载中' : '连接并查询' }}
        </button>
      </form>

      <form class="filters" @submit.prevent="applyFilters">
        <label>
          <span>搜索</span>
          <input v-model="filters.q" placeholder="练习编号或名称" />
        </label>
        <label>
          <span>主题精确筛选</span>
          <input v-model="filters.topic" placeholder="例如 fractions" />
        </label>
        <label>
          <span>领域精确筛选</span>
          <input v-model="filters.area" placeholder="例如 algebra" />
        </label>
        <label>
          <span>每页</span>
          <select v-model.number="size" @change="onSizeChange">
            <option :value="10">10</option>
            <option :value="20">20</option>
            <option :value="50">50</option>
          </select>
        </label>
        <div class="filter-actions">
          <button type="submit" :disabled="loading || !canQuery">筛选</button>
          <button type="button" :disabled="loading || !canQuery" @click="resetFilters">清空</button>
        </div>
      </form>
    </section>

    <section class="status-region" aria-live="polite">
      <p v-if="loading" class="status">正在读取研究练习目录...</p>
      <p v-else-if="error" class="status error">{{ error }}</p>
      <p v-else-if="hasLoaded && items.length === 0" class="status">没有匹配的研究练习记录。</p>
      <p v-else-if="!hasLoaded" class="status muted">输入后端 Basic Auth 凭据后查询本地接口。</p>
    </section>

    <section v-if="items.length > 0" class="catalog" aria-label="Junyi 研究练习列表">
      <article v-for="item in items" :key="`${item.recordNumber}-${item.externalId}`" class="exercise-row">
        <div class="row-main">
          <div class="title-line">
            <h2>{{ item.displayName || item.externalId }}</h2>
            <span v-if="item.duplicateExternalId" class="badge warning">重复 ID</span>
            <span class="badge" :class="item.live ? 'live' : 'offline'">
              {{ item.live ? '可用' : '停用' }}
            </span>
          </div>
          <p class="external-id">{{ item.externalId }}</p>
        </div>
        <dl class="facts">
          <div>
            <dt>领域</dt>
            <dd>{{ item.area }}</dd>
          </div>
          <div>
            <dt>主题</dt>
            <dd>{{ item.topic }}</dd>
          </div>
          <div>
            <dt>前置数量</dt>
            <dd>{{ prerequisiteCount(item) }}</dd>
          </div>
          <div>
            <dt>记录号</dt>
            <dd>{{ item.recordNumber }}</dd>
          </div>
        </dl>
      </article>
    </section>

    <nav class="pagination" aria-label="分页">
      <button type="button" :disabled="loading || !hasLoaded || page <= 0" @click="previousPage">上一页</button>
      <span>{{ pageLabel }}</span>
      <button type="button" :disabled="loading || !hasLoaded || page + 1 >= totalPages" @click="nextPage">
        下一页
      </button>
    </nav>
  </main>
</template>

<style scoped>
:global(*) {
  box-sizing: border-box;
}

:global(body) {
  margin: 0;
  background: #f6f7f9;
  color: #20242a;
  font-family: Inter, "Segoe UI", system-ui, -apple-system, BlinkMacSystemFont, sans-serif;
}

button,
input,
select {
  font: inherit;
}

button {
  min-height: 40px;
  border: 1px solid #c9ced6;
  border-radius: 6px;
  background: #ffffff;
  color: #20242a;
  cursor: pointer;
}

button:hover:not(:disabled) {
  border-color: #7b8798;
}

button:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

button.primary {
  border-color: #2158a8;
  background: #2158a8;
  color: #ffffff;
}

.page-shell {
  width: min(1180px, 100%);
  margin: 0 auto;
  padding: 32px 20px 40px;
}

.page-header {
  display: flex;
  gap: 24px;
  align-items: flex-end;
  justify-content: space-between;
  margin-bottom: 24px;
}

.eyebrow {
  margin: 0 0 6px;
  color: #637083;
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0;
  text-transform: uppercase;
}

h1 {
  margin: 0;
  font-size: 32px;
  line-height: 1.2;
}

.subtitle {
  max-width: 720px;
  margin: 10px 0 0;
  color: #576170;
  line-height: 1.6;
}

.summary-box {
  min-width: 128px;
  padding: 12px 16px;
  border: 1px solid #d9dde4;
  border-radius: 8px;
  background: #ffffff;
  text-align: right;
}

.summary-number {
  display: block;
  font-size: 28px;
  font-weight: 750;
}

.summary-label {
  color: #637083;
  font-size: 13px;
}

.toolbar {
  display: grid;
  gap: 14px;
  margin-bottom: 14px;
}

.auth-form,
.filters {
  display: grid;
  gap: 12px;
  align-items: end;
  padding: 16px;
  border: 1px solid #d9dde4;
  border-radius: 8px;
  background: #ffffff;
}

.auth-form {
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr) auto;
}

.filters {
  grid-template-columns: minmax(180px, 1.2fr) minmax(160px, 1fr) minmax(160px, 1fr) 96px auto;
}

label {
  display: grid;
  gap: 6px;
  min-width: 0;
}

label span {
  color: #4e5968;
  font-size: 13px;
  font-weight: 650;
}

input,
select {
  width: 100%;
  min-height: 40px;
  border: 1px solid #cfd5de;
  border-radius: 6px;
  background: #ffffff;
  color: #20242a;
  padding: 8px 10px;
}

input:focus,
select:focus,
button:focus-visible {
  outline: 3px solid #b8d4ff;
  outline-offset: 1px;
}

.filter-actions {
  display: flex;
  gap: 8px;
}

.filter-actions button {
  padding: 0 14px;
}

.status-region {
  min-height: 42px;
}

.status {
  margin: 0;
  padding: 10px 0;
  color: #4e5968;
}

.status.error {
  color: #b42318;
}

.status.muted {
  color: #7b8798;
}

.catalog {
  display: grid;
  gap: 10px;
}

.exercise-row {
  display: grid;
  grid-template-columns: minmax(220px, 1fr) minmax(360px, 1.4fr);
  gap: 18px;
  align-items: start;
  padding: 16px;
  border: 1px solid #d9dde4;
  border-radius: 8px;
  background: #ffffff;
}

.title-line {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

h2 {
  margin: 0;
  overflow-wrap: anywhere;
  font-size: 18px;
  line-height: 1.35;
}

.external-id {
  margin: 6px 0 0;
  color: #637083;
  font-family: "SFMono-Regular", Consolas, "Liberation Mono", monospace;
  font-size: 13px;
  overflow-wrap: anywhere;
}

.badge {
  display: inline-flex;
  align-items: center;
  min-height: 24px;
  border-radius: 999px;
  padding: 2px 8px;
  font-size: 12px;
  font-weight: 700;
}

.badge.live {
  background: #e8f5ee;
  color: #146c43;
}

.badge.offline {
  background: #eef0f3;
  color: #576170;
}

.badge.warning {
  background: #fff3cd;
  color: #8a5a00;
}

.facts {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin: 0;
}

.facts div {
  min-width: 0;
}

dt {
  color: #637083;
  font-size: 12px;
  font-weight: 700;
}

dd {
  margin: 4px 0 0;
  overflow-wrap: anywhere;
  font-size: 14px;
}

.pagination {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: flex-end;
  margin-top: 18px;
}

.pagination span {
  min-width: 72px;
  text-align: center;
  color: #4e5968;
}

@media (max-width: 820px) {
  .page-shell {
    padding: 24px 14px 32px;
  }

  .page-header {
    align-items: stretch;
    flex-direction: column;
  }

  .summary-box {
    text-align: left;
  }

  .auth-form,
  .filters,
  .exercise-row,
  .facts {
    grid-template-columns: 1fr;
  }

  .filter-actions,
  .pagination {
    justify-content: stretch;
  }

  .filter-actions button,
  .pagination button {
    flex: 1;
  }
}
</style>
