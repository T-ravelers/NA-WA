<script setup lang="ts">
import { IconCheck } from '@tabler/icons-vue'
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

import AppButton from '@/shared/ui/AppButton.vue'

import { intersectItemJourneyPeriod } from '../model/journeyPeriod'
import type { ExploreJourneySummary } from '../model/journeyIntegration'

interface Props {
  journeys: ExploreJourneySummary[]
  /** 담을 항목의 운영 기간. 운영 기간이 없는 Place는 양쪽이 `null`이다. */
  itemStartDate: string | null
  itemEndDate: string | null
  selectedJourneyId?: number | null
  loading?: boolean
  errorMessage?: string | null
}

const props = defineProps<Props>()

const emit = defineEmits<{
  close: []
  select: [journeyId: number]
  createJourney: []
}>()

const { t } = useI18n()

/**
 * 기간이 겹치지 않는 여정은 골라도 담을 수 없다. 목록에서 **감추지 않고** 고를 수
 * 없다는 것만 보인다 — 사라지면 사용자는 자기 여정이 없어진 줄 안다.
 */
const selectableJourneyIds = computed(
  () =>
    new Set(
      props.journeys
        .filter(
          (journey) =>
            intersectItemJourneyPeriod(
              { startDate: props.itemStartDate, endDate: props.itemEndDate },
              journey,
            ) !== null,
        )
        .map((journey) => journey.tripId),
    ),
)

function isSelectable(journeyId: number): boolean {
  return selectableJourneyIds.value.has(journeyId)
}

/**
 * 목록이 비었을 때만이 아니라 **고를 수 있는 여정이 하나도 없을 때도** 여기서 나가야
 * 한다. 둘 다 사용자에게는 담을 여정이 없는 것으로 똑같다.
 */
const noJourneyAvailable = computed(
  () => props.journeys.length === 0 || selectableJourneyIds.value.size === 0,
)
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
      <template v-else>
        <div
          v-if="journeys.length > 0"
          class="mt-4 flex max-h-[45dvh] flex-col gap-2 overflow-y-auto"
        >
          <button
            v-for="journey in journeys"
            :key="journey.tripId"
            type="button"
            class="flex items-center justify-between rounded-sm border px-4 py-3 text-left transition-colors"
            :class="[
              selectedJourneyId === journey.tripId
                ? 'border-paper-fill bg-paper-fill text-on-paper'
                : 'border-hairline-2 bg-transparent text-ink',
              !isSelectable(journey.tripId) && 'opacity-40',
            ]"
            :disabled="!isSelectable(journey.tripId)"
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
              <span
                v-if="!isSelectable(journey.tripId)"
                class="text-caption text-ink-3"
              >
                {{ t('explore.journeySelect.outsideItemPeriod') }}
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

        <div
          v-if="noJourneyAvailable"
          class="flex flex-col items-center gap-4 py-10 text-center"
        >
          <p class="text-body-sm text-ink-3">
            {{
              journeys.length === 0
                ? t('explore.journeySelect.empty')
                : t('explore.journeySelect.noneAvailable')
            }}
          </p>
          <AppButton
            type="button"
            @click="emit('createJourney')"
          >
            {{ t('explore.journeySelect.createJourney') }}
          </AppButton>
        </div>
      </template>
    </section>
  </div>
</template>
