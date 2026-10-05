import axios from 'axios'

// Same-origin: Vite dev proxy and any reverse proxy forward /api to FastAPI.
const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL || '',
  timeout: 15000
})

export const usersApi = {
  list: () => api.get('/api/users').then(r => r.data)
}

export const productsApi = {
  // Storefront: active products only. Catalog admin passes { all: true }.
  list: (params = {}) => api.get('/api/products', { params }).then(r => r.data),
  get: (id) => api.get(`/api/products/${id}`).then(r => r.data),
  create: (payload) => api.post('/api/products', payload).then(r => r.data),
  update: (id, payload) => api.put(`/api/products/${id}`, payload).then(r => r.data)
}

export const ordersApi = {
  create: (payload) => api.post('/api/orders', payload).then(r => r.data),
  list: (params = {}) => api.get('/api/orders', { params }).then(r => r.data),
  get: (id) => api.get(`/api/orders/${id}`).then(r => r.data),
  updateStatus: (id, status) => api.patch(`/api/orders/${id}/status`, { status }).then(r => r.data),
  syncStatus: (id) => api.get(`/api/orders/${id}/sync`).then(r => r.data)
}

export const searchApi = {
  orders: (payload) => api.post('/api/search/orders', payload).then(r => r.data)
}

export default api
