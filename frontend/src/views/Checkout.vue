<template>
  <div>
    <div class="page-head">
      <span class="source-badge">Screen 2 · MongoDB validation → PostgreSQL order</span>
      <h1>Cart & Checkout</h1>
      <p class="muted">Review your items, then place the order. Prices are re-validated against the live catalog.</p>
    </div>

    <div v-if="!store.cart.length && !msg" class="card empty">
      <div class="big">🛒</div>
      <h3>Your cart is empty</h3>
      <p class="muted">Browse the store and add some tech.</p>
      <router-link class="btn-shop" to="/">Back to store</router-link>
    </div>
    <div v-else class="split">
      <div class="card">
        <h3 style="margin-top:0">Your items ({{ cartCount }})</h3>
        <div v-for="l in store.cart" :key="l.id" class="cart-line">
          <img :src="artByTitle(l.title)" :alt="l.title" loading="lazy" />
          <div class="grow">
            <b>{{ l.title }}</b>
            <span class="muted">${{ Number(l.price).toFixed(2) }} each</span>
          </div>
          <span class="qty">
            <button @click="setQty(l.id, l.qty - 1)">−</button>{{ l.qty }}<button @click="setQty(l.id, l.qty + 1)">+</button>
          </span>
          <b>${{ (l.price * l.qty).toFixed(2) }}</b>
          <button class="btn secondary" @click="removeFromCart(l.id)">Remove</button>
        </div>
        <div v-if="msg" class="alert" :class="ok ? 'ok' : 'error'" style="margin-bottom:0">{{ msg }}</div>
      </div>
      <div class="card">
        <h3 style="margin-top:0">Order summary</h3>
        <div class="summary-row"><span>Subtotal</span><b>${{ cartTotal.toFixed(2) }}</b></div>
        <div class="summary-row grand"><span>Total</span><span>${{ cartTotal.toFixed(2) }}</span></div>
        <div style="margin:12px 0">
          <label class="lbl">Log In As</label>
          <select class="input" v-model="userId" style="width:100%">
            <option :value="null">Select customer…</option>
            <option v-for="u in users" :key="u.id" :value="u.id">{{ u.name }}</option>
          </select>
        </div>
        <button class="btn" style="width:100%" :disabled="placing || !userId || !store.cart.length" @click="placeOrder">
          {{ placing ? 'Placing order…' : `Place Order · $${cartTotal.toFixed(2)}` }}
        </button>
        <p v-if="ok" class="muted" style="font-size:13px">Synced to search automatically. <router-link to="/orders">View My Orders</router-link></p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ordersApi, usersApi } from '../api/client.js'
import { store, cartCount, cartTotal, setQty, removeFromCart, recordOrder } from '../api/store.js'
import { artByTitle } from '../api/productArt.js'

const users = ref([])
const userId = ref(store.user?.id ?? null)
const placing = ref(false)
const msg = ref('')
const ok = ref(false)

onMounted(async () => {
  users.value = await usersApi.list().catch(() => [])
  if (!store.user && users.value.length) {
    store.setUser(users.value[0])
    userId.value = users.value[0].id
  }
})

async function placeOrder() {
  placing.value = true
  msg.value = ''
  try {
    const order = await ordersApi.create({
      user_id: Number(userId.value),
      items: store.cart.map(l => ({ product_id: l.id, quantity: l.qty }))
    })
    ok.value = true
    msg.value = `Order #${order.id} placed ($${Number(order.total_amount).toFixed(2)}). Syncing to search…`
    recordOrder(order.id)
    store.clearCart()
  } catch (e) {
    ok.value = false
    msg.value = e.response?.data?.detail || 'Order failed.'
  } finally {
    placing.value = false
  }
}
</script>
