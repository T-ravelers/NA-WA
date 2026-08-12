<script setup lang="ts">
import { computed } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'

import AppButton from '@/shared/ui/AppButton.vue'
import StateEmpty from '@/shared/ui/StateEmpty.vue'
import StateError from '@/shared/ui/StateError.vue'
import StateLoading from '@/shared/ui/StateLoading.vue'

import JourneySummary from '../components/JourneySummary.vue'
import JourneyTimelineList from '../components/JourneyTimelineList.vue'
import { journeyErrorMessageKey, isJourneyForbidden } from '../model/journeyErrors'
import { getJourneyStatus } from '../model/journeyStatus'
import { journeyDetailQueryOptions, journeyTimelineQueryOptions } from '../model/journeyQueries'
import { useJourneyReportIntegration } from '../model/reportIntegration'

const i18n = useI18n()
const { t } = i18n
const route = useRoute()
const router = useRouter()
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
</script>

<template>
  <main class="flex w-full flex-col gap-8 px-screen py-8">
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
      <JourneySummary :journey="detailQuery.data.value" />

      <section v-if="journeyEnded">
        <AppButton
          v-if="matchingReport !== null"
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
          class="text-title text-ink"
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

        <StateEmpty
          v-else-if="timelineQuery.data.value?.timeline.length === 0"
          :title="t('journey.detail.timelineEmptyTitle')"
          :description="t('journey.detail.timelineEmptyDescription')"
        />

        <JourneyTimelineList
          v-else-if="timelineQuery.data.value !== undefined"
          :days="timelineQuery.data.value.timeline"
        />
      </section>
    </template>
  </main>
</template>
