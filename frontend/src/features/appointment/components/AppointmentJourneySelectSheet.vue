<script setup lang="ts">
import { IconCheck, IconChevronLeft } from '@tabler/icons-vue'
import { useI18n } from 'vue-i18n'

import AppButton from '@/shared/ui/AppButton.vue'

import type { AppointmentJourneySummary } from '../model/journeyIntegration'

interface Props {
  journeys: AppointmentJourneySummary[]
  selectedJourneyId?: number | null
  loading?: boolean
  errorMessage?: string | null
  /**
   * 고른 여정이 이 약속을 담을 수 없을 때의 안내. errorMessage와 달리 목록을
   * 대신하지 않고 목록 위에 붙는다 — 다른 여정을 바로 고를 수 있어야 한다.
   */
  selectionError?: string | null
  /** 고를 여정이 없을 때의 안내. 생성 흐름은 기본 문구를 그대로 쓴다. */
  emptyMessage?: string | null
}

defineProps<Props>()

const emit = defineEmits<{
  /** 뒤로가기 화살표 또는 백드롭 클릭 — 부모가 나가기 확인 모달을 띄운다. */
  close: []
  select: [journeyId: number]
  createJourney: []
}>()

const { t } = useI18n()
</script>

<template>
  <div class="fixed inset-0 z-40">
    <button
      type="button"
      class="absolute inset-0 bg-scrim/70"
      :aria-label="t('appointment.journeySelect.close')"
      @click="emit('close')"
    />

    <section
      role="dialog"
      aria-modal="true"
      :aria-label="t('appointment.journeySelect.title')"
      class="absolute inset-x-0 bottom-0 z-10 mx-auto flex max-h-[78dvh] w-full max-w-[390px] flex-col rounded-t-lg bg-canvas px-screen pt-3 pb-6 shadow-sheet"
    >
      <span
        aria-hidden="true"
        class="mb-4 h-1 w-10 shrink-0 self-center rounded-pill bg-hairline-2"
      />

      <header class="flex flex-col gap-1">
        <div class="flex items-center gap-3">
          <button
            type="button"
            :aria-label="t('appointment.journeySelect.back')"
            class="flex size-9 shrink-0 items-center justify-start text-ink"
            @click="emit('close')"
          >
            <IconChevronLeft
              :size="22"
              :stroke-width="1.8"
              aria-hidden="true"
            />
          </button>
          <h2 class="font-display text-section-header uppercase text-ink-display">
            {{ t('appointment.journeySelect.title') }}
          </h2>
        </div>
        <p class="pl-12 text-body-sm text-ink-3">
          {{ t('appointment.journeySelect.description') }}
        </p>
      </header>

      <p
        v-if="loading"
        class="py-10 text-center text-body-sm text-ink-3"
      >
        {{ t('appointment.journeySelect.loading') }}
      </p>
      <p
        v-else-if="errorMessage"
        class="py-10 text-center text-body-sm text-danger"
        role="alert"
      >
        {{ errorMessage }}
      </p>
      <div
        v-else-if="journeys.length === 0"
        class="flex flex-col items-center gap-4 py-10 text-center"
      >
        <p class="text-body-sm text-ink-3">
          {{ emptyMessage ?? t('appointment.journeySelect.empty') }}
        </p>
        <AppButton
          type="button"
          @click="emit('createJourney')"
        >
          {{ t('appointment.journeySelect.createJourney') }}
        </AppButton>
      </div>
      <p
        v-if="!loading && !errorMessage && selectionError"
        role="alert"
        class="mt-4 text-body-sm text-danger"
      >
        {{ selectionError }}
      </p>
      <div
        v-if="!loading && !errorMessage && journeys.length > 0"
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
      <!--
        목록이 비어 있지 않아도 만들러 갈 통로를 남긴다. 이 시트는 담을 수 없는 여정도
        감추지 않고 보여 주므로, 셋 다 날짜가 안 맞는 경우 안내만 세 번 보고 끝나는
        막다른 길이 생긴다.
      -->
      <AppButton
        v-if="!loading && !errorMessage && journeys.length > 0"
        block
        variant="secondary"
        type="button"
        class="mt-4 shrink-0"
        @click="emit('createJourney')"
      >
        {{ t('appointment.journeySelect.createJourney') }}
      </AppButton>
    </section>
  </div>
</template>
