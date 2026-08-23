<script setup lang="ts">
import { IconMenu2 } from '@tabler/icons-vue'
import { computed, ref } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'

import { formatServerDateTime, parseServerDateTime } from '@/shared/lib/datetime'
import { vFitText } from '@/shared/lib/fitText'
import AppBadge from '@/shared/ui/AppBadge.vue'
import type { AppointmentStatus } from '@/shared/lib/appointmentStatus'
import AppButton from '@/shared/ui/AppButton.vue'
import AppCard from '@/shared/ui/AppCard.vue'
import StateEmpty from '@/shared/ui/StateEmpty.vue'
import StateError from '@/shared/ui/StateError.vue'
import StateLoading from '@/shared/ui/StateLoading.vue'
import { showToast } from '@/shared/ui/toast'

import AppointmentMemberList from '../components/AppointmentMemberList.vue'
import AppointmentLeaveBlockedDialog from '../components/AppointmentLeaveBlockedDialog.vue'
import AppointmentLeaveConfirmSheet from '../components/AppointmentLeaveConfirmSheet.vue'
import AppointmentMenuSheet from '../components/AppointmentMenuSheet.vue'
import {
  cancelAppointmentParticipation,
  type AppointmentDateTimeValue,
  type AppointmentMember,
} from '../api/appointmentApi'
import { appointmentKeys } from '../model/appointmentKeys'

import { appointmentErrorMessageKey } from '../model/appointmentErrors'
import { APPOINTMENT_LIVE_REFETCH_INTERVAL_MS } from '../model/appointmentLiveRefresh'
import { appointmentStatusTone } from '../model/appointmentStatusPresentation'
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

// 세 쿼리 모두 주기적으로 다시 조회한다. 상태(detail), 회원 목록(members),
// 그리고 버거 메뉴 항목의 활성 여부를 정하는 내 참여 정보(participation)가
// 화면을 열어 둔 채로 따라와야 해서다. 폴링은 이 화면에서만 켠다 — 같은 쿼리
// 옵션을 쓰는 출석 확정·후기 화면은 한 번 받은 값으로 끝나는 화면이다.
const detailQuery = useQuery({
  ...appointmentDetailQueryOptions(appointmentId),
  enabled: computed(() => appointmentId.value !== null),
  retry: false,
  refetchInterval: APPOINTMENT_LIVE_REFETCH_INTERVAL_MS,
})

const membersQuery = useQuery({
  ...appointmentMembersQueryOptions(appointmentId),
  enabled: computed(() => appointmentId.value !== null),
  retry: false,
  refetchInterval: APPOINTMENT_LIVE_REFETCH_INTERVAL_MS,
})
const participationQuery = useQuery({
  ...appointmentParticipationQueryOptions(appointmentId),
  enabled: computed(() => appointmentId.value !== null),
  retry: false,
  refetchInterval: APPOINTMENT_LIVE_REFETCH_INTERVAL_MS,
})
const appointment = computed(() => detailQuery.data.value)
// 목록 순서는 방장 → 나 → 참여한 순서다. 서버가 이미 방장을 먼저, 그 뒤를
// joined_at 오름차순으로 내려주므로(findActiveMembersByAppointmentId) 여기서는
// "나"만 방장 뒤로 끌어올린다. 안정 정렬이라 나머지 참여 순서는 서버 것 그대로다.
// 응답에 참여 시각이 없어 클라이언트가 순서를 다시 계산할 방법도 없다.
const members = computed(() => {
  const currentId = participationQuery.data.value?.appointmentMemberId ?? null
  const rank = (member: AppointmentMember): number => {
    if (member.isHost) return 0
    return currentId !== null && member.appointmentMemberId === currentId ? 1 : 2
  }

  return (membersQuery.data.value ?? appointment.value?.members ?? [])
    .filter((member) => member.membershipStatus === 'ACTIVE')
    .sort((left, right) => rank(left) - rank(right))
})

