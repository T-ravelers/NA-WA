<script setup lang="ts">
import { computed } from 'vue'
import { IconChevronLeft, IconChevronRight } from '@tabler/icons-vue'

/**
 * 0부터 세는 페이지 번호 버튼. 현재 페이지 주변으로 최대 다섯 개를 보인다.
 *
 * 문구는 부르는 쪽이 넘긴다. "무엇의 페이지인지"는 화면마다 다르기 때문이다.
 */
interface Props {
  page: number
  totalPages: number
  loading?: boolean
  label: string
  previousLabel: string
  nextLabel: string
  /** 화면에 보이는 번호(1부터)를 받아 버튼의 읽기 이름을 만든다. */
  pageLabel: (page: number) => string
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
    :aria-label="label"
  >
    <button
      type="button"
      class="flex size-10 items-center justify-center rounded-pill text-ink-2 disabled:opacity-30"
      :aria-label="previousLabel"
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
      :aria-label="pageLabel(visiblePage + 1)"
      :disabled="loading"
      @click="selectPage(visiblePage)"
    >
      {{ visiblePage + 1 }}
    </button>

    <button
      type="button"
      class="flex size-10 items-center justify-center rounded-pill text-ink-2 disabled:opacity-30"
      :aria-label="nextLabel"
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
