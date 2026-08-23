import { useMutation, useQueryClient } from '@tanstack/vue-query'
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter, type LocationQueryRaw } from 'vue-router'

import { NormalizedApiError } from '@/shared/api/apiError'
import { toServerCalendarDate } from '@/shared/lib/datetime'
import { serializeReturnParams } from '@/shared/lib/returnRoute'

import { joinAppointment, type AppointmentDateTimeValue } from '../api/appointmentApi'
import { appointmentErrorMessageKey } from '../model/appointmentErrors'
import { appointmentKeys } from '../model/appointmentKeys'
import {
  useAppointmentJourneyIntegration,
  type AppointmentJourneySummary,
} from '../model/journeyIntegration'

/**
 * 참여에 필요한 약속 정보. 목록 카드와 상세 응답이 공통으로 가진 만큼만 받는다.
 */
export interface AppointmentJoinTarget {
  appointmentId: number
  appointmentName: string
  depositAmount: string
  activityStartAt: AppointmentDateTimeValue
}

export interface AppointmentJoinReturnRoute {
  name: string
  params?: Record<string, string>
  query?: LocationQueryRaw
}

interface Options {
  /**
   * 여정 생성·충전에서 돌아올 자리. 상세는 param으로, 목록은 query로 자기를 가리킨다.
   */
  returnRoute: (target: AppointmentJoinTarget) => AppointmentJoinReturnRoute
  /**
   * 충전으로 떠나기 전에 **지금 자리에** 남길 query. 충전을 마치면 규약대로 돌아오지만,
   * 뒤로 가기는 떠날 때의 URL을 그대로 되돌린다. 그 자리에 고른 여정이 없으면 충전을
   * 포기했을 뿐인데 처음부터 다시 골라야 한다.
   */
  resumeMarker: (target: AppointmentJoinTarget, tripId: number) => LocationQueryRaw
  /**
   * 참여할 수 없는 약속이면 시트를 열지 않는다. 이유는 화면이 이미 보여주고 있다.
   * 목록은 참여 여부를 모르므로 넘기지 않고, 서버 판정에 맡긴다.
   */
  blocked?: () => boolean
}

/**
 * 약속 참여 흐름. 여정을 고르고, 보증금을 확인하고, 참여를 요청한다.
 *
 * 목록 카드의 Join과 상세 화면의 참여 버튼이 같은 흐름을 쓴다. 두 화면이 각자 들고
 * 있으면 여정 날짜 검사나 잔액 부족 안내 같은 규칙이 한쪽만 고쳐진다.
 */
