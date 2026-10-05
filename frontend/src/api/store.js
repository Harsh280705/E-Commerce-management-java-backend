import { computed, reactive } from 'vue'

// Cart lives in frontend/local state only — no cart database.
const saved = JSON.parse(localStorage.getItem('l2-cart') || '[]')
const savedUser = JSON.parse(localStorage.getItem('l2-user') || 'null')

export const store = reactive({
  user: savedUser,
  setUser(u) {
    this.user = u
    localStorage.setItem('l2-user', JSON.stringify(u))
  },
  cart: reactive(saved),
  clearCart() {
    this.cart.splice(0, this.cart.length)
    persist()
  }
})

function persist() {
  localStorage.setItem('l2-cart', JSON.stringify(store.cart))
}

export function addToCart(product, qty = 1) {
  const line = store.cart.find(l => l.id === product.id)
  if (line) line.qty += qty
  else store.cart.push({ id: product.id, title: product.title, price: product.price, category: product.category || '', qty })
  persist()
}

export function setQty(id, qty) {
  const line = store.cart.find(l => l.id === id)
  if (!line) return
  if (qty <= 0) removeFromCart(id)
  else {
    line.qty = qty
    persist()
  }
}

export function removeFromCart(id) {
  const i = store.cart.findIndex(l => l.id === id)
  if (i >= 0) store.cart.splice(i, 1)
  persist()
}

export const cartCount = computed(() => store.cart.reduce((s, l) => s + l.qty, 0))
export const cartTotal = computed(() => store.cart.reduce((s, l) => s + l.qty * l.price, 0))

// Order IDs placed from this browser (frontend-only history via GET /api/orders/:id).
export function recordOrder(id) {
  const ids = JSON.parse(localStorage.getItem('l2-orders') || '[]')
  if (!ids.includes(id)) {
    ids.unshift(id)
    localStorage.setItem('l2-orders', JSON.stringify(ids.slice(0, 50)))
  }
}

export function recentOrderIds() {
  return JSON.parse(localStorage.getItem('l2-orders') || '[]')
}
