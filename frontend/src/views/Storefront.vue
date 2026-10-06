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

    <div class="section-title"><h2>Shop by category</h2><span>{{ categories.length }} departments · live counts</span></div>
    <div class="cat-tiles">
      <button v-for="c in categories" :key="c.key" class="cat-tile" :class="{ on: category === c.key }" @click="pickCategory(c.key)" :aria-pressed="category === c.key">
        <span class="cat-thumb"><img :src="c.art" :alt="c.name" loading="lazy" /></span>
        <span class="cat-meta"><b>{{ c.name }}</b><span>{{ c.count }} items</span></span>
        <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M9.5 6l6 6-6 6" /></svg>
      </button>
    </div>

    <div v-if="dealPicks.length" class="deals">
      <span class="deal-tag">Under $25</span>
      <router-link v-for="d in dealPicks" :key="d.id" class="deal-item" :to="`/products/${d.id}`">
        <img :src="art(d)" :alt="d.title" loading="lazy" />
        <span><b>{{ d.title }}</b><span>${{ Number(d.price).toFixed(2) }}</span></span>
      </router-link>
    </div>

    <div id="catalog" class="section-title catalog-title"><h2>All products</h2><div class="inf-toggle" title="Toggle infinite scrolling for the product catalog">
        <span class="inf-label">Infinite Scroll</span>
        <button class="switch" :class="{ on: infiniteEnabled }" role="switch" :aria-checked="infiniteEnabled" aria-label="Infinite Scroll" @click="toggleInfinite">
          <span class="knob" aria-hidden="true"></span>
        </button>
        <span class="inf-state" :class="{ on: infiniteEnabled }">{{ infiniteEnabled ? 'ON' : 'OFF' }}</span>
      </div><span class="result-count">{{ filtered.length }} result{{ filtered.length === 1 ? '' : 's' }}</span></div>
    <div class="filter-bar">
      <label class="f-search">
        <svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="11" cy="11" r="7" /><path d="M16.5 16.5L21 21" /></svg>
        <input v-model="q" placeholder="Search products…" aria-label="Search products" />
      </label>
      <label class="f-field"><span>Customer</span>
        <select v-model="userId" @change="persistUser" title="Log In As">
          <option :value="null">Log In As…</option>
          <option v-for="u in users" :key="u.id" :value="u.id">{{ u.name }}</option>
        </select>
      </label>
      <label class="f-field"><span>Category</span>
        <select v-model="category" aria-label="Category">
          <option value="">All categories</option>
          <option v-for="c in categories" :key="c.key" :value="c.key">{{ c.name }}</option>
        </select>
      </label>
      <label class="f-field"><span>Sort</span>
        <select v-model="sort" aria-label="Sort">
          <option value="featured">Featured</option>
          <option value="price_asc">Price: Low → High</option>
          <option value="price_desc">Price: High → Low</option>
          <option value="name">Name A–Z</option>
        </select>
      </label>
      <button class="deals-toggle" :class="{ on: onlyDeals }" @click="onlyDeals = !onlyDeals" :aria-pressed="onlyDeals">
        <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 2.5l2.6 5.6 6 .7-4.5 4.1 1.2 5.9-5.3-3-5.3 3 1.2-5.9L3.4 8.8l6-.7z" /></svg>
        Under $25
      </button>
    </div>

    <div v-if="loading" class="skel-grid"><div class="skel" v-for="i in 8" :key="i" /></div>
    <div v-else-if="error" class="alert error">{{ error }}</div>
    <div v-else-if="!filtered.length" class="card empty">
      <div class="big">🔌</div>
      <h3>No products found</h3>
      <p class="muted">Try a different search term or category.</p>
    </div>
    <div v-else class="grid">
      <div v-for="p in visible" :key="p.id" class="pcard">
        <div class="art">
          <router-link :to="`/products/${p.id}`" tabindex="-1"><img :src="art(p)" :alt="p.title" loading="lazy" /></router-link>
          <span class="cat">{{ displayCat(normCat(p.category)) }}</span>
          <span v-if="Number(p.price) < 25" class="deal-flag">Deal</span>
        </div>
        <div class="body">
          <h3><router-link :to="`/products/${p.id}`">{{ p.title }}</router-link></h3>
          <div class="desc">{{ p.description }}</div>
          <div class="tags"><span v-for="t in (p.tags || []).slice(0, 3)" :key="t" class="tag">{{ t }}</span></div>
          <div class="buy">
            <span class="price">${{ Number(p.price).toFixed(2) }}</span>
            <button class="add-btn" :class="{ added: addedMap[p.id] }" @click="handleAdd(p)">
              <svg v-if="!addedMap[p.id]" viewBox="0 0 24 24" aria-hidden="true"><path d="M3 4h2l2.4 12.2a1.5 1.5 0 0 0 1.5 1.3h8.6a1.5 1.5 0 0 0 1.5-1.2L21 8H6" /><circle cx="10" cy="20.5" r="1.3" /><circle cx="18" cy="20.5" r="1.3" /></svg>
              <svg v-else viewBox="0 0 24 24" aria-hidden="true"><path d="M4.5 12.5l5 5L19.5 7" /></svg>
              {{ addedMap[p.id] ? 'Added' : 'Add to Cart' }}
            </button>
          </div>
        </div>
      </div>
    </div>
    <div v-if="!loading && !error && filtered.length" class="infinite-foot">
      <div v-if="infiniteEnabled" ref="sentinel" class="infinite-sentinel" aria-hidden="true"></div>
      <p v-if="loadingMore" class="infinite-status" role="status"><span class="mini-spinner" aria-hidden="true"></span>Loading more products…</p>
      <p v-else-if="allLoaded && filtered.length > PAGE_SIZE" class="infinite-status done">All products loaded</p>
      <template v-else-if="!allLoaded">
        <p class="infinite-status muted">Showing {{ visible.length }} of {{ filtered.length }} products</p>
        <button v-if="!infiniteEnabled" class="btn-shop load-more" :disabled="loadingMore" @click="loadMore">Load More</button>
      </template>
      <p v-else class="infinite-status muted">Showing {{ visible.length }} of {{ filtered.length }} products</p>
    </div>
    <div v-if="toast" class="toast" role="status">
      <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4.5 12.5l5 5L19.5 7" /></svg>
      <span>{{ toast }}</span>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { productsApi, usersApi } from '../api/client.js'
