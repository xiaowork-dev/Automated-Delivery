<script setup>
import { computed, ref } from 'vue'
import { safeCover } from '../api'
const props = defineProps({ product: { type: Object, required: true }, index: { type: Number, default: 0 } })
const failed = ref(false)
const image = computed(() => safeCover(props.product.coverUrl))
</script>
<template>
  <div class="product-art" :class="`art-${index % 4}`">
    <img v-if="image && !failed" :src="image" :alt="product.name" loading="lazy" @error="failed = true" />
    <template v-else><div class="art-grid"></div><span class="art-label">DIGITAL / {{ String(product.id || 1).padStart(3, '0') }}</span><div class="art-symbol">{{ ['⌘', '&lt;/&gt;', '✦', '◈'][index % 4] }}</div><span class="art-caption">{{ product.name }}</span></template>
  </div>
</template>
