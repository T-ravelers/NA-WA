<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'

import { formatCalendarDate, parseCalendarDate, serializeCalendarDate } from '@/shared/lib/datetime'
import AppButton from '@/shared/ui/AppButton.vue'
import CalendarGrid from '@/shared/ui/CalendarGrid.vue'

interface Props {
  itemTitle: string
  itemLocation?: string
  startDate: string | null
  endDate: string | null
  isPermanent: boolean
  initialDate?: string | null
  loading?: boolean
  confirmDisabled?: boolean
  errorMessage?: string | null
}

const props = defineProps<Props>()

const emit = defineEmits<{
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

const selectedDateLabel = computed(() => {
  if (!selectedDate.value) return t('explore.journeyDate.chooseDate')
  const date = parseDate(selectedDate.value)
  return date
    ? formatCalendarDate(date, locale.value, { month: 'short', day: 'numeric' })
    : t('explore.journeyDate.chooseDate')
})

const description = computed(() =>
  props.itemLocation
    ? t('explore.journeyDate.description', {
        title: props.itemTitle,
        location: props.itemLocation,
      })
    : t('explore.journeyDate.descriptionWithoutLocation', { title: props.itemTitle }),
)

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
      <p
        v-else-if="props.confirmDisabled"
        class="mt-3 text-caption text-ink-3"
        role="note"
      >
        {{ t('explore.journeyDate.unavailable') }}
      </p>

      <AppButton
        block
        class="mt-5"
        :disabled="selectedDate === null || props.loading || props.confirmDisabled"
        :loading="props.loading"
        @click="confirm"
      >
        {{ t('explore.journeyDate.apply', { date: selectedDateLabel }) }}
      </AppButton>
    </section>
  </div>
</template>
