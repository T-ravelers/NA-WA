<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { IconChevronUp, IconChevronDown, IconCheck } from '@tabler/icons-vue'

import { serializeCalendarDate } from '@/shared/lib/datetime'
import CalendarGrid from '@/shared/ui/CalendarGrid.vue'
import AppButton from '@/shared/ui/AppButton.vue'
import CategoryDot from '@/shared/ui/CategoryDot.vue'

import { presetDateRange, todayDate, type PresetDateRange } from '../model/datePresets'
import type { EventSearchFilters } from '../model/eventExplore'
import { SEOUL_REGION1, SEOUL_REGION2_OPTIONS } from '../model/exploreRegions'
import { EVENT_SECTOR_OPTIONS } from '../model/exploreTaxonomy'
import type { ExploreSheetKind } from './ExploreFilterBar.vue'

interface Props {
  kind: ExploreSheetKind
  filters: EventSearchFilters
  resultCount: number
}

const props = defineProps<Props>()

const emit = defineEmits<{
  close: []
  change: [filters: EventSearchFilters]
  apply: [filters: EventSearchFilters]
}>()

const REGION2_OTHER = '__OTHER_REGION2__'

const { t } = useI18n()

const SHEET_TITLES: Record<ExploreSheetKind, string> = {
  date: 'explore.sheets.date',
  region: 'explore.sheets.region',
  category: 'explore.sheets.category',
  options: 'explore.sheets.options',
  sort: 'explore.sheets.sort',
}

const REGION_OPTIONS = [
  {
    labelKey: 'explore.regions.seoul',
    value: SEOUL_REGION1,
    areas: [
      { labelKey: 'explore.areas.allSeoul', value: '__ALL_SEOUL__' },
      ...SEOUL_REGION2_OPTIONS.map(({ labelKey, apiValue }) => ({ labelKey, value: apiValue })),
      { labelKey: 'explore.areas.other', value: REGION2_OTHER },
    ],
  },
  {
    labelKey: 'explore.regions.gyeonggi',
    value: 'Gyeonggi',
    areas: [
      { labelKey: 'explore.areas.allGyeonggi', value: 'All of Gyeonggi' },
      { labelKey: 'explore.areas.suwon', value: 'Suwon' },
      { labelKey: 'explore.areas.seongnam', value: 'Seongnam' },
      { labelKey: 'explore.areas.goyang', value: 'Goyang' },
      { labelKey: 'explore.areas.yongin', value: 'Yongin' },
      { labelKey: 'explore.areas.paju', value: 'Paju' },
      { labelKey: 'explore.areas.other', value: REGION2_OTHER },
    ],
  },
  {
    labelKey: 'explore.regions.busan',
    value: 'Busan',
    areas: [
      { labelKey: 'explore.areas.allBusan', value: 'All of Busan' },
      { labelKey: 'explore.areas.haeundae', value: 'Haeundae' },
      { labelKey: 'explore.areas.seomyeon', value: 'Seomyeon' },
      { labelKey: 'explore.areas.gwangalli', value: 'Gwangalli' },
      { labelKey: 'explore.areas.nampo', value: 'Nampo' },
      { labelKey: 'explore.areas.other', value: REGION2_OTHER },
    ],
  },
  {
    labelKey: 'explore.regions.gangwon',
    value: 'Gangwon',
    areas: [
      { labelKey: 'explore.areas.allGangwon', value: 'All of Gangwon' },
      { labelKey: 'explore.areas.chuncheon', value: 'Chuncheon' },
      { labelKey: 'explore.areas.gangneung', value: 'Gangneung' },
      { labelKey: 'explore.areas.sokcho', value: 'Sokcho' },
      { labelKey: 'explore.areas.pyeongchang', value: 'Pyeongchang' },
      { labelKey: 'explore.areas.other', value: REGION2_OTHER },
    ],
  },
  {
    labelKey: 'explore.regions.gyeongbuk',
    value: 'Gyeongbuk',
    areas: [
      { labelKey: 'explore.areas.allGyeongbuk', value: 'All of Gyeongbuk' },
      { labelKey: 'explore.areas.gyeongju', value: 'Gyeongju' },
      { labelKey: 'explore.areas.andong', value: 'Andong' },
      { labelKey: 'explore.areas.pohang', value: 'Pohang' },
      { labelKey: 'explore.areas.other', value: REGION2_OTHER },
    ],
  },
  {
    labelKey: 'explore.regions.chungnam',
    value: 'Chungnam',
    areas: [
      { labelKey: 'explore.areas.allChungnam', value: 'All of Chungnam' },
      { labelKey: 'explore.areas.cheonan', value: 'Cheonan' },
      { labelKey: 'explore.areas.asan', value: 'Asan' },
      { labelKey: 'explore.areas.gongju', value: 'Gongju' },
      { labelKey: 'explore.areas.boryeong', value: 'Boryeong' },
      { labelKey: 'explore.areas.other', value: REGION2_OTHER },
    ],
  },
  {
    labelKey: 'explore.regions.jeonbuk',
    value: 'Jeonbuk',
    areas: [
      { labelKey: 'explore.areas.allJeonbuk', value: 'All of Jeonbuk' },
      { labelKey: 'explore.areas.jeonju', value: 'Jeonju' },
      { labelKey: 'explore.areas.gunsan', value: 'Gunsan' },
      { labelKey: 'explore.areas.iksan', value: 'Iksan' },
      { labelKey: 'explore.areas.namwon', value: 'Namwon' },
      { labelKey: 'explore.areas.other', value: REGION2_OTHER },
    ],
  },
  {
    labelKey: 'explore.regions.jeju',
    value: 'Jeju',
    areas: [
      { labelKey: 'explore.areas.allJeju', value: 'All of Jeju' },
      { labelKey: 'explore.areas.jejuCity', value: 'Jeju City' },
      { labelKey: 'explore.areas.seogwipo', value: 'Seogwipo' },
      { labelKey: 'explore.areas.aewol', value: 'Aewol' },
      { labelKey: 'explore.areas.hallim', value: 'Hallim' },
      { labelKey: 'explore.areas.other', value: REGION2_OTHER },
    ],
  },
] as const

