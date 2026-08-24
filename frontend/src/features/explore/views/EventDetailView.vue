<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import {
  IconArrowLeft,
  IconChevronLeft,
  IconChevronRight,
  IconExternalLink,
  IconHeart,
  IconMapPin,
  IconShare2,
} from '@tabler/icons-vue'

import { formatCalendarDateString } from '@/shared/lib/datetime'
import { vFitTextGroup } from '@/shared/lib/fitText'
import { shareWithFallback } from '@/shared/lib/share'
import AppBadge from '@/shared/ui/AppBadge.vue'
import AppButton from '@/shared/ui/AppButton.vue'
import AppCard from '@/shared/ui/AppCard.vue'
import AppImage from '@/shared/ui/AppImage.vue'
import CategoryDot from '@/shared/ui/CategoryDot.vue'
import IconOrb from '@/shared/ui/IconOrb.vue'
import StateError from '@/shared/ui/StateError.vue'
import StateLoading from '@/shared/ui/StateLoading.vue'
import type { Category } from '@/shared/ui/category'
import { showToast } from '@/shared/ui/toast'

import { useEventDetailQuery } from '../composables/useEventDetailQuery'
import JourneyDateSheet from '../components/JourneyDateSheet.vue'
import JourneySelectSheet from '../components/JourneySelectSheet.vue'
import MapLinkButtons from '../components/MapLinkButtons.vue'
import {
  resolveHomepageUrl,
  resolveReservationUrl,
  toDetailEntries,
  toImageUrls,
  toStringList,
  type DetailEntry,
} from '../model/eventDetail'
import { useExploreItemLikeMutation } from '../composables/useExploreItemLikeMutation'
import { useExploreReturnContextStore } from '../model/exploreReturnContext'
import { useExploreJourneyIntegration } from '../model/journeyIntegration'
import { journeyAddErrorMessageKey } from '../model/journeyAddErrors'
import { intersectItemJourneyPeriod } from '../model/journeyPeriod'
import { findExploreRegionLabelKey } from '../model/exploreRegions'

const route = useRoute()
const router = useRouter()
const i18n = useI18n()
const { locale, t } = i18n
const hasMessage = (key: string): boolean => i18n.te(key)
const { addJourneyItem, parseJourneyRouteQuery, useJourneyListQuery } =
  useExploreJourneyIntegration()
const returnContext = useExploreReturnContextStore()

const eventId = computed(() => String(route.params.eventId ?? ''))
const eventQuery = useEventDetailQuery(eventId, locale)
const event = computed(() => eventQuery.data.value)
const likeMutation = useExploreItemLikeMutation()
const saved = computed(() => event.value?.saved ?? false)

const selectedImage = ref(0)
const journeyAdded = ref(false)
const journeySelectSheetOpen = ref(false)
const journeyDateSheetOpen = ref(false)
const journeyDate = ref<string | null>(null)
const selectedJourneyId = ref<number | null>(null)
const journeyAddPending = ref(false)
/** 담기 실패 문구의 i18n key. 원인별로 다른 key가 들어온다. */
const journeyAddError = ref<string | null>(null)
const shared = ref(false)

const imageUrls = computed(() => (event.value ? toImageUrls(event.value.imageUrls) : []))
const currentImage = computed(() => imageUrls.value[selectedImage.value])
const reservationUrl = computed(() => (event.value ? resolveReservationUrl(event.value) : null))
const homepageUrl = computed(() => (event.value ? resolveHomepageUrl(event.value) : null))

const category = computed<Category>(() => {
  const kind = event.value?.eventKind
  if (kind === 'POPUP') return 'shopping'
  if (kind === 'FESTIVAL') return 'food'
  if (kind === 'ETC') return 'beauty'
  return 'show'
})

const regionLabel = computed(() =>
  [event.value?.region1, event.value?.region2, event.value?.region3]
    .filter((value): value is string => Boolean(value))
    .map((value) => {
      const labelKey = findExploreRegionLabelKey(value)
      return labelKey ? t(labelKey) : value
    })
    .join(' · '),
)

