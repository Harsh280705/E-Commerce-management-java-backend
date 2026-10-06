<template>
  <div>
    <div class="page-head">
      <span class="source-badge es">Screen 3 · Elasticsearch ONLY</span>
      <h1>Order Search</h1>
      <p class="muted">Full-text admin search across customers and items — served by the search index.</p>
    </div>

    <div class="card search-card">
      <div class="search-row">
        <label class="f-search grow">
          <svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="11" cy="11" r="7" /><path d="M16.5 16.5L21 21" /></svg>
          <input v-model="f.q" placeholder="Search customers, products, orders…" aria-label="Search orders" @keyup.enter="search(1)" />
        </label>
        <button class="btn" @click="search(1)">Search</button>
      </div>
      <div class="filter-grid">
        <div class="f-group"><span>Status</span>
          <div class="status-pills">
            <label v-for="s in statuses" :key="s" class="pill-check" :class="{ on: f.statuses.includes(s) }">
              <input type="checkbox" :value="s" v-model="f.statuses" />
              <span class="status" :class="s">{{ s }}</span>
            </label>
          </div>
        </div>
        <label class="f-group"><span>From</span><input class="input" type="date" v-model="f.date_from" /></label>
        <label class="f-group"><span>To</span><input class="input" type="date" v-model="f.date_to" /></label>
        <label class="f-group"><span>Min $</span><input class="input" type="number" v-model.number="f.min_total" placeholder="0" /></label>
        <label class="f-group"><span>Max $</span><input class="input" type="number" v-model.number="f.max_total" placeholder="∞" /></label>
        <label class="f-group"><span>Sort</span>
          <select class="input" v-model="f.sort" @change="search(1)">
            <option value="newest">Newest</option>
            <option value="oldest">Oldest</option>
            <option value="total_desc">Total ↓</option>
            <option value="total_asc">Total ↑</option>
          </select>
        </label>
      </div>
    </div>

    <div class="kpis">
      <div class="kpi gold"><div class="muted">Filtered revenue</div><div class="v">${{ Number(aggs.total_revenue || 0).toFixed(2) }}</div></div>
      <div class="kpi" v-for="(c, s) in (aggs.by_status || {})" :key="s"><div class="muted"><span class="status" :class="s">{{ s }}</span></div><div class="v">{{ c }}</div></div>
      <div class="kpi green"><div class="muted">Total hits</div><div class="v">{{ total }}</div></div>
    </div>

    <div v-if="error" class="alert error">{{ error }}</div>
    <div v-else-if="!loading && !results.length" class="card empty">
      <div class="big">🔍</div><h3>No orders match</h3><p class="muted">Adjust the search or filters.</p>
    </div>
    <div v-else class="card results-card">
      <div class="table-wrap">
        <table class="results">
          <thead><tr><th>Order</th><th>Date</th><th>Customer</th><th>Items</th><th>Total</th><th>Status</th></tr></thead>
          <tbody>
            <tr v-for="r in results" :key="r.order_id">
              <td><router-link class="oid" :to="`/admin/orders/${r.order_id}`">#{{ r.order_id }}</router-link></td>
              <td>{{ (r.order_date || '').slice(0, 10) }}</td>
              <td>{{ r.customer?.name }}<br /><span class="muted" style="font-size:12px">{{ r.customer?.email }}</span></td>
              <td class="muted" style="font-size:13px">{{ (r.items || []).slice(0, 2).map(i => i.title).join(', ') }}{{ (r.items || []).length > 2 ? ' +' + ((r.items || []).length - 2) + ' more' : '' }}</td>
              <td><b>${{ Number(r.total_amount).toFixed(2) }}</b></td>
              <td><span class="status" :class="r.status">{{ r.status }}</span></td>
            </tr>
          </tbody>
        </table>
      </div>
      <div class="table-foot">
        <label class="f-field inline"><span>Rows</span>
          <select class="input page-size" v-model.number="f.size" @change="search(1)" title="Page size" aria-label="Rows per page">
            <option :value="10">10</option>
            <option :value="20">20</option>
            <option :value="50">50</option>
          </select>
        </label>
        <Pagination
          :page="page"
          :total-pages="totalPages"
          :range-text="rangeText"
          bare
          @change="search($event)"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { searchApi } from '../api/client.js'
import Pagination from '../components/Pagination.vue'

const statuses = ['PENDING', 'PROCESSING', 'SHIPPED']
const f = reactive({ q: '', statuses: [], date_from: '', date_to: '', min_total: null, max_total: null, sort: 'newest', size: 20 })
const results = ref([])
const aggs = ref({})
const total = ref(0)
const page = ref(1)
const error = ref('')
const loading = ref(true)

const totalPages = computed(() => Math.max(1, Math.ceil((Number(total.value) || 0) / (Number(f.size) || 20))))
const rangeText = computed(() => {
  const n = Number(total.value) || 0
  if (!n) return 'No orders'
  const size = Number(f.size) || 20
  const p = Math.min(Math.max(1, page.value), totalPages.value)
  const from = (p - 1) * size + 1
  return `Showing ${from}–${Math.min(p * size, n)} of ${n} orders`
})

async function search(p = 1) {
  // Clamp to the known page count so stale page numbers never query past the end.
  const knownMax = Math.max(1, Math.ceil((Number(total.value) || 0) / (Number(f.size) || 20)))
  page.value = Math.min(Math.max(1, p), knownMax)
  error.value = ''
  loading.value = true
  try {
    const out = await searchApi.orders({
      q: f.q || null,
      statuses: f.statuses.length ? f.statuses : null,
      date_from: f.date_from || null,
      date_to: f.date_to ? f.date_to + 'T23:59:59' : null,
      min_total: f.min_total ?? null,
      max_total: f.max_total ?? null,
      sort: f.sort, page: page.value, size: f.size
    })
    results.value = out.results
    aggs.value = out.aggs || {}
    total.value = out.total
    // Result count changed (new filters): if current page is now past the end,
    // fetch the last valid page once using the same filters/sort.
    const maxPage = Math.max(1, Math.ceil((Number(out.total) || 0) / (Number(f.size) || 20)))
    if (page.value > maxPage) {
      page.value = maxPage
      const fix = await searchApi.orders({
        q: f.q || null,
        statuses: f.statuses.length ? f.statuses : null,
        date_from: f.date_from || null,
        date_to: f.date_to ? f.date_to + 'T23:59:59' : null,
        min_total: f.min_total ?? null,
        max_total: f.max_total ?? null,
        sort: f.sort, page: page.value, size: f.size
      })
      results.value = fix.results
      aggs.value = fix.aggs || {}
      total.value = fix.total
    }
  } catch (e) {
    error.value = e.response?.status === 503
      ? 'Search index unavailable (Elasticsearch down). PostgreSQL orders are safe.'
      : 'Search failed. Is the API running?'
  } finally {
    loading.value = false
  }
}

onMounted(() => search(1))
</script>
