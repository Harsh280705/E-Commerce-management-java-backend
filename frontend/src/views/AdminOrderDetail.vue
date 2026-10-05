<template>
  <div>
    <router-link to="/admin">← Back to search</router-link>
    <div class="page-head" style="margin-top:8px">
      <span class="source-badge pg">Screen 4 · PostgreSQL (canonical)</span>
      <h1>Order #{{ id }} <span class="status" v-if="order" :class="order.status">{{ order.status }}</span></h1>
    </div>
    <div v-if="error" class="alert error">{{ error }}</div>
    <div v-else-if="order" class="split">
      <div class="card">
        <div class="summary-row"><span>Customer</span><b>{{ order.customer_name }}</b></div>
        <div class="summary-row"><span>Email</span><b>{{ order.customer_email }}</b></div>
        <div class="summary-row"><span>Placed</span><b>{{ (order.order_date || '').slice(0, 19).replace('T', ' ') }}</b></div>
        <div class="summary-row">
          <span>Search sync</span>
          <b>{{ sync ? (sync.in_sync ? 'In sync ✓' : 'Pending…') : '…' }}</b>
        </div>
        <p class="muted" style="font-size:12.5px">PG {{ (order.updated_at || '').slice(0, 19).replace('T', ' ') }} vs ES {{ sync ? (sync.es_updated_at || 'missing') : '…' }}</p>
        <h3>Receipt</h3>
        <div v-for="it in order.items" :key="it.id" class="cart-line">
          <img :src="artByTitle(it.title)" :alt="it.title" loading="lazy" />
          <div class="grow">
            <b>{{ it.title }}</b>
            <span class="muted">snapshot · {{ it.quantity }} × ${{ Number(it.unit_price).toFixed(2) }}</span><br />
            <span class="muted" style="font-size:12px">{{ it.product_id }}</span>
          </div>
          <b>${{ Number(it.line_total).toFixed(2) }}</b>
        </div>
        <div class="summary-row grand"><span>Total</span><span>${{ Number(order.total_amount).toFixed(2) }}</span></div>
      </div>
      <div class="card">
        <h3 style="margin-top:0">Fulfillment</h3>
        <p class="muted" style="font-size:13.5px">Status changes save to PostgreSQL first, then sync to the search index.</p>
        <label class="lbl">New status</label>
        <select class="input" v-model="nextStatus" style="width:100%;margin-bottom:10px">
          <option v-for="s in statuses" :key="s" :value="s">{{ s }}</option>
        </select>
        <button class="btn" style="width:100%" @click="updateStatus">Update Status</button>
        <div v-if="msg" class="alert ok">{{ msg }}</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ordersApi } from '../api/client.js'
import { artByTitle } from '../api/productArt.js'

const route = useRoute()
const id = route.params.id
const order = ref(null)
const sync = ref(null)
const error = ref('')
const msg = ref('')
const nextStatus = ref('PROCESSING')
const statuses = ['PENDING', 'PROCESSING', 'SHIPPED']

async function load() {
  try {
    order.value = await ordersApi.get(id)
    nextStatus.value = order.value.status
    sync.value = await ordersApi.syncStatus(id).catch(() => null)
  } catch (e) {
    error.value = 'Order not found in PostgreSQL.'
  }
}

async function updateStatus() {
  msg.value = ''
  order.value = await ordersApi.updateStatus(id, nextStatus.value)
  msg.value = 'Status saved to PostgreSQL and sync queued → search index updates shortly.'
  sync.value = await ordersApi.syncStatus(id).catch(() => null)
}

onMounted(load)
</script>
