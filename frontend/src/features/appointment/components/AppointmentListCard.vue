<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'

import { formatServerDateTime, parseServerDateTime } from '@/shared/lib/datetime'
import AppBadge from '@/shared/ui/AppBadge.vue'
import AppButton from '@/shared/ui/AppButton.vue'
import AppCard from '@/shared/ui/AppCard.vue'

import type { AppointmentSummary } from '../api/appointmentApi'
import { appointmentStatusTone } from '../model/appointmentStatusPresentation'

interface Props {
  appointment: AppointmentSummary
}

const { appointment } = defineProps<Props>()
const router = useRouter()
const { t, locale } = useI18n()

const scheduleLabel = computed(() => {
  const start = parseServerDateTime(appointment.activityStartAt)
  const end = parseServerDateTime(appointment.activityEndAt)

  if (!start || !end) return t('appointment.list.scheduleUnavailable')

  const dateOptions = { month: 'numeric' as const, day: 'numeric' as const }
  const timeOptions = { hour: 'numeric' as const, minute: '2-digit' as const }
  const startDate = formatServerDateTime(start, locale.value, dateOptions)
  const endDate = formatServerDateTime(end, locale.value, dateOptions)
  const startTime = formatServerDateTime(start, locale.value, timeOptions)
  const endTime = formatServerDateTime(end, locale.value, timeOptions)
  if (!startDate || !endDate || !startTime || !endTime) {
    return t('appointment.list.scheduleUnavailable')
  }

  return `${startDate} ${startTime} – ${endDate} ${endTime}`
})

const memberLabel = computed(() =>
  t('appointment.list.memberCount', {
    current: appointment.currentMemberCount,
    max: appointment.maxMembers,
  }),
)

function openDetail(): void {
  void router.push({
    name: 'appointment-detail',
    params: { appointmentId: appointment.appointmentId },
  })
}

function formatDeposit(value: string): string {
  const amount = Number(value)
  return Number.isFinite(amount) ? new Intl.NumberFormat(locale.value).format(amount) : value
}
</script>

<template>
  <AppCard padding="lg">
    <article class="flex flex-col gap-3">
      <div class="flex items-start justify-between gap-3">
        <div class="min-w-0">
          <h3 class="truncate text-title text-ink">{{ appointment.appointmentName }}</h3>
          <p class="mt-1 text-body-sm text-ink-2">{{ scheduleLabel }}</p>
        </div>
        <AppBadge
          :tone="appointmentStatusTone(appointment.appointmentStatus)"
          dot
        >
          {{ t(`appointment.status.${appointment.appointmentStatus}`) }}
        </AppBadge>
      </div>

      <p class="text-body-sm text-ink-2">
        {{ appointment.meetingPlace ?? t('appointment.list.placePending') }}
        <span aria-hidden="true"> · </span>
        {{ t(`appointment.languages.${appointment.languageCode}`) }}
        <span aria-hidden="true"> · </span>
        {{ memberLabel }}
      </p>

      <div class="flex items-center justify-between gap-3">
        <p class="min-w-0 truncate text-caption text-ink-3">
          {{ t('appointment.list.deposit', { amount: formatDeposit(appointment.depositAmount) }) }}
        </p>
        <div class="w-24 shrink-0">
          <AppButton
            block
            compact
            dense
            @click="openDetail"
          >
            {{ t('appointment.list.view') }}
          </AppButton>
        </div>
      </div>
    </article>
  </AppCard>
</template>
