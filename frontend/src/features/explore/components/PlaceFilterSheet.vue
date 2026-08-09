<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { IconCheck, IconChevronDown, IconChevronUp } from '@tabler/icons-vue'

import AppButton from '@/shared/ui/AppButton.vue'
import CategoryDot from '@/shared/ui/CategoryDot.vue'

import { EVENT_SECTOR_OPTIONS } from '../model/exploreTaxonomy'
import type { PlaceSearchFilters, PlaceSort } from '../model/placeExplore'
import type { PlaceSheetKind } from './PlaceFilterBar.vue'

interface Props {
  kind: PlaceSheetKind
  filters: PlaceSearchFilters
  resultCount: number
}

const props = defineProps<Props>()

const emit = defineEmits<{
  close: []
  change: [filters: PlaceSearchFilters]
  apply: [filters: PlaceSearchFilters]
}>()

const REGION2_OTHER = '__OTHER_REGION2__'

const { t } = useI18n()

const SHEET_TITLES: Record<PlaceSheetKind, string> = {
  region: 'explore.placeSheets.region',
  category: 'explore.placeSheets.category',
  options: 'explore.placeSheets.options',
  sort: 'explore.placeSheets.sort',
}

const REGION_OPTIONS = [
  {
    labelKey: 'explore.regions.seoul',
    value: 'Seoul',
    areas: ['Seongsu', 'Gangnam', 'Seocho', 'Apgujeong·Dosan', 'Cheongdam'],
  },
  {
    labelKey: 'explore.regions.gyeonggi',
    value: 'Gyeonggi',
    areas: ['Suwon', 'Seongnam', 'Goyang', 'Yongin', 'Paju'],
  },
  {
    labelKey: 'explore.regions.busan',
    value: 'Busan',
    areas: ['Haeundae', 'Seomyeon', 'Gwangalli', 'Nampo'],
  },
  {
    labelKey: 'explore.regions.gangwon',
    value: 'Gangwon',
    areas: ['Chuncheon', 'Gangneung', 'Sokcho', 'Pyeongchang'],
  },
  {
    labelKey: 'explore.regions.gyeongbuk',
    value: 'Gyeongbuk',
    areas: ['Gyeongju', 'Andong', 'Pohang'],
  },
  {
    labelKey: 'explore.regions.chungnam',
    value: 'Chungnam',
    areas: ['Cheonan', 'Asan', 'Gongju', 'Boryeong'],
  },
  {
    labelKey: 'explore.regions.jeonbuk',
    value: 'Jeonbuk',
    areas: ['Jeonju', 'Gunsan', 'Iksan', 'Namwon'],
  },
  {
    labelKey: 'explore.regions.jeju',
    value: 'Jeju',
    areas: ['Jeju City', 'Seogwipo', 'Aewol', 'Hallim'],
  },
] as const

type PlaceOptionKey =
  | 'hasForeignLang'
  | 'hasParking'
  | 'reservable'
  | 'takeoutAvailable'
  | 'cardPaymentAvailable'
  | 'smokeFree'
  | 'kidFacility'
  | 'hasRestroom'

const PLACE_OPTIONS: Array<{ key: PlaceOptionKey; labelKey: string }> = [
  { key: 'hasForeignLang', labelKey: 'explore.placeFilterOptions.foreignLanguage' },
  { key: 'hasParking', labelKey: 'explore.placeFilterOptions.parking' },
  { key: 'reservable', labelKey: 'explore.placeFilterOptions.reservation' },
  { key: 'takeoutAvailable', labelKey: 'explore.placeFilterOptions.takeout' },
  { key: 'cardPaymentAvailable', labelKey: 'explore.placeFilterOptions.card' },
  { key: 'smokeFree', labelKey: 'explore.placeFilterOptions.smokeFree' },
  { key: 'kidFacility', labelKey: 'explore.placeFilterOptions.kids' },
  { key: 'hasRestroom', labelKey: 'explore.placeFilterOptions.restroom' },
]

const draft = reactive<PlaceSearchFilters>(cloneFilters(props.filters))
const selectedRegion = ref(props.filters.region1?.[0] ?? 'Seoul')
const expandedCategories = ref<string[]>(['explore.categories.food'])

