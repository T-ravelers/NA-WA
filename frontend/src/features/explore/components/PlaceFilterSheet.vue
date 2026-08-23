<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { IconCheck, IconChevronDown, IconChevronUp } from '@tabler/icons-vue'

import AppButton from '@/shared/ui/AppButton.vue'
import CategoryDot from '@/shared/ui/CategoryDot.vue'

import { PLACE_SECTOR_OPTIONS } from '../model/exploreTaxonomy'
import { SEOUL_REGION1, SEOUL_REGION2_OPTIONS } from '../model/exploreRegions'
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

const { t } = useI18n()
const REGION2_OTHER = '__OTHER_REGION2__'

const SHEET_TITLES: Record<PlaceSheetKind, string> = {
  region: 'explore.placeSheets.region',
  category: 'explore.placeSheets.category',
  options: 'explore.placeSheets.options',
  sort: 'explore.placeSheets.sort',
}

const REGION_OPTIONS = [
  {
    labelKey: 'explore.regions.seoul',
    value: SEOUL_REGION1,
    areas: [
      { labelKey: 'explore.areas.allSeoul', apiValue: '__ALL_SEOUL__' },
      ...SEOUL_REGION2_OPTIONS,
      { labelKey: 'explore.areas.other', apiValue: REGION2_OTHER },
    ],
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
const selectedRegion = ref(SEOUL_REGION1)
// 시트를 열면 전부 접어 둔다. 하나만 펼쳐 두면 그 대분류만 있는 것처럼 보인다.
const expandedCategories = ref<string[]>([])

watch(
  () => props.filters,
  (filters) => {
    Object.assign(draft, cloneFilters(filters))
    selectedRegion.value = SEOUL_REGION1
  },
  { deep: true },
)

watch(
  draft,
  (filters) => {
    const changed = cloneFilters(filters)
    collapseCategorySelection(changed)
    emit('change', changed)
  },
  { deep: true },
)

const currentRegion = computed(() => REGION_OPTIONS[0])
const selectedAreas = computed(() => new Set(draft.region2 ?? []))

function cloneFilters(filters: PlaceSearchFilters): PlaceSearchFilters {
  return {
    ...filters,
    sectorIds: filters.sectorIds ? [...filters.sectorIds] : undefined,
    activityIds: filters.activityIds ? [...filters.activityIds] : undefined,
    placeKinds: filters.placeKinds ? [...filters.placeKinds] : undefined,
    region1: filters.region1 ? [...filters.region1] : [SEOUL_REGION1],
    region2: filters.region2 ? [...filters.region2] : undefined,
  }
}

function selectRegion(value: string): void {
  selectedRegion.value = value
  draft.region1 = [value]
  draft.region2 = undefined
  draft.region2Other = undefined
}

function toggleArea(value: string): void {
  if (value === REGION2_OTHER) {
    draft.region2Other = !draft.region2Other
    return
  }

  if (value === '__ALL_SEOUL__') {
    draft.region2 = undefined
    draft.region2Other = undefined
    return
  }

  const current = new Set(draft.region2 ?? [])
  if (current.has(value)) current.delete(value)
  else current.add(value)
  draft.region2 = current.size > 0 ? [...current] : undefined
}

function isAreaSelected(value: string): boolean {
  return selectedAreas.value.has(value)
}

/**
 * 화면이 들고 있는 소분류 체크 상태. `ExploreFilterSheet`와 같은 규칙이다.
 *
 * 대분류 체크는 따로 저장하지 않고 "그 아래 소분류가 전부 체크됐는가"로만 판단한다.
 * 주소에는 대분류가 ID 하나로 실려 오므로 여기서 소분류로 펼쳐 두고, 서버로 보낼 때
 * `collapseCategorySelection`이 다시 접는다.
 */
const checkedActivities = computed<Set<number>>(() => {
  const values = new Set(draft.activityIds ?? [])
  ;(draft.sectorIds ?? []).forEach((sectorId) => {
    PLACE_SECTOR_OPTIONS.find((sector) => sector.id === sectorId)?.activities.forEach((activity) =>
      values.add(activity.id),
    )
  })

  return values
})

function isActivitySelected(activityId: number): boolean {
  return checkedActivities.value.has(activityId)
}

/** 그 대분류에서 고른 소분류 개수. 접혀 있어도 무엇을 골랐는지 알 수 있게 헤더에 적는다. */
function selectedActivityCount(sector: (typeof PLACE_SECTOR_OPTIONS)[number]): number {
  return sector.activities.filter((activity) => checkedActivities.value.has(activity.id)).length
}

/** 대분류 체크는 그 아래 소분류가 전부 체크됐을 때만 켜진다. */
function isSectorFullySelected(sector: (typeof PLACE_SECTOR_OPTIONS)[number]): boolean {
  return sector.activities.every((activity) => checkedActivities.value.has(activity.id))
}

/** 화면 상태를 하나의 소분류 집합으로 확정한다. 대분류 칸은 늘 비운다. */
function writeCheckedActivities(values: Set<number>): void {
  draft.sectorIds = undefined
  draft.activityIds = values.size > 0 ? [...values].sort((a, b) => a - b) : undefined
}

/** 대분류를 켜면 그 아래 소분류가 전부 켜지고, 끄면 전부 꺼진다. */
function toggleSector(sector: (typeof PLACE_SECTOR_OPTIONS)[number]): void {
  const values = new Set(checkedActivities.value)
  const turningOff = isSectorFullySelected(sector)

  sector.activities.forEach((activity) => {
    if (turningOff) values.delete(activity.id)
    else values.add(activity.id)
  })

  writeCheckedActivities(values)
}

/** 소분류 하나를 켜고 끈다. 대분류 체크는 소분류 상태에서 저절로 따라온다. */
function toggleActivity(activityId: number): void {
  const values = new Set(checkedActivities.value)
  if (values.has(activityId)) values.delete(activityId)
  else values.add(activityId)

  writeCheckedActivities(values)
}

/**
 * 서버로 보낼 형태로 접는다. 소분류가 전부 켜진 대분류는 대분류 ID 하나로 바꾼다.
 *
 * 서버는 대분류 조건과 소분류 조건을 OR로 묶으므로 접지 않아도 결과는 같다. 그래도 접는
 * 것은 주소가 짧아지고, 두 조건이 삭제된 활동을 다르게 다루는 차이(소분류 조건은
 * `activity` 테이블을 join하지 않는다)에 덜 노출되기 때문이다.
 */
function collapseCategorySelection(filters: PlaceSearchFilters): void {
  const values = new Set(checkedActivities.value)
  const sectorIds: number[] = []

  PLACE_SECTOR_OPTIONS.forEach((sector) => {
    if (!sector.activities.every((activity) => values.has(activity.id))) return

    sector.activities.forEach((activity) => values.delete(activity.id))
    sectorIds.push(sector.id)
  })

  filters.sectorIds = sectorIds.length > 0 ? sectorIds : undefined
  filters.activityIds = values.size > 0 ? [...values].sort((a, b) => a - b) : undefined
}

function toggleExpandedCategory(label: string): void {
  expandedCategories.value = expandedCategories.value.includes(label)
    ? expandedCategories.value.filter((value) => value !== label)
    : [...expandedCategories.value, label]
}

function toggleOption(key: PlaceOptionKey): void {
  draft[key] = draft[key] === true ? undefined : true
}

/**
 * 정렬 시트에서 지금 무엇이 골라져 있는가. 세 항목은 서로 배타적이라 택 1이다.
 *
 * `ExploreFilterSheet`와 같은 규칙이다 — `Saved`는 정렬이 아니라 필터라 다른 곳에
 * 담기지만, 화면에는 하나만 체크돼야 하므로 한 값으로 합쳐서 본다.
 */
const sortSelection = computed<string>(() =>
  draft.savedOnly === true ? 'SAVED' : (draft.sort ?? 'POPULAR'),
)

function selectSort(value: PlaceSort): void {
  draft.sort = value
  draft.savedOnly = undefined
}

/** 목록 순서는 직전 정렬을 그대로 쓴다. Event 정렬 시트와 같은 규칙이다. */
function selectSavedOnly(): void {
  /* 다시 누르면 꺼진다 — 이 줄만 체크박스 모양이라 그렇게 읽힌다. */
  draft.savedOnly = draft.savedOnly === true ? undefined : true
}

function resetSheet(): void {
  if (props.kind === 'region') {
    draft.region1 = [SEOUL_REGION1]
    draft.region2 = undefined
    draft.region2Other = undefined
    selectedRegion.value = REGION_OPTIONS[0].value
  } else if (props.kind === 'category') {
    draft.sectorIds = undefined
    draft.activityIds = undefined
  } else if (props.kind === 'options') {
    PLACE_OPTIONS.forEach(({ key }) => (draft[key] = undefined))
  } else {
    draft.sort = 'POPULAR'
    draft.savedOnly = undefined
  }
}

function apply(): void {
  const filters = cloneFilters(draft)
  collapseCategorySelection(filters)
  emit('apply', filters)
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
                v-for="region in REGION_OPTIONS.slice(0, 1)"
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
                :key="area.apiValue"
                type="button"
                class="flex min-h-11 items-center justify-between rounded-sm px-3 text-left text-body-sm"
                :class="
                  (area.apiValue === '__ALL_SEOUL__' &&
                    selectedAreas.size === 0 &&
                    !draft.region2Other) ||
                  isAreaSelected(area.apiValue) ||
                  (area.apiValue === REGION2_OTHER && draft.region2Other)
                    ? 'text-ink'
                    : 'text-ink-2'
                "
                @click="toggleArea(area.apiValue)"
              >
                {{ t(area.labelKey) }}
                <span
                  class="flex size-6 items-center justify-center rounded-xs"
                  :class="
                    (area.apiValue === '__ALL_SEOUL__' &&
                      selectedAreas.size === 0 &&
                      !draft.region2Other) ||
                    isAreaSelected(area.apiValue) ||
                    (area.apiValue === REGION2_OTHER && draft.region2Other)
                      ? 'bg-paper-fill text-on-paper'
                      : 'border border-hairline-2'
                  "
                >
                  <IconCheck
                    v-if="
                      (area.apiValue === '__ALL_SEOUL__' &&
                        selectedAreas.size === 0 &&
                        !draft.region2Other) ||
                      isAreaSelected(area.apiValue) ||
                      (area.apiValue === REGION2_OTHER && draft.region2Other)
                    "
                    :size="15"
                    :stroke-width="2.5"
                    aria-hidden="true"
                  />
                </span>
              </button>
            </div>
          </div>
          <p class="mt-3 text-caption text-ink-3">{{ t('explore.regionHint') }}</p>
        </template>

        <template v-else-if="kind === 'category'">
          <div class="flex flex-col divide-y divide-hairline">
            <div
              v-for="sector in PLACE_SECTOR_OPTIONS"
              :key="sector.id"
              class="py-3"
            >
              <button
                type="button"
                class="flex w-full items-center gap-2 text-left"
                @click="toggleExpandedCategory(sector.labelKey)"
              >
                <CategoryDot :category="sector.category" />
                <span class="flex-1 text-title-sm text-ink"
                  >{{ t(sector.labelKey)
                  }}<span
                    v-if="selectedActivityCount(sector) > 0"
                    class="text-caption text-ink-3"
                  >
                    · {{ selectedActivityCount(sector) }}</span
                  ></span
                >
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
                  @click="toggleActivity(activity.id)"
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
                {
                  value: 'POPULAR',
                  labelKey: 'explore.sort.popular',
                  hintKey: 'explore.sort.defaultHint',
                },
                { value: 'NEWEST', labelKey: 'explore.sort.newest', hintKey: '' },
              ]"
              :key="sortOption.value"
              type="button"
              class="flex min-h-16 w-full items-center justify-between text-left"
              @click="selectSort(sortOption.value as PlaceSort)"
            >
              <span
                class="text-body"
                :class="sortSelection === sortOption.value ? 'text-ink' : 'text-ink-2'"
              >
                {{ t(sortOption.labelKey) }}
                <span
                  v-if="sortOption.hintKey"
                  class="text-caption text-ink-3"
                >
                  · {{ t(sortOption.hintKey) }}</span
                >
              </span>
              <span
                class="flex size-6 items-center justify-center rounded-pill"
                :class="
                  sortSelection === sortOption.value
                    ? 'bg-paper-fill text-on-paper'
                    : 'border border-hairline-2'
                "
              >
                <IconCheck
                  v-if="sortSelection === sortOption.value"
                  :size="15"
                  :stroke-width="2.5"
                  aria-hidden="true"
                />
              </span>
            </button>
            <button
              type="button"
              class="flex min-h-16 w-full items-center justify-between text-left"
              @click="selectSavedOnly"
            >
              <span
                class="text-body"
                :class="sortSelection === 'SAVED' ? 'text-ink' : 'text-ink-2'"
              >
                {{ t('explore.sort.saved') }}
              </span>
              <span
                class="flex size-6 items-center justify-center rounded-pill"
                :class="
                  sortSelection === 'SAVED'
                    ? 'bg-paper-fill text-on-paper'
                    : 'border border-hairline-2'
                "
              >
                <IconCheck
                  v-if="sortSelection === 'SAVED'"
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
