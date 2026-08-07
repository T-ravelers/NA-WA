<script setup lang="ts">
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
</script>

<template>
  <div class="flex min-w-0 flex-col gap-2">
    <div class="scrollbar-hidden -mx-screen flex gap-2 overflow-x-auto px-screen">
      <button
        v-for="kind in ['date', 'region', 'category', 'options'] as ExploreSheetKind[]"
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
        v-if="activeFilters.length > 0"
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
