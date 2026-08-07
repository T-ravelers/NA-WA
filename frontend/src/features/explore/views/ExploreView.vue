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
import { useEventListQuery } from '../composables/useEventListQuery'
import {
  EVENT_KINDS,
  type EventKind,
  type EventSearchFilters,
  type EventSort,
} from '../model/eventExplore'

type ExploreSheetKind = 'date' | 'region' | 'category' | 'options' | 'sort'

const { locale, t } = useI18n()
const route = useRoute()
const router = useRouter()

const selectedTab = ref<'events' | 'places'>('events')
const selectedSheet = ref<ExploreSheetKind | null>(null)
const searchOpen = ref(false)

const selectedEventKinds = ref<EventKind[]>(readQueryList('eventKinds').filter(isEventKind))
const selectedRegion1 = ref(readQueryList('region1'))
const selectedRegion2 = ref(readQueryList('region2'))
const selectedRegion3 = ref(readQueryList('region3'))
const datePreset = ref(readQueryString('datePreset'))
const startDate = ref(readQueryString('startDate'))
const endDate = ref(readQueryString('endDate'))
const keyword = ref(readQueryString('keyword') ?? '')
const sort = ref<EventSort>(readSort(readQueryString('sort')))
const savedOnly = ref(readQueryBoolean('savedOnly'))
const freeOnly = ref(readQueryBoolean('freeOnly'))
const openWeekendOnly = ref(readQueryBoolean('openWeekendOnly'))
const opensLateOnly = ref(readQueryBoolean('opensLateOnly'))
const preReservationOnly = ref(readQueryBoolean('preReservationOnly'))
const experienceOnly = ref(readQueryBoolean('experienceOnly'))

const filters = computed<EventSearchFilters>(() => ({
  language: locale.value,
  page: 0,
  size: 20,
  sort: sort.value,
  keyword: keyword.value || undefined,
  eventKinds: selectedEventKinds.value.length > 0 ? selectedEventKinds.value : undefined,
  region1: selectedRegion1.value.length > 0 ? selectedRegion1.value : undefined,
  region2: selectedRegion2.value.length > 0 ? selectedRegion2.value : undefined,
  region3: selectedRegion3.value.length > 0 ? selectedRegion3.value : undefined,
  datePreset: datePreset.value,
  startDate: startDate.value,
  endDate: endDate.value,
  savedOnly: savedOnly.value || undefined,
  freeOnly: freeOnly.value || undefined,
  openWeekendOnly: openWeekendOnly.value || undefined,
  opensLateOnly: opensLateOnly.value || undefined,
  preReservationOnly: preReservationOnly.value || undefined,
  experienceOnly: experienceOnly.value || undefined,
}))

const eventQuery = useEventListQuery(filters)
const eventList = computed(() => eventQuery.data.value?.content ?? [])
const totalEvents = computed(() => eventQuery.data.value?.totalElements ?? 0)

const eventKindOptions = computed(() =>
  EVENT_KINDS.map((kind) => ({
    key: kind,
    label: t(`explore.eventKinds.${kind}`),
    selected: selectedEventKinds.value.includes(kind),
  })),
)

const sortLabel = computed(() => t(`explore.sort.${sort.value.toLowerCase()}`))

const activeFilters = computed(() => {
  const values: Array<{ key: string; label: string }> = []

  if (datePreset.value) {
    values.push({ key: 'date:preset', label: t(`explore.datePresets.${datePreset.value}`) })
  } else if (startDate.value || endDate.value) {
    values.push({ key: 'date:range', label: `${startDate.value ?? '…'} – ${endDate.value ?? '…'}` })
  }

  selectedRegion1.value.forEach((value) => values.push({ key: `region1:${value}`, label: value }))
  selectedRegion2.value.forEach((value) => values.push({ key: `region2:${value}`, label: value }))
  selectedRegion3.value.forEach((value) => values.push({ key: `region3:${value}`, label: value }))

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

  if (savedOnly.value) values.push({ key: 'sort:saved', label: t('explore.sort.saved') })
  return values
})

