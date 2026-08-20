<script setup lang="ts">
import { IconMenu2 } from '@tabler/icons-vue'
import { computed, ref } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'

import { formatServerDateTime, parseServerDateTime } from '@/shared/lib/datetime'
import AppBadge from '@/shared/ui/AppBadge.vue'
import AppButton from '@/shared/ui/AppButton.vue'
import AppCard from '@/shared/ui/AppCard.vue'
import StateEmpty from '@/shared/ui/StateEmpty.vue'
import StateError from '@/shared/ui/StateError.vue'
import StateLoading from '@/shared/ui/StateLoading.vue'

import AppointmentMemberList from '../components/AppointmentMemberList.vue'
import AppointmentDepositSheet from '../components/AppointmentDepositSheet.vue'
import AppointmentLeaveConfirmSheet from '../components/AppointmentLeaveConfirmSheet.vue'
import AppointmentMenuSheet from '../components/AppointmentMenuSheet.vue'
import {
  cancelAppointmentParticipation,
  joinAppointment,
  type AppointmentDateTimeValue,
  type AppointmentStatus,
} from '../api/appointmentApi'
import { appointmentKeys } from '../model/appointmentKeys'
import { appointmentErrorMessageKey } from '../model/appointmentErrors'
import {
  appointmentDetailQueryOptions,
  appointmentMembersQueryOptions,
  appointmentParticipationQueryOptions,
} from '../model/appointmentQueries'

const route = useRoute()
const router = useRouter()
const queryClient = useQueryClient()
const i18n = useI18n()
const { locale, t } = i18n
const hasMessage = (key: string): boolean => i18n.te(key)

const appointmentId = computed(() => {
  const raw = Array.isArray(route.params.appointmentId)
    ? route.params.appointmentId[0]
    : route.params.appointmentId
  const parsed = Number(raw)
  return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : null
})

const detailQuery = useQuery({
  ...appointmentDetailQueryOptions(appointmentId),
  enabled: computed(() => appointmentId.value !== null),
  retry: false,
})

const membersQuery = useQuery({
  ...appointmentMembersQueryOptions(appointmentId),
  enabled: computed(() => appointmentId.value !== null),
  retry: false,
})
const participationQuery = useQuery({
  ...appointmentParticipationQueryOptions(appointmentId),
  enabled: computed(() => appointmentId.value !== null),
  retry: false,
})
const appointment = computed(() => detailQuery.data.value)
const members = computed(() =>
  (membersQuery.data.value ?? appointment.value?.members ?? []).filter(
    (member) => member.membershipStatus === 'ACTIVE',
  ),
)

const depositSheetOpen = ref(false)
const menuOpen = ref(false)
const leaveConfirmOpen = ref(false)
const hasJoined = computed(() => participationQuery.data.value?.joined === true)
// 조회 실패 시 hasJoined는 false로 남는다. 서버가 최종적으로 중복 참여를
// 막아주니 데이터는 안전하지만, 그대로 두면 사용자가 결제 시트까지 갔다가
// 거기서야 오류를 보게 된다. 조회 자체가 실패했을 때는 참여 여부를 확신할 수
// 없다는 걸 버튼 단계에서 미리 알려준다.
const participationCheckFailed = computed(() => participationQuery.isError.value)

const statusTone = computed(() =>
  appointment.value?.appointmentStatus === 'RECRUITING' ? 'ongoing' : 'neutral',
)