import { addToCart, store } from '../api/store.js'
import { categoryArt, productArt } from '../api/productArt.js'

const route = useRoute()
const products = ref([])
const users = ref([])
const q = ref(route.query.q || '')
// Normalized category key (lowercase, trimmed) — see normCat() below.
const category = ref(normCat(route.query.category || ''))
const sort = ref('featured')
const onlyDeals = ref(route.query.deals === '1')
const userId = ref(store.user?.id ?? null)
const loading = ref(true)
const error = ref('')
const addedMap = ref({})
const toast = ref('')
let toastTimer = null

// Infinite scroll: reveal the filtered list in fixed batches. The product
// API returns the whole catalog (no page/size params), so batching happens
// client-side over `filtered` — the rendered grid only ever contains the
// current batch (`visible`), never the full list.
//
// The "Infinite Scroll" toggle gates ONLY the automatic loading: when ON an
// IntersectionObserver appends the next batch on scroll; when OFF the same
// batches advance manually via the "Load More" button. Filters/search/sort
// and the batch state (PAGE_SIZE / visibleCount) are shared by both modes.
const PAGE_SIZE = 12
const INFINITE_KEY = 'l3-infinite-scroll'
const visibleCount = ref(PAGE_SIZE)
const loadingMore = ref(false)
const sentinel = ref(null)
// Default OFF: restore only an explicit saved '1', otherwise stay OFF.
const infiniteEnabled = ref(false)
try {
  infiniteEnabled.value = localStorage.getItem(INFINITE_KEY) === '1'
} catch { /* private mode etc. — stay OFF */ }
let observer = null
let loadTimer = null

const art = (p) => productArt(p)

// Category keys are normalized (trim + lowercase) so data variants like
// "Peripherals", "peripherals" or " Peripherals " collapse into ONE tile.
// Display names are title-cased; filtering compares normalized keys.
function normCat(c) {
  return (c || '').trim().toLowerCase()
}
function displayCat(key) {
  return key ? key.charAt(0).toUpperCase() + key.slice(1) : key
}

const categories = computed(() => {
  const map = {}
  for (const p of products.value) {
    const key = normCat(p.category)
    if (!key) continue
    map[key] = map[key] || { key, name: displayCat(key), count: 0, art: categoryArt(key) }
    map[key].count += 1
  }
  return Object.values(map).sort((a, b) => a.name.localeCompare(b.name))
})

const dealPicks = computed(() =>
  products.value.filter(p => Number(p.price) < 25).slice(0, 4))