const menuOpen = ref(false)
const leaveConfirmOpen = ref(false)
const leaveBlockedOpen = ref(false)
// 한 번이라도 받아 둔 참여 정보가 있으면 그 값을 계속 쓴다. 폴링은 5초마다
// 실패할 기회를 주는데, 실패를 곧바로 "모른다"로 읽으면 신호가 잠깐 끊길 때마다
// 메뉴 세 항목이 전부 "확인하지 못했다"로 바뀌었다가 돌아온다.
const participationCheckFailed = computed(
  () => participationQuery.isError.value && participationQuery.data.value === undefined,
)

const statusTone = computed(() => appointmentStatusTone(appointment.value?.appointmentStatus))

// 폴링이 실패해도 이미 보고 있던 내용은 지우지 않는다. 오류 화면은 보여줄 것이
// 아예 없을 때만 띄운다 — 그러지 않으면 지하철에서 신호가 한 번 끊기는 것만으로
// 약속 상세가 통째로 오류 화면이 된다(이 쿼리들은 retry를 쓰지 않는다).
// 다음 폴링이 성공하면 조용히 되돌아온다.
const detailLoadFailed = computed(
  () => detailQuery.isError.value && detailQuery.data.value === undefined,
)
const membersLoadFailed = computed(
  () => membersQuery.isError.value && membersQuery.data.value === undefined,
)

// 방장 여부·참여 상태는 members 목록에서 추리지 않고 participation 응답을 쓴다.
// 목록은 ACTIVE만 담고 있어 LEFT가 된 내 참여를 구분하지 못하고, 목록 조회가
// 실패하면 내 권한까지 함께 사라진다.
const participation = computed(() => participationQuery.data.value)
const isHost = computed(() => participation.value?.host === true)
const isActiveMember = computed(
  () => participation.value?.joined === true && participation.value.membershipStatus === 'ACTIVE',
)
// 나가기는 활동 종료 전까지 열린다. 시작 전(RECRUITING·FULL)에는 보증금을
// 환급받는 탈퇴, 활동 중(IN_PROGRESS)에는 노쇼로 굳어 보증금이 몰수되는
// 탈퇴다. 어느 구간인지는 클라이언트 시계로 재지 않고 서버가 계산한 표시
// 상태로 가른다 — 출석 확정 게이트와 같은 근거다.
const LEAVE_OPEN_STATUSES: AppointmentStatus[] = ['RECRUITING', 'FULL', 'IN_PROGRESS']
const isLeaveNoShow = computed(() => appointment.value?.appointmentStatus === 'IN_PROGRESS')
// 출석 확정은 언제나 시트에 있고, 조건을 만족하지 않으면 이유와 함께 비활성이다.
// 조건에 맞을 때만 넣으면 시트가 열 때마다 다른 모양이 되고 그 기능이 있다는
// 것조차 알 수 없다.
//
// 출석 확정은 활동이 끝난 뒤에 연다. "끝났는가"는 클라이언트 시계로 다시
// 계산하지 않는다 — 서버가 활동 종료 후 확정 전인 약속의 appointmentStatus를
// AWAITING_ATTENDANCE로 내려주므로 그 판정을 그대로 쓴다.
const canOpenAttendance = computed(
  () => isHost.value && appointment.value?.appointmentStatus === 'AWAITING_ATTENDANCE',
)
const canLeave = computed(() => {
  const status = appointment.value?.appointmentStatus
  return (
    isActiveMember.value &&
    !isHost.value &&
    status !== undefined &&
    LEAVE_OPEN_STATUSES.includes(status)
  )
})

