<script setup lang="ts">
import { IconMenu2 } from '@tabler/icons-vue'
import { computed, ref, watch } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'

import {
  formatServerDateTime,
  parseServerDateTime,
  toServerCalendarDate,
} from '@/shared/lib/datetime'
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
import AppointmentDepositSheet from '../components/AppointmentDepositSheet.vue'
import AppointmentJourneySelectSheet from '../components/AppointmentJourneySelectSheet.vue'
import AppointmentLeaveConfirmSheet from '../components/AppointmentLeaveConfirmSheet.vue'
import AppointmentMenuSheet from '../components/AppointmentMenuSheet.vue'
import {
  cancelAppointmentParticipation,
  joinAppointment,
  type AppointmentDateTimeValue,
} from '../api/appointmentApi'
import { appointmentKeys } from '../model/appointmentKeys'
import { NormalizedApiError } from '@/shared/api/apiError'
import { serializeReturnParams } from '@/shared/lib/returnRoute'

import { appointmentErrorMessageKey } from '../model/appointmentErrors'
import { appointmentStatusTone } from '../model/appointmentStatusPresentation'
import { useAppointmentJourneyIntegration } from '../model/journeyIntegration'
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

const journeyIntegration = useAppointmentJourneyIntegration()
const depositSheetOpen = ref(false)
const menuOpen = ref(false)
const leaveConfirmOpen = ref(false)
const hasJoined = computed(() => participationQuery.data.value?.joined === true)
// 조회 실패 시 hasJoined는 false로 남는다. 서버가 최종적으로 중복 참여를
// 막아주니 데이터는 안전하지만, 그대로 두면 사용자가 결제 시트까지 갔다가
// 거기서야 오류를 보게 된다. 조회 자체가 실패했을 때는 참여 여부를 확신할 수
// 없다는 걸 버튼 단계에서 미리 알려준다.
const participationCheckFailed = computed(() => participationQuery.isError.value)

const statusTone = computed(() => appointmentStatusTone(appointment.value?.appointmentStatus))

// 참여 가능 여부를 클라이언트 시계로 다시 재지 않는다. 서버가 정원이 찬 약속을
// FULL로, 활동이 시작된 약속을 IN_PROGRESS로 계산해 내려주므로 RECRUITING이라는
// 값 자체가 "지금 참여할 수 있다"를 뜻한다 — 나가기·출석 확정과 같은 근거다.
const isJoinAvailable = computed(() => appointment.value?.appointmentStatus === 'RECRUITING')
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

// 나가기는 활동 종료 전까지 열린다. 시작 전(RECRUITING·FULL)에는 보증금을
// 환급받는 탈퇴, 활동 중(IN_PROGRESS)에는 노쇼로 굳어 보증금이 몰수되는
// 탈퇴다. 어느 구간인지는 클라이언트 시계로 재지 않고 서버가 계산한 표시
// 상태로 가른다 — 출석 확정 게이트와 같은 근거다.
const LEAVE_OPEN_STATUSES: AppointmentStatus[] = ['RECRUITING', 'FULL', 'IN_PROGRESS']
const isLeaveNoShow = computed(() => appointment.value?.appointmentStatus === 'IN_PROGRESS')
// 세 항목은 언제나 시트에 있고, 조건을 만족하지 않으면 이유와 함께 비활성이다.
// 조건에 맞는 것만 넣으면 시트가 열 때마다 다른 모양이 되고 나머지 기능이
// 있다는 것조차 알 수 없다.
//
// 출석 확정은 활동이 끝난 뒤에 연다. "끝났는가"는 클라이언트 시계로 다시
// 계산하지 않는다 — 서버가 활동 종료 후 확정 전인 약속의 appointmentStatus를
// AWAITING_ATTENDANCE로 내려주므로 그 판정을 그대로 쓴다.
const canOpenAttendance = computed(
  () => isHost.value && appointment.value?.appointmentStatus === 'AWAITING_ATTENDANCE',
)
const canOpenReviews = computed(
  () => appointment.value?.appointmentStatus === 'COMPLETED' && isAttendedMember.value,
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
  return t('appointment.detail.menu.leaveActivityEnded')
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
  mutationFn: (tripId: number) => joinAppointment(appointmentId.value as number, tripId),
  onSuccess: async () => {
    depositSheetOpen.value = false
    await invalidateParticipationScopes()
  },
})

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