const isJoinAvailable = computed(() => {
  if (appointment.value?.appointmentStatus !== 'RECRUITING') return false
  const deadline = parseServerDateTime(appointment.value.joinDeadline)
  return deadline !== null && Date.now() < deadline.getTime()
})
const isJoinButtonEnabled = computed(
  () => isJoinAvailable.value && !hasJoined.value && !participationCheckFailed.value,
)
// 버튼 title 툴팁은 비활성 버튼에 pointer-events-none이 걸려 뜨지 않고,
// 390px 모바일 PWA라 애초에 hover도 없다. 그래서 비활성 이유는 버튼 아래에
// 상시 텍스트로 보여준다. 모집 종료(CLOSED/COMPLETED/CANCELLED)는 사용자
// 잘못이 아닌 정상 상태라 text-danger(빨강)로 알릴 일이 아니어서, 세 경우
// 모두 중립색(text-ink-3)으로 통일한다.
const joinDisabledReason = computed(() => {
  if (participationCheckFailed.value) return t('appointment.detail.participationCheckFailed')
  if (hasJoined.value) return t('appointment.detail.alreadyJoined')
  if (!isJoinAvailable.value) return t('appointment.detail.joinUnavailable')
  return undefined
})
// 방장 여부·참여 상태는 members 목록에서 추리지 않고 participation 응답을 쓴다.
// 목록은 ACTIVE만 담고 있어 LEFT가 된 내 참여를 구분하지 못하고, 목록 조회가
// 실패하면 내 권한까지 함께 사라진다.
const participation = computed(() => participationQuery.data.value)
const isHost = computed(() => participation.value?.host === true)
const isActiveMember = computed(
  () => participation.value?.joined === true && participation.value.membershipStatus === 'ACTIVE',
)
const isAttendedMember = computed(
  () => isActiveMember.value && participation.value?.attendanceStatus === 'ATTENDED',
)

const isJoinDeadlinePassed = computed(() => {
  const deadline = parseServerDateTime(appointment.value?.joinDeadline ?? null)
  return deadline === null || Date.now() >= deadline.getTime()
})
// 출석 확정은 활동이 끝난 뒤에 연다. 백엔드는 IN_PROGRESS이기만 하면 받아주지만
// (활동 시작 시각에 스케줄러가 바꾼다), 진행 중에 미리 확정하면 늦게 온 사람이
// 노쇼로 굳어 보증금을 잃는다.
const isActivityOver = computed(() => {
  const endAt = parseServerDateTime(appointment.value?.activityEndAt ?? null)
  return endAt !== null && Date.now() >= endAt.getTime()
})

// 세 항목은 언제나 시트에 있고, 조건을 만족하지 않으면 이유와 함께 비활성이다.
// 조건에 맞는 것만 넣으면 시트가 열 때마다 다른 모양이 되고 나머지 기능이
// 있다는 것조차 알 수 없다.
const canOpenAttendance = computed(
  () =>
    isHost.value && isActivityOver.value && appointment.value?.appointmentStatus === 'IN_PROGRESS',
)
const canOpenReviews = computed(
  () => appointment.value?.appointmentStatus === 'COMPLETED' && isAttendedMember.value,
)
const canLeave = computed(
  () => isActiveMember.value && !isHost.value && !isJoinDeadlinePassed.value,
)

const attendanceDisabledReason = computed(() => {
  if (canOpenAttendance.value) return undefined
  const status = appointment.value?.appointmentStatus
  if (status === 'CANCELLED') return t('appointment.detail.menu.attendanceCancelled')
  if (status === 'COMPLETED') return t('appointment.detail.menu.attendanceDone')
  return t('appointment.detail.menu.attendanceNotEnded')
})
const reviewsDisabledReason = computed(() => {
  if (canOpenReviews.value) return undefined
  if (appointment.value?.appointmentStatus !== 'COMPLETED') {
    return t('appointment.detail.menu.reviewsNotCompleted')
  }
  return t('appointment.detail.menu.reviewsNotAttended')
})
const leaveDisabledReason = computed(() => {
  if (canLeave.value) return undefined
  if (!isActiveMember.value) return t('appointment.detail.menu.leaveNotMember')
  return t('appointment.detail.menu.leaveDeadlinePassed')
})

