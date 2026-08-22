<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import {
  IconArrowLeft,
  IconChevronLeft,
  IconChevronRight,
  IconHeart,
  IconMapPin,
  IconShare2,
} from '@tabler/icons-vue'

import {
  buildGoogleMapsSearchUrl,
  buildGoogleMapsTransitRouteUrl,
  buildNaverMapPlaceUrl,
  buildNaverMapTransitRouteUrl,
  openMapAppUrl,
} from '@/shared/lib/mapLink'
import { vFitTextGroup } from '@/shared/lib/fitText'
import AppBadge from '@/shared/ui/AppBadge.vue'
import AppButton from '@/shared/ui/AppButton.vue'
import AppCard from '@/shared/ui/AppCard.vue'
import CategoryDot from '@/shared/ui/CategoryDot.vue'
import IconOrb from '@/shared/ui/IconOrb.vue'
import ImagePlaceholder from '@/shared/ui/ImagePlaceholder.vue'
import StateError from '@/shared/ui/StateError.vue'
import StateLoading from '@/shared/ui/StateLoading.vue'
import type { Category } from '@/shared/ui/category'

import JourneyDateSheet from '../components/JourneyDateSheet.vue'
import JourneySelectSheet from '../components/JourneySelectSheet.vue'
import { usePlaceDetailQuery } from '../composables/usePlaceDetailQuery'
import { useExploreItemLikeMutation } from '../composables/useExploreItemLikeMutation'
import { useExploreReturnContextStore } from '../model/exploreReturnContext'
import { useExploreJourneyIntegration } from '../model/journeyIntegration'
import { journeyAddErrorMessageKey } from '../model/journeyAddErrors'
import { intersectItemJourneyPeriod } from '../model/journeyPeriod'
import { findExploreRegionLabelKey } from '../model/exploreRegions'
import { normalizePlaceKind, type PlaceKind } from '../model/placeExplore'
import { toClosedDays, toDetailEntries } from '../model/placeDetail'

const route = useRoute()
const router = useRouter()
const i18n = useI18n()
const { locale, t } = i18n
const hasMessage = (key: string): boolean => i18n.te(key)
const { addJourneyItem, parseJourneyRouteQuery, useJourneyListQuery } =
  useExploreJourneyIntegration()
const returnContext = useExploreReturnContextStore()

const placeId = computed(() => String(route.params.placeId ?? ''))
const placeQuery = usePlaceDetailQuery(placeId, locale)
const place = computed(() => placeQuery.data.value)
const likeMutation = useExploreItemLikeMutation()
const saved = computed(() => place.value?.saved ?? false)

function toggleSaved(): void {
  const current = place.value
  if (!current || likeMutation.isPending.value) return
  likeMutation.mutate({ itemId: current.itemId, saved: !current.saved })
}

const selectedImage = ref(0)
const shared = ref(false)
const journeySelectSheetOpen = ref(false)
const journeyDateSheetOpen = ref(false)
const journeyDate = ref<string | null>(null)
const selectedJourneyId = ref<number | null>(null)
const journeyAdded = ref(false)
const journeyAddPending = ref(false)
/** 담기 실패 문구의 i18n key. 원인별로 다른 key가 들어온다. */
const journeyAddError = ref<string | null>(null)

const imageUrls = computed(() => place.value?.imageUrls ?? [])
const currentImage = computed(() => imageUrls.value[selectedImage.value])

const normalizedKind = computed<PlaceKind>(() => normalizePlaceKind(place.value?.placeKind))
const kindLabel = computed(() => t(`explore.placeKinds.${normalizedKind.value}`))

const category = computed<Category>(() => {
  if (normalizedKind.value === 'BEAUTY') return 'beauty'
  if (normalizedKind.value === 'MARKET') return 'shopping'
  if (normalizedKind.value === 'RESTAURANT' || normalizedKind.value === 'CAFE') return 'food'
  return 'show'
})

const categoryLabel = computed(() => t(`explore.categories.${category.value}`))

const regionLabel = computed(() =>
  [place.value?.region1, place.value?.region2, place.value?.region3]
    .filter((value): value is string => Boolean(value))
    .map((value) => {
      const labelKey = findExploreRegionLabelKey(value)
      return labelKey ? t(labelKey) : value
    })
    .join(' · '),
)