watch(
  () => props.filters,
  (filters) => {
    Object.assign(draft, cloneFilters(filters))
    selectedRegion.value = filters.region1?.[0] ?? 'Seoul'
  },
  { deep: true },
)

watch(draft, (filters) => emit('change', cloneFilters(filters)), { deep: true })

const selectedSectors = computed(() => new Set(draft.sectorIds ?? []))
const selectedActivities = computed(() => new Set(draft.activityIds ?? []))
const currentRegion = computed(
  () => REGION_OPTIONS.find((region) => region.value === selectedRegion.value) ?? REGION_OPTIONS[0],
)
const selectedAreas = computed(() => new Set(draft.region2 ?? []))

function cloneFilters(filters: PlaceSearchFilters): PlaceSearchFilters {
  return {
    ...filters,
    sectorIds: filters.sectorIds ? [...filters.sectorIds] : undefined,
    activityIds: filters.activityIds ? [...filters.activityIds] : undefined,
    placeKinds: filters.placeKinds ? [...filters.placeKinds] : undefined,
    region1: filters.region1 ? [...filters.region1] : undefined,
    region2: filters.region2 ? [...filters.region2] : undefined,
    region3: filters.region3 ? [...filters.region3] : undefined,
  }
}

function selectRegion(value: string): void {
  selectedRegion.value = value
  draft.region1 = [value]
  draft.region2 = undefined
  draft.region3 = undefined
}

function toggleArea(value: string): void {
  if (value === REGION2_OTHER) return

  const current = new Set(draft.region2 ?? [])
  if (current.has(value)) current.delete(value)
  else current.add(value)
  draft.region2 = current.size > 0 ? [...current] : undefined
}

function isAreaSelected(value: string): boolean {
  return selectedAreas.value.has(value)
}

function isSectorSelected(sectorId: number): boolean {
  return selectedSectors.value.has(sectorId)
}

function isActivitySelected(activityId: number): boolean {
  return selectedActivities.value.has(activityId)
}

function isSectorFullySelected(sector: (typeof EVENT_SECTOR_OPTIONS)[number]): boolean {
  return (
    isSectorSelected(sector.id) ||
    sector.activities.every((activity) => selectedActivities.value.has(activity.id))
  )
}

function toggleSector(sector: (typeof EVENT_SECTOR_OPTIONS)[number]): void {
  const sectorIds = new Set(draft.sectorIds ?? [])
  const activityIds = new Set(draft.activityIds ?? [])
  const selected = isSectorFullySelected(sector)

  sector.activities.forEach((activity) => activityIds.delete(activity.id))
  if (selected) sectorIds.delete(sector.id)
  else sectorIds.add(sector.id)

  draft.sectorIds = sectorIds.size > 0 ? [...sectorIds] : undefined
  draft.activityIds = activityIds.size > 0 ? [...activityIds] : undefined
}

function toggleActivity(sectorId: number, activityId: number): void {
  const sectorIds = new Set(draft.sectorIds ?? [])
  const activityIds = new Set(draft.activityIds ?? [])

  if (activityIds.has(activityId)) activityIds.delete(activityId)
  else activityIds.add(activityId)
  sectorIds.delete(sectorId)

  draft.sectorIds = sectorIds.size > 0 ? [...sectorIds] : undefined
  draft.activityIds = activityIds.size > 0 ? [...activityIds] : undefined
}

function toggleExpandedCategory(label: string): void {
  expandedCategories.value = expandedCategories.value.includes(label)
    ? expandedCategories.value.filter((value) => value !== label)
    : [...expandedCategories.value, label]
}

function toggleOption(key: PlaceOptionKey): void {
  draft[key] = !draft[key]
}

function resetSheet(): void {
  if (props.kind === 'region') {
    draft.region1 = undefined
    draft.region2 = undefined
    draft.region3 = undefined
    selectedRegion.value = REGION_OPTIONS[0].value
  } else if (props.kind === 'category') {
    draft.sectorIds = undefined
    draft.activityIds = undefined
  } else if (props.kind === 'options') {
    PLACE_OPTIONS.forEach(({ key }) => (draft[key] = undefined))
  } else {
    draft.sort = 'LATEST'
    draft.savedOnly = undefined
  }
}

