<template>
  <div>
    <section class="hero">
      <div class="hero-copy">
        <span class="hero-kicker">New season tech drop</span>
        <h1>Upgrade Your <span>Everyday Tech</span></h1>
        <p>Headphones, keyboards, monitors and desk gear — curated electronics with honest prices and fast checkout.</p>
        <div class="hero-cta">
          <a class="btn-shop" href="#catalog" @click.prevent="scrollToGrid">Shop Now</a>
          <router-link class="btn-ghost" to="/admin">Admin order search</router-link>
        </div>
        <div class="hero-stats">
          <div><b>{{ products.length }}+</b><span>products live</span></div>
          <div><b>{{ categories.length }}</b><span>categories</span></div>
          <div><b>$9.99+</b><span>starting price</span></div>
        </div>
      </div>
      <div class="hero-art" aria-hidden="true">
        <svg viewBox="0 0 500 340" preserveAspectRatio="xMidYMid slice">
          <defs>
            <linearGradient id="hh" x1="0" y1="0" x2="1" y2="1">
              <stop offset="0" stop-color="#ff5c1a" /><stop offset="1" stop-color="#7c2d12" />
            </linearGradient>
          </defs>
          <circle cx="420" cy="40" r="120" fill="#ffffff" opacity="0.07" />
          <circle cx="60" cy="300" r="90" fill="#000000" opacity="0.18" />
          <g fill="none" stroke="#fff" stroke-width="9">
            <path d="M150 220 v-40 a70 70 0 0 1 140 0 v40" />
            <rect x="132" y="212" width="36" height="62" rx="15" fill="#fff" stroke="none" />
            <rect x="272" y="212" width="36" height="62" rx="15" fill="#fff" stroke="none" />
          </g>
          <g fill="none" stroke="#ffd9b8" stroke-width="7">
            <rect x="330" y="200" width="110" height="40" rx="8" />
            <circle cx="352" cy="220" r="5" fill="#ffd9b8" />
            <circle cx="372" cy="220" r="5" fill="#ffd9b8" />
            <circle cx="392" cy="220" r="5" fill="#ffd9b8" />
          </g>
          <rect x="80" y="60" width="120" height="26" rx="13" fill="#f5a623" />
          <text x="140" y="78" text-anchor="middle" font-size="14" font-weight="800" fill="#0c1434" font-family="sans-serif">DEAL WEEK</text>
        </svg>
      </div>
    </section>

    <div class="section-title"><h2>Shop by category</h2><span>MongoDB catalog · live counts</span></div>
    <div class="cat-tiles">
      <button v-for="c in categories" :key="c.name" class="cat-tile" :class="{ on: category === c.name }" @click="pickCategory(c.name)">
        <img :src="c.art" :alt="c.name" loading="lazy" />
        <b>{{ c.name }}</b><span>{{ c.count }} items</span>
      </button>
    </div>

    <div v-if="dealPicks.length" class="deals">
      <span class="deal-tag">Under $25</span>
      <router-link v-for="d in dealPicks" :key="d.id" class="deal-item" :to="`/products/${d.id}`">
        <img :src="art(d)" :alt="d.title" loading="lazy" />
        <span><b>{{ d.title }}</b><span>${{ Number(d.price).toFixed(2) }}</span></span>
      </router-link>
    </div>

    <div id="catalog" class="section-title"><h2>All products</h2><span>{{ filtered.length }} result{{ filtered.length === 1 ? '' : 's' }}</span></div>
    <div class="toolbar">
      <select class="input" v-model="userId" @change="persistUser" title="Log In As">
        <option :value="null">Log In As…</option>
        <option v-for="u in users" :key="u.id" :value="u.id">{{ u.name }}</option>
      </select>
      <select class="input" v-model="category">
        <option value="">All categories</option>
        <option v-for="c in categories" :key="c.name" :value="c.name">{{ c.name }}</option>
      </select>
      <select class="input" v-model="sort">
        <option value="featured">Sort: Featured</option>
        <option value="price_asc">Price: Low → High</option>
        <option value="price_desc">Price: High → Low</option>
        <option value="name">Name A–Z</option>
      </select>
      <button class="btn" :class="{ dark: !onlyDeals, secondary: onlyDeals }" @click="onlyDeals = !onlyDeals">
        {{ onlyDeals ? '✓ Deals under $25' : 'Deals under $25' }}
      </button>
      <span v-if="onlyDeals" class="tag"><a href="#" @click.prevent="onlyDeals = false">clear deals</a></span>
    </div>

    <div v-if="loading" class="skel-grid"><div class="skel" v-for="i in 8" :key="i" /></div>
    <div v-else-if="error" class="alert error">{{ error }}</div>
    <div v-else-if="!filtered.length" class="card empty">
      <div class="big">🔌</div>
      <h3>No products found</h3>
      <p class="muted">Try a different search term or category.</p>
    </div>
    <div v-else class="grid">
      <div v-for="p in filtered" :key="p.id" class="pcard">
        <div class="art">
          <router-link :to="`/products/${p.id}`"><img :src="art(p)" :alt="p.title" loading="lazy" /></router-link>
          <span class="cat">{{ p.category }}</span>
        </div>
        <div class="body">
          <h3><router-link :to="`/products/${p.id}`">{{ p.title }}</router-link></h3>
          <div class="desc">{{ p.description }}</div>
          <div><span v-for="t in (p.tags || []).slice(0, 3)" :key="t" class="tag">{{ t }}</span></div>
          <div class="row">
            <span class="price">${{ Number(p.price).toFixed(2) }}</span>
            <button class="btn" @click="addToCart(p)">Add to Cart</button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { productsApi, usersApi } from '../api/client.js'
