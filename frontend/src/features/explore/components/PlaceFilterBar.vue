<script setup lang="ts">
import { computed } from 'vue'
import { IconChevronDown, IconX } from '@tabler/icons-vue'
import { useI18n } from 'vue-i18n'

import type { PlaceKind } from '../model/placeExplore'

export type PlaceSheetKind = 'region' | 'category' | 'options' | 'sort'

interface ActiveFilter {
  key: string
  label: string
}

interface PlaceKindOption {
  key: PlaceKind
  label: string
  selected: boolean
}

interface Props {
  activeSheet: PlaceSheetKind | null
  placeKindOptions: PlaceKindOption[]
  activeFilters: ActiveFilter[]
}

const { activeSheet, placeKindOptions, activeFilters } = defineProps<Props>()

const emit = defineEmits<{
  open: [kind: PlaceSheetKind]
  remove: [key: string]
  toggleKind: [kind: PlaceKind]
}>()

const { t } = useI18n()

const FILTER_LABELS: Record<PlaceSheetKind, string> = {
  region: 'explore.placeSheets.region',
  category: 'explore.placeSheets.category',
  options: 'explore.placeSheets.options',
  sort: 'explore.placeSheets.sort',
}

const hasAnyFilter = computed(
  () => activeFilters.length > 0 || placeKindOptions.some((option) => option.selected),
)
</script>

<template>
  <div class="flex min-w-0 flex-col gap-2">
    <div class="scrollbar-hidden -mx-screen flex gap-2 overflow-x-auto px-screen">
      <button
        v-for="kind in ['region', 'category', 'options'] as PlaceSheetKind[]"
        :key="kind"
        type="button"
        class="flex h-11 shrink-0 items-center gap-1 rounded-pill border px-4 text-body-sm transition-colors"
        :class="
          activeSheet === kind || activeFilters.some((filter) => filter.key.startsWith(kind))
            ? 'border-paper-fill bg-paper-fill text-on-paper'
            : 'border-hairline-2 bg-transparent text-ink-2'
        "
        @click="emit('open', kind)"
      >
        {{ t(FILTER_LABELS[kind]) }}
        <span
          v-if="activeFilters.some((filter) => filter.key.startsWith(kind))"
          class="text-caption"
        >
          · {{ activeFilters.filter((filter) => filter.key.startsWith(kind)).length }}
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
        v-for="option in placeKindOptions"
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

<style scoped>
.scrollbar-hidden {
  scrollbar-width: none;
}

.scrollbar-hidden::-webkit-scrollbar {
  display: none;
}
</style>
