<script setup lang="ts">
import { IconChevronLeft } from '@tabler/icons-vue'
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'

import { formatCalendarDate, parseCalendarDate, serializeCalendarDate } from '@/shared/lib/datetime'
import AppButton from '@/shared/ui/AppButton.vue'
import CalendarGrid from '@/shared/ui/CalendarGrid.vue'

interface Props {
  journeyTitle: string
  /** 선택한 여정의 기간. 이 범위 밖의 날짜는 고를 수 없다. */
  startDate: string
  endDate: string
  initialDate?: string | null
  loading?: boolean
  /** 이미 있는 (여정, 항목, 날짜) 조합을 확인 후 채워지는 에러. 시트는 닫히지 않는다. */
  errorMessage?: string | null
}

const props = defineProps<Props>()

const emit = defineEmits<{
  /** 뒤로가기 화살표 또는 백드롭 클릭 — 여정 선택 시트로 돌아간다. 확인 모달 없음. */
  close: []
  confirm: [date: string]
}>()

const { locale, t } = useI18n()

function today(): string {
  return serializeCalendarDate(new Date())
}

function parseDate(value: string | null | undefined): Date | null {
  return parseCalendarDate(value)
}

function isDateAllowed(value: string): boolean {
  // 여정 기간 안이라도 진행 중인(시작일이 과거인) 여정이면 지난 날짜가 섞여
  // 있을 수 있다. 지난 날짜를 고르면 백엔드의 "활동 시작이 현재 이후" 검증에
  // 항상 걸려 제출이 실패하므로, 여기서 오늘 이전 날짜를 먼저 걸러낸다.
  const lowerBound = props.startDate > today() ? props.startDate : today()
  return value >= lowerBound && value <= props.endDate
}

function getInitialDate(): string | null {
  const candidates = [props.initialDate, today(), props.startDate, props.endDate]
  return (
    candidates.find(
      (value): value is string => value !== null && value !== undefined && isDateAllowed(value),
    ) ?? null
  )
}

const selectedDate = ref<string | null>(getInitialDate())

const selectedDateLabel = computed(() => {
  if (!selectedDate.value) return t('appointment.journeyDate.chooseDate')
  const date = parseDate(selectedDate.value)
  return date
    ? formatCalendarDate(date, locale.value, { month: 'short', day: 'numeric' })
    : t('appointment.journeyDate.chooseDate')
})

function selectDate(value: string): void {
  if (isDateAllowed(value)) selectedDate.value = value
}

function confirm(): void {
  if (selectedDate.value) emit('confirm', selectedDate.value)
}
</script>

<template>
  <div class="fixed inset-0 z-40">
    <button
      type="button"
      class="absolute inset-0 bg-scrim/70"
      :aria-label="t('appointment.journeyDate.close')"
      @click="emit('close')"
    />

    <section
      role="dialog"
      aria-modal="true"
      :aria-label="t('appointment.journeyDate.title')"
      class="absolute inset-x-0 bottom-0 z-10 mx-auto flex max-h-[88dvh] w-full max-w-[390px] flex-col rounded-t-lg bg-canvas px-screen pt-3 pb-6 shadow-sheet"
    >
      <span
        aria-hidden="true"
        class="mb-4 h-1 w-10 shrink-0 self-center rounded-pill bg-hairline-2"
      />

      <header class="flex flex-col gap-1">
        <div class="flex items-center gap-3">
          <button
            type="button"
            :aria-label="t('appointment.journeyDate.back')"
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
            {{ t('appointment.journeyDate.title') }}
          </h2>
        </div>
        <p class="pl-12 text-body-sm text-ink-3">
          {{ t('appointment.journeyDate.description', { journey: journeyTitle }) }}
        </p>
      </header>

      <CalendarGrid
        class="mt-5"
        :selected="selectedDate"
        :is-date-allowed="isDateAllowed"
        @select="selectDate"
      />

      <p
        v-if="props.errorMessage"
        class="mt-3 text-caption text-danger"
        role="alert"
      >
        {{ props.errorMessage }}
      </p>

      <AppButton
        block
        class="mt-5"
        :disabled="selectedDate === null || props.loading"
        :loading="props.loading"
        @click="confirm"
      >
        {{ t('appointment.journeyDate.apply', { date: selectedDateLabel }) }}
      </AppButton>
    </section>
  </div>
</template>
