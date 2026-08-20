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
import { showToast } from '@/shared/ui/toast'

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
// 참여 버튼은 어떤 경우에도 눌린다. 비활성 버튼은 왜 안 되는지 말해 줄 방법이
// 마땅치 않아서다(모바일이라 hover도 없다). 대신 막히는 이유는 누르기 전부터
// 버튼 위에 한 줄로 떠 있는다. 눌러 봐야 아는 화면은 제일 큰 CTA를 "눌리기는
// 하는데 아무 일도 없는 버튼"으로 만든다.
//
// 상태에서 그대로 끌어내므로 따로 지워 줄 자리가 없다. 누를 때 ref에 담아 두면
// 이유가 해소된 뒤에도 남는다 — 나간 사람에게 "이미 참여했다"가 남던 식이다.
const joinBlockedReason = computed<string | undefined>(() => {
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
  // 활동이 이미 끝났는데도 열리지 않았다면 남은 조건은 방장 여부뿐이다. 그것을
  // 확인하지 못한 채 "활동이 끝나면 열린다"고 하면 틀린 안내가 된다.
  if (participationCheckFailed.value && isActivityOver.value) {
    return t('appointment.detail.participationCheckFailed')
  }
  return t('appointment.detail.menu.attendanceNotEnded')
})
const reviewsDisabledReason = computed(() => {
  if (canOpenReviews.value) return undefined
  if (appointment.value?.appointmentStatus !== 'COMPLETED') {
    return t('appointment.detail.menu.reviewsNotCompleted')
  }
  // 여기서부터는 participation 응답에 기대는 판정이다. 조회가 실패한 것은
  // 참여 여부를 모르는 것이지 아닌 것이 아니다.
  if (participationCheckFailed.value) return t('appointment.detail.participationCheckFailed')
  return t('appointment.detail.menu.reviewsNotAttended')
})
const leaveDisabledReason = computed(() => {
  if (canLeave.value) return undefined
  // isActiveMember는 조회 실패와 "회원이 아님"을 구분하지 못한다. 실패를 먼저
  // 가르지 않으면 못 읽었을 뿐인데 회원이 아니라고 단정한다.
  if (participationCheckFailed.value) return t('appointment.detail.participationCheckFailed')
  if (!isActiveMember.value) return t('appointment.detail.menu.leaveNotMember')
  return t('appointment.detail.menu.leaveDeadlinePassed')
})

// 영영 켜질 수 없는 항목은 아예 넣지 않는다. 출석 확정은 방장만 할 수 있고
// (APPOINTMENT-004), 방장은 어떤 상태에서도 자기 참여를 취소할 수 없다
// (APPOINTMENT-007). 비활성으로 둬 봐야 이유만 차지한다.
//
// 단 이 판단은 방장 여부를 알 때만 쓸 수 있다. participation 조회가 실패하면
// isHost가 false로 남아 정작 방장에게서 출석 확정이 통째로 사라진다. 모를 때는
// 감추지 말고 이유로 "확인하지 못했다"를 적는다.
const showAttendanceItem = computed(() => isHost.value || participationCheckFailed.value)
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

/**
 * 뒤로 가면 왔던 길을 되감는다. 출석·후기 화면과 같은 규칙이다.
 *
 * 히스토리가 없을 때(딥링크·PWA 재진입)만 목적지를 정해 보낸다. 이 약속이 속한
 * 약속 목록이고, 목록은 `itemId`·`itemType` 쿼리로 대상 Event·Place를 좁히므로
 * 상세가 가진 값을 그대로 넘긴다. 상세를 아직 못 받았으면 좁히지 않은 전체 목록이다.
 *
 * 한때는 히스토리와 무관하게 목록으로 push했다. 생성 직후 뒤로 가기가 방금 제출한
 * 폼으로 돌아가는 것을 막으려던 것인데, 그 대가로 여정 타임라인에서 들어온 사람이
 * 타임라인으로 못 돌아갔다. 생성 쪽을 replace로 바꿔 폼을 히스토리에서 뺐으니
 * 목적지를 고정할 이유가 없어졌다.
 */
function goBack(): void {
  if (window.history.length > 1) {
    void router.back()
    return
  }

  const current = appointment.value
  const scoped = current?.itemType === 'EVENT' || current?.itemType === 'PLACE'

  void router.push({
    name: 'appointment-list',
    query: scoped ? { itemId: String(current.itemId), itemType: current.itemType } : {},
  })
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
    // 서버가 같은 트랜잭션에서 보증금을 지갑으로 돌려준다(HELD → REFUNDED).
    // 확인 모달에서 환급을 예고했으니 실제로 됐다는 것도 알려 준다. 모달만 조용히
    // 닫히면 나간 것인지 확신할 수 없다.
    const refunded = appointment.value?.depositAmount
    leaveConfirmOpen.value = false
    await invalidateParticipationScopes()
    showToast(
      refunded === undefined
        ? t('appointment.leave.done')
        : t('appointment.leave.doneRefunded', { amount: formatDeposit(refunded) }),
    )
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
  // 이유는 이미 버튼 위에 떠 있다. 여기서는 시트를 열지 않는 것으로 끝낸다. 이미
  // 참여한 사람은 서버도 APPOINTMENT-003으로 막으므로 미리 알려 주는 셈이다.
  if (joinBlockedReason.value !== undefined) return

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
          :current-appointment-member-id="participation?.appointmentMemberId ?? null"
          @select="openMemberProfile"
        />
      </section>

      <div
        class="fixed inset-x-0 bottom-0 z-20 mx-auto w-full max-w-[390px] bg-canvas/95 px-screen py-3 backdrop-blur"
      >
        <p
          v-if="joinBlockedReason !== undefined"
          role="status"
          class="mb-2 text-center text-body-sm text-ink-3"
        >
          {{ joinBlockedReason }}
        </p>
        <AppButton
          block
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
