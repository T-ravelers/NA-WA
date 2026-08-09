<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter, type LocationQueryRaw } from 'vue-router'
import { IconChevronDown, IconSearch } from '@tabler/icons-vue'

import StateError from '@/shared/ui/StateError.vue'
import StateEmpty from '@/shared/ui/StateEmpty.vue'
import StateLoading from '@/shared/ui/StateLoading.vue'

import EventCard from '../components/EventCard.vue'
import ExploreFilterBar from '../components/ExploreFilterBar.vue'
import ExploreFilterSheet from '../components/ExploreFilterSheet.vue'
import ExploreItemTabs from '../components/ExploreItemTabs.vue'
import PlaceFilterBar from '../components/PlaceFilterBar.vue'
import PlaceFilterSheet from '../components/PlaceFilterSheet.vue'
import PlaceCard from '../components/PlaceCard.vue'
import { useEventListQuery } from '../composables/useEventListQuery'
import { usePlaceListQuery } from '../composables/usePlaceListQuery'
import {
  EVENT_KINDS,
  type EventKind,
  type EventSearchFilters,
  type EventSort,
} from '../model/eventExplore'
import { EVENT_SECTOR_OPTIONS } from '../model/exploreTaxonomy'
import {
  PLACE_KINDS,
  normalizePlaceKind,
  type PlaceKind,
  type PlaceSearchFilters,
  type PlaceSort,
} from '../model/placeExplore'

type ExploreSheetKind = 'date' | 'region' | 'category' | 'options' | 'sort'

const { locale, t } = useI18n()
const route = useRoute()
const router = useRouter()

const selectedTab = ref<'events' | 'places'>('events')
const selectedSheet = ref<ExploreSheetKind | null>(null)
const searchOpen = ref(false)

const selectedEventKinds = ref<EventKind[]>(readQueryList('eventKinds').filter(isEventKind))
const selectedSectorIds = ref(readQueryNumberList('sectorIds'))
const selectedActivityIds = ref(readQueryNumberList('activityIds'))
const selectedRegion1 = ref(readQueryList('region1'))
const selectedRegion2 = ref(readQueryList('region2'))
const selectedRegion2Other = ref(readQueryBoolean('region2Other'))
const selectedRegion3 = ref(readQueryList('region3'))
const datePreset = ref(readQueryString('datePreset'))
const startDate = ref(readQueryString('startDate'))
const endDate = ref(readQueryString('endDate'))
const keyword = ref(readQueryString('keyword') ?? '')
const sort = ref<EventSort>(readSort(readQueryString('sort')))
const freeOnly = ref(readQueryBoolean('freeOnly'))
const openWeekendOnly = ref(readQueryBoolean('openWeekendOnly'))
const opensLateOnly = ref(readQueryBoolean('opensLateOnly'))
const preReservationOnly = ref(readQueryBoolean('preReservationOnly'))
const experienceOnly = ref(readQueryBoolean('experienceOnly'))
const selectedPlaceKinds = ref<PlaceKind[]>(readQueryList('placeKinds').map(normalizePlaceKind))
const selectedPlaceSectorIds = ref(readQueryNumberList('placeSectorIds'))
const selectedPlaceActivityIds = ref(readQueryNumberList('placeActivityIds'))
const selectedPlaceRegion1 = ref(readQueryList('placeRegion1'))
const selectedPlaceRegion2 = ref(readQueryList('placeRegion2'))
const selectedPlaceHasForeignLang = ref(readQueryBoolean('hasForeignLang'))
const selectedPlaceHasParking = ref(readQueryBoolean('hasParking'))
const selectedPlaceReservable = ref(readQueryBoolean('reservable'))
const selectedPlaceTakeout = ref(readQueryBoolean('takeoutAvailable'))
const selectedPlaceCardPayment = ref(readQueryBoolean('cardPaymentAvailable'))
const selectedPlaceSmokeFree = ref(readQueryBoolean('smokeFree'))
const selectedPlaceKidFacility = ref(readQueryBoolean('kidFacility'))
const selectedPlaceRestroom = ref(readQueryBoolean('hasRestroom'))
const selectedPlaceSavedOnly = ref(readQueryBoolean('savedOnly'))
const selectedPlacePage = ref(readQueryPage('placePage'))
const placeSort = ref<PlaceSort>(readPlaceSort(readQueryString('placeSort')))
const sheetPreviewFilters = ref<EventSearchFilters | null>(null)
const placeSheetPreviewFilters = ref<PlaceSearchFilters | null>(null)

