<script setup>
import { useRoute } from 'vue-router'
const route = useRoute()
const links = [{ path: '/admin', label: '运营概览', icon: '◫', exact: true }, { path: '/admin/products', label: '商品管理', icon: '◈' }, { path: '/admin/codes', label: '卡密库存', icon: '⌘' }, { path: '/admin/orders', label: '订单管理', icon: '▤' }, { path: '/admin/users', label: '用户管理', icon: '◎' }, { path: '/admin/logs', label: '操作日志', icon: '≡' }]
</script>
<template><div class="admin-layout"><aside class="admin-sidebar"><div class="admin-sidebar-label">WORKSPACE <span>ADMIN</span></div><nav aria-label="后台导航"><RouterLink v-for="link in links" :key="link.path" :to="link.path" :class="{ selected: link.exact ? route.path === link.path : route.path.startsWith(link.path) }"><span class="nav-symbol">{{ link.icon }}</span>{{ link.label }}<span class="nav-chevron">›</span></RouterLink></nav><div class="sidebar-note"><span class="status-dot"></span> 模拟交易管理后台<p>高风险操作会记录审计日志。</p></div></aside><div class="admin-content"><RouterView /></div></div></template>
