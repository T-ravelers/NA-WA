<script setup lang="ts">
import { computed } from 'vue'
import { IconChevronLeft, IconChevronRight } from '@tabler/icons-vue'

interface Props {
  page: number
  totalPages: number
  loading?: boolean
}

const props = withDefaults(defineProps<Props>(), { loading: false })
const emit = defineEmits<{ change: [page: number] }>()

const visiblePages = computed(() => {
  const count = Math.min(5, props.totalPages)
  const maxStart = Math.max(0, props.totalPages - count)
  const start = Math.min(Math.max(0, props.page - Math.floor(count / 2)), maxStart)
  return Array.from({ length: count }, (_, index) => start + index)
})

function selectPage(page: number): void {
  if (props.loading || page === props.page || page < 0 || page >= props.totalPages) return
  emit('change', page)
}
</script>

<template>
  <nav
    v-if="totalPages > 1"
    class="mt-3 flex items-center justify-center gap-1"
    :aria-label="$t('explore.pagination.label')"
  >
    <button
      type="button"
      class="flex size-10 items-center justify-center rounded-pill text-ink-2 disabled:opacity-30"
      :aria-label="$t('explore.pagination.previousPage')"
      :disabled="page === 0 || loading"
      @click="selectPage(page - 1)"
    >
      <IconChevronLeft
        :size="18"
        aria-hidden="true"
      />
    </button>

    <button
      v-for="visiblePage in visiblePages"
      :key="visiblePage"
      type="button"
      class="flex size-10 items-center justify-center rounded-pill text-body-sm"
      :class="
        visiblePage === page
          ? 'bg-paper-fill font-bold text-on-paper'
          : 'text-ink-2 hover:bg-surface-2'
      "
      :aria-current="visiblePage === page ? 'page' : undefined"
      :aria-label="$t('explore.pagination.page', { page: visiblePage + 1 })"
      :disabled="loading"
      @click="selectPage(visiblePage)"
    >
      {{ visiblePage + 1 }}
    </button>

    <button
      type="button"
      class="flex size-10 items-center justify-center rounded-pill text-ink-2 disabled:opacity-30"
      :aria-label="$t('explore.pagination.nextPage')"
      :disabled="page >= totalPages - 1 || loading"
      @click="selectPage(page + 1)"
    >
      <IconChevronRight
        :size="18"
        aria-hidden="true"
      />
    </button>
  </nav>
</template>
