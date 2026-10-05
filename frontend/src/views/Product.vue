<script setup>
import { watch, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api, money } from '../api'
import { session } from '../auth'
import { ElMessage } from 'element-plus'
import LoadState from '../components/LoadState.vue'
import ProductArt from '../components/ProductArt.vue'
const route = useRoute(), router = useRouter(), product = ref(null), loading = ref(true), error = ref(''), busy = ref(false)
async function load() { loading.value = true; error.value = ''; try { product.value = await api.get(`/products/${route.params.id}`) } catch (e) { error.value = e.message } finally { loading.value = false } }
async function buy() { if (!session.user) { router.push({ path: '/login', query: { redirect: route.fullPath } }); return }; busy.value = true; try { const order = await api.post('/orders', { productId: product.value.id }); router.push(`/checkout/${order.orderNo}`) } catch (e) { ElMessage.error(e.message); load() } finally { busy.value = false } }
watch(() => route.params.id, load, { immediate: true })
</script>
<template><div class="content-container"><RouterLink class="back-link" to="/">← 返回商品商店</RouterLink><LoadState :loading="loading" :error="error" @retry="load"><section v-if="product" class="product-detail"><ProductArt :product="product" :index="Number(product.id) % 4" /><div class="product-info"><div class="eyebrow">DIGITAL GOODS / INSTANT DELIVERY</div><h1>{{ product.name }}</h1><p class="product-subtitle">{{ product.subtitle || '数字商品，自助交付。' }}</p><div class="detail-price"><small>¥</small>{{ money(product.price) }}<span>模拟交易金额</span></div><div class="purchase-facts"><div><span>库存状态</span><strong>{{ product.stockLabel || (product.stock > 0 ? '有货' : '暂时缺货') }}</strong></div><div><span>交付方式</span><strong>自动发放兑换码</strong></div><div><span>购买数量</span><strong>1 件</strong></div></div><el-button type="primary" size="large" :loading="busy" :disabled="product.stock === 0 || product.status !== 'ON_SALE'" class="full-width purchase-button" @click="buy">{{ product.status !== 'ON_SALE' ? '商品已下架' : product.stock === 0 ? '暂时缺货' : '立即购买 →' }}</el-button><p class="purchase-hint">演示 / 测试支付，不涉及任何真实资金。</p></div></section><section v-if="product" class="surface product-description"><h2>商品详情</h2><p>{{ product.description || '该商品通过兑换码交付。模拟支付成功后，可在订单详情中查看兑换码，并在兑换中心完成自助兑换。' }}</p><div class="info-note"><strong>购买须知</strong><p>每个订单购买 1 件商品。兑换码与账号绑定，仅可成功兑换一次。库存以支付时实际可用兑换码为准。</p></div></section></LoadState></div></template>