watch(
  filters,
  (next) => {
    const query: LocationQueryRaw = {}
    addQueryList(query, 'eventKinds', next.eventKinds)
    addQueryList(query, 'region1', next.region1)
    addQueryList(query, 'region2', next.region2)
    addQueryList(query, 'region3', next.region3)
    addQueryValue(query, 'datePreset', next.datePreset)
    addQueryValue(query, 'startDate', next.startDate)
    addQueryValue(query, 'endDate', next.endDate)
    addQueryValue(query, 'keyword', next.keyword)
    addQueryValue(query, 'sort', next.sort === 'LATEST' ? undefined : next.sort)
    addQueryValue(query, 'savedOnly', next.savedOnly)
    addQueryValue(query, 'freeOnly', next.freeOnly)
    addQueryValue(query, 'openWeekendOnly', next.openWeekendOnly)
    addQueryValue(query, 'opensLateOnly', next.opensLateOnly)
    addQueryValue(query, 'preReservationOnly', next.preReservationOnly)
    addQueryValue(query, 'experienceOnly', next.experienceOnly)

    router.replace({ query }).catch(() => undefined)
  },
  { deep: true },
)

function openSheet(kind: ExploreSheetKind): void {
  selectedSheet.value = kind
}

function applySheet(next: EventSearchFilters): void {
  selectedEventKinds.value = next.eventKinds ?? []
  selectedRegion1.value = next.region1 ?? []
  selectedRegion2.value = next.region2 ?? []
  selectedRegion3.value = next.region3 ?? []
  datePreset.value = next.datePreset
  startDate.value = next.startDate
  endDate.value = next.endDate
  sort.value = next.sort ?? 'LATEST'
  savedOnly.value = next.savedOnly ?? false
  freeOnly.value = next.freeOnly ?? false
  openWeekendOnly.value = next.openWeekendOnly ?? false
  opensLateOnly.value = next.opensLateOnly ?? false
  preReservationOnly.value = next.preReservationOnly ?? false
  experienceOnly.value = next.experienceOnly ?? false
  selectedSheet.value = null
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
    selectedRegion1.value = []
    selectedRegion2.value = []
    selectedRegion3.value = []
    datePreset.value = undefined
    startDate.value = undefined
    endDate.value = undefined
    keyword.value = ''
    sort.value = 'LATEST'
    savedOnly.value = false
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
    selectedRegion2.value = selectedRegion2.value.filter((value) => `region2:${value}` !== key)
  } else if (key.startsWith('region3:')) {
    selectedRegion3.value = selectedRegion3.value.filter((value) => `region3:${value}` !== key)
  } else if (key.startsWith('option:')) {
    const option = key.slice('option:'.length)
    if (option === 'freeOnly') freeOnly.value = false
    if (option === 'openWeekendOnly') openWeekendOnly.value = false
    if (option === 'opensLateOnly') opensLateOnly.value = false
    if (option === 'preReservationOnly') preReservationOnly.value = false
    if (option === 'experienceOnly') experienceOnly.value = false
  } else if (key === 'sort:saved') {
    savedOnly.value = false
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

function readQueryBoolean(key: string): boolean {
  return readQueryString(key) === 'true'
}

function readSort(value: string | undefined): EventSort {
  return value === 'POPULAR' || value === 'ENDING_SOON' ? value : 'LATEST'
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
        :aria-label="t('explore.search.open')"
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
        :placeholder="t('explore.search.placeholder')"
        :aria-label="t('explore.search.label')"
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
          {{ t('explore.resultCount', { count: totalEvents }) }}
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
        />
      </div>
    </template>

    <StateEmpty
      v-else
      :description="t('explore.placesComingSoon')"
    />

    <ExploreFilterSheet
      v-if="selectedSheet !== null"
      :kind="selectedSheet"
      :filters="filters"
      :result-count="totalEvents"
      @close="selectedSheet = null"
      @apply="applySheet"
    />
  </section>
</template>