const filters = computed<EventSearchFilters>(() => ({
  language: locale.value,
  page: 0,
  size: 20,
  sort: sort.value,
  keyword: keyword.value || undefined,
  eventKinds: selectedEventKinds.value.length > 0 ? selectedEventKinds.value : undefined,
  sectorIds: selectedSectorIds.value.length > 0 ? selectedSectorIds.value : undefined,
  activityIds: selectedActivityIds.value.length > 0 ? selectedActivityIds.value : undefined,
  region1: selectedRegion1.value.length > 0 ? selectedRegion1.value : undefined,
  region2: selectedRegion2.value.length > 0 ? selectedRegion2.value : undefined,
  region2Other: selectedRegion2Other.value || undefined,
  region3: selectedRegion3.value.length > 0 ? selectedRegion3.value : undefined,
  datePreset: datePreset.value,
  startDate: startDate.value,
  endDate: endDate.value,
  freeOnly: freeOnly.value || undefined,
  openWeekendOnly: openWeekendOnly.value || undefined,
  opensLateOnly: opensLateOnly.value || undefined,
  preReservationOnly: preReservationOnly.value || undefined,
  experienceOnly: experienceOnly.value || undefined,
}))

const eventQuery = useEventListQuery(filters)
const placeFilters = computed<PlaceSearchFilters>(() => ({
  language: locale.value,
  page: selectedPlacePage.value,
  size: 20,
  sort: placeSort.value,
  keyword: keyword.value || undefined,
  placeKinds: selectedPlaceKinds.value.length > 0 ? selectedPlaceKinds.value : undefined,
  sectorIds: selectedPlaceSectorIds.value.length > 0 ? selectedPlaceSectorIds.value : undefined,
  activityIds:
    selectedPlaceActivityIds.value.length > 0 ? selectedPlaceActivityIds.value : undefined,
  region1: selectedPlaceRegion1.value.length > 0 ? selectedPlaceRegion1.value : undefined,
  region2: selectedPlaceRegion2.value.length > 0 ? selectedPlaceRegion2.value : undefined,
  hasForeignLang: selectedPlaceHasForeignLang.value || undefined,
  hasParking: selectedPlaceHasParking.value || undefined,
  reservable: selectedPlaceReservable.value || undefined,
  takeoutAvailable: selectedPlaceTakeout.value || undefined,
  cardPaymentAvailable: selectedPlaceCardPayment.value || undefined,
  smokeFree: selectedPlaceSmokeFree.value || undefined,
  kidFacility: selectedPlaceKidFacility.value || undefined,
  hasRestroom: selectedPlaceRestroom.value || undefined,
  savedOnly: selectedPlaceSavedOnly.value || undefined,
}))
const placeQuery = usePlaceListQuery(placeFilters, {
  enabled: () => selectedTab.value === 'places',
})
const sheetPreviewQuery = useEventListQuery(
  computed(() => sheetPreviewFilters.value ?? filters.value),
)
const placeSheetPreviewQuery = usePlaceListQuery(
  computed(() => placeSheetPreviewFilters.value ?? placeFilters.value),
  { enabled: () => selectedTab.value === 'places' && selectedSheet.value !== null },
)
const eventList = computed(() => eventQuery.data.value?.content ?? [])
const visibleEventCount = computed(() => eventList.value.length)
const totalEventElements = computed(() => eventQuery.data.value?.totalElements ?? 0)
const placeList = computed(() => placeQuery.data.value?.content ?? [])
const visiblePlaceCount = computed(() => placeList.value.length)
const sheetResultCount = computed(
  () => sheetPreviewQuery.data.value?.totalElements ?? totalEventElements.value,
)
const placeSheetResultCount = computed(
  () =>
    placeSheetPreviewQuery.data.value?.totalElements ?? placeQuery.data.value?.totalElements ?? 0,
)