const addressLabel = computed(() =>
  [place.value?.addressRoad, place.value?.addressDetail].filter(Boolean).join(' '),
)

const locationLabel = computed(() =>
  [regionLabel.value, addressLabel.value].filter(Boolean).join(' · '),
)

const mapSearchUrl = computed(() =>
  buildGoogleMapsSearchUrl(place.value?.latitude, place.value?.longitude),
)
const mapTransitRouteUrl = computed(() =>
  buildGoogleMapsTransitRouteUrl(place.value?.latitude, place.value?.longitude),
)
const naverPlaceUrl = computed(() =>
  buildNaverMapPlaceUrl(place.value?.latitude, place.value?.longitude, place.value?.name ?? ''),
)
const naverRouteUrl = computed(() =>
  buildNaverMapTransitRouteUrl(
    place.value?.latitude,
    place.value?.longitude,
    place.value?.name ?? '',
  ),
)

const hours = computed(() => (place.value ? toDetailEntries(place.value.openingHours) : []))
const closedDays = computed(() => (place.value ? toClosedDays(place.value.closedDays) : ''))

/**
 * 영업시간 한 줄을 적는다.
 *
 * 수집한 영업시간은 대부분 `{ raw: '12:00 ~ 22:00' }` 한 칸짜리 객체다. `raw`는
 * 크롤러가 붙인 키 이름이라 화면 라벨이 아니고, 행에는 이미 "Hours"가 적혀 있다.
 * 그대로 찍으면 `raw: 12:00 ~ 22:00`이 된다. 문자열로 온 값에 우리가 붙이는
 * `hours`도 같은 이유로 감춘다. Event 상세도 같은 규칙이다.
 */
const SYNTHETIC_HOURS_LABELS = new Set(['raw', 'hours'])

function formatHoursEntry(entry: { label: string; value: string }): string {
  return SYNTHETIC_HOURS_LABELS.has(entry.label.toLowerCase())
    ? entry.value
    : `${entry.label}: ${entry.value}`
}

const detailRows = computed(() => {
  const current = place.value
  if (!current) return []

  const rows: Array<{ label: string; value: string }> = []
  if (hours.value.length > 0) {
    rows.push({
      label: t('explore.placeDetail.hours'),
      value: hours.value.map(formatHoursEntry).join('\n'),
    })
  }
  if (closedDays.value) {
    rows.push({ label: t('explore.placeDetail.closed'), value: closedDays.value })
  }
  if (addressLabel.value) {
    rows.push({ label: t('explore.placeDetail.address'), value: addressLabel.value })
  }
  if (current.tel) {
    rows.push({ label: t('explore.placeDetail.phone'), value: current.tel })
  }
  if (current.sourceUrl) {
    rows.push({ label: t('explore.placeDetail.homepage'), value: current.sourceUrl })
  }

  return rows
})

const optionBadges = computed(() => {
  const current = place.value
  if (!current) return []

  const options = [
    ['hasForeignLang', current.hasForeignLang, t('explore.placeDetail.options.foreignLanguage')],
    ['hasParking', current.hasParking, t('explore.placeDetail.options.parking')],
    ['reservable', current.reservable, t('explore.placeDetail.options.reservation')],
    ['takeoutAvailable', current.takeoutAvailable, t('explore.placeDetail.options.takeout')],
    ['cardPaymentAvailable', current.cardPaymentAvailable, t('explore.placeDetail.options.card')],
    ['smokeFree', current.smokeFree, t('explore.placeDetail.options.smokeFree')],
    ['kidFacility', current.kidFacility, t('explore.placeDetail.options.kids')],
    ['hasRestroom', current.hasRestroom, t('explore.placeDetail.options.restroom')],
  ] as const

  return options
    .filter(([, selected]) => selected === true)
    .map(([key, , label]) => ({ key, label }))
})

/**
 * 대표 메뉴는 한 문자열로 오고 구분자가 출처마다 다르다. 수집한 1,485건 중
 * 1,441건이 `/`, 32건이 쉼표, 9건이 가운뎃점을 쓴다. `/`를 빼면 문장 전체가 칩
 * 하나에 들어가 "A / B / C"가 통째로 붙어 나온다.
 */
