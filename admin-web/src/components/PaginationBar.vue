<script setup lang="ts">
import { computed } from "vue";
import { ChevronFirst, ChevronLast, ChevronLeft, ChevronRight } from "@lucide/vue";

const props = defineProps<{ modelValue: number; total: number; pageSize: number }>();
const emit = defineEmits<{ "update:modelValue": [page: number] }>();

const pageCount = computed(() => Math.max(1, Math.ceil(props.total / props.pageSize)));
const currentPage = computed(() => Math.min(pageCount.value, Math.max(1, props.modelValue)));
const rangeStart = computed(() => props.total ? (currentPage.value - 1) * props.pageSize + 1 : 0);
const rangeEnd = computed(() => Math.min(currentPage.value * props.pageSize, props.total));

function go(page: number) {
  emit("update:modelValue", Math.min(pageCount.value, Math.max(1, page)));
}
</script>

<template>
  <footer class="pagination-bar" aria-label="列表分页">
    <p class="pagination-summary">第 {{ rangeStart }}–{{ rangeEnd }} 条，共 {{ total }} 条</p>
    <div class="pagination-controls">
      <button class="icon-button compact" type="button" aria-label="第一页" title="第一页" :disabled="currentPage === 1" @click="go(1)">
        <ChevronFirst :size="17" aria-hidden="true" />
      </button>
      <button class="icon-button compact" type="button" aria-label="上一页" title="上一页" :disabled="currentPage === 1" @click="go(currentPage - 1)">
        <ChevronLeft :size="17" aria-hidden="true" />
      </button>
      <span class="pagination-page" aria-live="polite">{{ currentPage }} / {{ pageCount }}</span>
      <button class="icon-button compact" type="button" aria-label="下一页" title="下一页" :disabled="currentPage === pageCount" @click="go(currentPage + 1)">
        <ChevronRight :size="17" aria-hidden="true" />
      </button>
      <button class="icon-button compact" type="button" aria-label="最后一页" title="最后一页" :disabled="currentPage === pageCount" @click="go(pageCount)">
        <ChevronLast :size="17" aria-hidden="true" />
      </button>
    </div>
  </footer>
</template>