const eventKindOptions = computed(() =>
  EVENT_KINDS.map((kind) => ({
    key: kind,
    label: t(`explore.eventKinds.${kind}`),
    selected: selectedEventKinds.value.includes(kind),
  })),
)

const sortLabel = computed(() => t(`explore.sort.${sort.value.toLowerCase()}`))
const placeSortLabel = computed(() => t(`explore.sort.${placeSort.value.toLowerCase()}`))

const placeKindOptions = computed(() =>
  PLACE_KINDS.map((kind) => ({
    key: kind,
    label: t(`explore.placeKinds.${kind}`),
    selected: selectedPlaceKinds.value.includes(kind),
  })),
)

const activeFilters = computed(() => {
  const values: Array<{ key: string; label: string }> = []

  if (datePreset.value) {
    values.push({ key: 'date:preset', label: t(`explore.datePresets.${datePreset.value}`) })
  } else if (startDate.value || endDate.value) {
    values.push({ key: 'date:range', label: `${startDate.value ?? '…'} – ${endDate.value ?? '…'}` })
  }

  selectedRegion1.value.forEach((value) => values.push({ key: `region1:${value}`, label: value }))
  selectedRegion2.value.forEach((value) => values.push({ key: `region2:${value}`, label: value }))
  if (selectedRegion2Other.value) {
    values.push({ key: 'region2:other', label: t('explore.areas.other') })
  }
  selectedRegion3.value.forEach((value) => values.push({ key: `region3:${value}`, label: value }))

  selectedSectorIds.value.forEach((value) => {
    const sector = EVENT_SECTOR_OPTIONS.find((option) => option.id === value)
    if (sector) values.push({ key: `sector:${value}`, label: t(sector.labelKey) })
  })
  selectedActivityIds.value.forEach((value) => {
    const activity = EVENT_SECTOR_OPTIONS.flatMap((sector) => sector.activities).find(
      (option) => option.id === value,
    )
    if (activity) values.push({ key: `activity:${value}`, label: t(activity.labelKey) })
  })

  const options: Array<[string, boolean, string]> = [
    ['freeOnly', freeOnly.value, t('explore.options.free')],
    ['openWeekendOnly', openWeekendOnly.value, t('explore.options.openWeekend')],
    ['opensLateOnly', opensLateOnly.value, t('explore.options.openLate')],
    ['preReservationOnly', preReservationOnly.value, t('explore.options.preReservation')],
    ['experienceOnly', experienceOnly.value, t('explore.options.experience')],
  ]
  options.forEach(([key, selected, label]) => {
    if (selected) values.push({ key: `option:${key}`, label })
  })

  return values
})

const placeActiveFilters = computed(() => {
  const values: Array<{ key: string; label: string }> = []

  selectedPlaceRegion1.value.forEach((value) =>
    values.push({ key: `placeRegion1:${value}`, label: value }),
  )
  selectedPlaceRegion2.value.forEach((value) =>
    values.push({ key: `placeRegion2:${value}`, label: value }),
  )
  selectedPlaceSectorIds.value.forEach((value) => {
    const sector = EVENT_SECTOR_OPTIONS.find((option) => option.id === value)
    if (sector) values.push({ key: `placeSector:${value}`, label: t(sector.labelKey) })
  })
  selectedPlaceActivityIds.value.forEach((value) => {
    const activity = EVENT_SECTOR_OPTIONS.flatMap((sector) => sector.activities).find(
      (option) => option.id === value,
    )
    if (activity) values.push({ key: `placeActivity:${value}`, label: t(activity.labelKey) })
  })

  const options: Array<[string, boolean, string]> = [
    [
      'hasForeignLang',
      selectedPlaceHasForeignLang.value,
      t('explore.placeFilterOptions.foreignLanguage'),
    ],
    ['hasParking', selectedPlaceHasParking.value, t('explore.placeFilterOptions.parking')],
    ['reservable', selectedPlaceReservable.value, t('explore.placeFilterOptions.reservation')],
    ['takeoutAvailable', selectedPlaceTakeout.value, t('explore.placeFilterOptions.takeout')],
    ['cardPaymentAvailable', selectedPlaceCardPayment.value, t('explore.placeFilterOptions.card')],
    ['smokeFree', selectedPlaceSmokeFree.value, t('explore.placeFilterOptions.smokeFree')],
    ['kidFacility', selectedPlaceKidFacility.value, t('explore.placeFilterOptions.kids')],
    ['hasRestroom', selectedPlaceRestroom.value, t('explore.placeFilterOptions.restroom')],
    ['savedOnly', selectedPlaceSavedOnly.value, t('explore.sort.saved')],
  ]
  options.forEach(([key, selected, label]) => {
    if (selected) values.push({ key: `placeOption:${key}`, label })
  })

  return values
})

