<template>
  <nav class="pager" :class="{ bare }" aria-label="Pagination">
    <span v-if="rangeText" class="pager-range">{{ rangeText }}</span>
    <div v-if="totalPages > 1" class="pager-btns" role="group" aria-label="Page navigation">
      <button class="pg-btn" :disabled="page <= 1" @click="go(1)" title="First page" aria-label="First page">
        <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M11 6l-6 6 6 6M18 6l-6 6 6 6" /></svg>
        <span>First</span>
      </button>
      <button class="pg-btn" :disabled="page <= 1" @click="go(page - 1)" title="Previous page" aria-label="Previous page">
        <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M14.5 6l-6 6 6 6" /></svg>
        <span>Previous</span>
      </button>
      <template v-for="(item, i) in visible" :key="i">
        <span v-if="item === '…'" class="pg-ellipsis" aria-hidden="true">…</span>
        <button
          v-else
          class="pg-btn"
          :class="{ on: item === page }"
          :aria-current="item === page ? 'page' : undefined"
          @click="go(item)"
        >{{ item }}</button>
      </template>
      <button class="pg-btn" :disabled="page >= totalPages" @click="go(page + 1)" title="Next page" aria-label="Next page">
        <span>Next</span>
        <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M9.5 6l6 6-6 6" /></svg>
      </button>
      <button class="pg-btn" :disabled="page >= totalPages" @click="go(totalPages)" title="Last page" aria-label="Last page">
        <span>Last</span>
        <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M6 6l6 6-6 6M13 6l6 6-6 6" /></svg>
      </button>
    </div>
  </nav>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  page: { type: Number, required: true },
  totalPages: { type: Number, required: true },
  rangeText: { type: String, default: '' },
  bare: { type: Boolean, default: false }
})
const emit = defineEmits(['change'])

function go(p) {
  const next = Math.min(Math.max(1, Number(p) || 1), Math.max(1, props.totalPages))
  if (next !== props.page) emit('change', next)
}

// Windowed page list matching the spec:
// total 12, page 1 -> 1 2 3 4 5 … 12 ; page 6 -> 1 … 4 5 6 7 8 … 12
const visible = computed(() => {
  const total = Math.max(1, props.totalPages)
  const cur = Math.min(Math.max(1, props.page), total)
  if (total <= 7) return Array.from({ length: total }, (_, i) => i + 1)
  if (cur <= 4) return [1, 2, 3, 4, 5, '…', total]
  if (cur >= total - 3) return [1, '…', total - 4, total - 3, total - 2, total - 1, total]
  return [1, '…', cur - 2, cur - 1, cur, cur + 1, cur + 2, '…', total]
})
</script>