// 영영 켜질 수 없는 항목은 아예 넣지 않는다. 출석 확정은 방장만 할 수 있고
// (APPOINTMENT-004), 방장은 어떤 상태에서도 자기 참여를 취소할 수 없다
// (APPOINTMENT-007). 비활성으로 둬 봐야 이유만 차지한다.
const showAttendanceItem = computed(() => isHost.value)
const showLeaveItem = computed(() => !isHost.value)

// 시트는 상세를 다 받은 뒤에만 렌더되므로(약속 이름과 보증금이 필요하다) 버튼도
// 같은 조건을 쓴다. 버튼만 헤더에서 먼저 뜨면 눌러도 아무것도 열리지 않는다.
const canOpenMenu = computed(() => appointment.value !== undefined)

function formatDateTime(value: AppointmentDateTimeValue): string {
  if (!value) return t('appointment.detail.notProvided')
  const parsed = parseServerDateTime(value)
  if (!parsed) return typeof value === 'string' ? value : t('appointment.detail.notProvided')
  return formatServerDateTime(parsed, locale.value, {
    dateStyle: 'medium',
    timeStyle: 'short',
  })
}

function formatDeposit(value: string): string {
  const amount = Number(value)
  return Number.isFinite(amount) ? new Intl.NumberFormat(locale.value).format(amount) : value
}

function statusLabel(status: AppointmentStatus): string {
  return t(`appointment.status.${status}`)
}

function goBack(): void {
  const current = appointment.value
  if (current?.itemType === 'EVENT' || current?.itemType === 'PLACE') {
    void router.push({
      name: 'explore',
      query: { tab: current.itemType === 'PLACE' ? 'places' : 'events' },
    })
    return
  }

  void router.push({ name: 'explore' })
}

function openMemberProfile(member: { memberId: number }): void {
  if (appointmentId.value !== null) {
    void router.push({
      name: 'appointment-member-profile',
      params: { appointmentId: appointmentId.value, memberId: member.memberId },
    })
  }
}

function openAttendance(): void {
  if (appointmentId.value === null || !canOpenAttendance.value) return

  menuOpen.value = false
  void router.push({
    name: 'appointment-attendance',
    params: { appointmentId: appointmentId.value },
  })
}

function openReviews(): void {
  if (appointmentId.value === null || !canOpenReviews.value) return

  menuOpen.value = false
  void router.push({
    name: 'appointment-reviews',
    params: { appointmentId: appointmentId.value },
  })
}

function openLeaveConfirm(): void {
  if (!canLeave.value) return

  leaveMutation.reset()
  menuOpen.value = false
  leaveConfirmOpen.value = true
}

function closeLeaveConfirm(): void {
  leaveConfirmOpen.value = false
}

function confirmLeave(): void {
  if (!leaveMutation.isPending.value) leaveMutation.mutate()
}

function retry(): void {
  void detailQuery.refetch()
  void membersQuery.refetch()
  void participationQuery.refetch()
}

/**
 * 참여·탈퇴는 이 약속만 바꾸지 않는다. 목록 카드의 "{current}/{max} members"와
 * 내 약속 목록(지갑 QR 결제가 공동 지출 약속을 고를 때 쓴다)도 함께 어긋나므로
 * 다섯 갈래를 같이 무효화한다.
 */
async function invalidateParticipationScopes(): Promise<void> {
  await Promise.all([
    queryClient.invalidateQueries({ queryKey: appointmentKeys.detail(appointmentId.value) }),
    queryClient.invalidateQueries({ queryKey: appointmentKeys.members(appointmentId.value) }),
    queryClient.invalidateQueries({
      queryKey: appointmentKeys.participation(appointmentId.value),
    }),
    queryClient.invalidateQueries({ queryKey: appointmentKeys.lists() }),
    queryClient.invalidateQueries({ queryKey: appointmentKeys.mine() }),
  ])
}

const joinMutation = useMutation({
  mutationFn: () => joinAppointment(appointmentId.value as number),
  onSuccess: async () => {
    depositSheetOpen.value = false
    await invalidateParticipationScopes()
  },
})

