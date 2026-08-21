<script setup lang="ts">
import { computed, ref } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { IconSettings } from '@tabler/icons-vue'
import { useI18n } from 'vue-i18n'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import { NormalizedApiError } from '@/shared/api/apiError'
import AppButton from '@/shared/ui/AppButton.vue'
import StateEmpty from '@/shared/ui/StateEmpty.vue'
import StateError from '@/shared/ui/StateError.vue'
import StateLoading from '@/shared/ui/StateLoading.vue'

import JourneySummary from '../components/JourneySummary.vue'
import JourneyDialog from '../components/JourneyDialog.vue'
import JourneyTimelineList from '../components/JourneyTimelineList.vue'
import { deleteJourneyItem, type JourneyTimelineItem } from '../api/journeyApi'
import { journeyErrorMessageKey, isJourneyForbidden } from '../model/journeyErrors'
import { journeyKeys as journeyListKeys } from '../model/journeyKeys'
import { getJourneyStatus } from '../model/journeyStatus'
import {
  journeyDetailQueryOptions,
  journeyKeys,
  journeyTimelineQueryOptions,
} from '../model/journeyQueries'
import { useJourneyReportIntegration } from '../model/reportIntegration'

const i18n = useI18n()
const { t } = i18n
const route = useRoute()
const router = useRouter()
const queryClient = useQueryClient()
const hasMessage = (key: string): boolean => i18n.te(key)

const tripId = computed(() => {
  const rawTripId = Array.isArray(route.params.tripId)
    ? route.params.tripId[0]
    : route.params.tripId
  const parsedTripId = Number(rawTripId)

  return Number.isSafeInteger(parsedTripId) && parsedTripId > 0 ? parsedTripId : null
})

const detailQuery = useQuery({
  ...journeyDetailQueryOptions(tripId),
  enabled: computed(() => tripId.value !== null),
  retry: false,
})

const timelineQuery = useQuery({
  ...journeyTimelineQueryOptions(tripId),
  enabled: computed(() => tripId.value !== null),
  retry: false,
})

const requestError = computed(() => detailQuery.error.value ?? timelineQuery.error.value)
const forbidden = computed(() => isJourneyForbidden(requestError.value))
const requestErrorDescription = computed(() =>
  t(journeyErrorMessageKey(requestError.value, hasMessage)),
)
const selectedItem = ref<JourneyTimelineItem | null>(null)
const removeDialog = ref<'confirm' | 'blocked' | 'unavailable' | null>(null)
const removeError = ref<string | null>(null)

const removeMutation = useMutation({
  mutationFn: async () => {
    if (tripId.value === null || selectedItem.value === null) {
      throw new Error('A valid journey item is required.')
    }
    await deleteJourneyItem(tripId.value, selectedItem.value.tripItemId)
  },
  onSuccess: async () => {
    const id = tripId.value
    removeDialog.value = null
    selectedItem.value = null
    if (id === null) return
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: journeyListKeys.list() }),
      queryClient.invalidateQueries({ queryKey: journeyKeys.detail(id) }),
      queryClient.invalidateQueries({ queryKey: journeyKeys.timeline(id) }),
    ])
  },
  onError: (error) => {
    if (error instanceof NormalizedApiError && error.code === 'JOURNEY-011') {
      removeDialog.value = 'blocked'
      return
    }
    if (
      error instanceof NormalizedApiError &&
      (error.code === 'JOURNEY-010' || error.code === 'APPOINTMENT-007')
    ) {
      removeDialog.value = 'unavailable'
      return
    }
    removeError.value = t(journeyErrorMessageKey(error, hasMessage))
  },
})

const { useReportSummariesQuery } = useJourneyReportIntegration()
const reportsQuery = useReportSummariesQuery()
const journeyEnded = computed(() => {
  const journey = detailQuery.data.value
  return journey !== undefined && getJourneyStatus(journey.endDate) === 'past'
})
const matchingReport = computed(() => {
  const journey = detailQuery.data.value
  if (journey === undefined) {
    return null
  }

  return reportsQuery.data.value?.find((report) => report.tripId === journey.tripId) ?? null
})

function retryAll(): void {
  void detailQuery.refetch()
  void timelineQuery.refetch()
}

function viewReport(reportId: number): void {
  void router.push({ name: 'report-detail', params: { reportId } })
}

function createReport(): void {
  const journey = detailQuery.data.value
  if (journey === undefined) {
    return
  }

  void router.push({ name: 'report-list', query: { tripId: journey.tripId } })
}

function requestRemove(item: JourneyTimelineItem): void {
  selectedItem.value = item
  removeError.value = null
  removeDialog.value = 'confirm'
}

function closeRemoveDialog(): void {
  if (!removeMutation.isPending.value) {
    removeDialog.value = null
    selectedItem.value = null
  }
}
</script>

