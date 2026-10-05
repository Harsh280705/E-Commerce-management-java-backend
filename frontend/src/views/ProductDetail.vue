<template>
  <div>
    <router-link to="/">← Back to store</router-link>
    <div v-if="error" class="alert error" style="margin-top:12px">{{ error }}</div>
    <div v-else-if="product" class="split" style="margin-top:12px">
      <div class="detail-art"><img :src="art(product)" :alt="product.title" /></div>
      <div class="card">
        <span class="tag">{{ product.category }}</span>
        <div class="detail-info">
          <h1>{{ product.title }}</h1>
          <div class="muted">{{ product.description }}</div>
          <div class="detail-price">${{ Number(product.price).toFixed(2) }}</div>
          <div><span v-for="t in (product.tags || [])" :key="t" class="tag">{{ t }}</span></div>
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
        <div class="toolbar">
          <span class="qty">
            <button @click="qty = Math.max(1, qty - 1)">−</button>{{ qty }}<button @click="qty += 1">+</button>
          </span>
          <button class="btn" @click="addToCart(product, qty); added = true">Add to Cart</button>
          <router-link class="btn dark" to="/checkout">Go to Checkout</router-link>
        </div>
        <div v-if="added" class="alert ok">Added to cart.</div>
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

const art = (p) => productArt(p)

onMounted(async () => {
  try {
    product.value = await productsApi.get(route.params.id)
  } catch (e) {
    error.value = 'Product not found.'
  }
})
</script>
