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

const emit = defineEmits<{
  join: [appointment: AppointmentSummary]
}>()

const router = useRouter()
const { t, locale } = useI18n()

/**
 * 언제 만나는지. 날짜와 시간을 두 줄로 나눈다 — 시간대까지 한 줄에 담으면 좁은 화면에서
 * 시각이 먼저 잘려 "몇 시부터인지"를 잃는다.
 *
 * 날짜를 넘기는 약속은 날짜 줄에 양쪽을 함께 적는다. 읽을 수 없으면 두 값 모두 비운다.
 */
const schedule = computed(() => {
  const start = parseServerDateTime(appointment.activityStartAt)
  const end = parseServerDateTime(appointment.activityEndAt)

  if (!start || !end) return null

  const dateOptions = { month: 'numeric' as const, day: 'numeric' as const }
  const timeOptions = { hour: 'numeric' as const, minute: '2-digit' as const }
  const startDate = formatServerDateTime(start, locale.value, dateOptions)
  const endDate = formatServerDateTime(end, locale.value, dateOptions)
  const startTime = formatServerDateTime(start, locale.value, timeOptions)
  const endTime = formatServerDateTime(end, locale.value, timeOptions)
  if (!startDate || !endDate || !startTime || !endTime) return null

  return {
    date: startDate === endDate ? startDate : `${startDate} ~ ${endDate}`,
    time: `${startTime} ~ ${endTime}`,
  }
})

const memberLabel = computed(() =>
  t('appointment.list.memberCount', {
    current: appointment.currentMemberCount,
    max: appointment.maxMembers,
  }),
)

const depositLabel = computed(() => {
  const amount = Number(appointment.depositAmount)
  const formatted = Number.isFinite(amount)
    ? new Intl.NumberFormat(locale.value).format(amount)
    : appointment.depositAmount

  return t('appointment.list.deposit', { amount: formatted })
})

const placeLabel = computed(() => appointment.meetingPlace ?? t('appointment.list.placePending'))

/**
 * 참여할 수 없는 약속이면 Join을 비활성으로 둔다. 상세의 `isJoinAvailable`과 같은
 * 기준이다 — 서버가 정원이 찬 약속을 FULL로, 시작된 약속을 IN_PROGRESS로 계산해
 * 내려주므로 RECRUITING이라는 값 자체가 "지금 참여할 수 있다"를 뜻한다.
 *
 * 이유는 바로 옆 상태 뱃지가 이미 말하고 있다. 목록은 "내가 이미 참여했는지"는
 * 여전히 모르므로(응답에 없다) 그 갈래만 서버 판정에 맡긴다.
 */
const joinDisabled = computed(() => appointment.appointmentStatus !== 'RECRUITING')

function openDetail(): void {
  void router.push({
    name: 'appointment-detail',
    params: { appointmentId: appointment.appointmentId },
  })
}

function requestJoin(): void {
  emit('join', appointment)
}
</script>

<template>
  <AppCard padding="lg">
    <!--
      카드 어디를 눌러도 상세로 간다. 다만 이것은 마우스·터치 전용 지름길이라 카드를
      포커스 대상으로 만들지 않는다. `role="link"`를 걸면 스크린 리더가 카드 전체를
      링크 하나로 읽어 안쪽 View·Join 버튼을 놓치고, 카드가 포커스를 받으면 버튼에서
      누른 Enter·Space가 카드까지 올라와 참여 대신 상세로 넘어간다.
      키보드로 상세에 가는 길은 View 버튼이 맡는다.
    -->
    <article
      class="flex cursor-pointer flex-col gap-3 transition-transform motion-reduce:transition-none active:scale-[0.98] motion-reduce:active:scale-100"
      @click="openDetail"
    >
      <div class="flex items-start justify-between gap-3">
        <div class="min-w-0 flex-1">
          <div class="flex min-w-0 items-center gap-2">
            <h3 class="min-w-0 truncate text-title text-ink">
              {{ appointment.appointmentName }}
            </h3>
            <AppBadge tone="neutral">
              {{ t(`appointment.languages.${appointment.languageCode}`) }}
            </AppBadge>
          </div>
          <p class="mt-2 text-title text-ink-display">{{ depositLabel }}</p>
        </div>

        <div class="flex shrink-0 flex-col items-end gap-2">
          <p class="text-data-lg text-ink-display">{{ memberLabel }}</p>
          <AppBadge
            :tone="appointmentStatusTone(appointment.appointmentStatus)"
            dot
          >
            {{ t(`appointment.status.${appointment.appointmentStatus}`) }}
          </AppBadge>
        </div>
      </div>

      <dl class="flex flex-col gap-1">
        <div class="flex items-center gap-2">
          <dt class="shrink-0 text-caption text-ink-3">{{ t('appointment.list.when') }}</dt>
          <span
            aria-hidden="true"
            class="min-w-4 flex-1 border-b border-dashed border-hairline"
          />
          <dd class="min-w-0 text-right text-body-sm text-ink-2">
            <template v-if="schedule === null">
              {{ t('appointment.list.scheduleUnavailable') }}
            </template>
            <template v-else>
              <span class="block truncate">{{ schedule.date }}</span>
              <span class="block truncate">{{ schedule.time }}</span>
            </template>
          </dd>
        </div>
        <div class="flex items-center gap-2">
          <dt class="shrink-0 text-caption text-ink-3">{{ t('appointment.list.where') }}</dt>
          <span
            aria-hidden="true"
            class="min-w-4 flex-1 border-b border-dashed border-hairline"
          />
          <dd class="min-w-0 truncate text-body-sm text-ink-2">{{ placeLabel }}</dd>
        </div>
      </dl>

      <!--
        버튼 두 개는 각자 감싼 요소에서 전파를 끊는다. AppButton은 payload 없이 click을
        내보내므로 `@click.stop`을 컴포넌트에 걸 수 없어, 네이티브 click이 카드까지
        올라오는 것을 감싼 요소에서 막는다.
      -->
      <div class="flex justify-end gap-2">
        <div
          class="w-24"
          @click.stop
        >
          <AppButton
            block
            compact
            dense
            @click="openDetail"
          >
            {{ t('appointment.list.view') }}
          </AppButton>
        </div>
        <div
          class="w-24"
          @click.stop
        >
          <AppButton
            block
            compact
            dense
            :disabled="joinDisabled"
            @click="requestJoin"
          >
            {{ t('appointment.list.join') }}
          </AppButton>
        </div>
      </div>
    </article>
  </AppCard>
</template>
