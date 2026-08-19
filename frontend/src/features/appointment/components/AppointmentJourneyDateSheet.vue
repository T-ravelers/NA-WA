<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { IconChevronLeft, IconChevronRight } from '@tabler/icons-vue'

import { formatCalendarDate, parseCalendarDate, serializeCalendarDate } from '@/shared/lib/datetime'
import AppButton from '@/shared/ui/AppButton.vue'

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
  return value >= props.startDate && value <= props.endDate
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
const monthCursor = ref(
  parseDate(selectedDate.value) ?? new Date(new Date().getFullYear(), new Date().getMonth(), 1),
)

const monthLabel = computed(() =>
  new Intl.DateTimeFormat(locale.value, { month: 'long', year: 'numeric' }).format(
    monthCursor.value,
  ),
)

const selectedDateLabel = computed(() => {
  if (!selectedDate.value) return t('appointment.journeyDate.chooseDate')
  const date = parseDate(selectedDate.value)
  return date
    ? formatCalendarDate(date, locale.value, { month: 'short', day: 'numeric' })
    : t('appointment.journeyDate.chooseDate')
})

const calendarDays = computed(() => {
  const year = monthCursor.value.getFullYear()
  const month = monthCursor.value.getMonth()
  const firstDay = new Date(year, month, 1)
  const startOffset = firstDay.getDay()
  const daysInMonth = new Date(year, month + 1, 0).getDate()
  const previousMonthDays = new Date(year, month, 0).getDate()
  const cells: Array<{ date: string; day: number; inMonth: boolean }> = []

  for (let index = 0; index < 42; index += 1) {
    const rawDay = index - startOffset + 1
    const inMonth = rawDay >= 1 && rawDay <= daysInMonth
    const date = new Date(year, month, rawDay)
    const day = inMonth ? rawDay : rawDay < 1 ? previousMonthDays + rawDay : rawDay - daysInMonth
    cells.push({ date: serializeCalendarDate(date), day, inMonth })
  }

  return cells
})

function shiftMonth(offset: number): void {
  monthCursor.value = new Date(
    monthCursor.value.getFullYear(),
    monthCursor.value.getMonth() + offset,
    1,
  )
}

function selectDate(value: string): void {
  if (isDateAllowed(value)) selectedDate.value = value
}

function accessibleDateLabel(value: string): string {
  const date = parseDate(value)
  return date ? formatCalendarDate(date, locale.value, { dateStyle: 'long' }) : value
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
      class="absolute inset-x-0 bottom-0 z-10 mx-auto flex max-h-[88dvh] w-full max-w-[390px] flex-col rounded-t-lg bg-surface-1 px-screen pt-3 pb-6 shadow-sheet"
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

      <div class="mt-5 flex items-center justify-between">
        <button
          type="button"
          class="flex size-8 items-center justify-center text-ink-2"
          :aria-label="t('appointment.journeyDate.previousMonth')"
          @click="shiftMonth(-1)"
        >
          <IconChevronLeft
            :size="18"
            aria-hidden="true"
          />
        </button>
        <strong class="text-title-sm text-ink">{{ monthLabel }}</strong>
        <button
          type="button"
          class="flex size-8 items-center justify-center text-ink-2"
          :aria-label="t('appointment.journeyDate.nextMonth')"
          @click="shiftMonth(1)"
        >
          <IconChevronRight
            :size="18"
            aria-hidden="true"
          />
        </button>
      </div>

      <div class="mt-3 grid grid-cols-7 text-center text-micro text-ink-3">
        <span
          v-for="day in ['sun', 'mon', 'tue', 'wed', 'thu', 'fri', 'sat']"
          :key="day"
          >{{ t(`appointment.calendar.weekdays.${day}`) }}</span
        >
      </div>
      <div class="mt-2 grid grid-cols-7 gap-y-1 text-center">
        <button
          v-for="cell in calendarDays"
          :key="cell.date"
          type="button"
          class="mx-auto flex size-9 items-center justify-center rounded-pill text-caption"
          :class="[
            !cell.inMonth || !isDateAllowed(cell.date) ? 'text-ink-3/40' : 'text-ink-2',
            selectedDate === cell.date && 'bg-paper-fill text-on-paper',
          ]"
          :aria-label="
            t('appointment.journeyDate.selectDate', { date: accessibleDateLabel(cell.date) })
          "
          :aria-pressed="selectedDate === cell.date"
          :disabled="!cell.inMonth || !isDateAllowed(cell.date)"
          @click="selectDate(cell.date)"
        >
          {{ cell.day }}
        </button>
      </div>

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
