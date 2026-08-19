<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter, type LocationQueryRaw } from 'vue-router'
import { IconChevronDown, IconSearch } from '@tabler/icons-vue'

import IconOrb from '@/shared/ui/IconOrb.vue'
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
import ExplorePagination from '../components/ExplorePagination.vue'
import { useEventListQuery } from '../composables/useEventListQuery'
import { usePlaceListQuery } from '../composables/usePlaceListQuery'
import {
  EVENT_KINDS,
  type EventKind,
  type EventSearchFilters,
  type EventSort,
} from '../model/eventExplore'
import {
  SEOUL_REGION1,
  SEOUL_REGION2_OPTIONS,
  VALID_SEOUL_REGION2_VALUES,
} from '../model/exploreRegions'
import {
  EVENT_SECTOR_OPTIONS,
  PLACE_SECTOR_OPTIONS,
  VALID_EXPLORE_ACTIVITY_IDS,
  VALID_EXPLORE_SECTOR_IDS,
} from '../model/exploreTaxonomy'
import {
  PLACE_KINDS,
  isPlaceKind,
  type PlaceKind,
  type PlaceSearchFilters,
  type PlaceSort,
} from '../model/placeExplore'

type ExploreSheetKind = 'date' | 'region' | 'category' | 'options' | 'sort'
type ExploreTab = 'events' | 'places'

const { locale, t } = useI18n()
const route = useRoute()
const router = useRouter()

const selectedTab = ref<ExploreTab>(readExploreTab(readQueryString('tab')))
const selectedSheet = ref<ExploreSheetKind | null>(null)
const searchOpen = ref(false)

const selectedEventKinds = ref<EventKind[]>(readQueryList('eventKinds').filter(isEventKind))
const selectedEventSectorIds = ref(readValidTaxonomyIds('eventSectorIds', VALID_EXPLORE_SECTOR_IDS))
const selectedEventActivityIds = ref(
  readValidTaxonomyIds('eventActivityIds', VALID_EXPLORE_ACTIVITY_IDS),
)
const selectedRegion1 = ref(readRegion1List('eventRegion1', 'region1'))
const selectedRegion2 = ref(readRegion2List('eventRegion2', selectedRegion1.value))
const selectedRegion2Other = ref(readQueryBoolean('region2Other'))
const selectedRegion3 = ref(readQueryList('region3'))
const datePreset = ref(readQueryString('datePreset'))
const startDate = ref(readQueryString('startDate'))
const endDate = ref(readQueryString('endDate'))
const selectedEventPage = ref(readQueryPage('eventPage'))
const eventKeywordInput = ref(readQueryString('eventKeyword') ?? '')
const eventKeyword = ref(eventKeywordInput.value.trim())
const sort = ref<EventSort>(readSort(readQueryString('sort')))
const freeOnly = ref(readQueryBoolean('freeOnly'))
const openWeekendOnly = ref(readQueryBoolean('openWeekendOnly'))
const opensLateOnly = ref(readQueryBoolean('opensLateOnly'))
const preReservationOnly = ref(readQueryBoolean('preReservationOnly'))
const experienceOnly = ref(readQueryBoolean('experienceOnly'))
const selectedPlaceKinds = ref<PlaceKind[]>(readQueryList('placeKinds').filter(isPlaceKind))
const selectedPlaceSectorIds = ref(readValidTaxonomyIds('placeSectorIds', VALID_EXPLORE_SECTOR_IDS))
const selectedPlaceActivityIds = ref(
  readValidTaxonomyIds('placeActivityIds', VALID_EXPLORE_ACTIVITY_IDS),
)
const selectedPlaceRegion1 = ref(readRegion1List('placeRegion1'))
const selectedPlaceRegion2 = ref(readRegion2List('placeRegion2', selectedPlaceRegion1.value))
const selectedPlaceRegion2Other = ref(readQueryBoolean('placeRegion2Other'))
const placeKeywordInput = ref(readQueryString('placeKeyword') ?? '')
const placeKeyword = ref(placeKeywordInput.value.trim())
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
const hydratingEventQuery = ref(false)
const hydratingPlaceQuery = ref(false)
const sheetPreviewFilters = ref<EventSearchFilters | null>(null)
const placeSheetPreviewFilters = ref<PlaceSearchFilters | null>(null)
const searchKeyword = computed({
  get: () => (selectedTab.value === 'events' ? eventKeywordInput.value : placeKeywordInput.value),
  set: (value: string) => {
    if (selectedTab.value === 'events') eventKeywordInput.value = value
    else placeKeywordInput.value = value
  },
})