watch(
  filters,
  (next) => {
    if (selectedTab.value !== 'events') return

    const query: LocationQueryRaw = {}
    addQueryList(query, 'eventKinds', next.eventKinds)
    addQueryList(query, 'sectorIds', next.sectorIds)
    addQueryList(query, 'activityIds', next.activityIds)
    addQueryList(query, 'region1', next.region1)
    addQueryList(query, 'region2', next.region2)
    addQueryValue(query, 'region2Other', next.region2Other)
    addQueryList(query, 'region3', next.region3)
    addQueryValue(query, 'datePreset', next.datePreset)
    addQueryValue(query, 'startDate', next.startDate)
    addQueryValue(query, 'endDate', next.endDate)
    addQueryValue(query, 'keyword', next.keyword)
    addQueryValue(query, 'sort', next.sort === 'LATEST' ? undefined : next.sort)
    addQueryValue(query, 'freeOnly', next.freeOnly)
    addQueryValue(query, 'openWeekendOnly', next.openWeekendOnly)
    addQueryValue(query, 'opensLateOnly', next.opensLateOnly)
    addQueryValue(query, 'preReservationOnly', next.preReservationOnly)
    addQueryValue(query, 'experienceOnly', next.experienceOnly)

    router.replace({ query }).catch(() => undefined)
  },
  { deep: true },
)

watch(
  placeFilters,
  (next) => {
    if (selectedTab.value !== 'places') return

    const query: LocationQueryRaw = {}
    addQueryList(query, 'placeKinds', next.placeKinds)
    addQueryList(query, 'placeSectorIds', next.sectorIds)
    addQueryList(query, 'placeActivityIds', next.activityIds)
    addQueryList(query, 'placeRegion1', next.region1)
    addQueryList(query, 'placeRegion2', next.region2)
    addQueryValue(query, 'keyword', next.keyword)
    addQueryValue(query, 'placeSort', next.sort === 'LATEST' ? undefined : next.sort)
    addQueryValue(query, 'hasForeignLang', next.hasForeignLang)
    addQueryValue(query, 'hasParking', next.hasParking)
    addQueryValue(query, 'reservable', next.reservable)
    addQueryValue(query, 'takeoutAvailable', next.takeoutAvailable)
    addQueryValue(query, 'cardPaymentAvailable', next.cardPaymentAvailable)
    addQueryValue(query, 'smokeFree', next.smokeFree)
    addQueryValue(query, 'kidFacility', next.kidFacility)
    addQueryValue(query, 'hasRestroom', next.hasRestroom)
    addQueryValue(query, 'savedOnly', next.savedOnly)
    addQueryValue(query, 'placePage', next.page && next.page > 0 ? String(next.page) : undefined)
    router.replace({ query }).catch(() => undefined)
  },
  { deep: true },
)

watch(selectedTab, closeSheet)

function openSheet(kind: ExploreSheetKind): void {
  selectedSheet.value = kind
  if (selectedTab.value === 'events') {
    sheetPreviewFilters.value = { ...filters.value }
    placeSheetPreviewFilters.value = null
  } else if (kind !== 'date') {
    placeSheetPreviewFilters.value = { ...placeFilters.value }
    sheetPreviewFilters.value = null
  }
}

