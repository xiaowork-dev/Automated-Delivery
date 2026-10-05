<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { session, clearSession } from './auth'
import { version } from '../package.json'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
const route = useRoute(), router = useRouter()
const admin = computed(() => route.path.startsWith('/admin'))
function logout() { clearSession(); router.push('/') }
</script>
<template>
  <el-config-provider :locale="zhCn"><div class="app-shell">
    <div class="demo-strip"><span class="status-dot"></span>演示环境 · 所有交易均为模拟支付，不涉及真实资金</div>
    <header class="site-header">
      <div class="header-inner"><RouterLink to="/" class="brand"><span class="brand-mark">↗</span><span>自助发货<small>AUTOMATED DELIVERY</small></span></RouterLink>
      <nav class="main-nav" aria-label="主导航"><RouterLink to="/" :class="{ 'is-active': route.path === '/' || route.path.startsWith('/product/') }">商品商店</RouterLink><RouterLink to="/orders">我的订单</RouterLink><RouterLink to="/redeem">兑换中心</RouterLink></nav>
      <div class="header-actions"><RouterLink v-if="session.user?.role === 'ADMIN'" to="/admin" class="admin-entry">管理后台 ↗</RouterLink><template v-if="session.user"><el-dropdown trigger="click"><button class="user-trigger"><span class="avatar">{{ session.user.username?.[0]?.toUpperCase() }}</span><span>{{ session.user.username }}</span><span>⌄</span></button><template #dropdown><el-dropdown-menu><el-dropdown-item v-if="session.user?.role === 'ADMIN'" @click="router.push('/admin')">管理后台</el-dropdown-item><el-dropdown-item @click="router.push('/profile')">个人中心</el-dropdown-item><el-dropdown-item divided @click="logout">退出登录</el-dropdown-item></el-dropdown-menu></template></el-dropdown></template><RouterLink v-else to="/login" class="login-link">登录 / 注册 <span>→</span></RouterLink></div>
      </div>
    </header>
    <main :class="admin ? 'admin-main' : 'page-main'"><RouterView /></main>
    <footer v-if="!admin" class="site-footer"><div><RouterLink to="/" class="footer-brand">↗ 自助发货</RouterLink><p>数字商品 · 即时交付 · 自助兑换</p></div><div class="footer-meta"><span>仅用于学习与演示，交易不涉及真实资金</span><span>Automated Delivery · v{{ version }}</span></div></footer>
  </div></el-config-provider>
</template>