type OptionKey =
  'freeOnly' | 'openWeekendOnly' | 'opensLateOnly' | 'preReservationOnly' | 'experienceOnly'

const EVENT_OPTIONS: Array<{ key: OptionKey; labelKey: string }> = [
  { key: 'freeOnly', labelKey: 'explore.options.free' },
  { key: 'openWeekendOnly', labelKey: 'explore.options.openWeekend' },
  { key: 'opensLateOnly', labelKey: 'explore.options.openLate' },
  { key: 'preReservationOnly', labelKey: 'explore.options.preReservation' },
  { key: 'experienceOnly', labelKey: 'explore.options.experience' },
]

const DATE_PRESETS = [
  { value: 'ONGOING', labelKey: 'explore.datePresets.ONGOING' },
  { value: 'OPENING_SOON', labelKey: 'explore.datePresets.OPENING_SOON' },
  { value: 'THIS_WEEKEND', labelKey: 'explore.datePresets.THIS_WEEKEND' },
  { value: 'THIS_MONTH', labelKey: 'explore.datePresets.THIS_MONTH' },
] as const

const draft = reactive<EventSearchFilters>(cloneFilters(props.filters))
const selectedRegion = ref(SEOUL_REGION1)
// 시트를 열면 전부 접어 둔다. 하나만 펼쳐 두면 그 대분류만 있는 것처럼 보인다.
const expandedCategories = ref<string[]>([])

