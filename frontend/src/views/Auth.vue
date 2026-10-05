<script setup>
import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '../api'
import { setSession } from '../auth'
import { ElMessage } from 'element-plus'
const route = useRoute(), router = useRouter()
const register = computed(() => route.path === '/register')
const form = reactive({ username: '', password: '', confirm: '' }), busy = ref(false), error = ref('')
async function submit() {
  error.value = ''
  if (!form.username.trim() || !form.password) { error.value = '请输入用户名和密码。'; return }
  if (register.value && !/^[A-Za-z0-9_-]{3,50}$/.test(form.username.trim())) { error.value = '用户名需为 3～50 个英文字母、数字、下划线或连字符。'; return }
  if (register.value && (form.password.length < 8 || new TextEncoder().encode(form.password).length > 72)) { error.value = '密码至少 8 个字符，UTF-8 编码长度不超过 72 字节。'; return }
  if (register.value && form.password !== form.confirm) { error.value = '两次输入的密码不一致。'; return }
  busy.value = true
  try { const result = await api.post(register.value ? '/auth/register' : '/auth/login', { username: form.username.trim(), password: form.password }); setSession(result); ElMessage.success(register.value ? '注册成功，已为你登录。' : '登录成功'); const target = typeof route.query.redirect === 'string' && route.query.redirect.startsWith('/') && !route.query.redirect.startsWith('//') ? route.query.redirect : '/'; router.replace(target) } catch (e) { error.value = e.message } finally { busy.value = false }
}
</script>
<template><div class="auth-layout"><div class="auth-story"><div class="eyebrow">YOUR DIGITAL DELIVERY</div><h1>登录后，<br>好物即刻<span>送达。</span></h1><p>管理你的订单，获取专属兑换码，<br>完成每一次自助交付。</p><div class="auth-decor" aria-hidden="true">↗</div><div class="auth-note"><span class="status-dot"></span> 本平台为演示环境，仅支持模拟支付</div></div><section class="auth-card"><div class="eyebrow">{{ register ? 'CREATE AN ACCOUNT' : 'WELCOME BACK' }}</div><h2>{{ register ? '创建账号' : '欢迎回来' }}</h2><p class="muted">{{ register ? '开启你的数字商品体验' : '登录，继续你的自助交付体验' }}</p><el-alert v-if="error" :title="error" type="error" :closable="false" show-icon class="form-alert" /><form @submit.prevent="submit"><label class="field-label" for="username">用户名</label><el-input id="username" v-model="form.username" autocomplete="username" :maxlength="50" placeholder="请输入用户名" size="large" /><label class="field-label" for="password">密码</label><el-input id="password" v-model="form.password" type="password" :autocomplete="register ? 'new-password' : 'current-password'" show-password :maxlength="72" :placeholder="register ? '至少 8 个字符' : '请输入密码'" size="large" /><template v-if="register"><label class="field-label" for="confirm">确认密码</label><el-input id="confirm" v-model="form.confirm" type="password" autocomplete="new-password" show-password :maxlength="72" placeholder="再次输入密码" size="large" /></template><el-button type="primary" native-type="submit" :loading="busy" size="large" class="full-width auth-submit">{{ register ? '注册并登录' : '登录' }} →</el-button></form><div class="auth-switch">{{ register ? '已有账号？' : '还没有账号？' }}<RouterLink :to="{ path: register ? '/login' : '/register', query: route.query }">{{ register ? '立即登录' : '创建账号' }}</RouterLink></div><p class="auth-footnote">仅使用用户名注册，无需提供手机号或邮箱。</p></section></div></template>