const attendanceDisabledReason = computed(() => {
  if (canOpenAttendance.value) return undefined
  const status = appointment.value?.appointmentStatus
  if (status === 'CANCELLED') return t('appointment.detail.menu.attendanceCancelled')
  if (status === 'COMPLETED') return t('appointment.detail.menu.attendanceDone')
  // 활동이 이미 끝났는데도(서버가 AWAITING_ATTENDANCE로 판정) 열리지 않았다면
  // 남은 조건은 방장 여부뿐이다. 그것을 확인하지 못한 채 "활동이 끝나면
  // 열린다"고 하면 틀린 안내가 된다.
  if (participationCheckFailed.value && status === 'AWAITING_ATTENDANCE') {
    return t('appointment.detail.participationCheckFailed')
  }
  return t('appointment.detail.menu.attendanceNotEnded')
})
// 나가기 버튼을 눌렀는데 막혔을 때 모달이 말할 이유.
//
// 회원 자격부터 가른다. 버튼이 그려지는 근거는 회원 목록의 ACTIVE 행이고, 막히는
// 이유를 고르는 근거는 참여 조회다 — 출처가 둘이라 "버튼이 있으니 ACTIVE 회원"이
// 성립하지 않는다. 두 응답의 도착 시점이 어긋나면 나간 사람에게 버튼이 남는데,
// 이 갈래가 없으면 그 사람에게 시계 탓("활동이 끝났다")을 하게 된다 — 이 PR이
// 참여 쪽에서 고친 것과 같은 종류의 거짓말이다.
const leaveBlockedReason = computed(() => {
  if (canLeave.value) return undefined
  if (!isActiveMember.value) return t('appointment.members.leaveNotMember')
  if (appointment.value?.appointmentStatus === 'CANCELLED') {
    return t('appointment.members.leaveCancelled')
  }
  return t('appointment.members.leaveEnded')
})

// 영영 켜질 수 없는 항목은 아예 넣지 않는다. 출석 확정은 방장만 할 수 있고
// (APPOINTMENT-004), 방장은 어떤 상태에서도 자기 참여를 취소할 수 없다
// (APPOINTMENT-007). 비활성으로 둬 봐야 이유만 차지한다.
//
// 단 이 판단은 방장 여부를 알 때만 쓸 수 있다. participation 조회가 실패하면
// isHost가 false로 남아 정작 방장에게서 출석 확정이 통째로 사라진다. 모를 때는
// 감추지 말고 이유로 "확인하지 못했다"를 적는다.
const showAttendanceItem = computed(() => isHost.value || participationCheckFailed.value)
// 시트는 상세를 다 받은 뒤에만 렌더되므로(약속 이름과 보증금이 필요하다) 버튼도
// 같은 조건을 쓴다. 버튼만 헤더에서 먼저 뜨면 눌러도 아무것도 열리지 않는다.
//
// 남은 항목이 출석 확정 하나뿐이라 방장이 아니면 시트가 비어 버린다. 빈 시트를
// 여는 버튼은 눌러도 아무것도 열리지 않는 것과 같으므로 버튼째 감춘다.
const canOpenMenu = computed(() => appointment.value !== undefined && showAttendanceItem.value)

/**
 * 일정은 날짜 한 줄과 시각 범위 한 줄로 나눠 적는다.
 *
 * 날짜를 시작·종료에 늘 두 번 적으면 같은 날짜가 한 줄에서 반복돼, 정작 다른 값인
 * 시각이 묻힌다. 지금 서버는 활동 시작·종료를 `visitDate` 하루 위에서만 조립하므로
 * (생성 검증도 시작 < 종료를 요구한다) 날짜는 언제나 하나다.
 *
 * 그래도 **두 날짜가 다르면 양쪽을 적는다.** 하나만 믿고 시작 날짜만 그리면 그 전제가
 * 깨진 날 종료 날짜가 화면에서 조용히 사라진다 — 목록 카드도 같은 규칙이다.
 */
function formatScheduleDate(value: AppointmentDateTimeValue): string | null {
  const parsed = value ? parseServerDateTime(value) : null
  return parsed ? formatServerDateTime(parsed, locale.value, { dateStyle: 'medium' }) : null
}

function formatScheduleTime(value: AppointmentDateTimeValue): string {
  const parsed = value ? parseServerDateTime(value) : null
  if (!parsed) return t('appointment.detail.notProvided')
  return formatServerDateTime(parsed, locale.value, { timeStyle: 'short' })
}

const scheduleDate = computed(() => {
  const start = formatScheduleDate(appointment.value?.activityStartAt ?? null)
  const end = formatScheduleDate(appointment.value?.activityEndAt ?? null)

  if (start === null) return end
  if (end === null || start === end) return start
  return `${start} ~ ${end}`
})

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