import { addToCart, store } from '../api/store.js'
import { categoryArt, productArt } from '../api/productArt.js'

const route = useRoute()
const products = ref([])
const users = ref([])
const q = ref(route.query.q || '')
const category = ref(route.query.category || '')
const sort = ref('featured')
const onlyDeals = ref(route.query.deals === '1')
const userId = ref(store.user?.id ?? null)
const loading = ref(true)
const error = ref('')

const art = (p) => productArt(p)

const categories = computed(() => {
  const map = {}
  for (const p of products.value) {
    if (!p.category) continue
    map[p.category] = map[p.category] || { name: p.category, count: 0, art: categoryArt(p.category) }
    map[p.category].count += 1
  }
  return Object.values(map).sort((a, b) => a.name.localeCompare(b.name))
})

const dealPicks = computed(() =>
  products.value.filter(p => Number(p.price) < 25).slice(0, 4))

const filtered = computed(() => {
  const needle = q.value.trim().toLowerCase()
  let list = products.value.filter(p =>
    (!category.value || p.category === category.value) &&
    (!onlyDeals.value || Number(p.price) < 25) &&
    (!needle || [p.title, p.description, p.sku, (p.tags || []).join(' ')].join(' ').toLowerCase().includes(needle)))
  if (sort.value === 'price_asc') list = [...list].sort((a, b) => a.price - b.price)
  if (sort.value === 'price_desc') list = [...list].sort((a, b) => b.price - a.price)
  if (sort.value === 'name') list = [...list].sort((a, b) => a.title.localeCompare(b.title))
  return list
})

function pickCategory(c) {
  category.value = category.value === c ? '' : c
}

function scrollToGrid() {
  document.getElementById('catalog')?.scrollIntoView({ behavior: 'smooth' })
}

function persistUser() {
  const u = users.value.find(x => x.id === Number(userId.value))
  if (u) store.setUser(u)
}

watch(() => route.query, (nq) => {
  q.value = nq.q || ''
  category.value = nq.category || ''
  onlyDeals.value = nq.deals === '1'
})

onMounted(async () => {
  try {
    const [prods, us] = await Promise.all([productsApi.list({}), usersApi.list()])
    products.value = prods
    users.value = us
    if (!store.user && us.length) {
      store.setUser(us[0])
      userId.value = us[0].id
    }
  } catch (e) {
    error.value = 'Could not load store. Start the API first.'
  } finally {
    loading.value = false
  }
})
</script>
