import { computed, ref, toValue, watch, type MaybeRefOrGetter } from "vue";

export const DEFAULT_PAGE_SIZE = 8;

export function usePagination<T>(items: MaybeRefOrGetter<readonly T[]>, pageSize = DEFAULT_PAGE_SIZE) {
  const page = ref(1);
  const source = computed(() => toValue(items));
  const total = computed(() => source.value.length);
  const pagedItems = computed(() => {
    const start = (page.value - 1) * pageSize;
    return source.value.slice(start, start + pageSize);
  });

  watch(source, () => { page.value = 1; });

  return { page, pageSize, total, pagedItems };
}