const menuItems = computed(() => {
  const value = place.value?.menuSummary
  if (!value) return []

  return value
    .split(/\n|\s*·\s*|\s*,\s*|\s*\/\s*/)
    .map((item) => item.trim())
    .filter(Boolean)
})

const activeJourneyId = computed(
  () => parseJourneyRouteQuery(route.query.journeyId) ?? returnContext.journeyId,
)
const journeyListQuery = useJourneyListQuery(journeySelectSheetOpen)
const journeys = computed(() => journeyListQuery.data.value ?? [])

/**
 * Place는 운영 기간이라는 개념이 없다. 그래서 담을 수 있는 날은 여정 기간 전체이며,
 * 여정을 고르기 전까지는 알 수 없다.
 *
 * 예전에는 날짜 시트에 `isPermanent: true`를 넘겨 **모든 날짜**를 열었다. 여정 기간
 * 밖까지 열려 확정한 뒤에야 `JOURNEY-007`로 실패했다.
 */
const itemPeriod = { startDate: null, endDate: null }

const selectedJourney = computed(
  () => journeys.value.find((journey) => journey.tripId === selectedJourneyId.value) ?? null,
)

const journeyDateRange = computed(() => {
  const journey = selectedJourney.value
  return journey === null ? null : intersectItemJourneyPeriod(itemPeriod, journey)
})

watch(imageUrls, () => {
  selectedImage.value = 0
})

function showPreviousImage(): void {
  if (imageUrls.value.length === 0) return
  selectedImage.value =
    selectedImage.value === 0 ? imageUrls.value.length - 1 : selectedImage.value - 1
}

function showNextImage(): void {
  if (imageUrls.value.length === 0) return
  selectedImage.value = (selectedImage.value + 1) % imageUrls.value.length
}

function goBack(): void {
  void router.push({
    name: 'explore',
    query: { tab: 'places' },
  })
}

async function sharePlace(): Promise<void> {
  const current = place.value
  if (!current) return

  try {
    if (navigator.share) {
      await navigator.share({ title: current.name, url: window.location.href })
      shared.value = true
      return
    }

    if (navigator.clipboard) {
      await navigator.clipboard.writeText(window.location.href)
      shared.value = true
    }
  } catch {
    // The native share sheet can be dismissed without completing the action.
  }
}

function openMapUrl(url: string | null): void {
  if (url) window.open(url, '_blank', 'noopener,noreferrer')
}

function retry(): void {
  void placeQuery.refetch()
}

function openAppointmentList(): void {
  const current = place.value
  if (!current) return

  void router.push({
    name: 'appointment-list',
    query: {
      itemId: String(current.itemId),
      itemType: 'PLACE',
    },
  })
}

function openJourneySelectSheet(): void {
  journeyAddError.value = null
  selectedJourneyId.value = activeJourneyId.value
  journeySelectSheetOpen.value = true
}

function closeJourneySelectSheet(): void {
  journeySelectSheetOpen.value = false
}

function selectJourney(journeyId: number): void {
  selectedJourneyId.value = journeyId
  returnContext.setJourneyId(journeyId)
  journeyDate.value = returnContext.visitDate
  journeySelectSheetOpen.value = false
  journeyDateSheetOpen.value = journeyDateRange.value !== null
}

/**
 * 담을 여정이 없을 때 이 자리에서 만들러 나간다.
 *
 * 돌아올 위치를 먼저 심는다. route param(`placeId`)을 실어야 해서 query만 나르는
 * `returnRouteName`으로는 이 화면으로 돌아올 수 없다.
 */
