<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api, money, time, orderStates } from '../api'
import { ElMessage, ElMessageBox } from 'element-plus'
import LoadState from '../components/LoadState.vue'
const route = useRoute(), router = useRouter(), order = ref(null), loading = ref(true), error = ref(''), busy = ref(false), now = ref(Date.now())
let timer
const seconds = computed(() => Math.max(0, Math.floor((new Date(order.value?.expireAt).getTime() - now.value) / 1000)))
const remaining = computed(() => `${String(Math.floor(seconds.value / 60)).padStart(2, '0')}:${String(seconds.value % 60).padStart(2, '0')}`)
async function load() { loading.value = true; error.value = ''; try { order.value = await api.get(`/orders/${route.params.orderNo}`) } catch (e) { error.value = e.message } finally { loading.value = false } }
async function pay() { busy.value = true; try { const result = await api.post(`/orders/${order.value.orderNo}/mock-pay`); order.value = result; router.replace(`/order/${result.orderNo}/success`) } catch (e) { ElMessage.error(e.message); await load() } finally { busy.value = false } }
async function cancel() { try { await ElMessageBox.confirm('确定取消这笔待支付订单？', '取消订单', { confirmButtonText: '确认取消', cancelButtonText: '继续支付', type: 'warning' }); busy.value = true; await api.post(`/orders/${order.value.orderNo}/cancel`); router.replace(`/orders/${order.value.orderNo}`) } catch (e) { if (e instanceof Error) ElMessage.error(e.message) } finally { busy.value = false } }
watch(() => route.params.orderNo, load, { immediate: true })
onMounted(() => { timer = setInterval(() => { now.value = Date.now() }, 1000) }); onUnmounted(() => clearInterval(timer))
</script>
<template><div class="narrow-container"><RouterLink to="/orders" class="back-link">← 我的订单</RouterLink><div class="page-heading"><div><div class="eyebrow">SIMULATED CHECKOUT</div><h1>模拟收银台</h1><p>确认订单后，体验自动发货。</p></div></div><LoadState :loading="loading" :error="error" @retry="load"><section v-if="order" class="checkout-card surface"><el-alert title="演示 / 测试支付：不扣款，不涉及真实资金" type="warning" :closable="false" show-icon /><div class="checkout-total"><span>模拟支付金额</span><strong><small>¥</small>{{ money(order.amount) }}</strong></div><dl class="detail-list"><div><dt>商品</dt><dd>{{ order.productName }}</dd></div><div><dt>订单号</dt><dd class="mono">{{ order.orderNo }}</dd></div><div><dt>购买数量</dt><dd>1 件</dd></div><div><dt>创建时间</dt><dd>{{ time(order.createdAt) }}</dd></div></dl><template v-if="order.status === 'WAIT_PAY' && seconds > 0"><div class="countdown"><span>请在倒计时结束前完成模拟支付</span><strong>{{ remaining }}</strong></div><el-button type="primary" size="large" class="full-width" :loading="busy" @click="pay">模拟支付成功 →</el-button><el-button text class="full-width cancel-button" :disabled="busy" @click="cancel">取消订单</el-button></template><template v-else><el-alert :title="order.status === 'WAIT_PAY' ? '支付时间已结束，请刷新订单确认状态或重新下单。' : `订单当前状态：${orderStates[order.status] || order.status}`" type="info" :closable="false" class="form-alert" /><RouterLink :to="`/orders/${order.orderNo}`" class="solid-link full-width centered">查看订单详情 →</RouterLink></template><p class="checkout-footnote">支付成功后兑换码会自动分配至此订单。<br>遇到网络超时，可在订单详情确认结果。</p></section></LoadState></div></template>
