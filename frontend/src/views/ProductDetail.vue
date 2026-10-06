<template>
  <div>
    <router-link class="back-link" to="/">← Back to store</router-link>
    <div v-if="error" class="alert error" style="margin-top:12px">{{ error }}</div>
    <div v-else-if="product" class="detail split" style="margin-top:12px">
      <div class="detail-art"><img :src="art(product)" :alt="product.title" /></div>
      <div class="card buy-panel">
        <span class="eyebrow">{{ product.category }}</span>
        <div class="detail-info">
          <h1>{{ product.title }}</h1>
          <div class="muted">{{ product.description }}</div>
          <div class="detail-price">${{ Number(product.price).toFixed(2) }}</div>
          <div class="tags"><span v-for="t in (product.tags || [])" :key="t" class="tag">{{ t }}</span></div>
        </div>
        <table class="spec-table">
          <tbody>
            <tr><td>SKU</td><td>{{ product.sku }}</td></tr>
            <tr v-for="(v, k) in (product.attributes || {})" :key="k"><td>{{ k }}</td><td>{{ v }}</td></tr>
            <tr v-if="(product.variants || []).length">
              <td>Variants</td>
              <td>{{ product.variants.map(v => v.name).join(', ') }}</td>
            </tr>
          </tbody>
        </table>
        <div class="buy-actions">
          <span class="qty" role="group" aria-label="Quantity">
            <button @click="qty = Math.max(1, qty - 1)" aria-label="Decrease quantity">−</button><b>{{ qty }}</b><button @click="qty += 1" aria-label="Increase quantity">+</button>
          </span>
          <button class="add-btn grow" :class="{ added: justAdded }" @click="handleAdd">
            <svg v-if="!justAdded" viewBox="0 0 24 24" aria-hidden="true"><path d="M3 4h2l2.4 12.2a1.5 1.5 0 0 0 1.5 1.3h8.6a1.5 1.5 0 0 0 1.5-1.2L21 8H6" /><circle cx="10" cy="20.5" r="1.3" /><circle cx="18" cy="20.5" r="1.3" /></svg>
            <svg v-else viewBox="0 0 24 24" aria-hidden="true"><path d="M4.5 12.5l5 5L19.5 7" /></svg>
            {{ justAdded ? 'Added' : 'Add to Cart' }}
          </button>
        </div>
        <router-link class="btn dark block" to="/checkout">Go to Checkout</router-link>
        <div v-if="added" class="added-note" role="status">
          <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4.5 12.5l5 5L19.5 7" /></svg>
          Added to cart.
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { productsApi } from '../api/client.js'
import { addToCart } from '../api/store.js'
import { productArt } from '../api/productArt.js'

const route = useRoute()
const product = ref(null)
const error = ref('')
const qty = ref(1)
const added = ref(false)
const justAdded = ref(false)
let addedTimer = null

const art = (p) => productArt(p)

function handleAdd() {
  if (!product.value) return
  addToCart(product.value, qty.value)
  added.value = true
  justAdded.value = true
  if (addedTimer) clearTimeout(addedTimer)
  addedTimer = setTimeout(() => { justAdded.value = false }, 1300)
}

onMounted(async () => {
  try {
    product.value = await productsApi.get(route.params.id)
  } catch (e) {
    error.value = 'Product not found.'
  }
})
</script>
