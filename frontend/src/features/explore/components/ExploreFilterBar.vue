<script setup lang="ts">
import { computed } from 'vue'
import { IconChevronDown, IconX } from '@tabler/icons-vue'
import { useI18n } from 'vue-i18n'

export type ExploreSheetKind = 'date' | 'region' | 'category' | 'options' | 'sort'

interface ActiveFilter {
  key: string
  label: string
}

interface EventKindOption {
  key: string
  label: string
  selected: boolean
}

interface Props {
  activeSheet: ExploreSheetKind | null
  eventKindOptions: EventKindOption[]
  activeFilters: ActiveFilter[]
}

const { activeSheet, eventKindOptions, activeFilters } = defineProps<Props>()

const emit = defineEmits<{
  open: [kind: ExploreSheetKind]
  remove: [key: string]
  toggleKind: [key: string]
}>()

const { t } = useI18n()

const FILTER_LABELS: Record<ExploreSheetKind, string> = {
  date: 'explore.sheets.date',
  region: 'explore.sheets.region',
  category: 'explore.sheets.category',
  options: 'explore.sheets.options',
  sort: 'explore.sheets.sort',
}

const FILTERABLE_SHEET_KINDS = ['date', 'region', 'category', 'options'] as const

/**
 * 버튼과 그 버튼이 여는 시트에서 나온 칩을 잇는 표. `PlaceFilterBar`와 같은 방식이다.
 *
 * 버튼 이름으로 `startsWith`를 하면 안 된다 — 칩 key는 시트 이름이 아니라 필터 이름이라
 * `category` 버튼이 `sector:`·`activity:` 칩을, `options` 버튼이 `option:` 칩을 놓친다.
 * 그러면 그 두 버튼은 필터를 아무리 걸어도 켜지지 않는다.
 */
const FILTER_PREFIXES: Record<(typeof FILTERABLE_SHEET_KINDS)[number], string[]> = {
  date: ['date:'],
  region: ['region1:', 'region2:', 'region3:'],
  category: ['sector:', 'activity:'],
  options: ['option:'],
}

function filtersFor(kind: (typeof FILTERABLE_SHEET_KINDS)[number]): ActiveFilter[] {
  return activeFilters.filter((filter) =>
    FILTER_PREFIXES[kind].some((prefix) => filter.key.startsWith(prefix)),
  )
}

const hasAnyFilter = computed(
  () => activeFilters.length > 0 || eventKindOptions.some((option) => option.selected),
)
</script>

<template>
  <div class="flex min-w-0 flex-col gap-2">
    <div class="scrollbar-hidden -mx-screen flex gap-2 overflow-x-auto px-screen">
      <button
        v-for="kind in FILTERABLE_SHEET_KINDS"
        :key="kind"
        type="button"
        class="flex h-11 shrink-0 items-center gap-1 rounded-pill border px-4 text-body-sm transition-colors"
        :class="
          activeSheet === kind || filtersFor(kind).length > 0
            ? 'border-paper-fill bg-paper-fill text-on-paper'
            : 'border-hairline-2 bg-transparent text-ink-2'
        "
        @click="emit('open', kind)"
      >
        {{ t(FILTER_LABELS[kind]) }}
        <span
          v-if="filtersFor(kind).length > 0"
          class="text-caption"
        >
          · {{ filtersFor(kind).length }}
        </span>
        <IconChevronDown
          :size="16"
          :stroke-width="1.8"
          aria-hidden="true"
        />
      </button>
    </div>

    <div class="scrollbar-hidden -mx-screen flex gap-2 overflow-x-auto px-screen">
      <button
        v-for="option in eventKindOptions"
        :key="option.key"
        type="button"
        class="flex h-9 shrink-0 items-center rounded-sm px-3 text-caption"
        :class="option.selected ? 'bg-paper-fill text-on-paper' : 'bg-surface-1 text-ink-2'"
        :aria-pressed="option.selected"
        @click="emit('toggleKind', option.key)"
      >
        {{ option.label }}
      </button>

      <button
        v-for="filter in activeFilters"
        :key="filter.key"
        type="button"
        class="flex h-9 shrink-0 items-center gap-1 rounded-pill bg-surface-2 px-3 text-caption text-ink"
        @click="emit('remove', filter.key)"
      >
        {{ filter.label }}
        <IconX
          :size="14"
          :stroke-width="2"
          aria-hidden="true"
        />
      </button>

      <button
        v-if="hasAnyFilter"
        type="button"
        class="h-9 shrink-0 px-2 text-caption text-ink-3"
        @click="emit('remove', '*')"
      >
        {{ t('explore.filter.reset') }}
      </button>
    </div>
  </div>
</template>
