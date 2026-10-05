<template>
  <div>
    <div class="page-head">
      <span class="source-badge es">Screen 3 · Elasticsearch ONLY</span>
      <h1>Order Search</h1>
      <p class="muted">Full-text admin search across customers and items — served by the search index.</p>
    </div>

    <div class="card">
      <div class="toolbar" style="border:0;box-shadow:none;padding:0;margin:0 0 12px">
        <input class="input" v-model="f.q" placeholder="Search customers, products, orders…" style="min-width:260px;flex:1" @keyup.enter="search(1)" />
        <button class="btn" @click="search(1)">Search</button>
      </div>
      <div class="toolbar" style="border:0;box-shadow:none;padding:0;margin:0">
        <span class="checkline" v-for="s in statuses" :key="s">
          <input type="checkbox" :id="'st-' + s" :value="s" v-model="f.statuses" />
          <label :for="'st-' + s"><span class="status" :class="s">{{ s }}</span></label>
        </span>
        <input class="input" type="date" v-model="f.date_from" title="From" />
        <input class="input" type="date" v-model="f.date_to" title="To" />
        <input class="input" type="number" v-model.number="f.min_total" placeholder="Min $" style="width:110px" />
        <input class="input" type="number" v-model.number="f.max_total" placeholder="Max $" style="width:110px" />
        <select class="input" v-model="f.sort">
          <option value="newest">Newest</option>
          <option value="oldest">Oldest</option>
          <option value="total_desc">Total ↓</option>
          <option value="total_asc">Total ↑</option>
        </select>
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
    <div v-else class="card" style="padding:0;overflow:hidden">
      <div class="table-wrap" style="border:0">
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
    </div>
    <div class="toolbar">
      <button class="btn secondary" :disabled="page <= 1" @click="search(page - 1)">← Prev</button>
      <span class="muted">Page {{ page }} · {{ total }} hits</span>
      <button class="btn secondary" :disabled="results.length < f.size" @click="search(page + 1)">Next →</button>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { searchApi } from '../api/client.js'

const statuses = ['PENDING', 'PROCESSING', 'SHIPPED']
const f = reactive({ q: '', statuses: [], date_from: '', date_to: '', min_total: null, max_total: null, sort: 'newest', size: 20 })
const results = ref([])
const aggs = ref({})
const total = ref(0)
const page = ref(1)
const error = ref('')
const loading = ref(true)

async function search(p = 1) {
  page.value = p
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
      sort: f.sort, page: p, size: f.size
    })
    results.value = out.results
    aggs.value = out.aggs || {}
    total.value = out.total
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