const filters = computed<EventSearchFilters>(() => ({
  language: locale.value,
  page: selectedEventPage.value,
  size: 20,
  sort: sort.value,
  keyword: eventKeyword.value || undefined,
  eventKinds: selectedEventKinds.value.length > 0 ? selectedEventKinds.value : undefined,
  sectorIds: selectedEventSectorIds.value.length > 0 ? selectedEventSectorIds.value : undefined,
  activityIds:
    selectedEventActivityIds.value.length > 0 ? selectedEventActivityIds.value : undefined,
  region1: selectedRegion1.value.length > 0 ? selectedRegion1.value : undefined,
  region2: selectedRegion2.value.length > 0 ? selectedRegion2.value : undefined,
  region2Other: selectedRegion2Other.value || undefined,
  region3: selectedRegion3.value.length > 0 ? selectedRegion3.value : undefined,
  datePreset: datePreset.value,
  startDate: startDate.value,
  // 단일 날짜 선택은 시작=종료의 하루짜리 기간으로 보낸다. 시작일만 보내면
  // 백엔드 겹침 조건에 상한이 없어 "그 날짜 이후 유효한 이벤트 전부"가 나온다.
  endDate: endDate.value ?? startDate.value,
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
  keyword: placeKeyword.value || undefined,
  placeKinds: selectedPlaceKinds.value.length > 0 ? selectedPlaceKinds.value : undefined,
  sectorIds: selectedPlaceSectorIds.value.length > 0 ? selectedPlaceSectorIds.value : undefined,
  activityIds:
    selectedPlaceActivityIds.value.length > 0 ? selectedPlaceActivityIds.value : undefined,
  region1: selectedPlaceRegion1.value.length > 0 ? selectedPlaceRegion1.value : undefined,
  region2: selectedPlaceRegion2.value.length > 0 ? selectedPlaceRegion2.value : undefined,
  region2Other: selectedPlaceRegion2Other.value || undefined,
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
  { enabled: () => selectedTab.value === 'events' && selectedSheet.value !== null },
)
const placeSheetPreviewQuery = usePlaceListQuery(
  computed(() => placeSheetPreviewFilters.value ?? placeFilters.value),
  { enabled: () => selectedTab.value === 'places' && selectedSheet.value !== null },
)
const eventList = computed(() => eventQuery.data.value?.content ?? [])
const totalEventElements = computed(() => eventQuery.data.value?.totalElements ?? 0)
const placeList = computed(() => placeQuery.data.value?.content ?? [])
const totalPlaceElements = computed(() => placeQuery.data.value?.totalElements ?? 0)
const sheetResultCount = computed(
  () => sheetPreviewQuery.data.value?.totalElements ?? totalEventElements.value,
)
const placeSheetResultCount = computed(
  () =>
    placeSheetPreviewQuery.data.value?.totalElements ?? placeQuery.data.value?.totalElements ?? 0,
)

watch(
  () => eventQuery.data.value?.totalPages,
  (totalPages) => {
    if (totalPages === undefined || selectedEventPage.value < totalPages) return
    selectedEventPage.value = Math.max(0, totalPages - 1)
  },
)

watch(
  () => placeQuery.data.value?.totalPages,
  (totalPages) => {
    if (totalPages === undefined || selectedPlacePage.value < totalPages) return
    selectedPlacePage.value = Math.max(0, totalPages - 1)
  },
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

  selectedRegion1.value.forEach((value) =>
    values.push({ key: `region1:${value}`, label: region1Label(value) }),
  )
  selectedRegion2.value.forEach((value) =>
    values.push({ key: `region2:${value}`, label: region2Label(value) }),
  )
  if (selectedRegion2Other.value) {
    values.push({ key: 'region2:other', label: t('explore.areas.other') })
  }
  selectedRegion3.value.forEach((value) => values.push({ key: `region3:${value}`, label: value }))

  selectedEventSectorIds.value.forEach((value) => {
    const sector = EVENT_SECTOR_OPTIONS.find((option) => option.id === value)
    if (sector) values.push({ key: `sector:${value}`, label: t(sector.labelKey) })
  })
  selectedEventActivityIds.value.forEach((value) => {
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
    values.push({ key: `placeRegion1:${value}`, label: region1Label(value) }),
  )
  selectedPlaceRegion2.value.forEach((value) =>
    values.push({ key: `placeRegion2:${value}`, label: region2Label(value) }),
  )
  if (selectedPlaceRegion2Other.value) {
    values.push({ key: 'placeRegion2Other', label: t('explore.areas.other') })
  }
  selectedPlaceSectorIds.value.forEach((value) => {
    const sector = PLACE_SECTOR_OPTIONS.find((option) => option.id === value)
    if (sector) values.push({ key: `placeSector:${value}`, label: t(sector.labelKey) })
  })
  selectedPlaceActivityIds.value.forEach((value) => {
    const activity = PLACE_SECTOR_OPTIONS.flatMap((sector) => sector.activities).find(
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

function buildEventQuery(next: EventSearchFilters): LocationQueryRaw {
  const query: LocationQueryRaw = {}
  addQueryList(query, 'eventKinds', next.eventKinds)
  addQueryList(query, 'eventSectorIds', next.sectorIds)
  addQueryList(query, 'eventActivityIds', next.activityIds)
  addRegion1Query(query, 'eventRegion1', next.region1)
  addQueryList(query, 'eventRegion2', next.region2)
  addQueryValue(query, 'region2Other', next.region2Other)
  addQueryList(query, 'region3', next.region3)
  addQueryValue(query, 'datePreset', next.datePreset)
  addQueryValue(query, 'startDate', next.startDate)
  addQueryValue(query, 'endDate', next.endDate)
  addQueryValue(query, 'eventKeyword', next.keyword)
  addQueryValue(query, 'eventPage', next.page && next.page > 0 ? String(next.page) : undefined)
  addQueryValue(query, 'sort', next.sort === 'NEWEST' ? undefined : next.sort)
  addQueryValue(query, 'freeOnly', next.freeOnly)
  addQueryValue(query, 'openWeekendOnly', next.openWeekendOnly)
  addQueryValue(query, 'opensLateOnly', next.opensLateOnly)
  addQueryValue(query, 'preReservationOnly', next.preReservationOnly)
  addQueryValue(query, 'experienceOnly', next.experienceOnly)
  return query
}

function buildPlaceQuery(next: PlaceSearchFilters): LocationQueryRaw {
  const query: LocationQueryRaw = { tab: 'places' }
  addQueryList(query, 'placeKinds', next.placeKinds)
  addQueryList(query, 'placeSectorIds', next.sectorIds)
  addQueryList(query, 'placeActivityIds', next.activityIds)
  addRegion1Query(query, 'placeRegion1', next.region1)
  addQueryList(query, 'placeRegion2', next.region2)
  addQueryValue(query, 'placeRegion2Other', next.region2Other)
  addQueryValue(query, 'placeKeyword', next.keyword)
  addQueryValue(query, 'placeSort', next.sort === 'POPULAR' ? undefined : next.sort)
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
  return query
}

watch(
  filters,
  (next) => {
    if (selectedTab.value !== 'events' || hydratingEventQuery.value) return
    router.replace({ query: buildEventQuery(next) }).catch(() => undefined)
  },
  { deep: true },
)

watch(
  placeFilters,
  (next) => {
    if (selectedTab.value !== 'places' || hydratingPlaceQuery.value) return
    router.replace({ query: buildPlaceQuery(next) }).catch(() => undefined)
  },
  { deep: true },
)

watch(selectedTab, (next, previous) => {
  closeSheet()
  if (next === previous) return

  const query =
    next === 'places' ? buildPlaceQuery(placeFilters.value) : buildEventQuery(filters.value)
  router.push({ query }).catch(() => undefined)
})

watch(
  () => route.query,
  async (query) => {
    const tabValue = typeof query.tab === 'string' ? query.tab : undefined
    const nextTab = readExploreTab(tabValue)
    if (selectedTab.value !== nextTab) selectedTab.value = nextTab
    if (nextTab === 'events') {
      hydratingEventQuery.value = true
      selectedEventKinds.value = readQueryList('eventKinds').filter(isEventKind)
      selectedEventSectorIds.value = readValidTaxonomyIds(
        'eventSectorIds',
        VALID_EXPLORE_SECTOR_IDS,
      )
      selectedEventActivityIds.value = readValidTaxonomyIds(
        'eventActivityIds',
        VALID_EXPLORE_ACTIVITY_IDS,
      )
      selectedRegion1.value = readRegion1List('eventRegion1', 'region1')
      selectedRegion2.value = readRegion2List('eventRegion2', selectedRegion1.value)
      selectedRegion2Other.value = readQueryBoolean('region2Other')
      selectedRegion3.value = readQueryList('region3')
      datePreset.value = readQueryString('datePreset')
      startDate.value = readQueryString('startDate')
      endDate.value = readQueryString('endDate')
      selectedEventPage.value = readQueryPage('eventPage')
      eventKeywordInput.value = readQueryString('eventKeyword') ?? ''
      eventKeyword.value = eventKeywordInput.value.trim()
      sort.value = readSort(readQueryString('sort'))
      freeOnly.value = readQueryBoolean('freeOnly')
      openWeekendOnly.value = readQueryBoolean('openWeekendOnly')
      opensLateOnly.value = readQueryBoolean('opensLateOnly')
      preReservationOnly.value = readQueryBoolean('preReservationOnly')
      experienceOnly.value = readQueryBoolean('experienceOnly')
      await nextTick()
      hydratingEventQuery.value = false
      sanitizeExploreQuery('events')
      return
    }

    hydratingPlaceQuery.value = true
    selectedPlaceKinds.value = readQueryList('placeKinds').filter(isPlaceKind)
    selectedPlaceSectorIds.value = readValidTaxonomyIds('placeSectorIds', VALID_EXPLORE_SECTOR_IDS)
    selectedPlaceActivityIds.value = readValidTaxonomyIds(
      'placeActivityIds',
      VALID_EXPLORE_ACTIVITY_IDS,
    )
    selectedPlaceRegion1.value = readRegion1List('placeRegion1')
    selectedPlaceRegion2.value = readRegion2List('placeRegion2', selectedPlaceRegion1.value)
    selectedPlaceRegion2Other.value = readQueryBoolean('placeRegion2Other')
    selectedPlaceHasForeignLang.value = readQueryBoolean('hasForeignLang')
    selectedPlaceHasParking.value = readQueryBoolean('hasParking')
    selectedPlaceReservable.value = readQueryBoolean('reservable')
    selectedPlaceTakeout.value = readQueryBoolean('takeoutAvailable')
    selectedPlaceCardPayment.value = readQueryBoolean('cardPaymentAvailable')
    selectedPlaceSmokeFree.value = readQueryBoolean('smokeFree')
    selectedPlaceKidFacility.value = readQueryBoolean('kidFacility')
    selectedPlaceRestroom.value = readQueryBoolean('hasRestroom')
    selectedPlaceSavedOnly.value = readQueryBoolean('savedOnly')
    selectedPlacePage.value = readQueryPage('placePage')
    placeSort.value = readPlaceSort(readQueryString('placeSort'))
    placeKeywordInput.value = readQueryString('placeKeyword') ?? ''
    placeKeyword.value = placeKeywordInput.value.trim()
    await nextTick()
    hydratingPlaceQuery.value = false
    sanitizeExploreQuery('places')
  },
  { deep: true, immediate: true },
)

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
  const applyingRegion = selectedSheet.value === 'region'
  selectedEventKinds.value = next.eventKinds ?? []
  selectedEventPage.value = 0
  selectedEventSectorIds.value = next.sectorIds ?? []
  selectedEventActivityIds.value = next.activityIds ?? []
  selectedRegion1.value = applyingRegion ? [SEOUL_REGION1] : (next.region1 ?? [SEOUL_REGION1])
  selectedRegion2.value =
    selectedRegion1.value.length === 1 && selectedRegion1.value[0] === SEOUL_REGION1
      ? (next.region2 ?? []).filter((value) => VALID_SEOUL_REGION2_VALUES.has(value))
      : (next.region2 ?? [])
  selectedRegion2Other.value = next.region2Other ?? false
  selectedRegion3.value = next.region3 ?? []
  datePreset.value = next.datePreset
  startDate.value = next.startDate
  endDate.value = next.endDate
  sort.value = next.sort ?? 'NEWEST'
  freeOnly.value = next.freeOnly ?? false
  openWeekendOnly.value = next.openWeekendOnly ?? false
  opensLateOnly.value = next.opensLateOnly ?? false
  preReservationOnly.value = next.preReservationOnly ?? false
  experienceOnly.value = next.experienceOnly ?? false
  selectedSheet.value = null
  sheetPreviewFilters.value = null
}

function applyPlaceSheet(next: PlaceSearchFilters): void {
  const applyingRegion = selectedSheet.value === 'region'
  selectedPlacePage.value = 0
  selectedPlaceKinds.value = next.placeKinds ?? []
  selectedPlaceSectorIds.value = next.sectorIds ?? []
  selectedPlaceActivityIds.value = next.activityIds ?? []
  selectedPlaceRegion1.value = applyingRegion ? [SEOUL_REGION1] : (next.region1 ?? [SEOUL_REGION1])
  selectedPlaceRegion2.value =
    selectedPlaceRegion1.value.length === 1 && selectedPlaceRegion1.value[0] === SEOUL_REGION1
      ? (next.region2 ?? []).filter((value) => VALID_SEOUL_REGION2_VALUES.has(value))
      : (next.region2 ?? [])
  selectedPlaceRegion2Other.value = next.region2Other ?? false
  placeSort.value = next.sort ?? 'POPULAR'
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

let eventSearchTimer: ReturnType<typeof setTimeout> | undefined
let placeSearchTimer: ReturnType<typeof setTimeout> | undefined
let eventPreviewTimer: ReturnType<typeof setTimeout> | undefined
let placePreviewTimer: ReturnType<typeof setTimeout> | undefined

function previewSheet(next: EventSearchFilters): void {
  clearTimeout(eventPreviewTimer)
  eventPreviewTimer = setTimeout(() => {
    sheetPreviewFilters.value = { ...next, page: 0, size: 20, language: locale.value }
  }, 250)
}

function previewPlaceSheet(next: PlaceSearchFilters): void {
  clearTimeout(placePreviewTimer)
  placePreviewTimer = setTimeout(() => {
    placeSheetPreviewFilters.value = { ...next, page: 0, size: 20, language: locale.value }
  }, 250)
}

watch(eventKeywordInput, (value) => {
  clearTimeout(eventSearchTimer)
  eventSearchTimer = setTimeout(() => {
    const next = value.trim()
    if (eventKeyword.value === next) return
    eventKeyword.value = next
    selectedEventPage.value = 0
  }, 300)
})

watch(placeKeywordInput, (value) => {
  clearTimeout(placeSearchTimer)
  placeSearchTimer = setTimeout(() => {
    const next = value.trim()
    if (placeKeyword.value === next) return
    placeKeyword.value = next
    selectedPlacePage.value = 0
  }, 300)
})

function changePage(target: 'events' | 'places', page: number): void {
  window.scrollTo({
    top: 0,
    behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth',
  })
  if (target === 'events') selectedEventPage.value = page
  else selectedPlacePage.value = page
}

function closeSheet(): void {
  clearTimeout(eventPreviewTimer)
  clearTimeout(placePreviewTimer)
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
  selectedEventPage.value = 0
  selectedEventKinds.value = selectedEventKinds.value.includes(kind)
    ? selectedEventKinds.value.filter((value) => value !== kind)
    : [...selectedEventKinds.value, kind]
}

function removeFilter(key: string): void {
  if (key === '*') {
    selectedEventPage.value = 0
    selectedEventKinds.value = []
    selectedEventSectorIds.value = []
    selectedEventActivityIds.value = []
    selectedRegion1.value = [SEOUL_REGION1]
    selectedRegion2.value = []
    selectedRegion2Other.value = false
    selectedRegion3.value = []
    datePreset.value = undefined
    startDate.value = undefined
    endDate.value = undefined
    eventKeywordInput.value = ''
    eventKeyword.value = ''
    sort.value = 'NEWEST'
    freeOnly.value = false
    openWeekendOnly.value = false
    opensLateOnly.value = false
    preReservationOnly.value = false
    experienceOnly.value = false
    return
  }

  selectedEventPage.value = 0

  if (key.startsWith('date:')) {
    datePreset.value = undefined
    startDate.value = undefined
    endDate.value = undefined
  } else if (key.startsWith('region1:')) {
    selectedRegion1.value = [SEOUL_REGION1]
    selectedRegion2.value = []
    selectedRegion2Other.value = false
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
    selectedEventSectorIds.value = selectedEventSectorIds.value.filter(
      (value) => value !== sectorId,
    )
  } else if (key.startsWith('activity:')) {
    const activityId = Number(key.slice('activity:'.length))
    selectedEventActivityIds.value = selectedEventActivityIds.value.filter(
      (value) => value !== activityId,
    )
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
    selectedPlaceRegion1.value = [SEOUL_REGION1]
    selectedPlaceRegion2.value = []
    selectedPlaceRegion2Other.value = false
    placeSort.value = 'POPULAR'
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
    selectedPlaceRegion1.value = [SEOUL_REGION1]
    selectedPlaceRegion2.value = []
  } else if (key.startsWith('placeRegion2:')) {
    selectedPlaceRegion2.value = selectedPlaceRegion2.value.filter(
      (value) => `placeRegion2:${value}` !== key,
    )
  } else if (key === 'placeRegion2Other') {
    selectedPlaceRegion2Other.value = false
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

function readExploreTab(value: string | undefined): ExploreTab {
  return value === 'places' ? 'places' : 'events'
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

function readValidTaxonomyIds(key: string, validIds: ReadonlySet<number>): number[] {
  return readQueryNumberList(key).filter((value) => validIds.has(value))
}

function readRegion1List(key: string, legacyKey?: string): string[] {
  const values = readQueryList(key)
  const legacyValues = values.length === 0 && legacyKey ? readQueryList(legacyKey) : []
  const selected = values.length > 0 ? values : legacyValues
  return selected.length > 0 ? selected : [SEOUL_REGION1]
}

function readRegion2List(key: string, region1: string[]): string[] {
  const values = readQueryList(key)
  if (region1.length === 1 && region1[0] === SEOUL_REGION1) {
    return values.filter((value) => VALID_SEOUL_REGION2_VALUES.has(value))
  }
  return values
}

function region1Label(value: string): string {
  return value === SEOUL_REGION1 ? t('explore.regions.seoul') : value
}

function region2Label(value: string): string {
  const option = SEOUL_REGION2_OPTIONS.find((candidate) => candidate.apiValue === value)
  return option ? t(option.labelKey) : value
}

function sanitizeExploreQuery(tab: ExploreTab): void {
  const query: LocationQueryRaw = { ...route.query }
  const before = JSON.stringify(query)

  delete query.sectorIds
  delete query.activityIds
  delete query.keyword

  if (tab === 'events') {
    setSanitizedQueryList(query, 'eventSectorIds', selectedEventSectorIds.value)
    setSanitizedQueryList(query, 'eventActivityIds', selectedEventActivityIds.value)
    setRegion1Query(query, 'eventRegion1', selectedRegion1.value)
    setSanitizedQueryList(query, 'eventRegion2', selectedRegion2.value)
  } else {
    setSanitizedQueryList(query, 'placeSectorIds', selectedPlaceSectorIds.value)
    setSanitizedQueryList(query, 'placeActivityIds', selectedPlaceActivityIds.value)
    setRegion1Query(query, 'placeRegion1', selectedPlaceRegion1.value)
    setSanitizedQueryList(query, 'placeRegion2', selectedPlaceRegion2.value)
  }

  if (before !== JSON.stringify(query)) {
    router.replace({ query }).catch(() => undefined)
  }
}

function addRegion1Query(
  query: LocationQueryRaw,
  key: string,
  values: readonly string[] | undefined,
): void {
  if (values && (values.length !== 1 || values[0] !== SEOUL_REGION1)) {
    query[key] = [...values]
  }
}

function setRegion1Query(query: LocationQueryRaw, key: string, values: string[]): void {
  if (values.length === 1 && values[0] === SEOUL_REGION1) {
    delete query[key]
  } else {
    query[key] = values
  }
}

function setSanitizedQueryList(
  query: LocationQueryRaw,
  key: string,
  values: number[] | string[],
): void {
  if (values.length === 0) {
    delete query[key]
  } else {
    query[key] = values.map(String)
  }
}

function readQueryBoolean(key: string): boolean {
  return readQueryString(key) === 'true'
}

function readQueryPage(key: string): number {
  const value = Number(readQueryString(key))
  return Number.isInteger(value) && value >= 0 ? value : 0
}

// 개명 전에 만들어진 URL이 남아 있을 수 있어 레거시 'LATEST'는 'NEWEST'로
// 해석한다. 기본값 처리에 맡기면 기본 정렬이 다른 탭에서 의도가 뒤집힌다.
function readSort(value: string | undefined): EventSort {
  return value === 'POPULAR' || value === 'ENDING_SOON' ? value : 'NEWEST'
}

function readPlaceSort(value: string | undefined): PlaceSort {
  if (value === 'NEWEST' || value === 'LATEST') return 'NEWEST'
  return 'POPULAR'
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
      <IconOrb
        :label="t(selectedTab === 'events' ? 'explore.search.open' : 'explore.search.placeOpen')"
        size="lg"
        variant="surface"
        :pressed="searchOpen"
        @click="searchOpen = !searchOpen"
      >
        <IconSearch
          :size="24"
          :stroke-width="1.8"
          aria-hidden="true"
        />
      </IconOrb>
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
        v-model="searchKeyword"
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
          {{ t('explore.resultCount', { count: totalEventElements }) }}
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
      <ExplorePagination
        :page="selectedEventPage"
        :total-pages="eventQuery.data.value?.totalPages ?? 0"
        :loading="eventQuery.isFetching.value"
        @change="changePage('events', $event)"
      />
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
          {{ t('explore.placeResultCount', { count: totalPlaceElements }) }}
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

      <ExplorePagination
        :page="selectedPlacePage"
        :total-pages="placeQuery.data.value?.totalPages ?? 0"
        :loading="placeQuery.isFetching.value"
        @change="changePage('places', $event)"
      />
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