const locationLabel = computed(() =>
  [event.value?.venueName, event.value?.addressRoad].filter(Boolean).join(' · '),
)

const journeyLocation = computed(() => regionLabel.value || locationLabel.value)
const activeJourneyId = computed(
  () => parseJourneyRouteQuery(route.query.journeyId) ?? returnContext.journeyId,
)
const journeyListQuery = useJourneyListQuery(journeySelectSheetOpen)
const journeys = computed(() => journeyListQuery.data.value ?? [])

/**
 * 이벤트의 운영 기간.
 *
 * 상시 이벤트는 `end_date`가 NULL이라는 것이 DB 불변식이지만, 응답이 무엇을 주든
 * `isPermanent`가 참이면 상한이 없는 것으로 읽는다.
 */
const itemPeriod = computed(() => ({
  startDate: event.value?.startDate ?? null,
  endDate: event.value?.isPermanent === true ? null : (event.value?.endDate ?? null),
}))

const selectedJourney = computed(
  () => journeys.value.find((journey) => journey.tripId === selectedJourneyId.value) ?? null,
)

/**
 * 달력이 열어 줄 구간. 이벤트 기간과 여정 기간이 겹치는 날만 담을 수 있다.
 *
 * 여정을 아직 고르지 않았거나 겹치는 날이 없으면 `null`이고, 그때는 날짜 시트를 열지
 * 않는다 — 열어 봐야 고를 수 있는 날이 없다.
 */
const journeyDateRange = computed(() => {
  const journey = selectedJourney.value
  return journey === null ? null : intersectItemJourneyPeriod(itemPeriod.value, journey)
})

const hours = computed(() => (event.value ? toDetailEntries(event.value.operatingHours) : []))
const openDays = computed(() => (event.value ? toStringList(event.value.openDays).join(', ') : ''))

const detailRows = computed(() => {
  const current = event.value
  if (!current) return []

  const rows: DetailEntry[] = []
  const period = current.isPermanent
    ? t('explore.detail.permanent')
    : [formatCalendarDateString(current.startDate), formatCalendarDateString(current.endDate)]
        .filter(Boolean)
        .join(' – ')
  if (period) rows.push({ label: t('explore.detail.period'), value: period })
  if (current.venueName || current.addressRoad) {
    rows.push({ label: t('explore.detail.venue'), value: locationLabel.value })
  }
  if (hours.value.length > 0) {
    rows.push({
      label: t('explore.detail.hours'),
      value: hours.value
        .map((entry) =>
          entry.label.toLowerCase() === 'raw' ? entry.value : `${entry.label}: ${entry.value}`,
        )
        .join('\n'),
    })
  } else if (openDays.value) {
    rows.push({ label: t('explore.detail.hours'), value: openDays.value })
  }
  if (current.isFree === true) {
    rows.push({ label: t('explore.detail.price'), value: t('explore.detail.free') })
  } else if (current.priceText) {
    rows.push({ label: t('explore.detail.price'), value: current.priceText })
  }
  if (current.ageLimit) rows.push({ label: t('explore.detail.age'), value: current.ageLimit })
  if (current.organizer) rows.push({ label: t('explore.detail.host'), value: current.organizer })
  if (current.contact) rows.push({ label: t('explore.detail.contact'), value: current.contact })
  if (homepageUrl.value)
    rows.push({ label: t('explore.detail.homepage'), value: homepageUrl.value })

  return rows
})

const statusTone = computed(() => (event.value?.status === 'ONGOING' ? 'ongoing' : 'scheduled'))

const statusLabel = computed(() => (event.value ? t(`explore.statuses.${event.value.status}`) : ''))

const kindLabel = computed(() =>
  event.value ? t(`explore.eventKinds.${event.value.eventKind}`) : '',
)

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
    query: { tab: 'events' },
  })
}