const leaveMutation = useMutation({
  mutationFn: () => cancelAppointmentParticipation(appointmentId.value as number),
  onSuccess: async () => {
    leaveConfirmOpen.value = false
    await invalidateParticipationScopes()
  },
})

const leaveErrorMessage = computed(() =>
  leaveMutation.error.value === null
    ? undefined
    : t(appointmentErrorMessageKey(leaveMutation.error.value, hasMessage)),
)

const joinErrorMessage = computed(() =>
  joinMutation.error.value === null
    ? undefined
    : t(appointmentErrorMessageKey(joinMutation.error.value, hasMessage)),
)

function openDepositSheet(): void {
  if (!isJoinButtonEnabled.value) return

  joinMutation.reset()
  depositSheetOpen.value = true
}

function closeDepositSheet(): void {
  depositSheetOpen.value = false
}

function confirmJoin(): void {
  if (!joinMutation.isPending.value) joinMutation.mutate()
}
</script>

<template>
  <main class="flex min-h-dvh w-full flex-col gap-8 px-screen pb-28 pt-6">
    <header class="flex items-center gap-3">
      <AppButton
        compact
        variant="secondary"
        :aria-label="t('action.back')"
        @click="goBack"
      >
        ‹
      </AppButton>
      <h1 class="min-w-0 flex-1 truncate font-display text-section-header text-ink-display">
        {{ t('appointment.detail.title') }}
      </h1>
      <AppButton
        v-if="canOpenMenu"
        compact
        variant="secondary"
        :aria-label="t('appointment.detail.openMenu')"
        :aria-expanded="menuOpen"
        aria-haspopup="dialog"
        @click="menuOpen = true"
      >
        <IconMenu2
          :size="20"
          :stroke-width="2"
          aria-hidden="true"
        />
      </AppButton>
    </header>

    <StateEmpty
      v-if="appointmentId === null"
      :title="t('appointment.detail.invalidTitle')"
      :description="t('appointment.detail.invalidDescription')"
    />

    <StateLoading
      v-else-if="detailQuery.isPending.value"
      :label="t('state.loading')"
    />

    <StateError
      v-else-if="detailQuery.isError.value"
      :title="t('appointment.detail.loadFailed')"
      :description="t('appointment.detail.loadFailedDescription')"
      :action-label="t('action.retry')"
      @retry="retry"
    />

    <template v-else-if="appointment !== undefined">
      <section class="flex flex-col gap-5">
        <div class="flex items-start justify-between gap-3">
          <div class="min-w-0">
            <p class="text-caption text-ink-3">
              {{
                appointment.itemType === 'EVENT'
                  ? t('appointment.detail.event')
                  : t('appointment.detail.place')
              }}
            </p>
            <h2 class="mt-1 break-words font-display text-screen-title text-ink-display">
              {{ appointment.appointmentName }}
            </h2>
          </div>
          <AppBadge
            :tone="statusTone"
            dot
          >
            {{ statusLabel(appointment.appointmentStatus) }}
          </AppBadge>
        </div>

        <AppCard padding="lg">
          <dl class="flex flex-col divide-y divide-hairline">
            <div class="grid grid-cols-[7rem_1fr] gap-3 py-3 first:pt-0 last:pb-0">
              <dt class="text-caption text-ink-3">{{ t('appointment.detail.schedule') }}</dt>
              <dd class="text-body-sm text-ink">
                {{ formatDateTime(appointment.activityStartAt) }}
                <span aria-hidden="true">–</span>
                {{ formatDateTime(appointment.activityEndAt) }}
              </dd>
            </div>
            <div class="grid grid-cols-[7rem_1fr] gap-3 py-3">
              <dt class="text-caption text-ink-3">{{ t('appointment.detail.meetingPlace') }}</dt>
              <dd class="text-body-sm text-ink">
                {{ appointment.meetingPlace ?? t('appointment.detail.notProvided') }}
                <span
                  v-if="appointment.meetingAddress"
                  class="mt-1 block text-caption text-ink-3"
                >
                  {{ appointment.meetingAddress }}
                </span>
              </dd>
            </div>
            <div class="grid grid-cols-[7rem_1fr] gap-3 py-3">
              <dt class="text-caption text-ink-3">{{ t('appointment.detail.language') }}</dt>
              <dd class="text-body-sm text-ink">
                {{ t(`appointment.languages.${appointment.languageCode}`) }}
              </dd>
            </div>
            <div class="grid grid-cols-[7rem_1fr] gap-3 py-3">
              <dt class="text-caption text-ink-3">{{ t('appointment.detail.deposit') }}</dt>
              <dd class="text-body-sm text-ink">₩{{ formatDeposit(appointment.depositAmount) }}</dd>
            </div>
            <div class="grid grid-cols-[7rem_1fr] gap-3 py-3 last:pb-0">
              <dt class="text-caption text-ink-3">{{ t('appointment.detail.joinDeadline') }}</dt>
              <dd class="text-body-sm text-ink">
                {{ formatDateTime(appointment.joinDeadline) }}
              </dd>
            </div>
          </dl>
        </AppCard>
      </section>

      <section
        class="flex flex-col gap-5"
        aria-labelledby="appointment-members-heading"
      >
        <h2
          id="appointment-members-heading"
          class="text-title text-ink"
        >
          {{ t('appointment.members.title') }}
        </h2>

        <StateLoading
          v-if="membersQuery.isPending.value"
          :label="t('appointment.members.loading')"
          :lines="2"
        />
        <StateError
          v-else-if="membersQuery.isError.value"
          :title="t('appointment.members.loadFailed')"
          :description="t('appointment.members.loadFailedDescription')"
          :action-label="t('action.retry')"
          @retry="retry"
        />
        <StateEmpty
          v-else-if="members.length === 0"
          :title="t('appointment.members.emptyTitle')"
          :description="t('appointment.members.emptyDescription')"
        />
        <AppointmentMemberList
          v-else
          :members="members"
          @select="openMemberProfile"
        />
      </section>

      <div
        class="fixed inset-x-0 bottom-0 z-20 mx-auto w-full max-w-[390px] bg-canvas/95 px-screen py-3 backdrop-blur"
      >
        <p
          v-if="joinDisabledReason !== undefined"
          class="mb-2 text-center text-body-sm text-ink-3"
        >
          {{ joinDisabledReason }}
        </p>
        <AppButton
          block
          :disabled="!isJoinButtonEnabled"
          @click="openDepositSheet"
        >
          {{ t('appointment.detail.join') }}
        </AppButton>
      </div>

      <AppointmentDepositSheet
        v-if="depositSheetOpen"
        :appointment-name="appointment.appointmentName"
        :deposit-amount="appointment.depositAmount"
        :confirm-disabled="joinMutation.isPending.value"
        :error-message="joinErrorMessage"
        @close="closeDepositSheet"
        @confirm="confirmJoin"
      />

      <AppointmentMenuSheet
        v-if="menuOpen"
        :appointment-name="appointment.appointmentName"
        :show-attendance="showAttendanceItem"
        :attendance-disabled-reason="attendanceDisabledReason"
        :reviews-disabled-reason="reviewsDisabledReason"
        :show-leave="showLeaveItem"
        :leave-disabled-reason="leaveDisabledReason"
        @close="menuOpen = false"
        @attendance="openAttendance"
        @reviews="openReviews"
        @leave="openLeaveConfirm"
      />

      <AppointmentLeaveConfirmSheet
        v-if="leaveConfirmOpen"
        :appointment-name="appointment.appointmentName"
        :deposit-amount="appointment.depositAmount"
        :confirm-disabled="leaveMutation.isPending.value"
        :error-message="leaveErrorMessage"
        @close="closeLeaveConfirm"
        @confirm="confirmLeave"
      />
    </template>
  </main>
</template>