function applySheet(next: EventSearchFilters): void {
  selectedEventKinds.value = next.eventKinds ?? []
  selectedSectorIds.value = next.sectorIds ?? []
  selectedActivityIds.value = next.activityIds ?? []
  selectedRegion1.value = next.region1 ?? []
  selectedRegion2.value = next.region2 ?? []
  selectedRegion2Other.value = next.region2Other ?? false
  selectedRegion3.value = next.region3 ?? []
  datePreset.value = next.datePreset
  startDate.value = next.startDate
  endDate.value = next.endDate
  sort.value = next.sort ?? 'LATEST'
  freeOnly.value = next.freeOnly ?? false
  openWeekendOnly.value = next.openWeekendOnly ?? false
  opensLateOnly.value = next.opensLateOnly ?? false
  preReservationOnly.value = next.preReservationOnly ?? false
  experienceOnly.value = next.experienceOnly ?? false
  selectedSheet.value = null
  sheetPreviewFilters.value = null
}

function applyPlaceSheet(next: PlaceSearchFilters): void {
  selectedPlacePage.value = 0
  selectedPlaceKinds.value = next.placeKinds ?? []
  selectedPlaceSectorIds.value = next.sectorIds ?? []
  selectedPlaceActivityIds.value = next.activityIds ?? []
  selectedPlaceRegion1.value = next.region1 ?? []
  selectedPlaceRegion2.value = next.region2 ?? []
  placeSort.value = next.sort ?? 'LATEST'
  selectedPlaceHasForeignLang.value = next.hasForeignLang ?? false
  selectedPlaceHasParking.value = next.hasParking ?? false
  selectedPlaceReservable.value = next.reservable ?? false
  selectedPlaceTakeout.value = next.takeoutAvailable ?? false
  selectedPlaceCardPayment.value = next.cardPaymentAvailable ?? false
  selectedPlaceSmokeFree.value = next.smokeFree ?? false
  selectedPlaceKidFacility.value = next.kidFacility ?? false
  selectedPlaceRestroom.value = next.hasRestroom ?? false
  selectedPlaceSavedOnly.value = next.savedOnly ?? false
  selectedSheet.value = null
  placeSheetPreviewFilters.value = null
}

function previewSheet(next: EventSearchFilters): void {
  sheetPreviewFilters.value = { ...next, page: 0, size: 20, language: locale.value }
}

function previewPlaceSheet(next: PlaceSearchFilters): void {
  placeSheetPreviewFilters.value = { ...next, page: 0, size: 20, language: locale.value }
}

watch(keyword, () => {
  if (selectedTab.value === 'places') selectedPlacePage.value = 0
})

function goToPreviousPlacePage(): void {
  if (selectedPlacePage.value > 0) selectedPlacePage.value -= 1
}

function goToNextPlacePage(): void {
  if (placeQuery.data.value?.hasNext) selectedPlacePage.value += 1
}

function closeSheet(): void {
  selectedSheet.value = null
  sheetPreviewFilters.value = null
  placeSheetPreviewFilters.value = null
}

function togglePlaceKind(kind: PlaceKind): void {
  selectedPlacePage.value = 0
  selectedPlaceKinds.value = selectedPlaceKinds.value.includes(kind)
    ? selectedPlaceKinds.value.filter((value) => value !== kind)
    : [...selectedPlaceKinds.value, kind]
}

function openPlaceDetail(placeId: number): void {
  void router.push({
    name: 'explore-place-detail',
    params: { placeId },
  })
}

function toggleEventKind(kind: string): void {
  if (!isEventKind(kind)) return
  selectedEventKinds.value = selectedEventKinds.value.includes(kind)
    ? selectedEventKinds.value.filter((value) => value !== kind)
    : [...selectedEventKinds.value, kind]
}

