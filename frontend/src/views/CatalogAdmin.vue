<template>
  <div>
    <div class="page-head">
      <span class="source-badge mongo">Screen 5 · MongoDB products</span>
      <h1>Catalog Manager</h1>
      <p class="muted">Catalog edits change MongoDB only — historical orders are never rewritten.
        Proof: catalog shows <b>Wireless Mouse Pro</b>, old orders still show <b>Wireless Mouse</b>.</p>
    </div>

    <div class="admin-toolbar">
      <label class="f-search">
        <svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="11" cy="11" r="7" /><path d="M16.5 16.5L21 21" /></svg>
        <input v-model="q" placeholder="Filter by title, SKU, category…" aria-label="Filter catalog" />
      </label>
      <span class="admin-count">{{ filtered.length }} product{{ filtered.length === 1 ? '' : 's' }}</span>
      <button class="btn" @click="startCreate">
        <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 5v14M5 12h14" /></svg>
        New product
      </button>
    </div>
    <div v-if="error" class="alert error">{{ error }}</div>
    <div v-if="msg" class="alert ok">{{ msg }}</div>

    <div v-if="editing" class="card" style="margin-bottom:16px">
      <h3 style="margin-top:0">{{ editing.id ? 'Edit product' : 'New product' }}</h3>
      <div class="form-grid">
        <div><label class="lbl">SKU</label><input class="input" v-model="form.sku" /></div>
        <div><label class="lbl">Title</label><input class="input" v-model="form.title" /></div>
        <div class="full"><label class="lbl">Description</label><input class="input" v-model="form.description" style="width:100%" /></div>
        <div><label class="lbl">Price ($)</label><input class="input" type="number" step="0.01" v-model.number="form.price" /></div>
        <div><label class="lbl">Category</label><input class="input" v-model="form.category" /></div>
        <div><label class="lbl">Tags (comma separated)</label><input class="input" v-model="form.tags" /></div>
        <div><label class="lbl">Brand</label><input class="input" v-model="form.brand" /></div>
        <div><label class="lbl">Active</label><select class="input" v-model="form.active"><option :value="true">active</option><option :value="false">inactive</option></select></div>
      </div>
      <div class="toolbar" style="border:0;box-shadow:none;padding:12px 0 0;margin-bottom:0">
        <button class="btn" @click="save">Save to MongoDB</button>
        <button class="btn secondary" @click="editing = null">Cancel</button>
      </div>
    </div>

    <div class="admin-list">
      <div v-for="p in paged" :key="p.id" class="admin-row">
        <img class="admin-thumb" :src="art(p)" :alt="p.title" loading="lazy" />
        <div class="admin-info">
          <b>{{ p.title }}</b>
          <span class="muted">{{ p.sku }} · {{ p.category }}</span>
        </div>
        <span class="stock" :class="{ off: !p.active }"><i></i>{{ p.active ? 'Active' : 'Inactive' }}</span>
        <span class="price">${{ Number(p.price).toFixed(2) }}</span>
        <button class="btn secondary" @click="startEdit(p)">Edit</button>
      </div>
    </div>

    <Pagination
      :page="page"
      :total-pages="totalPages"
      :range-text="rangeText"
      @change="page = $event"
    />
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { productsApi } from '../api/client.js'
import { productArt } from '../api/productArt.js'
import Pagination from '../components/Pagination.vue'

const products = ref([])
const editing = ref(null)
const error = ref('')
const msg = ref('')
const q = ref('')
const page = ref(1)
const PAGE_SIZE = 12
const form = reactive({ sku: '', title: '', description: '', price: 0, category: '', tags: '', brand: '', active: true })

const art = (p) => productArt(p)
const filtered = computed(() => {
  const needle = q.value.trim().toLowerCase()
  if (!needle) return products.value
  return products.value.filter(p => [p.title, p.sku, p.category].join(' ').toLowerCase().includes(needle))
})
const totalPages = computed(() => Math.max(1, Math.ceil(filtered.value.length / PAGE_SIZE)))
const paged = computed(() => {
  const p = Math.min(Math.max(1, page.value), totalPages.value)
  return filtered.value.slice((p - 1) * PAGE_SIZE, p * PAGE_SIZE)
})
const rangeText = computed(() => {
  const n = filtered.value.length
  if (!n) return 'No products'
  const p = Math.min(Math.max(1, page.value), totalPages.value)
  const from = (p - 1) * PAGE_SIZE + 1
  return `Showing ${from}–${Math.min(p * PAGE_SIZE, n)} of ${n} products`
})

// Preserve filter/search state across pages; reset to page 1 only when the
// filter itself changes. Clamp when the catalog shrinks/grows (create/edit).
watch(q, () => { page.value = 1 })
watch([filtered, totalPages], () => {
  if (page.value > totalPages.value) page.value = totalPages.value
  if (page.value < 1) page.value = 1
})

async function load() {
  products.value = await productsApi.list({ all: true }).catch(() => [])
  if (page.value > totalPages.value) page.value = totalPages.value
}

function startCreate() {
  editing.value = { id: null }
  Object.assign(form, { sku: '', title: '', description: '', price: 0, category: '', tags: '', brand: '', active: true })
}

function startEdit(p) {
  editing.value = { id: p.id }
  Object.assign(form, {
    sku: p.sku, title: p.title, description: p.description, price: p.price,
    category: p.category, tags: (p.tags || []).join(', '),
    brand: (p.attributes || {}).brand || '', active: p.active
  })
}

async function save() {
  error.value = ''
  msg.value = ''
  const payload = {
    sku: form.sku, title: form.title, description: form.description, price: Number(form.price),
    category: form.category, tags: form.tags.split(',').map(t => t.trim()).filter(Boolean),
    attributes: { brand: form.brand }, variants: editing.value?.id
      ? (products.value.find(p => p.id === editing.value.id)?.variants || [])
      : [{ name: 'Standard', sku: form.sku, price_adjustment: 0 }],
    active: form.active
  }
  try {
    const isCreate = !editing.value.id
    if (editing.value.id) await productsApi.update(editing.value.id, payload)
    else await productsApi.create(payload)
    msg.value = 'Saved to MongoDB. Historical orders untouched.'
    editing.value = null
    await load()
    // New products land at the end of the list — jump there so the user sees it.
    // Edits keep the current page (clamped inside load()).
    if (isCreate) page.value = totalPages.value
  } catch (e) {
    error.value = e.response?.data?.detail || 'Save failed.'
  }
}

onMounted(load)
</script>
