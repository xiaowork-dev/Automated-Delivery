import { createRouter, createWebHistory } from 'vue-router'
import { clearSession, initializeSession, session } from './auth'
import { ElMessage } from 'element-plus'

const router = createRouter({
  history: createWebHistory(),
  scrollBehavior: () => ({ top: 0 }),
  routes: [
    { path: '/', component: () => import('./views/Store.vue'), meta: { title: '商品商店' } },
    { path: '/product/:id', component: () => import('./views/Product.vue'), meta: { title: '商品详情' } },
    { path: '/login', component: () => import('./views/Auth.vue'), meta: { title: '登录' } },
    { path: '/register', component: () => import('./views/Auth.vue'), meta: { title: '注册' } },
    { path: '/orders', component: () => import('./views/Orders.vue'), meta: { auth: true, title: '我的订单' } },
    { path: '/orders/:orderNo', component: () => import('./views/Order.vue'), meta: { auth: true, title: '订单详情' } },
    { path: '/checkout/:orderNo', component: () => import('./views/Checkout.vue'), meta: { auth: true, title: '模拟收银台' } },
    { path: '/order/:orderNo/success', component: () => import('./views/Order.vue'), meta: { auth: true, title: '支付结果', success: true } },
    { path: '/redeem', component: () => import('./views/Redeem.vue'), meta: { auth: true, title: '兑换中心' } },
    { path: '/profile', component: () => import('./views/Profile.vue'), meta: { auth: true, title: '个人中心' } },
    { path: '/admin', component: () => import('./views/Admin.vue'), meta: { auth: true, admin: true }, children: [
      { path: '', component: () => import('./views/admin/Dashboard.vue'), meta: { title: '后台概览' } },
      { path: 'products', component: () => import('./views/admin/Products.vue'), meta: { title: '商品管理' } },
      { path: 'codes', component: () => import('./views/admin/Codes.vue'), meta: { title: '卡密管理' } },
      { path: 'orders', component: () => import('./views/admin/Orders.vue'), meta: { title: '订单管理' } },
      { path: 'users', component: () => import('./views/admin/Users.vue'), meta: { title: '用户管理' } },
      { path: 'logs', component: () => import('./views/admin/Logs.vue'), meta: { title: '操作日志' } },
    ] },
    { path: '/:pathMatch(.*)*', component: () => import('./views/NotFound.vue'), meta: { title: '页面不存在' } },
  ],
})
router.beforeEach(async to => {
  document.title = `${to.meta.title || '管理后台'} · 自助发货`
  try { await initializeSession() } catch (error) { if (to.meta.auth) { ElMessage.error(error.message); return { path: '/login', query: { redirect: to.fullPath } } } }
  if (to.meta.auth && !session.user) return { path: '/login', query: { redirect: to.fullPath } }
  if (to.meta.admin && session.user?.role !== 'ADMIN') { ElMessage.error('仅管理员可以进入后台。'); return '/' }
})
window.addEventListener('delivery:unauthorized', () => {
  const shouldRedirect = session.user && router.currentRoute.value.meta.auth
  clearSession()
  if (shouldRedirect) router.replace({ path: '/login', query: { redirect: router.currentRoute.value.fullPath } })
})
export default router