const joinErrorMessage = computed(() =>
  joinMutation.error.value === null || topupPromptOpen.value
    ? undefined
    : t(appointmentErrorMessageKey(joinMutation.error.value, hasMessage)),
)

// 참여도 방장처럼 여정을 고른다. 서버가 멤버십의 trip_id와 여정 항목을 함께 걸어
// 두므로, 고르지 않으면 참여한 약속이 진행 중 목록과 QR 공동결제에서 빠진다.
// 여정을 먼저 고르고 보증금 확인으로 넘어간다 — 보증금 시트가 마지막 확인이다.
const journeySelectOpen = ref(false)
const selectedTripId = ref<number | null>(null)
const journeyListQuery = journeyIntegration.useJourneyListQuery(journeySelectOpen)

// 약속 날짜는 이미 정해져 있다. 그 날짜를 품지 못하는 여정을 고르면 서버가
// JOURNEY-007로 되돌려보내므로, 목록은 다 보여 주되 고르는 순간 이유를 알려 준다.
// 걸러서 감추지 않는 것은 "내 여정이 왜 없지"로 읽히지 않게 하기 위해서다.
// 활동 날짜(YYYY-MM-DD). 문자열이라고 보고 자르면 안 된다 — 이 값은 Jackson이
// 숫자 배열로도 보낼 수 있는 타입이고, 배열에 slice를 쓰면 **배열**이 나온다. 그러면
// 아래 여정 기간 비교가 문자열 대 배열이 되어 조용히 전부 false가 되고, 모든 여정이
// "날짜를 담지 못함"으로 보여 참여가 통째로 막힌다. tsc는 이 비교를 잡지 않는다.
const activityDate = computed(() => {
  // 읽을 수 없으면 빈 문자열이 온다. 날짜를 모르는 것과 구분해야 하므로 null로 바꾼다 —
  // 아래 coversActivityDate가 null이면 거르지 않고 서버 판단에 맡긴다.
  const date = toServerCalendarDate(appointment.value?.activityStartAt)
  return date === '' ? null : date
})
const journeySelectionError = ref<string | null>(null)

const journeyListErrorMessage = computed(() =>
  journeyListQuery.isError.value ? t('appointment.journeySelect.error') : null,
)

function coversActivityDate(journey: { startDate: string; endDate: string }): boolean {
  const date = activityDate.value
  if (date === null) return true
  return journey.startDate <= date && date <= journey.endDate
}

function readPositiveInteger(value: unknown): number | undefined {
  const raw = Array.isArray(value) ? value[0] : value
  const parsed = Number(raw)
  return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : undefined
}

// 여정을 만들거나 충전하고 돌아온 경우(?tripId=). 참여를 다시 누르게 하지 않고 시트를
// 열어, 그 여정을 골라 둔 채로 보여 준다 — 맞는지 확인하고 넘어간다.
//
// 이 값은 **사용자가 시트에서 행동하면 소비한다**(고르거나 닫으면 URL에서 지운다).
// 남겨 두면 "이 화면을 열 때마다 시트를 열라"는 상시 지시가 되어, 참여가 끝난 뒤
// 목록이 갱신되기만 해도 시트가 혼자 다시 뜬다 — 참여 직후에는 상세와 참여 정보를
// 함께 무효화하는데 상세가 먼저 도착하면 "아직 참여 안 함"으로 보여 가드를 통과한다.
//
// 심는 즉시 지우지는 않는다. 충전으로 떠날 때 자기 자리에 tripId를 남겨 두는데(뒤로
// 가기로 돌아와도 이어지도록), 적용과 동시에 지우면 그 표시가 바로 사라진다.
const createdTripId = computed(() => readPositiveInteger(route.query.tripId))

function consumeCreatedTripId(): void {
  const rest = { ...route.query }
  delete rest.tripId
  void router.replace({ path: route.path, query: rest })
}