<template>
  <main class="flex w-full flex-col gap-6 px-screen py-8">
    <section
      v-if="tripId === null"
      role="alert"
    >
      <StateEmpty
        :title="t('journey.detail.invalidTitle')"
        :description="t('journey.detail.invalidDescription')"
      />
    </section>

    <StateLoading
      v-else-if="detailQuery.isPending.value"
      :label="t('state.loading')"
    />

    <section
      v-else-if="forbidden"
      role="alert"
    >
      <StateEmpty
        :title="t('journey.detail.accessDeniedTitle')"
        :description="t('journey.detail.accessDeniedDescription')"
      />
    </section>

    <StateError
      v-else-if="detailQuery.isError.value"
      :title="t('journey.detail.loadFailed')"
      :description="requestErrorDescription"
      :action-label="t('journey.detail.retry')"
      @retry="retryAll"
    />

    <template v-else-if="detailQuery.data.value !== undefined">
      <JourneySummary :journey="detailQuery.data.value">
        <RouterLink
          :to="{
            name: 'journey-settings',
            params: { tripId: detailQuery.data.value.tripId },
          }"
          :aria-label="t('journey.settings.open')"
          class="flex size-11 shrink-0 items-center justify-center rounded-pill bg-surface-1 text-ink"
        >
          <IconSettings
            :size="20"
            :stroke-width="1.75"
            aria-hidden="true"
          />
        </RouterLink>
      </JourneySummary>

      <section v-if="journeyEnded">
        <StateLoading
          v-if="reportsQuery.isPending.value"
          :label="t('journey.detail.reportChecking')"
        />

        <StateError
          v-else-if="reportsQuery.isError.value"
          :title="t('journey.detail.reportLoadFailed')"
          :description="t('journey.detail.reportLoadFailedDescription')"
          :action-label="t('action.retry')"
          @retry="reportsQuery.refetch"
        />

        <AppButton
          v-else-if="matchingReport !== null"
          variant="secondary"
          block
          @click="viewReport(matchingReport.reportId)"
        >
          {{ t('journey.detail.viewReport') }}
        </AppButton>
        <AppButton
          v-else
          variant="secondary"
          block
          @click="createReport"
        >
          {{ t('journey.detail.createReport') }}
        </AppButton>
      </section>

      <section
        class="flex flex-col gap-4"
        aria-labelledby="journey-timeline-title"
      >
        <h2
          id="journey-timeline-title"
          class="font-display text-section-header uppercase text-ink-display"
        >
          {{ t('journey.detail.timeline') }}
        </h2>

        <StateLoading
          v-if="timelineQuery.isPending.value"
          :label="t('journey.detail.timelineLoading')"
        />

        <section
          v-else-if="isJourneyForbidden(timelineQuery.error.value)"
          role="alert"
        >
          <StateEmpty
            :title="t('journey.detail.accessDeniedTitle')"
            :description="t('journey.detail.accessDeniedDescription')"
          />
        </section>

        <StateError
          v-else-if="timelineQuery.isError.value"
          :title="t('journey.detail.loadFailed')"
          :description="requestErrorDescription"
          :action-label="t('journey.detail.retry')"
          @retry="retryAll"
        />

        <JourneyTimelineList
          v-else-if="timelineQuery.data.value !== undefined"
          :days="timelineQuery.data.value.timeline"
          :trip-id="detailQuery.data.value.tripId"
          :start-date="detailQuery.data.value.startDate"
          :end-date="detailQuery.data.value.endDate"
          :removing-trip-item-id="removeMutation.isPending.value ? selectedItem?.tripItemId : null"
          @remove="requestRemove"
        />
      </section>
    </template>

    <JourneyDialog
      v-if="removeDialog === 'confirm' && selectedItem !== null"
      id="remove-journey-item-dialog"
      :title="t('journey.remove.title')"
      :description="
        selectedItem.status === 'CONFIRMED'
          ? t('journey.remove.confirmedDescription', {
              title: selectedItem.exploreItem.title,
            })
          : t('journey.remove.description', { title: selectedItem.exploreItem.title })
      "
      :confirm-label="t('journey.remove.delete')"
      :cancel-label="t('journey.remove.cancel')"
      :pending="removeMutation.isPending.value"
      destructive
      @confirm="removeMutation.mutate()"
      @cancel="closeRemoveDialog"
    >
      <p
        v-if="removeError !== null"
        class="text-body-sm text-danger"
        role="alert"
      >
        {{ removeError }}
      </p>
    </JourneyDialog>

    <JourneyDialog
      v-else-if="removeDialog === 'blocked'"
      id="remove-journey-item-blocked-dialog"
      :title="t('journey.remove.blockedTitle')"
      :description="t('journey.remove.blockedDescription')"
      :confirm-label="t('action.close')"
      single-action
      @confirm="closeRemoveDialog"
      @cancel="closeRemoveDialog"
    />

    <JourneyDialog
      v-else-if="removeDialog === 'unavailable'"
      id="remove-journey-item-unavailable-dialog"
      :title="t('journey.remove.unavailableTitle')"
      :description="t('journey.remove.unavailableDescription')"
      :confirm-label="t('action.close')"
      single-action
      @confirm="
        () => {
          closeRemoveDialog()
          timelineQuery.refetch()
        }
      "
      @cancel="closeRemoveDialog"
    />
  </main>
</template>