async function shareEvent(): Promise<void> {
  const current = event.value
  if (!current) return

  shared.value = false
  const result = await shareWithFallback(
    { title: current.title, url: window.location.href },
    window.location.href,
  )

  if (result === 'copied') {
    shared.value = true
  } else if (result === 'unavailable' || result === 'failed') {
    showToast(t('explore.detail.shareFailed'))
  }
}

function openReservation(): void {
  if (reservationUrl.value) window.open(reservationUrl.value, '_blank', 'noopener,noreferrer')
}

function openAppointmentList(): void {
  const current = event.value
  if (!current) return

  void router.push({
    name: 'appointment-list',
    query: {
      itemId: String(current.eventId),
      itemType: 'EVENT',
    },
  })
}

function toggleSaved(): void {
  const current = event.value
  if (!current || likeMutation.isPending.value) return
  likeMutation.mutate({ itemId: current.eventId, saved: !current.saved })
}

function openJourneySelectSheet(): void {
  journeyAddError.value = null
  // 이 화면에서 이미 고른 값이 URL의 진입 맥락보다 최신이다. 매번 query로 덮어쓰면
  // 날짜 시트를 닫고 다시 열 때 사용자의 마지막 선택이 사라진다(#390).
  selectedJourneyId.value ??= activeJourneyId.value
  journeySelectSheetOpen.value = true
}

function closeJourneySelectSheet(): void {
  journeySelectSheetOpen.value = false
}

function selectJourney(journeyId: number): void {
  selectedJourneyId.value = journeyId
  returnContext.setJourneyId(journeyId)
  if (
    route.query.journeyId !== undefined &&
    parseJourneyRouteQuery(route.query.journeyId) !== journeyId
  ) {
    const restQuery = { ...route.query }
    delete restQuery.journeyId
    void router.replace({ query: restQuery })
  }
  journeyDate.value = returnContext.visitDate
  journeySelectSheetOpen.value = false
  // 시트가 고를 수 없는 여정을 막지만, 범위가 없으면 열 것이 없으므로 한 번 더 본다.
  journeyDateSheetOpen.value = journeyDateRange.value !== null
}

/**
 * 담을 여정이 없을 때 이 자리에서 만들러 나간다.
 *
 * 돌아올 위치를 먼저 심는다. route param(`eventId`)을 실어야 해서 query만 나르는
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
  /*
   * 이벤트 기간을 함께 보낸다. 이 버튼을 누른 사람은 정의상 **겹치는 여정이 하나도
   * 없는** 사람인데, 빈 폼에는 무엇과 겹쳐야 하는지가 없다. 안 겹치는 기간으로 또
   * 만들고 돌아오면 없애려던 막다른 길이 한 바퀴 뒤로 옮겨질 뿐이다.
   *
   * 상시 이벤트는 상한이 없어 `endDate`를 싣지 않는다.
   */
  void router.replace({
    name: 'journey-create',
    query: {
      returnRouteName: 'explore-event-detail',
      returnParams: `eventId:${eventId.value}`,
      openJourneySelect: '1',
      ...(itemPeriod.value.startDate !== null ? { itemStartDate: itemPeriod.value.startDate } : {}),
      ...(itemPeriod.value.endDate !== null ? { itemEndDate: itemPeriod.value.endDate } : {}),
    },
  })
}

function closeJourneyDateSheet(): void {
  journeyDateSheetOpen.value = false
}