function apply(): void {
  emit('apply', cloneFilters(draft))
}
</script>

<template>
  <div class="fixed inset-0 z-30">
    <button
      type="button"
      class="absolute inset-0 bg-scrim/65"
      :aria-label="t('explore.filter.close')"
      @click="emit('close')"
    />

    <section
      role="dialog"
      aria-modal="true"
      :aria-label="t(SHEET_TITLES[kind])"
      class="absolute inset-x-0 bottom-0 z-10 mx-auto flex max-h-[88dvh] w-full max-w-[390px] flex-col rounded-t-lg bg-surface-1 px-screen pt-3 pb-6 shadow-sheet"
    >
      <span
        aria-hidden="true"
        class="mb-4 h-1 w-10 shrink-0 self-center rounded-pill bg-hairline-2"
      />

      <header class="mb-4 flex items-center justify-between">
        <h2 class="font-display text-section-header uppercase text-ink-display">
          {{ t(SHEET_TITLES[kind]) }}
        </h2>
        <button
          type="button"
          class="text-caption text-ink-3"
          @click="resetSheet"
        >
          {{ t('explore.filter.reset') }}
        </button>
      </header>

      <div class="min-h-0 overflow-y-auto">
        <template v-if="kind === 'region'">
          <div class="grid min-h-80 grid-cols-[112px_1fr] border-y border-hairline">
            <div class="border-r border-hairline">
              <button
                v-for="region in REGION_OPTIONS"
                :key="region.value"
                type="button"
                class="flex w-full items-center justify-between px-1 py-3 text-left text-body-sm"
                :class="selectedRegion === region.value ? 'bg-surface-2 text-ink' : 'text-ink-2'"
                @click="selectRegion(region.value)"
              >
                <span>{{ t(region.labelKey) }}</span>
              </button>
            </div>
            <div class="flex flex-col gap-1 p-2">
              <button
                v-for="area in currentRegion.areas"
                :key="area"
                type="button"
                class="flex min-h-11 items-center justify-between rounded-sm px-3 text-left text-body-sm"
                :class="isAreaSelected(area) ? 'text-ink' : 'text-ink-2'"
                @click="toggleArea(area)"
              >
                {{ area }}
                <span
                  class="flex size-6 items-center justify-center rounded-xs"
                  :class="
                    isAreaSelected(area)
                      ? 'bg-paper-fill text-on-paper'
                      : 'border border-hairline-2'
                  "
                >
                  <IconCheck
                    v-if="isAreaSelected(area)"
                    :size="15"
                    :stroke-width="2.5"
                    aria-hidden="true"
                  />
                </span>
              </button>
              <span class="px-3 pt-2 text-caption text-ink-3">Other areas</span>
            </div>
          </div>
          <p class="mt-3 text-caption text-ink-3">{{ t('explore.regionHint') }}</p>
        </template>

        <template v-else-if="kind === 'category'">
          <div class="flex flex-col divide-y divide-hairline">
            <div
              v-for="sector in EVENT_SECTOR_OPTIONS"
              :key="sector.id"
              class="py-3"
            >
              <button
                type="button"
                class="flex w-full items-center gap-2 text-left"
                @click="toggleExpandedCategory(sector.labelKey)"
              >
                <CategoryDot :category="sector.category" />
                <span class="flex-1 text-title-sm text-ink">{{ t(sector.labelKey) }}</span>
                <span
                  role="checkbox"
                  tabindex="0"
                  :aria-checked="isSectorFullySelected(sector)"
                  class="flex size-6 items-center justify-center rounded-xs"
                  :class="
                    isSectorFullySelected(sector)
                      ? 'bg-paper-fill text-on-paper'
                      : 'border border-hairline-2'
                  "
                  @click.stop="toggleSector(sector)"
                  @keydown.space.prevent.stop="toggleSector(sector)"
                  @keydown.enter.prevent.stop="toggleSector(sector)"
                >
                  <IconCheck
                    v-if="isSectorFullySelected(sector)"
                    :size="15"
                    :stroke-width="2.5"
                    aria-hidden="true"
                  />
                </span>
                <IconChevronUp
                  v-if="expandedCategories.includes(sector.labelKey)"
                  :size="16"
                  class="text-ink-3"
                  aria-hidden="true"
                />
                <IconChevronDown
                  v-else
                  :size="16"
                  class="text-ink-3"
                  aria-hidden="true"
                />
              </button>
              <div
                v-if="expandedCategories.includes(sector.labelKey)"
                class="mt-3 flex flex-wrap gap-2"
              >
                <button
                  v-for="activity in sector.activities"
                  :key="activity.id"
                  type="button"
                  class="rounded-pill border px-3 py-2 text-caption"
                  :class="
                    isActivitySelected(activity.id)
                      ? 'border-paper-fill bg-paper-fill text-on-paper'
                      : 'border-hairline text-ink-2'
                  "
                  @click="toggleActivity(sector.id, activity.id)"
                >
                  {{ t(activity.labelKey) }}
                </button>
              </div>
            </div>
          </div>
          <p class="mt-3 text-caption text-ink-3">{{ t('explore.categoryHint') }}</p>
        </template>

        <template v-else-if="kind === 'options'">
          <p class="mb-5 text-caption text-ink-3">{{ t('explore.placeOptionsHint') }}</p>
          <div class="divide-y divide-hairline">
            <button
              v-for="option in PLACE_OPTIONS"
              :key="option.key"
              type="button"
              class="flex min-h-14 w-full items-center justify-between text-left"
              @click="toggleOption(option.key)"
            >
              <span
                class="text-body text-ink-2"
                :class="draft[option.key] ? 'text-ink' : ''"
              >
                {{ t(option.labelKey) }}
              </span>
              <span
                class="flex size-6 items-center justify-center rounded-xs"
                :class="
                  draft[option.key] ? 'bg-paper-fill text-on-paper' : 'border border-hairline-2'
                "
              >
                <IconCheck
                  v-if="draft[option.key]"
                  :size="15"
                  :stroke-width="2.5"
                  aria-hidden="true"
                />
              </span>
            </button>
          </div>
        </template>

        <template v-else>
          <div class="divide-y divide-hairline">
            <button
              v-for="sortOption in [
                { value: 'LATEST', labelKey: 'explore.sort.latest', hint: 'default' },
                { value: 'POPULAR', labelKey: 'explore.sort.popular', hint: '' },
              ]"
              :key="sortOption.value"
              type="button"
              class="flex min-h-16 w-full items-center justify-between text-left"
              @click="draft.sort = sortOption.value as PlaceSort"
            >
              <span
                class="text-body"
                :class="draft.sort === sortOption.value ? 'text-ink' : 'text-ink-2'"
              >
                {{ t(sortOption.labelKey) }}
                <span
                  v-if="sortOption.hint"
                  class="text-caption text-ink-3"
                >
                  · {{ sortOption.hint }}</span
                >
              </span>
              <span
                class="flex size-6 items-center justify-center rounded-pill"
                :class="
                  draft.sort === sortOption.value
                    ? 'bg-paper-fill text-on-paper'
                    : 'border border-hairline-2'
                "
              >
                <IconCheck
                  v-if="draft.sort === sortOption.value"
                  :size="15"
                  :stroke-width="2.5"
                  aria-hidden="true"
                />
              </span>
            </button>
            <button
              type="button"
              class="flex min-h-16 w-full items-center justify-between text-left"
              @click="draft.savedOnly = !draft.savedOnly"
            >
              <span
                class="text-body"
                :class="draft.savedOnly ? 'text-ink' : 'text-ink-2'"
              >
                {{ t('explore.sort.saved') }}
              </span>
              <span
                class="flex size-6 items-center justify-center rounded-pill"
                :class="
                  draft.savedOnly ? 'bg-paper-fill text-on-paper' : 'border border-hairline-2'
                "
              >
                <IconCheck
                  v-if="draft.savedOnly"
                  :size="15"
                  :stroke-width="2.5"
                  aria-hidden="true"
                />
              </span>
            </button>
          </div>
        </template>
      </div>

      <AppButton
        block
        class="mt-5"
        @click="apply"
      >
        {{ t('explore.filter.apply') }} · {{ resultCount }} {{ t('explore.resultUnit') }}
      </AppButton>
    </section>
  </div>
</template>