watch(
  () => props.filters,
  (filters) => {
    Object.assign(draft, cloneFilters(filters))
    selectedRegion.value = SEOUL_REGION1
    calendarTouched.value = false
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

const currentRegion = computed(
  () => REGION_OPTIONS.find((region) => region.value === selectedRegion.value) ?? REGION_OPTIONS[0],
)
const selectedAreas = computed(() => new Set(draft.region2 ?? []))
function cloneFilters(filters: EventSearchFilters): EventSearchFilters {
  return {
    ...filters,
    sectorIds: filters.sectorIds ? [...filters.sectorIds] : undefined,
    activityIds: filters.activityIds ? [...filters.activityIds] : undefined,
    eventKinds: filters.eventKinds ? [...filters.eventKinds] : undefined,
    region1: filters.region1 ? [...filters.region1] : [SEOUL_REGION1],
    region2: filters.region2 ? [...filters.region2] : undefined,
    region3: filters.region3 ? [...filters.region3] : undefined,
  }
}

const selectableRange = computed<PresetDateRange>(() => {
  const preset = draft.datePreset ? presetDateRange(draft.datePreset) : null
  // 프리셋이 없으면 오늘부터 고를 수 있다. 지난 날짜의 이벤트를 찾을 일은 없다.
  return preset ?? { min: serializeCalendarDate(todayDate()) }
})

function isDateAllowed(value: string): boolean {
  const range = selectableRange.value
  if (value < range.min) return false
  return range.max === undefined || value <= range.max
}

// 사용자가 달력을 직접 만졌는지 여부. 프리셋 기본 상태(범위 전체 선택)와
// "프리셋 시작일과 같은 날을 직접 고른 경우"를 값만으로는 구분할 수 없어서
// 플래그로 기억한다.
const calendarTouched = ref(false)

function setDatePreset(value: string): void {
  calendarTouched.value = false
  if (draft.datePreset === value) {
    draft.datePreset = undefined
    draft.startDate = undefined
    draft.endDate = undefined
    return
  }

  // 프리셋만 골라도 그 범위 전체가 필터로 적용된다. 이후 달력에서 범위 안의
  // 날짜로 더 좁힐 수 있다.
  const range = presetDateRange(value)
  draft.datePreset = value
  draft.startDate = range?.min
  draft.endDate = range?.max
}

function setCalendarDate(value: string): void {
  if (!isDateAllowed(value)) return

  // 이번 탭이 달력의 첫 탭인지 먼저 붙잡는다. 아래 판정보다 늦게 읽으면 항상 거짓이다.
  const firstTap = !calendarTouched.value
  calendarTouched.value = true

  /*
   * 프리셋 범위 전체가 그대로 선택돼 있는 상태라면, 첫 탭은 범위를 닫는 게 아니라 그 안에서
   * 새로 고르기 시작하는 것으로 본다.
   *
   * 값만으로는 부족하다. Opening soon은 상한이 없어 `max`가 `undefined`인데, 첫 탭을 마친
   * 뒤의 `endDate`도 `undefined`다. 그래서 첫 탭이 하필 프리셋 시작일이면 두 상태가 똑같아
   * 보이고, 두 번째 탭이 범위를 닫지 못한 채 시작일만 덮어쓴다. 첫 탭인지를 함께 본다.
   */
  const presetRange = draft.datePreset ? presetDateRange(draft.datePreset) : null
  const presetPristine =
    firstTap &&
    presetRange !== null &&
    draft.startDate === presetRange.min &&
    draft.endDate === presetRange.max

  if (presetPristine || draft.startDate === undefined || draft.endDate !== undefined) {
    draft.startDate = value
    draft.endDate = undefined
    return
  }

  if (value < draft.startDate) {
    draft.endDate = draft.startDate
    draft.startDate = value
    return
  }

  draft.endDate = value
}

/**
 * 정렬 시트에서 지금 무엇이 골라져 있는가. 네 항목은 서로 배타적이라 택 1이다.
 *
 * `Saved`는 정렬이 아니라 필터라서 `sort`와 다른 곳에 담긴다. 그래서 화면에 체크를
 * 그리려면 둘을 한 값으로 합쳐야 한다. `Saved`가 켜져 있으면 `sort`가 무엇이든 체크는
 * `Saved`에만 붙는다 — 두 개가 동시에 체크된 것처럼 보이면 택 1이 아니게 된다.
 */
const sortSelection = computed<string>(() =>
  draft.savedOnly === true ? 'SAVED' : (draft.sort ?? 'NEWEST'),
)

function selectSort(value: EventSearchFilters['sort']): void {
  draft.sort = value
  draft.savedOnly = undefined
}

/**
 * 찜한 항목만 보기.
 *
 * 목록 순서는 직전 정렬을 그대로 쓴다 — `sort`를 건드리지 않는 이유다. 체크만 옮겨간다.
 *
 * 다시 누르면 꺼진다. 라디오라면 켠 것을 다시 눌러도 안 꺼지는 게 맞지만, 이 줄만
 * 체크박스 모양이라 다시 누르면 꺼진다고 읽힌다. 끄는 길이 화면에 없으면 잘못 눌렀을 때
 * 되돌릴 방법을 찾지 못한다 — 체크가 정렬 항목으로 돌아가고 순서도 그대로다.
 */
function selectSavedOnly(): void {
  draft.savedOnly = draft.savedOnly === true ? undefined : true
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

  const current = new Set(draft.region2 ?? [])
  if (value === '__ALL_SEOUL__' || value.startsWith('All of ')) {
    draft.region2 = undefined
    draft.region2Other = undefined
    return
  }
  if (current.has(value)) current.delete(value)
  else current.add(value)
  draft.region2 = [...current]
}

/**
 * 화면이 들고 있는 소분류 체크 상태.
 *
 * 대분류 체크는 따로 저장하지 않고 "그 아래 소분류가 전부 체크됐는가"로만 판단한다. 두 곳에
 * 따로 두면 소분류를 하나 끄고도 대분류 체크가 남는 어긋남이 생긴다.
 *
 * 주소에는 대분류가 ID 하나로 실려 오므로(`eventSectorIds=2`) 여기서 소분류로 펼쳐 둔다.
 * 서버로 보낼 때는 `collapseCategorySelection`이 다시 대분류로 접는다.
 */
const checkedActivities = computed<Set<number>>(() => {
  const values = new Set(draft.activityIds ?? [])
  ;(draft.sectorIds ?? []).forEach((sectorId) => {
    EVENT_SECTOR_OPTIONS.find((sector) => sector.id === sectorId)?.activities.forEach((activity) =>
      values.add(activity.id),
    )
  })

  return values
})

function isActivitySelected(activityId: number): boolean {
  return checkedActivities.value.has(activityId)
}

/** 그 대분류에서 고른 소분류 개수. 접혀 있어도 무엇을 골랐는지 알 수 있게 헤더에 적는다. */
function selectedActivityCount(sector: (typeof EVENT_SECTOR_OPTIONS)[number]): number {
  return sector.activities.filter((activity) => checkedActivities.value.has(activity.id)).length
}

/** 대분류 체크는 그 아래 소분류가 전부 체크됐을 때만 켜진다. */
function isSectorFullySelected(sector: (typeof EVENT_SECTOR_OPTIONS)[number]): boolean {
  return sector.activities.every((activity) => checkedActivities.value.has(activity.id))
}

/** 화면 상태를 하나의 소분류 집합으로 확정한다. 대분류 칸은 늘 비운다. */
function writeCheckedActivities(values: Set<number>): void {
  draft.sectorIds = undefined
  draft.activityIds = values.size > 0 ? [...values].sort((a, b) => a - b) : undefined
}

/** 대분류를 켜면 그 아래 소분류가 전부 켜지고, 끄면 전부 꺼진다. */
function toggleSector(sector: (typeof EVENT_SECTOR_OPTIONS)[number]): void {
  const values = new Set(checkedActivities.value)
  const turningOff = isSectorFullySelected(sector)

  sector.activities.forEach((activity) => {
    if (turningOff) values.delete(activity.id)
    else values.add(activity.id)
  })

  writeCheckedActivities(values)
}

/**
 * 소분류 하나를 켜고 끈다.
 *
 * 대분류를 따로 건드리지 않는다 — 하나라도 꺼지면 `isSectorFullySelected`가 저절로 거짓이
 * 되고, 전부 켜지면 저절로 참이 된다.
 */
function toggleActivity(activityId: number): void {
  const values = new Set(checkedActivities.value)
  if (values.has(activityId)) values.delete(activityId)
  else values.add(activityId)

  writeCheckedActivities(values)
}

/**
 * 서버로 보낼 형태로 접는다. 소분류가 전부 켜진 대분류는 대분류 ID 하나로 바꾼다.
 *
 * 접지 않으면 대분류 조건과 소분류 조건이 함께 나가는데, 서버는 그 둘을 AND로 묶는다.
 * 다른 대분류의 소분류를 섞어 고른 순간 조건이 무너지므로 지금까지 보내던 형태를 지킨다.
 */
function collapseCategorySelection(filters: EventSearchFilters): void {
  const values = new Set(checkedActivities.value)
  const sectorIds: number[] = []

  EVENT_SECTOR_OPTIONS.forEach((sector) => {
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

function toggleOption(key: OptionKey): void {
  draft[key] = !draft[key]
}

function resetSheet(): void {
  if (props.kind === 'date') {
    draft.datePreset = undefined
    draft.startDate = undefined
    draft.endDate = undefined
    calendarTouched.value = false
  } else if (props.kind === 'region') {
    draft.region1 = [SEOUL_REGION1]
    draft.region2 = undefined
    draft.region2Other = undefined
    draft.region3 = undefined
    selectedRegion.value = REGION_OPTIONS[0].value
  } else if (props.kind === 'category') {
    draft.sectorIds = undefined
    draft.activityIds = undefined
  } else if (props.kind === 'options') {
    EVENT_OPTIONS.forEach(({ key }) => (draft[key] = undefined))
  } else {
    draft.sort = 'NEWEST'
    draft.savedOnly = undefined
  }
}

function apply(): void {
  const filters = cloneFilters(draft)
  collapseCategorySelection(filters)
  // 하루만 고른 선택은 여기서 시작=종료의 하루짜리 기간으로 확정한다.
  // 달력을 만지지 않은 Opening soon 기본 상태만 상한 없이 시작일을 열어 둔다.
  if (
    filters.startDate !== undefined &&
    filters.endDate === undefined &&
    (calendarTouched.value || draft.datePreset !== 'OPENING_SOON')
  ) {
    filters.endDate = filters.startDate
  }
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
        <template v-if="kind === 'date'">
          <div class="grid grid-cols-2 gap-2">
            <button
              v-for="preset in DATE_PRESETS"
              :key="preset.value"
              type="button"
              class="min-h-11 rounded-pill border px-3 text-caption"
              :class="
                draft.datePreset === preset.value
                  ? 'border-paper-fill bg-paper-fill text-on-paper'
                  : 'border-hairline text-ink-2'
              "
              @click="setDatePreset(preset.value)"
            >
              {{ t(preset.labelKey) }}
            </button>
          </div>

          <div class="my-5 border-t border-hairline" />

          <CalendarGrid
            :range-start="draft.startDate ?? null"
            :range-end="draft.endDate ?? null"
            :is-date-allowed="isDateAllowed"
            :label-dates="false"
            @select="setCalendarDate"
          />

          <p class="mt-3 text-caption text-ink-3">
            {{ draft.startDate ?? t('explore.calendar.startDate')
            }}<span v-if="draft.endDate"> – {{ draft.endDate }}</span>
          </p>
        </template>

        <template v-else-if="kind === 'region'">
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
                <span class="text-ink-3">—</span>
              </button>
            </div>
            <div class="flex flex-col gap-1 p-2">
              <button
                v-for="area in currentRegion.areas"
                :key="area.value"
                type="button"
                class="flex min-h-11 items-center justify-between rounded-sm px-3 text-left text-body-sm"
                :class="
                  (area.value === '__ALL_SEOUL__' &&
                    selectedAreas.size === 0 &&
                    !draft.region2Other) ||
                  selectedAreas.has(area.value) ||
                  (area.value === REGION2_OTHER && draft.region2Other)
                    ? 'text-ink'
                    : 'text-ink-2'
                "
                @click="toggleArea(area.value)"
              >
                {{ t(area.labelKey) }}
                <span
                  class="flex size-6 items-center justify-center rounded-xs"
                  :class="
                    (area.value === '__ALL_SEOUL__' &&
                      selectedAreas.size === 0 &&
                      !draft.region2Other) ||
                    selectedAreas.has(area.value) ||
                    (area.value === REGION2_OTHER && draft.region2Other)
                      ? 'bg-paper-fill text-on-paper'
                      : 'border border-hairline-2'
                  "
                >
                  <IconCheck
                    v-if="
                      (area.value === '__ALL_SEOUL__' &&
                        selectedAreas.size === 0 &&
                        !draft.region2Other) ||
                      selectedAreas.has(area.value) ||
                      (area.value === REGION2_OTHER && draft.region2Other)
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
          <p class="mb-5 text-caption text-ink-3">{{ t('explore.optionsHint') }}</p>
          <div class="divide-y divide-hairline">
            <button
              v-for="option in EVENT_OPTIONS"
              :key="option.key"
              type="button"
              class="flex min-h-14 w-full items-center justify-between text-left"
              @click="toggleOption(option.key)"
            >
              <span
                class="text-body text-ink-2"
                :class="draft[option.key] ? 'text-ink' : ''"
                >{{ t(option.labelKey) }}</span
              >
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
                { value: 'NEWEST', labelKey: 'explore.sort.newest', hint: 'default' },
                { value: 'POPULAR', labelKey: 'explore.sort.popular', hint: '' },
                { value: 'ENDING_SOON', labelKey: 'explore.sort.ending_soon', hint: '' },
              ]"
              :key="sortOption.value"
              type="button"
              class="flex min-h-16 w-full items-center justify-between text-left"
              @click="selectSort(sortOption.value as EventSearchFilters['sort'])"
            >
              <span
                class="text-body"
                :class="sortSelection === sortOption.value ? 'text-ink' : 'text-ink-2'"
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
