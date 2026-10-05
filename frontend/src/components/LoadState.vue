<script setup>
defineProps({ loading: Boolean, error: String, empty: Boolean, emptyText: { type: String, default: '暂时没有记录' } })
defineEmits(['retry'])
</script>
<template>
  <div v-if="loading" class="loading-panel" role="status"><el-skeleton :rows="4" animated /><span class="sr-only">正在加载</span></div>
  <div v-else-if="error" class="state-panel" role="alert"><div class="state-icon">!</div><h3>暂时无法加载</h3><p>{{ error }}</p><el-button @click="$emit('retry')">重新加载</el-button></div>
  <el-empty v-else-if="empty" :description="emptyText" :image-size="96"><slot name="empty" /></el-empty>
  <slot v-else />
</template>