watch(
  () => [createdTripId.value, appointment.value, journeyListQuery.data.value] as const,
  ([tripId, current, journeys]) => {
    if (tripId === undefined || current === undefined) return
    // 이미 참여했거나 참여할 수 없는 약속이면 열지 않는다. 버튼과 같은 기준이다.
    if (joinBlockedReason.value !== undefined) return
    // 보증금 확인까지 넘어간 뒤에는 시트를 다시 열지 않는다.
    if (depositSheetOpen.value) return

    journeySelectOpen.value = true
    // 목록이 오기 전에는 고를 수 없다. 오고 나서 그 여정이 실제로 있을 때만 고른다.
    if (journeys?.some((journey) => journey.tripId === tripId) === true) {
      selectedTripId.value = tripId
    }
  },
  { immediate: true },
)

function openJourneySelect(): void {
  // 이유는 이미 버튼 위에 떠 있다. 여기서는 시트를 열지 않는 것으로 끝낸다. 이미
  // 참여한 사람은 서버도 APPOINTMENT-003으로 막으므로 미리 알려 주는 셈이다.
  if (joinBlockedReason.value !== undefined) return

  joinMutation.reset()
  selectedTripId.value = null
  journeySelectionError.value = null
  journeySelectOpen.value = true
}

function closeJourneySelect(): void {
  journeySelectOpen.value = false
  consumeCreatedTripId()
}

function selectJourney(tripId: number): void {
  const journey = journeyListQuery.data.value?.find((candidate) => candidate.tripId === tripId)
  if (journey !== undefined && !coversActivityDate(journey)) {
    // 시트를 닫지 않는다 — 다른 여정을 바로 고를 수 있어야 한다.
    journeySelectionError.value = t('appointment.journeySelect.dateOutOfRange')
    selectedTripId.value = null
    return
  }

  journeySelectionError.value = null
  selectedTripId.value = tripId
  journeySelectOpen.value = false
  depositSheetOpen.value = true
  consumeCreatedTripId()
}

// 이 약속을 담을 여정이 없다. 자리를 내주고(replace) 보내면 여정 생성이 그 자리를
// 돌려주므로, 돌아온 뒤 상세가 히스토리에 두 번 쌓이지 않는다.
// 이 약속을 담을 여정이 없다. 자리를 내주고(replace) 보내면 여정 생성이 그 자리를
// 돌려주므로, 돌아온 뒤 상세가 히스토리에 두 번 쌓이지 않는다. 이 화면은 param
// 라우트라 이름만으로는 돌아올 수 없어 returnParams도 함께 싣는다.
function goToCreateJourney(): void {
  journeySelectOpen.value = false
  void router.replace({
    name: 'journey-create',
    query: {
      returnRouteName: 'appointment-detail',
      returnParams: serializeReturnParams({ appointmentId: appointmentId.value ?? '' }),
    },
  })
}

function closeDepositSheet(): void {
  depositSheetOpen.value = false
}

function confirmJoin(): void {
  if (joinMutation.isPending.value || selectedTripId.value === null) return
  joinMutation.mutate(selectedTripId.value)
}

// 보증금을 예치할 잔액이 없으면 서버가 WALLET-015로 거절한다. 빨간 한 줄 대신
// 부족하다는 사실과 다음 행동(그만큼 충전)을 한 번에 묻는다 — 약속 생성과 같은 규칙이다.
const INSUFFICIENT_BALANCE_CODE = 'WALLET-015'
const topupPromptOpen = ref(false)

watch(
  () => joinMutation.error.value,
  (error) => {
    if (!(error instanceof NormalizedApiError)) return
    if (error.code !== INSUFFICIENT_BALANCE_CODE) return
    // 보증금 시트를 닫고 팝업만 남긴다. 두 겹으로 쌓이면 무엇을 눌러야 할지 흐려진다.
    depositSheetOpen.value = false
    topupPromptOpen.value = true
  },
)

const formattedDepositAmount = computed(() =>
  new Intl.NumberFormat('en-US').format(Number(appointment.value?.depositAmount ?? 0)),
)

function closeTopupPrompt(): void {
  topupPromptOpen.value = false
  // 팝업을 닫았으면 그 오류는 다 본 것이다. 남겨두면 일반 오류 문구로 다시 나타난다.
  joinMutation.reset()
}

