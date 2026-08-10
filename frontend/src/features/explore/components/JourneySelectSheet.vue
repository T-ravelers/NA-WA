<script setup lang="ts">
import { IconCheck } from '@tabler/icons-vue'
import { useI18n } from 'vue-i18n'

import type { ExploreJourneySummary } from '../model/journeyIntegration'

interface Props {
  journeys: ExploreJourneySummary[]
  selectedJourneyId?: number | null
  loading?: boolean
  errorMessage?: string | null
}

defineProps<Props>()

const emit = defineEmits<{
  close: []
  select: [journeyId: number]
}>()

const { t } = useI18n()
</script>

<template>
  <div class="fixed inset-0 z-40">
    <button
      type="button"
      class="absolute inset-0 bg-scrim/70"
      :aria-label="t('explore.journeySelect.close')"
      @click="emit('close')"
    />

    <section
      role="dialog"
      aria-modal="true"
      :aria-label="t('explore.journeySelect.title')"
      class="absolute inset-x-0 bottom-0 z-10 mx-auto flex max-h-[78dvh] w-full max-w-[390px] flex-col rounded-t-lg bg-surface-1 px-screen pt-3 pb-6 shadow-sheet"
    >
      <span
        aria-hidden="true"
        class="mb-4 h-1 w-10 shrink-0 self-center rounded-pill bg-hairline-2"
      />

      <header class="flex flex-col gap-1">
        <h2 class="font-display text-section-header uppercase text-ink-display">
          {{ t('explore.journeySelect.title') }}
        </h2>
        <p class="text-body-sm text-ink-3">{{ t('explore.journeySelect.description') }}</p>
      </header>

      <p
        v-if="loading"
        class="py-10 text-center text-body-sm text-ink-3"
      >
        {{ t('explore.journeySelect.loading') }}
      </p>
      <p
        v-else-if="errorMessage"
        class="py-10 text-center text-body-sm text-danger"
        role="alert"
      >
        {{ errorMessage }}
      </p>
      <p
        v-else-if="journeys.length === 0"
        class="py-10 text-center text-body-sm text-ink-3"
      >
        {{ t('explore.journeySelect.empty') }}
      </p>
      <div
        v-else
        class="mt-4 flex max-h-[45dvh] flex-col gap-2 overflow-y-auto"
      >
        <button
          v-for="journey in journeys"
          :key="journey.tripId"
          type="button"
          class="flex items-center justify-between rounded-sm border px-4 py-3 text-left transition-colors"
          :class="
            selectedJourneyId === journey.tripId
              ? 'border-paper-fill bg-paper-fill text-on-paper'
              : 'border-hairline-2 bg-transparent text-ink'
          "
          :aria-pressed="selectedJourneyId === journey.tripId"
          @click="emit('select', journey.tripId)"
        >
          <span class="flex min-w-0 flex-col gap-1">
            <strong class="truncate text-title-sm">{{ journey.title }}</strong>
            <span
              class="text-caption"
              :class="selectedJourneyId === journey.tripId ? 'text-on-paper/70' : 'text-ink-3'"
            >
              {{ journey.startDate }} – {{ journey.endDate }}
            </span>
          </span>
          <span
            class="flex size-6 shrink-0 items-center justify-center rounded-pill"
            :class="
              selectedJourneyId === journey.tripId
                ? 'bg-on-paper text-paper-fill'
                : 'border border-hairline-2'
            "
          >
            <IconCheck
              v-if="selectedJourneyId === journey.tripId"
              :size="15"
              :stroke-width="2.5"
              aria-hidden="true"
            />
          </span>
        </button>
      </div>
    </section>
  </div>
</template>