const filtered = computed(() => {
  const needle = q.value.trim().toLowerCase()
  let list = products.value.filter(p =>
    (!category.value || normCat(p.category) === category.value) &&
    (!onlyDeals.value || Number(p.price) < 25) &&
    (!needle || [p.title, p.description, p.sku, (p.tags || []).join(' ')].join(' ').toLowerCase().includes(needle)))
  if (sort.value === 'price_asc') list = [...list].sort((a, b) => a.price - b.price)
  if (sort.value === 'price_desc') list = [...list].sort((a, b) => b.price - a.price)
  if (sort.value === 'name') list = [...list].sort((a, b) => a.title.localeCompare(b.title))
  return list
})

// Currently rendered batch. Product IDs are the uniqueness key: already
// displayed products are never appended again, so batches are 1–12, 13–24…
const visible = computed(() => {
  const seen = new Set()
  const out = []
  for (const p of filtered.value) {
    if (seen.has(p.id)) continue
    seen.add(p.id)
    out.push(p)
    if (out.length >= visibleCount.value) break
  }
  return out
})
const allLoaded = computed(() => visible.value.length >= filtered.value.length)

function loadMore() {
  if (loading.value || loadingMore.value || allLoaded.value) return
  loadingMore.value = true
  loadTimer = setTimeout(() => {
    visibleCount.value = Math.min(visibleCount.value + PAGE_SIZE, filtered.value.length)
    loadingMore.value = false
    nextTick(() => {
      if (allLoaded.value) teardownObserver()
      else if (infiniteEnabled.value) observeSentinel()
    })
  }, 350)
}

// User-facing toggle: takes effect immediately on the current Shop page,
// keeps the current search/filter/category and the already-loaded batch,
// persists to localStorage, and never reloads the app.
function toggleInfinite() {
  infiniteEnabled.value = !infiniteEnabled.value
  try {
    localStorage.setItem(INFINITE_KEY, infiniteEnabled.value ? '1' : '0')
  } catch { /* ignore persistence failures */ }
  if (infiniteEnabled.value) {
    nextTick(() => observeSentinel())
  } else {
    // Stop automatic loading at once: drop the observer and cancel any
    // in-flight batch append so rapid toggling can't duplicate products.
    teardownObserver()
    loadingMore.value = false
    if (loadTimer) {
      clearTimeout(loadTimer)
      loadTimer = null
    }
  }
}

function teardownObserver() {
  if (observer) {
    observer.disconnect()
    observer = null
  }
}

function observeSentinel() {
  teardownObserver()
  if (loading.value || allLoaded.value || !infiniteEnabled.value) return
  const el = sentinel.value
  if (!el || typeof IntersectionObserver === 'undefined') return
  // Single-flight via loadMore()'s loadingMore/allLoaded guards, so rapid
  // scrolling can never fire overlapping appends for the same batch.
  observer = new IntersectionObserver((entries) => {
    if (entries.some((e) => e.isIntersecting)) loadMore()
  }, { root: null, rootMargin: '400px 0px', threshold: 0 })
  observer.observe(el)
}

// Any filter/search/sort change resets infinite scrolling: clear the batch
// and start again from the first PAGE_SIZE products (in both modes; the
// observer itself is only re-attached when infinite scrolling is ON).
function resetInfinite() {
  visibleCount.value = PAGE_SIZE
  loadingMore.value = false
  if (loadTimer) {
    clearTimeout(loadTimer)
    loadTimer = null
  }
  nextTick(() => observeSentinel())
}

function handleAdd(p) {
  addToCart(p) // existing cart logic untouched (qty accumulates per product id)
  addedMap.value = { ...addedMap.value, [p.id]: true }
  toast.value = `${p.title} added to cart`
  if (toastTimer) clearTimeout(toastTimer)
  toastTimer = setTimeout(() => { toast.value = '' }, 2200)
  setTimeout(() => {
    const next = { ...addedMap.value }
    delete next[p.id]
    addedMap.value = next
  }, 1300)
}

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
  category.value = normCat(nq.category || '')
  onlyDeals.value = nq.deals === '1'
})

watch([q, category, sort, onlyDeals], resetInfinite)
watch(sentinel, () => { nextTick(() => observeSentinel()) })

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
    nextTick(() => observeSentinel())
  }
})

onUnmounted(() => {
  teardownObserver()
  if (loadTimer) clearTimeout(loadTimer)
  if (toastTimer) clearTimeout(toastTimer)
})
</script>