export function useAppointmentJoinFlow(options: Options) {
  const route = useRoute()
  const router = useRouter()
  const queryClient = useQueryClient()
  const i18n = useI18n()
  const { t } = i18n
  const hasMessage = (key: string): boolean => i18n.te(key)

  const journeyIntegration = useAppointmentJourneyIntegration()

  const target = ref<AppointmentJoinTarget | null>(null)
  const journeySelectOpen = ref(false)
  const depositSheetOpen = ref(false)
  const topupPromptOpen = ref(false)
  const selectedTripId = ref<number | null>(null)
  const journeySelectionError = ref<string | null>(null)

  const journeyListQuery = journeyIntegration.useJourneyListQuery(journeySelectOpen)
  const journeyListErrorMessage = computed(() =>
    journeyListQuery.isError.value ? t('appointment.journeySelect.error') : null,
  )

  // 활동 날짜(YYYY-MM-DD). 문자열이라고 보고 자르면 안 된다 — 이 값은 Jackson이 숫자
  // 배열로도 보낼 수 있는 타입이라, 배열에 slice를 쓰면 배열이 나오고 아래 기간 비교가
  // 조용히 전부 false가 되어 모든 여정이 "날짜를 담지 못함"으로 보인다.
  const activityDate = computed(() => {
    const date = toServerCalendarDate(target.value?.activityStartAt)
    return date === '' ? null : date
  })

  /** 날짜를 모르면 거르지 않고 서버 판단에 맡긴다. */
  function coversActivityDate(journey: AppointmentJourneySummary): boolean {
    const date = activityDate.value
    if (date === null) return true
    return journey.startDate <= date && date <= journey.endDate
  }

  /**
   * 참여·탈퇴는 이 약속만 바꾸지 않는다. 목록 카드의 인원수와 내 약속 목록(지갑 QR
   * 공동결제가 쓴다)도 함께 어긋나므로 다섯 갈래를 같이 무효화한다.
   */
  async function invalidateParticipationScopes(appointmentId: number): Promise<void> {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: appointmentKeys.detail(appointmentId) }),
      queryClient.invalidateQueries({ queryKey: appointmentKeys.members(appointmentId) }),
      queryClient.invalidateQueries({ queryKey: appointmentKeys.participation(appointmentId) }),
      queryClient.invalidateQueries({ queryKey: appointmentKeys.lists() }),
      queryClient.invalidateQueries({ queryKey: appointmentKeys.mine() }),
    ])
  }

  const joinMutation = useMutation({
    mutationFn: (tripId: number) => joinAppointment(target.value?.appointmentId as number, tripId),
    onSuccess: async () => {
      const joined = target.value
      depositSheetOpen.value = false
      if (joined !== null) await invalidateParticipationScopes(joined.appointmentId)
    },
  })

  const joinErrorMessage = computed(() =>
    joinMutation.error.value === null || topupPromptOpen.value
      ? undefined
      : t(appointmentErrorMessageKey(joinMutation.error.value, hasMessage)),
  )

  const formattedDepositAmount = computed(() =>
    new Intl.NumberFormat('en-US').format(Number(target.value?.depositAmount ?? 0)),
  )

  function open(next: AppointmentJoinTarget): void {
    if (options.blocked?.() === true) return

    joinMutation.reset()
    target.value = next
    selectedTripId.value = null
    journeySelectionError.value = null
    journeySelectOpen.value = true
  }

  /**
   * 여정을 만들거나 충전하고 돌아온 경우. 참여를 다시 누르게 하지 않고 시트를 열어,
   * 그 여정을 골라 둔 채로 보여 준다.
   */
  function resume(next: AppointmentJoinTarget, tripId: number): void {
    if (options.blocked?.() === true) return
    // 보증금 확인까지 넘어간 뒤에는 시트를 다시 열지 않는다.
    if (depositSheetOpen.value) return

    target.value = next
    journeySelectOpen.value = true
    // 목록이 오기 전에는 고를 수 없다. 오고 나서 그 여정이 실제로 있을 때만 고른다.
    if (journeyListQuery.data.value?.some((journey) => journey.tripId === tripId) === true) {
      selectedTripId.value = tripId
    }
  }

  function closeJourneySelect(): void {
    journeySelectOpen.value = false
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
  }

  function closeDepositSheet(): void {
    depositSheetOpen.value = false
  }

  function confirmJoin(): void {
    if (joinMutation.isPending.value || selectedTripId.value === null) return
    joinMutation.mutate(selectedTripId.value)
  }

  function closeTopupPrompt(): void {
    topupPromptOpen.value = false
    // 팝업을 닫았으면 그 오류는 다 본 것이다. 남겨두면 일반 오류 문구로 다시 나타난다.
    joinMutation.reset()
  }

  // 보증금을 예치할 잔액이 없으면 서버가 WALLET-015로 거절한다. 빨간 한 줄 대신
  // 부족하다는 사실과 다음 행동을 한 번에 묻는다 — 약속 생성과 같은 규칙이다.
  const INSUFFICIENT_BALANCE_CODE = 'WALLET-015'

  function handleJoinError(error: unknown): void {
    if (!(error instanceof NormalizedApiError)) return
    if (error.code !== INSUFFICIENT_BALANCE_CODE) return
    // 보증금 시트를 닫고 팝업만 남긴다. 두 겹으로 쌓이면 무엇을 눌러야 할지 흐려진다.
    depositSheetOpen.value = false
    topupPromptOpen.value = true
  }

  /** 이 약속을 담을 여정이 없다. 자리를 내주고(replace) 보낸다. */
  function goToCreateJourney(): void {
    const current = target.value
    if (current === null) return

    journeySelectOpen.value = false
    const back = options.returnRoute(current)
    void router.replace({
      name: 'journey-create',
      query: {
        ...back.query,
        returnRouteName: back.name,
        ...(back.params === undefined ? {} : { returnParams: serializeReturnParams(back.params) }),
      },
    })
  }

  /**
   * 충전 화면으로 간다. 금액을 미리 채우고, 돌아올 곳으로 지금 고른 여정까지 실어
   * 보낸다. replace가 아니라 push다 — 충전을 포기하고 뒤로 와도 이 화면으로 돌아와야
   * 한다.
   */
  function goToTopup(): void {
    const current = target.value
    if (current === null) return

    topupPromptOpen.value = false
    const tripId = selectedTripId.value
    const back = options.returnRoute(current)

    const openTopup = () =>
      router.push({
        name: 'wallet-top-up',
        query: {
          ...back.query,
          amount: current.depositAmount,
          returnRouteName: back.name,
          ...(back.params === undefined
            ? {}
            : { returnParams: serializeReturnParams(back.params) }),
          ...(tripId === null ? {} : { tripId: String(tripId) }),
        },
      })

    if (tripId === null) {
      void openTopup()
      return
    }

    // 표시를 먼저 남기고 떠난다. 순서가 뒤집히면 replace가 충전 화면 위에서 일어난다.
    void router
      .replace({ path: route.path, query: options.resumeMarker(current, tripId) })
      .then(openTopup)
  }

  return {
    target,
    journeySelectOpen,
    depositSheetOpen,
    topupPromptOpen,
    selectedTripId,
    journeySelectionError,
    journeyListQuery,
    journeyListErrorMessage,
    joinMutation,
    joinErrorMessage,
    formattedDepositAmount,
    coversActivityDate,
    open,
    resume,
    closeJourneySelect,
    selectJourney,
    closeDepositSheet,
    confirmJoin,
    closeTopupPrompt,
    handleJoinError,
    goToCreateJourney,
    goToTopup,
  }
}