// 충전 화면으로 간다. 금액을 미리 채우고, 돌아올 곳으로 지금 고른 여정까지 실어
// 보낸다 — 돌아오면 그 여정이 골라진 채로 참여 시트가 다시 열린다(?tripId=).
// replace가 아니라 push다. 충전을 포기하고 뒤로 와도 이 화면으로 돌아와야 한다.
function goToTopup(): void {
  topupPromptOpen.value = false
  const amount = appointment.value?.depositAmount
  const tripId = selectedTripId.value

  const openTopup = () =>
    router.push({
      name: 'wallet-top-up',
      query: {
        ...(amount === undefined ? {} : { amount: String(amount) }),
        returnRouteName: 'appointment-detail',
        returnParams: serializeReturnParams({ appointmentId: appointmentId.value ?? '' }),
        ...(tripId === null ? {} : { tripId: String(tripId) }),
      },
    })

  if (tripId === null) {
    void openTopup()
    return
  }

  // 돌아오는 길이 둘인데 도착지가 다르다. 충전을 마치면 충전 화면이 규약대로
  // 보내 주지만, 뒤로가기는 브라우저가 **떠날 때의 URL 그대로** 되돌린다. 그 자리에
  // 고른 여정이 없으면 충전을 포기했을 뿐인데 처음부터 다시 골라야 한다. 떠나기 전에
  // 지금 자리에도 tripId를 남겨 어느 길로 돌아오든 이어지게 한다.
  // 표시를 먼저 남기고 떠난다. 순서가 뒤집히면 replace가 충전 화면 위에서 일어난다.
  void router
    .replace({ path: route.path, query: { ...route.query, tripId: String(tripId) } })
    .then(openTopup)
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
          @click="openJourneySelect"
        >
          {{ t('appointment.detail.join') }}
        </AppButton>
      </div>

      <AppointmentJourneySelectSheet
        v-if="journeySelectOpen"
        :journeys="journeyListQuery.data.value ?? []"
        :selected-journey-id="selectedTripId"
        :loading="journeyListQuery.isPending.value"
        :error-message="journeyListErrorMessage"
        :selection-error="journeySelectionError"
        :empty-message="t('appointment.journeySelect.emptyForJoin')"
        @close="closeJourneySelect"
        @select="selectJourney"
        @create-journey="goToCreateJourney"
      />

      <AppointmentDepositSheet
        v-if="depositSheetOpen"
        :appointment-name="appointment.appointmentName"
        :deposit-amount="appointment.depositAmount"
        :confirm-disabled="joinMutation.isPending.value"
        :error-message="joinErrorMessage"
        @close="closeDepositSheet"
        @confirm="confirmJoin"
      />

      <div
        v-if="topupPromptOpen"
        class="fixed inset-0 z-50 flex items-center justify-center bg-scrim/70 px-screen"
      >
        <section
          role="dialog"
          aria-modal="true"
          :aria-label="t('appointment.create.insufficientTitle')"
          class="w-full max-w-[390px] rounded-card bg-surface-1 p-5 shadow-sheet"
        >
          <h2 class="text-title text-ink-display">
            {{ t('appointment.create.insufficientTitle') }}
          </h2>
          <p class="mt-2 text-body-sm text-ink-3">
            {{
              t('appointment.create.insufficientDescription', { amount: formattedDepositAmount })
            }}
          </p>
          <div class="mt-5 grid grid-cols-2 gap-3">
            <AppButton
              block
              variant="secondary"
              @click="closeTopupPrompt"
            >
              {{ t('appointment.create.insufficientLater') }}
            </AppButton>
            <AppButton
              block
              @click="goToTopup"
            >
              {{ t('appointment.create.insufficientTopup') }}
            </AppButton>
          </div>
        </section>
      </div>

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
        :no-show="isLeaveNoShow"
        :confirm-disabled="leaveMutation.isPending.value"
        :error-message="leaveErrorMessage"
        @close="closeLeaveConfirm"
        @confirm="confirmLeave"
      />
    </template>
  </main>
</template>
