<script setup lang="ts">
import { computed, ref, watch } from 'vue'
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

import { buildGoogleMapsDirectionsUrl, buildGoogleMapsSearchUrl } from '@/shared/lib/mapLink'
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
import { useExploreReturnContextStore } from '../model/exploreReturnContext'
import { useExploreJourneyIntegration } from '../model/journeyIntegration'
import { findExploreRegionLabelKey } from '../model/exploreRegions'
import { normalizePlaceKind, type PlaceKind } from '../model/placeExplore'
import { toClosedDays, toDetailEntries } from '../model/placeDetail'

const route = useRoute()
const router = useRouter()
const { locale, t } = useI18n()
const { addJourneyItem, parseJourneyRouteQuery, useJourneyListQuery } =
  useExploreJourneyIntegration()
const returnContext = useExploreReturnContextStore()

const placeId = computed(() => String(route.params.placeId ?? ''))
const placeQuery = usePlaceDetailQuery(placeId, locale)
const place = computed(() => placeQuery.data.value)

const selectedImage = ref(0)
const shared = ref(false)
const journeySelectSheetOpen = ref(false)
const journeyDateSheetOpen = ref(false)
const journeyDate = ref<string | null>(null)
const selectedJourneyId = ref<number | null>(null)
const journeyAdded = ref(false)
const journeyAddPending = ref(false)
const journeyAddError = ref<'missing' | 'failed' | null>(null)

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
const mapDirectionsUrl = computed(() =>
  buildGoogleMapsDirectionsUrl(place.value?.latitude, place.value?.longitude),
)

const hours = computed(() => (place.value ? toDetailEntries(place.value.openingHours) : []))
const closedDays = computed(() => (place.value ? toClosedDays(place.value.closedDays) : ''))

const detailRows = computed(() => {
  const current = place.value
  if (!current) return []

  const rows: Array<{ label: string; value: string }> = []
  if (hours.value.length > 0) {
    rows.push({
      label: t('explore.placeDetail.hours'),
      value: hours.value.map((entry) => `${entry.label}: ${entry.value}`).join('\n'),
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

const menuItems = computed(() => {
  const value = place.value?.menuSummary
  if (!value) return []

  return value
    .split(/\n|\s*·\s*|\s*,\s*/)
    .map((item) => item.trim())
    .filter(Boolean)
})

const activeJourneyId = computed(
  () => parseJourneyRouteQuery(route.query.journeyId) ?? returnContext.journeyId,
)
const journeyListQuery = useJourneyListQuery(journeySelectSheetOpen)
const journeys = computed(() => journeyListQuery.data.value ?? [])

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

function openJourneyDateSheet(): void {
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
  journeyDateSheetOpen.value = true
}

function closeJourneyDateSheet(): void {
  journeyDateSheetOpen.value = false
}

async function confirmJourneyDate(date: string): Promise<void> {
  if (journeyAddPending.value) return

  const current = place.value
  const journeyId = selectedJourneyId.value ?? activeJourneyId.value
  if (!current || journeyId === null) {
    journeyAddError.value = 'missing'
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
  } catch {
    journeyAddError.value = 'failed'
  } finally {
    journeyAddPending.value = false
  }
}
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
            class="flex min-w-0 gap-2"
          >
            <div class="min-w-0 flex-1">
              <AppButton
                block
                variant="secondary"
                class="h-12 whitespace-nowrap px-2"
                @click="openMapUrl(mapSearchUrl)"
              >
                {{ t('explore.placeDetail.openInGoogleMaps') }}
              </AppButton>
            </div>
            <div class="min-w-0 flex-1">
              <AppButton
                block
                variant="secondary"
                class="h-12 whitespace-nowrap px-2"
                @click="openMapUrl(mapDirectionsUrl)"
              >
                {{ t('explore.placeDetail.directions') }}
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
        class="sticky bottom-0 z-10 mt-6 flex w-full min-w-0 items-center gap-2 bg-canvas/95 px-screen py-3 backdrop-blur"
      >
        <button
          type="button"
          class="flex size-12 shrink-0 items-center justify-center rounded-sm border border-hairline-strong bg-transparent text-ink"
          :aria-label="t('explore.placeDetail.saveUnavailable')"
          disabled
        >
          <IconHeart
            :size="21"
            :stroke-width="1.8"
            aria-hidden="true"
          />
        </button>
        <div class="min-w-0 flex-1">
          <AppButton
            block
            class="h-12 whitespace-nowrap px-2 text-on-paper"
            @click="openJourneyDateSheet"
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
            variant="secondary"
            class="h-12 whitespace-nowrap border-success px-2 text-success"
            @click="openAppointmentList"
          >
            {{ t('explore.placeDetail.findCompanions') }}
          </AppButton>
        </div>
      </div>

      <JourneyDateSheet
        v-if="journeyDateSheetOpen"
        :item-title="place.name"
        :item-location="locationLabel"
        :start-date="null"
        :end-date="null"
        :is-permanent="true"
        :initial-date="journeyDate"
        :loading="journeyAddPending"
        :error-message="
          journeyAddError === 'missing'
            ? t('explore.journeyDate.selectItemFirst')
            : journeyAddError === 'failed'
              ? t('explore.journeyDate.addItemFailed')
              : null
        "
        @close="closeJourneyDateSheet"
        @confirm="confirmJourneyDate"
      />

      <JourneySelectSheet
        v-if="journeySelectSheetOpen"
        :journeys="journeys"
        :selected-journey-id="selectedJourneyId"
        :loading="journeyListQuery.isPending.value"
        :error-message="journeyListQuery.isError.value ? t('explore.journeySelect.error') : null"
        @close="closeJourneySelectSheet"
        @select="selectJourney"
      />
    </template>
  </section>
</template>
