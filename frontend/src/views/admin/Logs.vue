<script setup>
import { onMounted } from 'vue'
import { time } from '../../api'
import { usePage } from '../../composables/usePage'
import LoadState from '../../components/LoadState.vue'
const { rows, total, page, loading, error, load } = usePage('/admin/logs')
const actions = { CREATE_PRODUCT: '新增商品', UPDATE_PRODUCT: '编辑商品', IMPORT_CODES: '导入卡密', IMPORT_CODE: '导入卡密', DISABLE_CODE: '作废卡密', CLOSE_ORDER: '关闭订单', UPDATE_USER_STATUS: '变更用户状态' }
onMounted(load)
</script>
<template><div class="page-heading"><div><div class="eyebrow">OPERATION AUDIT</div><h1>操作日志</h1><p>重要管理操作的审计记录，可追踪操作人与变更对象。</p></div><el-button @click="load">刷新 ↻</el-button></div><section class="surface admin-table-panel"><LoadState :loading="loading" :error="error" :empty="!rows.length" empty-text="暂时没有管理操作日志" @retry="load"><el-table :data="rows"><el-table-column prop="id" label="ID" width="80" /><el-table-column prop="operatorId" label="操作人 ID" width="110" /><el-table-column label="操作" min-width="150"><template #default="{ row }">{{ actions[row.action] || row.action }}</template></el-table-column><el-table-column label="目标" min-width="140"><template #default="{ row }"><span class="mono">{{ row.targetType }} #{{ row.targetId }}</span></template></el-table-column><el-table-column prop="detail" label="变更摘要" min-width="260" show-overflow-tooltip /><el-table-column label="操作时间" min-width="190"><template #default="{ row }">{{ time(row.createdAt) }}</template></el-table-column></el-table><el-pagination v-if="total > 15" v-model:current-page="page" :page-size="15" :total="total" layout="total, prev, pager, next" class="pagination" @current-change="load" /></LoadState></section></template>
