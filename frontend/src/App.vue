<template>
  <div>
    <div class="utility-strip">Free shipping over $99 · <b>Deal week:</b> up to 40% off top accessories</div>
    <header class="topbar">
      <div class="topbar-inner">
        <router-link class="logo" to="/">
          <span class="logo-mark">V</span>
          <span class="logo-text"><b>VoltEdge</b><small>Electronics Market</small></span>
        </router-link>
        <div class="header-search" role="search">
          <svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="11" cy="11" r="7" /><path d="M16.5 16.5L21 21" /></svg>
          <input v-model="q" placeholder="Search headphones, keyboards, monitors…" @keyup.enter="goSearch" aria-label="Search products" />
          <button @click="goSearch">Search</button>
        </div>
        <nav class="header-actions" aria-label="Primary">
          <select class="user-select" v-model="userId" @change="persistUser" title="Log In As" aria-label="Log in as">
            <option :value="null">Log In As…</option>
            <option v-for="u in users" :key="u.id" :value="u.id">{{ u.name }}</option>
          </select>
          <router-link class="hlink" to="/">Shop</router-link>
          <router-link class="hlink" to="/checkout">Checkout</router-link>
          <router-link class="hlink" to="/orders">My Orders</router-link>
          <router-link class="hlink" to="/admin">Admin</router-link>
          <router-link class="hlink" to="/catalog">Catalog</router-link>
          <router-link class="cart-pill" :class="{ bump: cartBump }" to="/checkout" aria-label="Cart">
            <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M3 4h2l2.4 12.2a1.5 1.5 0 0 0 1.5 1.3h8.6a1.5 1.5 0 0 0 1.5-1.2L21 8H6" /><circle cx="10" cy="20.5" r="1.3" /><circle cx="18" cy="20.5" r="1.3" /></svg>
            <span>Cart</span><span class="count">{{ cartCount }}</span>
          </router-link>
        </nav>
      </div>
    </header>
    <main class="container">
      <router-view :key="$route.fullPath" />
    </main>
    <footer class="footer">
      <span><b>VoltEdge</b> · demo electronics marketplace (Level 2 POC)</span>
      <span>Storefront: MongoDB · Orders: PostgreSQL · Search: Elasticsearch</span>
    </footer>
  </div>
</template>

<script setup>
import { onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { usersApi } from './api/client.js'
import { store, cartCount } from './api/store.js'

const router = useRouter()
const users = ref([])
const userId = ref(store.user?.id ?? null)
const q = ref('')
const cartBump = ref(false)
let bumpTimer = null

// Immediate visual feedback: pulse the cart pill whenever the count changes.
watch(cartCount, () => {
  cartBump.value = true
  if (bumpTimer) clearTimeout(bumpTimer)
  bumpTimer = setTimeout(() => { cartBump.value = false }, 450)
})

function persistUser() {
  const u = users.value.find(x => x.id === Number(userId.value))
  if (u) store.setUser(u)
}

function goSearch() {
  router.push({ path: '/', query: q.value ? { q: q.value } : {} })
}

onMounted(async () => {
  users.value = await usersApi.list().catch(() => [])
  if (!store.user && users.value.length) {
    store.setUser(users.value[0])
    userId.value = users.value[0].id
  }
})
</script>
