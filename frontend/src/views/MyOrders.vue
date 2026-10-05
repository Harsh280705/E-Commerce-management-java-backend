<template>
  <div>
    <div class="page-head">
      <span class="source-badge pg">PostgreSQL · canonical order reads</span>
      <h1>My Orders</h1>
      <p class="muted">Orders placed from this browser (stored locally, read live from PostgreSQL).</p>
    </div>
    <div v-if="loading" class="skel-grid"><div class="skel" v-for="i in 3" :key="i" /></div>
    <div v-else-if="!orders.length" class="card empty">
      <div class="big">📦</div>
      <h3>No orders yet</h3>
      <p class="muted">Place an order from the checkout to see it here.</p>
      <router-link class="btn-shop" to="/">Start shopping</router-link>
    </div>
    <div v-else class="grid">
      <div v-for="o in orders" :key="o.id" class="pcard">
        <div class="body">
          <div class="row" style="padding-top:0">
            <b>Order #{{ o.id }}</b>
            <span class="status" :class="o.status">{{ o.status }}</span>
          </div>
          <span class="muted">{{ (o.order_date || '').slice(0, 10) }} · {{ o.items.length }} item(s)</span>
          <div>
            <div v-for="it in o.items.slice(0, 3)" :key="it.id" class="cart-line" style="padding:6px 0">
              <img :src="artByTitle(it.title)" :alt="it.title" loading="lazy" style="width:46px;height:46px" />
              <div class="grow"><b style="font-size:13px">{{ it.title }}</b><span class="muted">× {{ it.quantity }}</span></div>
            </div>
          </div>
          <div class="row"><span class="price">${{ Number(o.total_amount).toFixed(2) }}</span>
            <router-link class="btn secondary" :to="`/admin/orders/${o.id}`">Details</router-link>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ordersApi } from '../api/client.js'
import { recentOrderIds, store } from '../api/store.js'
import { artByTitle } from '../api/productArt.js'

const orders = ref([])
const loading = ref(true)

onMounted(async () => {
  // Prefer the user's PostgreSQL order history; fall back to this browser's
  // recent order IDs (same cards either way).
  const seen = new Set()
  const out = []
  if (store.user) {
    try {
      for (const o of await ordersApi.list({ user_id: store.user.id, size: 50 })) {
        if (!seen.has(o.id)) {
          seen.add(o.id)
          out.push(o)
        }
      }
    } catch (e) { /* fall back to local IDs below */ }
  }
  for (const id of recentOrderIds()) {
    if (seen.has(id)) continue
    try {
      out.push(await ordersApi.get(id))
      seen.add(id)
    } catch (e) { /* order may predate this browser — skip */ }
  }
  orders.value = out
  loading.value = false
})
</script>
