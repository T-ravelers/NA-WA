<script setup lang="ts">
import { computed, ref, watch } from 'vue'
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

import AppBadge from '@/shared/ui/AppBadge.vue'
import AppButton from '@/shared/ui/AppButton.vue'
import AppCard from '@/shared/ui/AppCard.vue'
import CategoryDot from '@/shared/ui/CategoryDot.vue'
import IconOrb from '@/shared/ui/IconOrb.vue'
import ImagePlaceholder from '@/shared/ui/ImagePlaceholder.vue'
import StateError from '@/shared/ui/StateError.vue'
import StateLoading from '@/shared/ui/StateLoading.vue'
import type { Category } from '@/shared/ui/category'

import { useEventDetailQuery } from '../composables/useEventDetailQuery'
import JourneyDateSheet from '../components/JourneyDateSheet.vue'
import JourneySelectSheet from '../components/JourneySelectSheet.vue'
import {
  resolveHomepageUrl,
  resolveReservationUrl,
  toDetailEntries,
  toImageUrls,
  toStringList,
  type DetailEntry,
} from '../model/eventDetail'
import { useExploreJourneyIntegration } from '../model/journeyIntegration'
import { useSavedEventsStore } from '../model/savedEvents'

const route = useRoute()
const router = useRouter()
const { locale, t } = useI18n()
const {
  addJourneyItem,
  parseJourneyRouteQuery,
  readActiveJourneyId,
  storeActiveJourneyId,
  useJourneyListQuery,
} = useExploreJourneyIntegration()

const eventId = computed(() => String(route.params.eventId ?? ''))
const eventQuery = useEventDetailQuery(eventId, locale)
const event = computed(() => eventQuery.data.value)
const savedEvents = useSavedEventsStore()
const saved = computed(() => {
  const current = event.value
  return current ? savedEvents.isSaved(current.eventId) : false
})

const selectedImage = ref(0)
const journeyAdded = ref(false)
const journeySelectSheetOpen = ref(false)
const journeyDateSheetOpen = ref(false)
const journeyDate = ref<string | null>(null)
const selectedJourneyId = ref<number | null>(null)
const journeyAddPending = ref(false)
const journeyAddError = ref<'missing' | 'failed' | null>(null)
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
  [event.value?.region1, event.value?.region2, event.value?.region3].filter(Boolean).join(' · '),
)

const locationLabel = computed(() =>
  [event.value?.venueName, event.value?.addressRoad].filter(Boolean).join(' · '),
)

const journeyLocation = computed(() => regionLabel.value || locationLabel.value)
const activeJourneyId = computed(
  () => parseJourneyRouteQuery(route.query.journeyId) ?? readActiveJourneyId(),
)
const journeyListQuery = useJourneyListQuery(journeySelectSheetOpen)
const journeys = computed(() => journeyListQuery.data.value ?? [])

const hours = computed(() => (event.value ? toDetailEntries(event.value.operatingHours) : []))
const openDays = computed(() => (event.value ? toStringList(event.value.openDays).join(', ') : ''))

const detailRows = computed(() => {
  const current = event.value
  if (!current) return []

  const rows: DetailEntry[] = []
  const period = current.isPermanent
    ? t('explore.detail.permanent')
    : [formatDate(current.startDate), formatDate(current.endDate)].filter(Boolean).join(' – ')
  if (period) rows.push({ label: t('explore.detail.period'), value: period })
  if (current.venueName || current.addressRoad) {
    rows.push({ label: t('explore.detail.venue'), value: locationLabel.value })
  }
  if (hours.value.length > 0) {
    rows.push({
      label: t('explore.detail.hours'),
      value: hours.value.map((entry) => `${entry.label}: ${entry.value}`).join('\n'),
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

function formatDate(value: string | null): string {
  return value ? value.replace(/-/g, '.') : ''
}

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
  if (window.history.length > 1) {
    router.back()
    return
  }
  void router.push({ name: 'explore' })
}

function openAppointments(): void {
  const current = event.value
  const itemId = Number(current?.eventId)
  if (!current || !Number.isSafeInteger(itemId) || itemId <= 0) return

  void router.push({
    name: 'appointment-list',
    query: { itemId: String(itemId), itemType: 'EVENT' },
  })
}

async function shareEvent(): Promise<void> {
  const current = event.value
  if (!current) return

  try {
    if (navigator.share) {
      await navigator.share({ title: current.title, url: window.location.href })
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

function openReservation(): void {
  if (reservationUrl.value) window.open(reservationUrl.value, '_blank', 'noopener,noreferrer')
}

function toggleSaved(): void {
  const current = event.value
  if (current) savedEvents.toggle(current.eventId)
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
  storeActiveJourneyId(journeyId)
  journeySelectSheetOpen.value = false
  journeyDateSheetOpen.value = true
}

function closeJourneyDateSheet(): void {
  journeyDateSheetOpen.value = false
}

async function confirmJourneyDate(date: string): Promise<void> {
  if (journeyAddPending.value) return

  const current = event.value
  const journeyId = selectedJourneyId.value ?? activeJourneyId.value
  if (!current || journeyId === null) {
    journeyAddError.value = 'missing'
    return
  }

  journeyAddPending.value = true
  journeyAddError.value = null

  try {
    await addJourneyItem(journeyId, {
      itemId: current.eventId,
      visitDate: date,
    })
    storeActiveJourneyId(journeyId)
    journeyDate.value = date
    journeyAdded.value = true
    journeyDateSheetOpen.value = false
    await router.push({ name: 'journey-detail', params: { tripId: journeyId } })
  } catch {
    journeyAddError.value = 'failed'
  } finally {
    journeyAddPending.value = false
  }
}

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
        <img
          v-if="currentImage"
          :src="currentImage"
          :alt="event.title"
          class="size-full object-cover"
        />
        <ImagePlaceholder
          v-else
          :label="t('explore.imageUnavailable')"
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
          <h2 class="text-section-header text-ink">{{ t('explore.detail.location') }}</h2>
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
            <div class="absolute inset-x-3 bottom-3 flex justify-end">
              <button
                type="button"
                class="rounded-pill bg-canvas/85 px-3 py-2 text-caption text-ink shadow-raised disabled:pointer-events-none disabled:opacity-40"
                disabled
              >
                {{ t('explore.detail.directions') }}
              </button>
            </div>
          </div>
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
            class="h-12 whitespace-nowrap px-2 text-on-paper"
            @click="openJourneyDateSheet"
          >
            {{
              journeyAdded ? t('explore.detail.addedToJourney') : t('explore.detail.addToJourney')
            }}
          </AppButton>
        </div>
        <div class="min-w-0 flex-1">
          <AppButton
            block
            variant="secondary"
            class="h-12 whitespace-nowrap border-success px-2 text-success"
            @click="openAppointments"
          >
            {{ t('explore.detail.findCompanions') }}
          </AppButton>
        </div>
      </div>

      <JourneyDateSheet
        v-if="journeyDateSheetOpen"
        :item-title="event.title"
        :item-location="journeyLocation"
        :start-date="event.startDate"
        :end-date="event.endDate"
        :is-permanent="event.isPermanent === true"
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
