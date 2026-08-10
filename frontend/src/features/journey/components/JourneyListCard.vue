<script setup lang="ts">
import { RouterLink } from 'vue-router'

import AppBadge from '@/shared/ui/AppBadge.vue'
import AppCard from '@/shared/ui/AppCard.vue'

import type { JourneySummary } from '../api/journeyApi'
import { formatJourneyDate, type JourneyListStatus } from '../model/journeyStatus'

interface Props {
  journey: JourneySummary
  status: JourneyListStatus
  statusLabel: string
}

const { journey, status, statusLabel } = defineProps<Props>()
</script>

<template>
  <li>
    <AppCard padding="lg">
      <RouterLink
        :to="{ name: 'journey-detail', params: { tripId: journey.tripId } }"
        class="block rounded-card focus-visible:outline-2 focus-visible:outline-ink"
      >
        <div class="flex items-start justify-between gap-4">
          <h3 class="min-w-0 flex-1 text-title text-ink">
            {{ journey.title }}
          </h3>
          <AppBadge
            :tone="status === 'ongoing' ? 'ongoing' : 'neutral'"
            dot
          >
            {{ statusLabel }}
          </AppBadge>
        </div>
        <p class="mt-4 text-body-sm text-ink-2">
          <time :datetime="journey.startDate">{{ formatJourneyDate(journey.startDate) }}</time>
          <span aria-hidden="true"> – </span>
          <time :datetime="journey.endDate">{{ formatJourneyDate(journey.endDate) }}</time>
        </p>
      </RouterLink>
    </AppCard>
  </li>
</template>