function removeFilter(key: string): void {
  if (key === '*') {
    selectedEventKinds.value = []
    selectedSectorIds.value = []
    selectedActivityIds.value = []
    selectedRegion1.value = []
    selectedRegion2.value = []
    selectedRegion2Other.value = false
    selectedRegion3.value = []
    datePreset.value = undefined
    startDate.value = undefined
    endDate.value = undefined
    keyword.value = ''
    sort.value = 'LATEST'
    freeOnly.value = false
    openWeekendOnly.value = false
    opensLateOnly.value = false
    preReservationOnly.value = false
    experienceOnly.value = false
    return
  }

  if (key.startsWith('date:')) {
    datePreset.value = undefined
    startDate.value = undefined
    endDate.value = undefined
  } else if (key.startsWith('region1:')) {
    selectedRegion1.value = selectedRegion1.value.filter((value) => `region1:${value}` !== key)
  } else if (key.startsWith('region2:')) {
    if (key === 'region2:other') {
      selectedRegion2Other.value = false
    } else {
      selectedRegion2.value = selectedRegion2.value.filter((value) => `region2:${value}` !== key)
    }
  } else if (key.startsWith('region3:')) {
    selectedRegion3.value = selectedRegion3.value.filter((value) => `region3:${value}` !== key)
  } else if (key.startsWith('sector:')) {
    const sectorId = Number(key.slice('sector:'.length))
    selectedSectorIds.value = selectedSectorIds.value.filter((value) => value !== sectorId)
  } else if (key.startsWith('activity:')) {
    const activityId = Number(key.slice('activity:'.length))
    selectedActivityIds.value = selectedActivityIds.value.filter((value) => value !== activityId)
  } else if (key.startsWith('option:')) {
    const option = key.slice('option:'.length)
    if (option === 'freeOnly') freeOnly.value = false
    if (option === 'openWeekendOnly') openWeekendOnly.value = false
    if (option === 'opensLateOnly') opensLateOnly.value = false
    if (option === 'preReservationOnly') preReservationOnly.value = false
    if (option === 'experienceOnly') experienceOnly.value = false
  }
}

function removePlaceFilter(key: string): void {
  selectedPlacePage.value = 0
  if (key === '*') {
    selectedPlaceKinds.value = []
    selectedPlaceSectorIds.value = []
    selectedPlaceActivityIds.value = []
    selectedPlaceRegion1.value = []
    selectedPlaceRegion2.value = []
    placeSort.value = 'LATEST'
    selectedPlaceHasForeignLang.value = false
    selectedPlaceHasParking.value = false
    selectedPlaceReservable.value = false
    selectedPlaceTakeout.value = false
    selectedPlaceCardPayment.value = false
    selectedPlaceSmokeFree.value = false
    selectedPlaceKidFacility.value = false
    selectedPlaceRestroom.value = false
    selectedPlaceSavedOnly.value = false
    return
  }

  if (key.startsWith('placeRegion1:')) {
    selectedPlaceRegion1.value = selectedPlaceRegion1.value.filter(
      (value) => `placeRegion1:${value}` !== key,
    )
  } else if (key.startsWith('placeRegion2:')) {
    selectedPlaceRegion2.value = selectedPlaceRegion2.value.filter(
      (value) => `placeRegion2:${value}` !== key,
    )
  } else if (key.startsWith('placeSector:')) {
    const sectorId = Number(key.slice('placeSector:'.length))
    selectedPlaceSectorIds.value = selectedPlaceSectorIds.value.filter(
      (value) => value !== sectorId,
    )
  } else if (key.startsWith('placeActivity:')) {
    const activityId = Number(key.slice('placeActivity:'.length))
    selectedPlaceActivityIds.value = selectedPlaceActivityIds.value.filter(
      (value) => value !== activityId,
    )
  } else if (key.startsWith('placeOption:')) {
    const option = key.slice('placeOption:'.length)
    if (option === 'hasForeignLang') selectedPlaceHasForeignLang.value = false
    if (option === 'hasParking') selectedPlaceHasParking.value = false
    if (option === 'reservable') selectedPlaceReservable.value = false
    if (option === 'takeoutAvailable') selectedPlaceTakeout.value = false
    if (option === 'cardPaymentAvailable') selectedPlaceCardPayment.value = false
    if (option === 'smokeFree') selectedPlaceSmokeFree.value = false
    if (option === 'kidFacility') selectedPlaceKidFacility.value = false
    if (option === 'hasRestroom') selectedPlaceRestroom.value = false
    if (option === 'savedOnly') selectedPlaceSavedOnly.value = false
  }
}

