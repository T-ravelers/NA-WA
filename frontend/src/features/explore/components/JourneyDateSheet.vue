<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { IconChevronLeft, IconChevronRight } from '@tabler/icons-vue'

import AppButton from '@/shared/ui/AppButton.vue'

interface Props {
  eventTitle: string
  eventLocation?: string
  startDate: string | null
  endDate: string | null
  isPermanent: boolean
  initialDate?: string | null
}

const props = defineProps<Props>()

const emit = defineEmits<{
  close: []
  confirm: [date: string]
}>()

const { locale, t } = useI18n()

function today(): string {
  return formatDate(new Date())
}

function parseDate(value: string | null | undefined): Date | null {
  if (!value) return null
  const parts = value.split('-')
  if (parts.length !== 3) return null
  const year = Number(parts[0])
  const month = Number(parts[1])
  const day = Number(parts[2])
  if ([year, month, day].some((part) => Number.isNaN(part))) return null
  return new Date(year, month - 1, day)
}

function formatDate(date: Date): string {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function isDateAllowed(value: string): boolean {
  if (props.isPermanent) return true
  if (props.startDate && value < props.startDate) return false
  if (props.endDate && value > props.endDate) return false
  return true
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
  if (!selectedDate.value) return t('explore.journeyDate.chooseDate')
  const date = parseDate(selectedDate.value)
  return date
    ? new Intl.DateTimeFormat(locale.value, { month: 'short', day: 'numeric' }).format(date)
    : t('explore.journeyDate.chooseDate')
})

const description = computed(() =>
  props.eventLocation
    ? t('explore.journeyDate.description', {
        title: props.eventTitle,
        location: props.eventLocation,
      })
    : t('explore.journeyDate.descriptionWithoutLocation', { title: props.eventTitle }),
)

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
    cells.push({ date: formatDate(date), day, inMonth })
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
  return date
    ? new Intl.DateTimeFormat(locale.value, {
        dateStyle: 'long',
      }).format(date)
    : value
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
      :aria-label="t('explore.journeyDate.close')"
      @click="emit('close')"
    />

    <section
      role="dialog"
      aria-modal="true"
      :aria-label="t('explore.journeyDate.title')"
      class="absolute inset-x-0 bottom-0 z-10 mx-auto flex max-h-[88dvh] w-full max-w-[390px] flex-col rounded-t-lg bg-surface-1 px-screen pt-3 pb-6 shadow-sheet"
    >
      <span
        aria-hidden="true"
        class="mb-4 h-1 w-10 shrink-0 self-center rounded-pill bg-hairline-2"
      />

      <header class="flex flex-col gap-1">
        <h2 class="font-display text-section-header uppercase text-ink-display">
          {{ t('explore.journeyDate.title') }}
        </h2>
        <p class="text-body-sm text-ink-3">{{ description }}</p>
      </header>

      <div class="mt-5 flex items-center justify-between">
        <button
          type="button"
          class="flex size-8 items-center justify-center text-ink-2"
          :aria-label="t('explore.journeyDate.previousMonth')"
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
          :aria-label="t('explore.journeyDate.nextMonth')"
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
          >{{ t(`explore.calendar.weekdays.${day}`) }}</span
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
            t('explore.journeyDate.selectDate', { date: accessibleDateLabel(cell.date) })
          "
          :aria-pressed="selectedDate === cell.date"
          :disabled="!cell.inMonth || !isDateAllowed(cell.date)"
          @click="selectDate(cell.date)"
        >
          {{ cell.day }}
        </button>
      </div>

      <AppButton
        block
        class="mt-5"
        :disabled="selectedDate === null"
        @click="confirm"
      >
        {{ t('explore.journeyDate.apply', { date: selectedDateLabel }) }}
      </AppButton>
    </section>
  </div>
</template>
