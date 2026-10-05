<template>
  <div>
    <div class="utility-strip">Free shipping over $99 · <b>Deal week:</b> up to 40% off top accessories</div>
    <header class="topbar">
      <div class="topbar-inner">
        <router-link class="logo" to="/">
          <span class="logo-mark">V</span>
          <span class="logo-text"><b>VoltEdge</b><small>Electronics Market</small></span>
        </router-link>
        <div class="header-search">
          <input v-model="q" placeholder="Search headphones, keyboards, monitors…" @keyup.enter="goSearch" />
          <button @click="goSearch">Search</button>
        </div>
        <div class="header-actions">
          <select class="input" v-model="userId" @change="persistUser" title="Log In As">
            <option :value="null">Log In As…</option>
            <option v-for="u in users" :key="u.id" :value="u.id">{{ u.name }}</option>
          </select>
          <router-link class="hlink" to="/">Shop</router-link>
          <router-link class="hlink" to="/checkout">Checkout</router-link>
          <router-link class="hlink" to="/orders">My Orders</router-link>
          <router-link class="hlink" to="/admin">Admin</router-link>
          <router-link class="hlink" to="/catalog">Catalog</router-link>
          <router-link class="cart-pill" to="/checkout">Cart<span class="count">{{ cartCount }}</span></router-link>
        </div>
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
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { usersApi } from './api/client.js'
import { store, cartCount } from './api/store.js'

const router = useRouter()
const users = ref([])
const userId = ref(store.user?.id ?? null)
const q = ref('')

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