function readQueryString(key: string): string | undefined {
  const value = route.query[key]
  return Array.isArray(value) ? (value[0] ?? undefined) : (value ?? undefined)
}

function readQueryList(key: string): string[] {
  const value = route.query[key]
  if (Array.isArray(value)) return value.filter((item): item is string => item !== null)
  return value === undefined || value === null ? [] : [value]
}

function readQueryNumberList(key: string): number[] {
  return readQueryList(key)
    .map((value) => Number(value))
    .filter((value) => Number.isInteger(value) && value > 0)
}

function readQueryBoolean(key: string): boolean {
  return readQueryString(key) === 'true'
}

function readQueryPage(key: string): number {
  const value = Number(readQueryString(key))
  return Number.isInteger(value) && value >= 0 ? value : 0
}

function readSort(value: string | undefined): EventSort {
  return value === 'POPULAR' || value === 'ENDING_SOON' ? value : 'LATEST'
}

function readPlaceSort(value: string | undefined): PlaceSort {
  return value === 'POPULAR' ? value : 'LATEST'
}

function isEventKind(value: string): value is EventKind {
  return (EVENT_KINDS as readonly string[]).includes(value)
}

function addQueryValue(
  query: LocationQueryRaw,
  key: string,
  value: string | boolean | undefined,
): void {
  if (value !== undefined && value !== false && value !== '') query[key] = String(value)
}

function addQueryList(
  query: LocationQueryRaw,
  key: string,
  values: readonly (string | number)[] | undefined,
): void {
  if (values !== undefined && values.length > 0) query[key] = values.map(String)
}
</script>