function goToCreateJourney(): void {
  journeySelectSheetOpen.value = false
  /*
   * 다른 화면에 들렀다 돌아오는 query 규약을 쓴다(`frontend/docs/DEVELOPMENT_CONVENTION.md`).
   * 두 feature가 서로를 import하지 않고 주소만으로 이어진다.
   *
   * `replace`인 것은 새 여정 id를 돌려받아야 해서다 — 받는 쪽이 이 자리를 넘겨받았다가
   * 일을 마치든 그냥 나가든 돌려준다. 그래서 왕복이 히스토리에 한 자리만 차지한다.
   *
   * 이 화면은 route param을 쓰므로 `returnParams`로 함께 넘긴다. `openJourneySelect`는
   * 규약이 "나머지 query"로 그대로 돌려주므로, 돌아왔을 때 하던 일을 이어 갈 표시가 된다.
   */
  void router.replace({
    name: 'journey-create',
    query: {
      returnRouteName: 'explore-place-detail',
      returnParams: `placeId:${placeId.value}`,
      openJourneySelect: '1',
    },
  })
}

function closeJourneyDateSheet(): void {
  journeyDateSheetOpen.value = false
}

async function confirmJourneyDate(date: string): Promise<void> {
  if (journeyAddPending.value) return

  const current = place.value
  const journeyId = selectedJourneyId.value ?? activeJourneyId.value
  if (!current || journeyId === null) {
    journeyAddError.value = 'explore.journeyDate.selectItemFirst'
    return
  }

  journeyAddPending.value = true
  journeyAddError.value = null

  try {
    await addJourneyItem(journeyId, {
      itemId: current.itemId,
      visitDate: date,
    })
    returnContext.setJourneyId(journeyId)
    journeyDate.value = date
    journeyAdded.value = true
    journeyDateSheetOpen.value = false
    const destination = returnContext.consumeReturn()
    await router.push(destination ?? { name: 'journey-detail', params: { tripId: journeyId } })
  } catch (error) {
    journeyAddError.value = journeyAddErrorMessageKey(error, hasMessage)
  } finally {
    journeyAddPending.value = false
  }
}

/**
 * 여정을 만들고 돌아온 진입. 하던 일을 그대로 이어 담기 시트를 다시 연다.
 *
 * 새 여정 id를 어떻게 받는지는 아래 블록 주석에 적었다.
 */
onMounted(() => {
  if (route.query.openJourneySelect !== '1') return

  openJourneySelectSheet()

  /*
   * 규약이 정한 결과 key는 `tripId`다. 여기서 한 번 읽어 고른 여정으로 삼고 주소에서
   * 지운다 — 남겨두면 시트를 다시 열 때마다 사용자가 그 뒤에 고른 여정을 덮어쓴다.
   */
  const createdJourneyId = parseJourneyRouteQuery(route.query.tripId)
  if (createdJourneyId !== null) {
    selectedJourneyId.value = createdJourneyId
    returnContext.setJourneyId(createdJourneyId)
  }

  const restQuery = { ...route.query }
  delete restQuery.openJourneySelect
  delete restQuery.tripId
  void router.replace({ query: restQuery })
})
</script>