/**
 * 나가기 버튼은 언제나 눌린다. 지금 나갈 수 없으면 확인 모달 대신 이유를 말하는
 * 모달을 연다 — 비활성 버튼은 모바일에서 이유를 말할 자리가 없다.
 */
function requestLeave(): void {
  if (!canLeave.value) {
    leaveBlockedOpen.value = true
    return
  }

  leaveMutation.reset()
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

const leaveMutation = useMutation({
  mutationFn: () => cancelAppointmentParticipation(appointmentId.value as number),
  onSuccess: async () => {
    // 활동 시작 전 탈퇴면 서버가 같은 트랜잭션에서 보증금을 지갑으로 돌려주고
    // (HELD → REFUNDED), 활동 중 탈퇴면 노쇼로 굳어 몰수된다. 확인 모달에서
    // 예고한 결과가 실제로 됐다는 것도 알려 준다 — 모달만 조용히 닫히면 나간
    // 것인지, 돈이 어떻게 됐는지 확신할 수 없다.
    const noShowLeave = isLeaveNoShow.value
    const refunded = appointment.value?.depositAmount
    leaveConfirmOpen.value = false
    await invalidateParticipationScopes()
    showToast(
      noShowLeave
        ? t('appointment.leave.doneNoShow')
        : refunded === undefined
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

/**
 * 하단 CTA. 이 약속을 떠나 탐색 화면으로 간다.
 *
 * `push`가 아니라 `replace`다. 「이 화면을 떠난다」는 동작이라 상세를 히스토리에 남기지
 * 않는다 — 남기면 탐색에서 뒤로 갔을 때 방금 떠난 약속으로 되돌아온다. 왔던 길로
 * 돌아가는 것은 헤더의 뒤로가기가 맡는다.
 */
function goHome(): void {
  void router.replace({ name: 'explore' })
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
      <h1
        v-fit-text
        class="min-w-0 flex-1 truncate font-display text-section-header text-ink-display"
      >
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
      v-else-if="detailLoadFailed"
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
                <span class="block">{{ scheduleDate ?? t('appointment.detail.notProvided') }}</span>
                <span class="block">
                  {{ formatScheduleTime(appointment.activityStartAt) }}
                  <span aria-hidden="true">–</span>
                  {{ formatScheduleTime(appointment.activityEndAt) }}
                </span>
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
            <div class="grid grid-cols-[7rem_1fr] gap-3 py-3 last:pb-0">
              <dt class="text-caption text-ink-3">{{ t('appointment.detail.deposit') }}</dt>
              <dd class="text-body-sm text-ink">
                {{ t('appointment.points', { amount: formatDeposit(appointment.depositAmount) }) }}
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
          v-else-if="membersLoadFailed"
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
          @leave="requestLeave"
        />
      </section>

      <div
        class="fixed inset-x-0 bottom-0 z-20 mx-auto w-full max-w-[390px] bg-canvas/95 px-screen py-3 backdrop-blur"
      >
        <AppButton
          block
          @click="goHome"
        >
          {{ t('appointment.detail.goHome') }}
        </AppButton>
      </div>

      <AppointmentMenuSheet
        v-if="menuOpen"
        :appointment-name="appointment.appointmentName"
        :show-attendance="showAttendanceItem"
        :attendance-disabled-reason="attendanceDisabledReason"
        @close="menuOpen = false"
        @attendance="openAttendance"
      />

      <AppointmentLeaveBlockedDialog
        v-if="leaveBlockedOpen && leaveBlockedReason !== undefined"
        :reason="leaveBlockedReason"
        @close="leaveBlockedOpen = false"
      />

      <AppointmentLeaveConfirmSheet
        v-if="leaveConfirmOpen"
        :appointment-name="appointment.appointmentName"
        :deposit-amount="appointment.depositAmount"
        :no-show="isLeaveNoShow"
        :confirm-disabled="leaveMutation.isPending.value"
        :error-message="leaveErrorMessage"
        @close="closeLeaveConfirm"
        @confirm="confirmLeave"
      />
    </template>
  </main>
</template>
