import { createRouter, createWebHistory } from 'vue-router'

import Storefront from '../views/Storefront.vue'
import ProductDetail from '../views/ProductDetail.vue'
import Checkout from '../views/Checkout.vue'
import MyOrders from '../views/MyOrders.vue'
import AdminSearch from '../views/AdminSearch.vue'
import AdminOrderDetail from '../views/AdminOrderDetail.vue'
import CatalogAdmin from '../views/CatalogAdmin.vue'

export default createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', component: Storefront },
    { path: '/products/:id', component: ProductDetail },
    { path: '/checkout', component: Checkout },
    { path: '/orders', component: MyOrders },
    { path: '/admin', component: AdminSearch },
    { path: '/admin/orders/:id', component: AdminOrderDetail },
    { path: '/catalog', component: CatalogAdmin }
  ]
})
