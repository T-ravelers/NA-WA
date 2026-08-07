<script setup lang="ts">
import { computed } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'

import StateEmpty from '@/shared/ui/StateEmpty.vue'
import StateError from '@/shared/ui/StateError.vue'
import StateLoading from '@/shared/ui/StateLoading.vue'

import JourneySummary from '../components/JourneySummary.vue'
import JourneyTimelineList from '../components/JourneyTimelineList.vue'
import { journeyErrorMessageKey, isJourneyForbidden } from '../model/journeyErrors'
import { journeyDetailQueryOptions, journeyTimelineQueryOptions } from '../model/journeyQueries'

const { t } = useI18n()
const route = useRoute()

const tripId = computed(() => {
  const rawTripId = Array.isArray(route.params.tripId)
    ? route.params.tripId[0]
    : route.params.tripId
  const parsedTripId = Number(rawTripId)

  return Number.isSafeInteger(parsedTripId) && parsedTripId > 0 ? parsedTripId : null
})

const detailQuery = useQuery({
  ...journeyDetailQueryOptions(tripId.value ?? 0),
  enabled: computed(() => tripId.value !== null),
  retry: false,
})

const timelineQuery = useQuery({
  ...journeyTimelineQueryOptions(tripId.value ?? 0),
  enabled: computed(() => tripId.value !== null),
  retry: false,
})

const requestError = computed(() => detailQuery.error.value ?? timelineQuery.error.value)
const forbidden = computed(() => isJourneyForbidden(requestError.value))
const requestErrorDescription = computed(() => t(journeyErrorMessageKey(requestError.value)))

function retryAll(): void {
  void detailQuery.refetch()
  void timelineQuery.refetch()
}
</script>

<template>
  <main class="mx-auto flex w-full max-w-screen-sm flex-col gap-8 px-5 py-8">
    <section
      v-if="tripId === null"
      class="flex flex-col gap-2 py-12 text-center"
      role="alert"
    >
      <h1 class="text-title text-ink">{{ t('journey.detail.invalidTitle') }}</h1>
      <p class="text-body-sm text-ink-3">{{ t('journey.detail.invalidDescription') }}</p>
    </section>

    <StateLoading
      v-else-if="detailQuery.isPending.value"
      :label="t('state.loading')"
    />

    <section
      v-else-if="forbidden"
      class="flex flex-col gap-2 py-12 text-center"
      role="alert"
    >
      <h1 class="text-title text-ink">{{ t('journey.detail.accessDeniedTitle') }}</h1>
      <p class="text-body-sm text-ink-3">{{ t('journey.detail.accessDeniedDescription') }}</p>
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
          class="flex flex-col gap-2 py-12 text-center"
          role="alert"
        >
          <h3 class="text-title text-ink">{{ t('journey.detail.accessDeniedTitle') }}</h3>
          <p class="text-body-sm text-ink-3">{{ t('journey.detail.accessDeniedDescription') }}</p>
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
