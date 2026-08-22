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

/**
 * 일정은 날짜 한 줄과 시각 범위 한 줄로 나눠 적는다. 상세 화면과 같은 규칙이다.
 *
 * 활동 시작·종료는 서버가 `visitDate` 하루 위에서만 조립하므로(생성 검증도 시작 <
 * 종료를 요구한다) 날짜는 언제나 하나다. 그것을 양쪽에 다 적으면 같은 값이 한 줄에서
 * 반복돼 정작 다른 값인 시각이 묻힌다.
 */
const schedule = computed(() => {
  const start = parseServerDateTime(appointment.activityStartAt)
  const end = parseServerDateTime(appointment.activityEndAt)

  if (!start || !end) return null

  const date = formatServerDateTime(start, locale.value, { dateStyle: 'medium' })
  const timeOptions = { timeStyle: 'short' as const }
  const startTime = formatServerDateTime(start, locale.value, timeOptions)
  const endTime = formatServerDateTime(end, locale.value, timeOptions)
  if (!date || !startTime || !endTime) return null

  return { date, time: `${startTime} – ${endTime}` }
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
          <p
            v-if="schedule"
            class="mt-1 text-body-sm text-ink-2"
          >
            <span class="block">{{ schedule.date }}</span>
            <span class="block">{{ schedule.time }}</span>
          </p>
          <p
            v-else
            class="mt-1 text-body-sm text-ink-2"
          >
            {{ t('appointment.list.scheduleUnavailable') }}
          </p>
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