async function confirmJourneyDate(date: string): Promise<void> {
  if (journeyAddPending.value) return

  const current = event.value
  const journeyId = selectedJourneyId.value ?? activeJourneyId.value
  if (!current || journeyId === null) {
    journeyAddError.value = 'explore.journeyDate.selectItemFirst'
    return
  }

  journeyAddPending.value = true
  journeyAddError.value = null

  try {
    await addJourneyItem(journeyId, {
      itemId: current.eventId,
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
 * `openJourneySelect` 표시를 남겨 두면 새로고침이나 뒤로 가기에서도 시트가 다시
 * 열리므로 읽자마자 주소에서 지운다. 새 여정 id를 어떻게 받는지는 아래 블록 주석에
 * 적었다.
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
  // 규약이 나머지 query를 그대로 돌려주므로 보낼 때 실은 기간도 함께 돌아온다.
  // 이 화면에서는 쓰이지 않으니 주소에 남기지 않는다.
  delete restQuery.itemStartDate
  delete restQuery.itemEndDate
  void router.replace({ query: restQuery })
})

function retry(): void {
  void eventQuery.refetch()
}
</script>

<template>
  <section class="min-h-dvh pb-32">
    <header
      class="sticky top-0 z-20 flex items-center justify-between bg-canvas/95 px-screen py-3 backdrop-blur"
    >
      <IconOrb
        :label="t('explore.detail.back')"
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
        {{ t('explore.detail.shared') }}
      </span>
      <IconOrb
        :label="t('explore.detail.share')"
        @click="shareEvent"
      >
        <IconShare2
          :size="21"
          :stroke-width="1.8"
          aria-hidden="true"
        />
      </IconOrb>
    </header>

    <StateLoading
      v-if="eventQuery.isPending.value"
      :lines="4"
      class="px-screen pt-4"
    />
    <StateError
      v-else-if="eventQuery.isError.value"
      class="px-screen pt-4"
      :description="t('explore.detail.detailError')"
      @retry="retry"
    />
    <StateError
      v-else-if="event === undefined"
      class="px-screen pt-4"
      :description="t('explore.detail.notFound')"
      @retry="retry"
    />
    <template v-else>
      <div class="relative aspect-[4/3] w-full overflow-hidden bg-surface-1">
        <AppImage
          :src="currentImage"
          :alt="event.title"
          :placeholder-label="t('explore.imageUnavailable')"
          class="size-full object-cover"
        />
        <div
          v-if="imageUrls.length > 1"
          class="absolute inset-x-0 bottom-4 flex items-center justify-center gap-3"
        >
          <button
            type="button"
            class="flex size-9 items-center justify-center rounded-pill bg-scrim/55 text-ink"
            :aria-label="t('explore.detail.previousImage')"
            @click="showPreviousImage"
          >
            <IconChevronLeft
              :size="18"
              aria-hidden="true"
            />
          </button>
          <span class="rounded-pill bg-scrim/65 px-3 py-1 text-micro text-ink">
            {{
              t('explore.detail.imageCount', {
                current: selectedImage + 1,
                total: imageUrls.length,
              })
            }}
          </span>
          <button
            type="button"
            class="flex size-9 items-center justify-center rounded-pill bg-scrim/55 text-ink"
            :aria-label="t('explore.detail.nextImage')"
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
            <span>{{ kindLabel }}</span>
          </div>
          <h1 class="font-display text-4xl leading-[0.98] text-ink-display">{{ event.title }}</h1>
          <p
            v-if="event.subtitle"
            class="text-body-sm text-ink-2"
          >
            {{ event.subtitle }}
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
              :tone="statusTone"
              dot
              >{{ statusLabel }}</AppBadge
            >
            <AppBadge
              v-if="event.isPermanent"
              tone="neutral"
              >{{ t('explore.detail.permanent') }}</AppBadge
            >
            <AppBadge
              v-if="reservationUrl"
              tone="settlement"
              >{{ t('explore.detail.reservation') }}</AppBadge
            >
            <AppBadge
              v-if="event.isFree"
              tone="neutral"
              >{{ t('explore.detail.free') }}</AppBadge
            >
          </div>
        </section>

        <section
          v-if="event.description || event.programText"
          class="flex flex-col gap-5"
        >
          <div
            v-if="event.description"
            class="flex flex-col gap-2"
          >
            <h2 class="text-section-header text-ink">{{ t('explore.detail.description') }}</h2>
            <p class="whitespace-pre-line text-body-sm leading-relaxed text-ink-2">
              {{ event.description }}
            </p>
          </div>
          <div
            v-if="event.programText"
            class="flex flex-col gap-2"
          >
            <h2 class="text-section-header text-ink">{{ t('explore.detail.program') }}</h2>
            <p class="whitespace-pre-line text-body-sm leading-relaxed text-ink-2">
              {{ event.programText }}
            </p>
          </div>
        </section>

        <AppCard
          v-if="detailRows.length > 0"
          padding="none"
        >
          <section>
            <h2 class="px-5 pt-5 text-section-header text-ink">{{ t('explore.detail.basics') }}</h2>
            <dl class="mt-3 divide-y divide-hairline">
              <div
                v-for="row in detailRows"
                :key="row.label"
                class="grid grid-cols-[5.5rem_1fr] gap-3 px-5 py-4"
              >
                <dt class="text-caption text-ink-3">{{ row.label }}</dt>
                <dd class="whitespace-pre-line text-body-sm text-ink">{{ row.value }}</dd>
              </div>
            </dl>
          </section>
        </AppCard>

        <section
          v-if="event.activities.length > 0"
          class="flex flex-col gap-3"
        >
          <h2 class="text-section-header text-ink">{{ t('explore.detail.activities') }}</h2>
          <div class="flex flex-wrap gap-2">
            <AppBadge
              v-for="activity in event.activities"
              :key="activity.activityId"
              tone="neutral"
              >{{ activity.activityName ?? activity.activityCode }}</AppBadge
            >
          </div>
        </section>

        <section class="flex flex-col gap-3">
          <h2
            class="text-section-header text-ink"
            data-testid="event-location"
          >
            {{ t('explore.detail.location') }}
          </h2>
          <div class="relative aspect-[1.8] overflow-hidden rounded-card bg-surface-1">
            <div
              class="pointer-events-none absolute inset-0 opacity-75"
              role="img"
              :aria-label="locationLabel || t('explore.detail.location')"
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
              <span
                class="absolute -left-[12%] bottom-[6%] h-3 w-[124%] rotate-6 border-y-4 border-hairline-2/45"
                aria-hidden="true"
              />
            </div>
            <span
              class="absolute left-1/2 top-1/2 size-4 -translate-x-1/2 -translate-y-1/2 rounded-pill bg-show ring-4 ring-show/20"
              aria-hidden="true"
            />
          </div>
          <MapLinkButtons
            :latitude="event.latitude"
            :longitude="event.longitude"
            :name="event.title"
          />
        </section>

        <a
          v-if="reservationUrl"
          href="#"
          class="flex items-center justify-between rounded-sm bg-settlement px-5 py-4 text-title-sm text-on-paper"
          @click.prevent="openReservation"
        >
          {{ t('explore.detail.openReservation') }}
          <IconExternalLink
            :size="18"
            aria-hidden="true"
          />
        </a>
      </main>

      <div
        v-fit-text-group
        class="sticky bottom-0 z-10 mt-6 flex w-full min-w-0 items-center gap-2 bg-canvas/95 px-screen py-3 backdrop-blur"
      >
        <IconOrb
          :label="saved ? t('explore.detail.unsave') : t('explore.detail.save')"
          variant="surface"
          class="size-12 rounded-sm border border-hairline-strong bg-transparent"
          @click="toggleSaved"
        >
          <IconHeart
            :size="21"
            :stroke-width="1.8"
            :class="saved ? 'fill-danger text-danger' : ''"
            aria-hidden="true"
          />
        </IconOrb>
        <div class="min-w-0 flex-1">
          <AppButton
            block
            compact
            class="whitespace-nowrap text-on-paper"
            @click="openJourneySelectSheet"
          >
            {{
              journeyAdded ? t('explore.detail.addedToJourney') : t('explore.detail.addToJourney')
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
            {{ t('explore.detail.findCompanions') }}
          </AppButton>
        </div>
      </div>

      <JourneyDateSheet
        v-if="journeyDateSheetOpen && journeyDateRange"
        :item-title="event.title"
        :item-location="journeyLocation"
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