<template>
  <section class="flex min-h-dvh flex-col gap-4 px-screen pt-8 pb-28">
    <header class="flex items-center justify-between gap-4">
      <h1 class="font-display text-screen-title uppercase text-ink-display">
        {{ t('explore.title') }}
      </h1>
      <button
        type="button"
        class="flex size-12 items-center justify-center rounded-pill bg-surface-2 text-ink"
        :aria-label="
          t(selectedTab === 'events' ? 'explore.search.open' : 'explore.search.placeOpen')
        "
        :aria-pressed="searchOpen"
        @click="searchOpen = !searchOpen"
      >
        <IconSearch
          :size="24"
          :stroke-width="1.8"
          aria-hidden="true"
        />
      </button>
    </header>

    <div
      v-if="searchOpen"
      class="flex items-center gap-2 rounded-sm bg-surface-2 px-4"
    >
      <IconSearch
        :size="18"
        class="shrink-0 text-ink-3"
        aria-hidden="true"
      />
      <input
        v-model="keyword"
        type="search"
        class="min-w-0 flex-1 bg-transparent py-3 text-body text-ink outline-none placeholder:text-ink-3"
        :placeholder="
          t(
            selectedTab === 'events'
              ? 'explore.search.placeholder'
              : 'explore.search.placePlaceholder',
          )
        "
        :aria-label="
          t(selectedTab === 'events' ? 'explore.search.label' : 'explore.search.placeLabel')
        "
      />
    </div>

    <ExploreItemTabs
      v-model="selectedTab"
      :events-label="t('explore.tabs.events')"
      :places-label="t('explore.tabs.places')"
      :label="t('explore.tabs.label')"
    />

    <template v-if="selectedTab === 'events'">
      <ExploreFilterBar
        :active-sheet="selectedSheet"
        :event-kind-options="eventKindOptions"
        :active-filters="activeFilters"
        @open="openSheet"
        @remove="removeFilter"
        @toggle-kind="toggleEventKind"
      />

      <div class="flex items-center justify-between gap-4 pt-1">
        <h2 class="text-title-sm text-ink">
          {{ t('explore.resultCount', { count: visibleEventCount }) }}
        </h2>
        <button
          type="button"
          class="flex items-center gap-1 text-body-sm text-ink-2"
          @click="openSheet('sort')"
        >
          {{ sortLabel }}
          <IconChevronDown
            :size="16"
            :stroke-width="1.8"
            aria-hidden="true"
          />
        </button>
      </div>

      <StateLoading v-if="eventQuery.isPending.value" />
      <StateError
        v-else-if="eventQuery.isError.value"
        :description="t('explore.eventListError')"
        @retry="eventQuery.refetch"
      />
      <StateEmpty
        v-else-if="eventList.length === 0"
        :description="t('state.empty.description')"
      />
      <div
        v-else
        class="flex flex-col gap-3"
      >
        <EventCard
          v-for="event in eventList"
          :key="event.itemId"
          :event="event"
          @open="router.push({ name: 'explore-event-detail', params: { eventId: event.itemId } })"
        />
      </div>
    </template>

    <template v-else>
      <PlaceFilterBar
        :active-sheet="selectedSheet === 'date' ? null : selectedSheet"
        :place-kind-options="placeKindOptions"
        :active-filters="placeActiveFilters"
        @open="openSheet"
        @remove="removePlaceFilter"
        @toggle-kind="togglePlaceKind"
      />

      <div class="flex items-center justify-between gap-4 pt-1">
        <h2 class="text-title-sm text-ink">
          {{ t('explore.placeResultCount', { count: visiblePlaceCount }) }}
        </h2>
        <button
          type="button"
          class="flex items-center gap-1 text-body-sm text-ink-2"
          @click="openSheet('sort')"
        >
          {{ placeSortLabel }}
          <IconChevronDown
            :size="16"
            :stroke-width="1.8"
            aria-hidden="true"
          />
        </button>
      </div>

      <StateLoading v-if="placeQuery.isPending.value" />
      <StateError
        v-else-if="placeQuery.isError.value"
        :description="t('explore.placeListError')"
        @retry="placeQuery.refetch"
      />
      <StateEmpty
        v-else-if="placeList.length === 0"
        :description="t('state.empty.description')"
      />
      <div
        v-else
        class="flex flex-col gap-3"
      >
        <PlaceCard
          v-for="place in placeList"
          :key="place.itemId"
          :place="place"
          @open="openPlaceDetail"
        />
      </div>

      <nav
        v-if="placeQuery.data.value && (selectedPlacePage > 0 || placeQuery.data.value.hasNext)"
        class="mt-2 flex items-center justify-between gap-3"
        :aria-label="t('explore.pagination.page', { page: selectedPlacePage + 1 })"
      >
        <button
          type="button"
          class="rounded-pill border border-hairline-2 px-4 py-2 text-body-sm text-ink-2 disabled:opacity-40"
          :disabled="selectedPlacePage === 0 || placeQuery.isFetching.value"
          @click="goToPreviousPlacePage"
        >
          {{ t('explore.pagination.previousPage') }}
        </button>
        <span class="text-caption text-ink-3">
          {{ t('explore.pagination.page', { page: selectedPlacePage + 1 }) }}
        </span>
        <button
          type="button"
          class="rounded-pill border border-hairline-2 px-4 py-2 text-body-sm text-ink-2 disabled:opacity-40"
          :disabled="!placeQuery.data.value?.hasNext || placeQuery.isFetching.value"
          @click="goToNextPlacePage"
        >
          {{ t('explore.pagination.nextPage') }}
        </button>
      </nav>
    </template>

    <ExploreFilterSheet
      v-if="selectedSheet !== null && selectedTab === 'events'"
      :kind="selectedSheet"
      :filters="filters"
      :result-count="sheetResultCount"
      @close="closeSheet"
      @change="previewSheet"
      @apply="applySheet"
    />

    <PlaceFilterSheet
      v-if="selectedSheet !== null && selectedTab === 'places' && selectedSheet !== 'date'"
      :kind="selectedSheet"
      :filters="placeFilters"
      :result-count="placeSheetResultCount"
      @close="closeSheet"
      @change="previewPlaceSheet"
      @apply="applyPlaceSheet"
    />
  </section>
</template>