<template>
  <section class="min-h-dvh pb-32">
    <header
      class="sticky top-0 z-20 flex items-center justify-between bg-canvas/95 px-screen py-3 backdrop-blur"
    >
      <IconOrb
        :label="t('explore.placeDetail.back')"
        @click="goBack"
      >
        <IconArrowLeft
          :size="22"
          :stroke-width="1.8"
          aria-hidden="true"
        />
      </IconOrb>
      <span
        v-if="shared"
        class="sr-only"
        aria-live="polite"
      >
        {{ t('explore.placeDetail.shared') }}
      </span>
      <IconOrb
        :label="t('explore.placeDetail.share')"
        @click="sharePlace"
      >
        <IconShare2
          :size="21"
          :stroke-width="1.8"
          aria-hidden="true"
        />
      </IconOrb>
    </header>

    <StateLoading
      v-if="placeQuery.isPending.value"
      :lines="4"
      class="px-screen pt-4"
    />
    <StateError
      v-else-if="placeQuery.isError.value"
      class="px-screen pt-4"
      :description="t('explore.placeDetail.detailError')"
      @retry="retry"
    />
    <StateError
      v-else-if="place === undefined"
      class="px-screen pt-4"
      :description="t('explore.placeDetail.notFound')"
      @retry="retry"
    />
    <template v-else>
      <div class="relative aspect-[4/3] w-full overflow-hidden bg-surface-1">
        <img
          v-if="currentImage"
          :src="currentImage"
          :alt="place.name"
          class="size-full object-cover"
        />
        <ImagePlaceholder
          v-else
          :label="t('explore.placePhoto')"
        />
        <div
          v-if="imageUrls.length > 1"
          class="absolute inset-x-0 bottom-4 flex items-center justify-center gap-3"
        >
          <button
            type="button"
            class="flex size-9 items-center justify-center rounded-pill bg-scrim/55 text-ink"
            :aria-label="t('explore.placeDetail.previousImage')"
            @click="showPreviousImage"
          >
            <IconChevronLeft
              :size="18"
              aria-hidden="true"
            />
          </button>
          <span class="rounded-pill bg-scrim/65 px-3 py-1 text-micro text-ink">
            {{
              t('explore.placeDetail.imageCount', {
                current: selectedImage + 1,
                total: imageUrls.length,
              })
            }}
          </span>
          <button
            type="button"
            class="flex size-9 items-center justify-center rounded-pill bg-scrim/55 text-ink"
            :aria-label="t('explore.placeDetail.nextImage')"
            @click="showNextImage"
          >
            <IconChevronRight
              :size="18"
              aria-hidden="true"
            />
          </button>
        </div>
      </div>

      <main class="flex flex-col gap-6 px-screen pt-6">
        <section class="flex flex-col gap-3">
          <div class="flex items-center gap-2 text-micro uppercase tracking-wide text-ink-2">
            <CategoryDot :category="category" />
            <span>{{ categoryLabel }} · {{ kindLabel }}</span>
          </div>
          <h1 class="font-display text-4xl leading-[0.98] text-ink-display">{{ place.name }}</h1>
          <p
            v-if="place.brand || place.branch"
            class="text-body-sm text-ink-2"
          >
            {{ [place.brand, place.branch].filter(Boolean).join(' · ') }}
          </p>
          <p
            v-if="regionLabel"
            class="flex items-center gap-1 text-body-sm text-ink-2"
          >
            <IconMapPin
              :size="16"
              aria-hidden="true"
            />
            {{ regionLabel }}
          </p>
          <div class="flex flex-wrap gap-2">
            <AppBadge
              v-for="badge in optionBadges"
              :key="badge.key"
              tone="settlement"
            >
              {{ badge.label }}
            </AppBadge>
          </div>
        </section>

        <AppCard
          v-if="detailRows.length > 0"
          padding="none"
        >
          <section>
            <h2 class="px-5 pt-5 text-section-header text-ink">
              {{ t('explore.placeDetail.openingInfo') }}
            </h2>
            <dl class="mt-3 divide-y divide-hairline">
              <div
                v-for="row in detailRows"
                :key="row.label"
                class="grid grid-cols-[5.5rem_1fr] gap-3 px-5 py-4"
              >
                <dt class="text-caption text-ink-3">{{ row.label }}</dt>
                <dd class="whitespace-pre-line break-words text-body-sm text-ink">
                  {{ row.value }}
                </dd>
              </div>
            </dl>
          </section>
        </AppCard>

        <section class="flex flex-col gap-3">
          <h2 class="text-section-header text-ink">{{ t('explore.placeDetail.location') }}</h2>
          <div class="relative aspect-[1.8] overflow-hidden rounded-card bg-surface-1">
            <div
              class="pointer-events-none absolute inset-0 opacity-75"
              role="img"
              :aria-label="locationLabel || t('explore.placeDetail.location')"
            >
              <div aria-hidden="true">
                <span class="absolute inset-y-0 left-1/4 w-px bg-hairline-2/70" />
                <span class="absolute inset-y-0 left-1/2 w-px bg-hairline-2/70" />
                <span class="absolute inset-y-0 left-3/4 w-px bg-hairline-2/70" />
                <span class="absolute inset-x-0 top-1/3 h-px bg-hairline-2/70" />
                <span class="absolute inset-x-0 top-2/3 h-px bg-hairline-2/70" />
              </div>
              <span
                class="absolute -left-[12%] bottom-[27%] h-3 w-[124%] -rotate-6 border-y-4 border-hairline-2/70"
                aria-hidden="true"
              />
            </div>
            <span
              class="absolute left-1/2 top-1/2 size-4 -translate-x-1/2 -translate-y-1/2 rounded-pill bg-food ring-4 ring-food/20"
              aria-hidden="true"
            />
          </div>
          <div
            v-if="mapSearchUrl"
            v-fit-text-group
            class="grid min-w-0 grid-cols-2 gap-2"
          >
            <div class="min-w-0">
              <AppButton
                block
                compact
                variant="secondary"
                @click="openMapUrl(mapSearchUrl)"
              >
                {{ t('explore.placeDetail.openInGoogleMaps') }}
              </AppButton>
            </div>
            <div class="min-w-0">
              <AppButton
                block
                compact
                variant="secondary"
                @click="openMapUrl(mapTransitRouteUrl)"
              >
                {{ t('explore.placeDetail.directions') }}
              </AppButton>
            </div>
            <div class="min-w-0">
              <AppButton
                block
                compact
                variant="secondary"
                @click="openMapAppUrl(naverPlaceUrl)"
              >
                {{ t('explore.placeDetail.openInNaverMap') }}
              </AppButton>
            </div>
            <div class="min-w-0">
              <AppButton
                block
                compact
                variant="secondary"
                @click="openMapAppUrl(naverRouteUrl)"
              >
                {{ t('explore.placeDetail.naverDirections') }}
              </AppButton>
            </div>
          </div>
        </section>

        <section
          v-if="menuItems.length > 0"
          class="flex flex-col gap-3"
        >
          <h2 class="text-section-header text-ink">{{ t('explore.placeDetail.signatureMenu') }}</h2>
          <div class="flex flex-wrap gap-2">
            <AppBadge
              v-for="item in menuItems"
              :key="item"
              tone="neutral"
            >
              {{ item }}
            </AppBadge>
          </div>
        </section>
      </main>

      <div
        v-fit-text-group
        class="sticky bottom-0 z-10 mt-6 flex w-full min-w-0 items-center gap-2 bg-canvas/95 px-screen py-3 backdrop-blur"
      >
        <button
          type="button"
          class="flex size-12 shrink-0 items-center justify-center rounded-sm border border-hairline-strong bg-transparent text-ink"
          :aria-label="saved ? t('explore.placeDetail.unsave') : t('explore.placeDetail.save')"
          :aria-pressed="saved"
          @click="toggleSaved"
        >
          <IconHeart
            :size="21"
            :stroke-width="1.8"
            :class="saved ? 'fill-danger text-danger' : ''"
            aria-hidden="true"
          />
        </button>
        <div class="min-w-0 flex-1">
          <AppButton
            block
            compact
            class="whitespace-nowrap text-on-paper"
            @click="openJourneySelectSheet"
          >
            {{
              journeyAdded
                ? t('explore.placeDetail.addedToJourney')
                : t('explore.placeDetail.addToJourney')
            }}
          </AppButton>
        </div>
        <div class="min-w-0 flex-1">
          <AppButton
            block
            compact
            variant="secondary"
            class="whitespace-nowrap border-success text-success"
            @click="openAppointmentList"
          >
            {{ t('explore.placeDetail.findCompanions') }}
          </AppButton>
        </div>
      </div>

      <JourneyDateSheet
        v-if="journeyDateSheetOpen && journeyDateRange"
        :item-title="place.name"
        :item-location="locationLabel"
        :start-date="journeyDateRange.start"
        :end-date="journeyDateRange.end"
        :initial-date="journeyDate"
        :loading="journeyAddPending"
        :error-message="journeyAddError ? t(journeyAddError) : null"
        @close="closeJourneyDateSheet"
        @confirm="confirmJourneyDate"
      />

      <JourneySelectSheet
        v-if="journeySelectSheetOpen"
        :journeys="journeys"
        :item-start-date="itemPeriod.startDate"
        :item-end-date="itemPeriod.endDate"
        :selected-journey-id="selectedJourneyId"
        :loading="journeyListQuery.isPending.value"
        :error-message="journeyListQuery.isError.value ? t('explore.journeySelect.error') : null"
        @close="closeJourneySelectSheet"
        @select="selectJourney"
        @create-journey="goToCreateJourney"
      />
    </template>
  </section>
</template>
