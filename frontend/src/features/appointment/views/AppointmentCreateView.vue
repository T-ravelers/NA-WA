<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useMutation, useQueryClient } from '@tanstack/vue-query'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'

import { NormalizedApiError } from '@/shared/api/apiError'
import { vFitText } from '@/shared/lib/fitText'
import AppButton from '@/shared/ui/AppButton.vue'
import InsufficientBalanceDialog from '@/shared/ui/InsufficientBalanceDialog.vue'

import {
  createAppointment,
  type AppointmentCreateRequest,
  type AppointmentItemType,
} from '../api/appointmentApi'
import AppointmentCreateForm from '../components/AppointmentCreateForm.vue'
import AppointmentJourneyDateSheet from '../components/AppointmentJourneyDateSheet.vue'
import AppointmentJourneySelectSheet from '../components/AppointmentJourneySelectSheet.vue'
import type { AppointmentFormSnapshot } from '../model/appointmentForm'
import { appointmentKeys } from '../model/appointmentKeys'
import { useAppointmentJourneyIntegration } from '../model/journeyIntegration'

const route = useRoute()
const router = useRouter()
const queryClient = useQueryClient()
const i18n = useI18n()
const { t } = i18n

// 충전하러 떠났다 돌아올 때 폼을 되살리기 위한 임시 저장. 화면을 떠나는 순간 사라지는
// 컴포넌트 상태를 같은 탭 안에서만 이어 준다(sessionStorage) — 새 탭·재로그인까지
// 이어질 필요는 없다. 다른 항목의 약속 생성으로 들어왔을 때 엉뚱한 초안을 쓰지 않도록
// 항목·여정을 함께 적어 두고 돌아올 때 대조한다.
const RESUME_STORAGE_KEY = 'appointment-create:resume'
interface ResumeState {
  itemId: number
  itemType: AppointmentItemType
  tripId: number
  visitDate: string
  form: AppointmentFormSnapshot
}

function clearResumeState(): void {
  sessionStorage.removeItem(RESUME_STORAGE_KEY)
}

const createMutation = useMutation({
  mutationFn: (request: AppointmentCreateRequest) => createAppointment(request),
  onSuccess: async (appointment) => {
    clearResumeState()
    queryClient.setQueryData(appointmentKeys.detail(appointment.appointmentId), appointment)
    await queryClient.invalidateQueries({ queryKey: appointmentKeys.lists() })
    // push가 아니라 replace다. 상세의 뒤로 가기는 왔던 길을 되감으므로, 제출한
    // 폼이 히스토리에 남아 있으면 만들자마자 그 폼으로 돌아간다.
    await router.replace({
      name: 'appointment-detail',
      params: { appointmentId: appointment.appointmentId },
    })
  },
})

// 날짜 선택 시 미리 확인했더라도, 같은 계정의 다른 세션이 그 사이 먼저 그 자리를
// 약속으로 차지해버리는 드문 경쟁 상태가 있을 수 있다(JOURNEY-004). 이 경우 폼
// 입력은 그대로 둔 채 날짜 선택 시트만 다시 띄운다.
const JOURNEY_ITEM_DUPLICATE_CODE = 'JOURNEY-004'
const dateConflictRetryOpen = ref(false)

// 보증금을 예치할 잔액이 없으면 서버가 WALLET-015로 거절한다(약속 행은 만들어지지
// 않는다). 폼 위에 빨간 한 줄을 띄우는 대신, 부족하다는 사실과 다음 행동(그 금액만큼
// 충전)을 한 번에 묻는 팝업을 띄운다 — 오류 문구만 보면 왜 안 되는지, 뭘 해야
// 되는지 사용자가 스스로 알아내야 한다.
const INSUFFICIENT_BALANCE_CODE = 'WALLET-015'
const topupPromptOpen = ref(false)
// 팝업 문구와 충전 화면에 넘길 금액. 방금 제출한 요청의 보증금이다.
const requestedDepositAmount = ref<number | null>(null)

watch(
  () => createMutation.error.value,
  (error) => {
    if (!(error instanceof NormalizedApiError)) return
    if (error.code === JOURNEY_ITEM_DUPLICATE_CODE) dateConflictRetryOpen.value = true
    if (error.code === INSUFFICIENT_BALANCE_CODE) topupPromptOpen.value = true
  },
)

const errorMessage = computed(() => {
  const error = createMutation.error.value
  if (error === null || dateConflictRetryOpen.value || topupPromptOpen.value) return undefined

  if (!(error instanceof NormalizedApiError) || !i18n.te(error.messageKey)) {
    return t('appointment.create.loadFailed')
  }
  return t(error.messageKey)
})

const formattedDepositAmount = computed(() =>
  new Intl.NumberFormat('en-US').format(requestedDepositAmount.value ?? 0),
)

function closeTopupPrompt(): void {
  topupPromptOpen.value = false
  // 팝업을 닫았으면 그 오류는 다 본 것이다. 남겨두면 폼 위에 일반 오류 문구로
  // 다시 나타난다.
  createMutation.reset()
}

function submit(request: AppointmentCreateRequest): void {
  if (!createMutation.isPending.value) {
    requestedDepositAmount.value = Number(request.depositAmount)
    createMutation.mutate(request)
  }
}

function readPositiveInteger(value: unknown): number | undefined {
  const raw = Array.isArray(value) ? value[0] : value
  const parsed = Number(raw)
  return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : undefined
}

function readItemType(value: unknown): AppointmentItemType | undefined {
  const raw = Array.isArray(value) ? value[0] : value
  return raw === 'EVENT' || raw === 'PLACE' ? raw : undefined
}

const itemId = computed(() => readPositiveInteger(route.query.itemId))
const itemType = computed(() => readItemType(route.query.itemType))

// 약속 생성은 항상 여정 항목을 하나 확정하는 것으로 시작한다. 화면 진입과 동시에
// 여정 선택 시트가 뜨고, 여정·날짜를 고르기 전에는 본문 폼이 렌더링되지 않는다.
type Phase = 'journeySelect' | 'journeyDate' | 'form'
const phase = ref<Phase>('journeySelect')
const selectedTripId = ref<number | null>(null)
const selectedVisitDate = ref<string | null>(null)
const exitConfirmOpen = ref(false)
const dateCheckPending = ref(false)
const dateCheckError = ref<string | undefined>(undefined)

const journeyIntegration = useAppointmentJourneyIntegration()
const journeyListQuery = journeyIntegration.useJourneyListQuery(true)

const selectedJourney = computed(
  () =>
    journeyListQuery.data.value?.find((journey) => journey.tripId === selectedTripId.value) ?? null,
)

const journeySelectErrorMessage = computed(() =>
  journeyListQuery.isError.value ? t('appointment.journeySelect.error') : null,
)

// 여정 생성 화면에서 이 화면으로 돌아온 경우(?tripId=…), 새로 만든 여정이 목록에
// 보이는 즉시 그 여정을 선택한 채로 날짜 선택 시트로 바로 이어간다.
watch(
  () => [route.query.tripId, journeyListQuery.data.value] as const,
  ([tripIdParam, journeys]) => {
    if (phase.value !== 'journeySelect') return

    const parsed = readPositiveInteger(tripIdParam)
    if (parsed === undefined || journeys === undefined) return
    if (!journeys.some((journey) => journey.tripId === parsed)) return

    selectedTripId.value = parsed
    phase.value = 'journeyDate'
  },
  { immediate: true },
)

function selectJourney(tripId: number): void {
  selectedTripId.value = tripId
  dateCheckError.value = undefined
  phase.value = 'journeyDate'
}

// 여정 생성으로 간다. push가 아니라 replace다 — 이 단계에는 아직 적은 것이 없어
// 자리를 지킬 이유가 없고, 남겨두면 여정을 만들고 돌아왔을 때 같은 라우트가 히스토리에
// 두 번 쌓인다. 그러면 시트를 닫아 흐름을 떠나려 해도 같은 라우트의 옛 엔트리로 되감길
// 뿐이라(라우트가 같아 화면이 다시 그려지지도 않는다) 한 번 더 눌러야 빠져나간다.
// 여정 생성은 나갈 때(제출이든 포기든) 이 자리를 돌려준다.
function goToCreateJourney(): void {
  void router.replace({
    name: 'journey-create',
    query: {
      returnRouteName: 'appointment-create',
      ...(itemId.value !== undefined ? { itemId: String(itemId.value) } : {}),
      ...(itemType.value !== undefined ? { itemType: itemType.value } : {}),
    },
  })
}

function closeJourneyDate(): void {
  dateCheckError.value = undefined
  phase.value = 'journeySelect'
}

// itemType이 없으면 폼에 들어가도 항목 위치 조회가 켜지지 않아 만남 장소를 영영 읽지
// 못한다. 날짜 확인이 실패한 것처럼 말하지 말고, 진입 자체가 잘못됐다고 알리고 폼
// 단계로 넘기지 않는다.
async function confirmDate(date: string): Promise<void> {
  if (dateCheckPending.value) return
  if (itemId.value === undefined || itemType.value === undefined) {
    dateCheckError.value = t('appointment.create.validation.itemContext')
    return
  }
  if (selectedTripId.value === null) {
    dateCheckError.value = t('appointment.journeyDate.checkFailed')
    return
  }

  dateCheckPending.value = true
  dateCheckError.value = undefined

  try {
    // 담아만 둔 자리는 막지 않는다 — 서버가 그 항목을 약속 항목으로 올린다. 여기서
    // 거르는 것은 다른 약속이 이미 차지한 날짜뿐이고, 화면 문구도 그것을 말한다.
    const taken = await journeyIntegration.checkAppointmentSlotTaken(
      selectedTripId.value,
      itemId.value,
      date,
    )
    if (taken) {
      dateCheckError.value = t('appointment.journeyDate.alreadyLinked')
      return
    }

    selectedVisitDate.value = date
    phase.value = 'form'
  } catch {
    dateCheckError.value = t('appointment.journeyDate.checkFailed')
  } finally {
    dateCheckPending.value = false
  }
}

async function retryDate(date: string): Promise<void> {
  if (dateCheckPending.value) return
  if (itemId.value === undefined || itemType.value === undefined) {
    dateCheckError.value = t('appointment.create.validation.itemContext')
    return
  }
  if (selectedTripId.value === null) {
    dateCheckError.value = t('appointment.journeyDate.checkFailed')
    return
  }

  dateCheckPending.value = true
  dateCheckError.value = undefined

  try {
    const taken = await journeyIntegration.checkAppointmentSlotTaken(
      selectedTripId.value,
      itemId.value,
      date,
    )
    if (taken) {
      dateCheckError.value = t('appointment.journeyDate.alreadyLinked')
      return
    }

    selectedVisitDate.value = date
    dateConflictRetryOpen.value = false
    createMutation.reset()
  } catch {
    dateCheckError.value = t('appointment.journeyDate.checkFailed')
  } finally {
    dateCheckPending.value = false
  }
}

function closeDateConflictRetry(): void {
  dateCheckError.value = undefined
  dateConflictRetryOpen.value = false
}

type AppointmentCreateFormExposed = {
  goToPreviousStep: () => boolean
  snapshot: () => AppointmentFormSnapshot
  restore: (saved: AppointmentFormSnapshot) => void
}

const createForm = ref<AppointmentCreateFormExposed | null>(null)

// 충전 화면이 `?resume=1`로 돌려보낸 경우에만 저장해 둔 초안을 쓴다. 같은 항목의
// 약속 생성이 아니면(다른 활동에서 새로 들어온 경우) 무시한다.
function readResumeState(): ResumeState | null {
  if (route.query.resume !== '1') return null
  try {
    const raw = sessionStorage.getItem(RESUME_STORAGE_KEY)
    if (raw === null) return null
    const saved = JSON.parse(raw) as ResumeState
    if (saved.itemId !== itemId.value || saved.itemType !== itemType.value) return null
    return saved
  } catch {
    return null
  }
}

// 여정·날짜는 시트를 다시 거치지 않고 바로 폼으로 연다. 폼 자체의 값(이름·장소·
// 시각·보증금·스텝)은 폼이 마운트된 뒤 되살린다 — 그 전에는 인스턴스가 없다.
const resumeState = readResumeState()
if (resumeState !== null) {
  selectedTripId.value = resumeState.tripId
  selectedVisitDate.value = resumeState.visitDate
  phase.value = 'form'
}
onMounted(() => {
  if (resumeState === null) return
  createForm.value?.restore(resumeState.form)
  // 한 번 되살렸으면 끝이다. 남겨두면 나중에 같은 항목으로 새로 들어올 때 옛 초안이 뜬다.
  clearResumeState()
})

// 충전 화면으로 간다. 금액을 query로 넘겨 입력란이 보증금만큼 미리 채워지게 하고,
// 어디서 왔는지(returnRouteName)와 이 화면의 query를 함께 넘겨 충전이 끝나면 여기로
// 돌아오게 한다. 떠나기 전 폼 초안을 저장해 둬, 돌아왔을 때 "Create appointment"만
// 다시 누르면 되게 한다. replace가 아니라 push다 — 충전을 포기하고 뒤로 와도 작성하던
// 흐름으로 돌아와야 한다.
//
// 다만 돌아오는 길이 둘이고 도착지가 다르다. 충전을 마치면 충전 화면이 `?resume=1`을
// 붙여 보내지만, 뒤로가기는 브라우저가 **떠날 때의 URL 그대로** 되돌린다. 그 자리에
// 표시가 없으면 초안을 저장해 두고도 읽지 않아, 충전을 포기했을 뿐인데 적은 내용이
// 사라진다. 그래서 떠나기 전에 지금 자리를 `?resume=1`로 바꿔 둔다 — 어느 길로 돌아오든
// 같은 자리에서 되살아난다. 초안을 실제로 저장했을 때만 표시한다.
function goToTopup(): void {
  const amount = requestedDepositAmount.value
  topupPromptOpen.value = false

  const openTopup = () =>
    router.push({
      name: 'wallet-top-up',
      query: {
        ...(amount === null ? {} : { amount: String(amount) }),
        returnRouteName: 'appointment-create',
        ...(itemId.value !== undefined ? { itemId: String(itemId.value) } : {}),
        ...(itemType.value !== undefined ? { itemType: itemType.value } : {}),
        ...(selectedTripId.value !== null ? { tripId: String(selectedTripId.value) } : {}),
      },
    })

  const form = createForm.value?.snapshot()
  if (
    form !== undefined &&
    itemId.value !== undefined &&
    itemType.value !== undefined &&
    selectedTripId.value !== null &&
    selectedVisitDate.value !== null
  ) {
    const state: ResumeState = {
      itemId: itemId.value,
      itemType: itemType.value,
      tripId: selectedTripId.value,
      visitDate: selectedVisitDate.value,
      form,
    }
    sessionStorage.setItem(RESUME_STORAGE_KEY, JSON.stringify(state))
    // 표시를 먼저 남기고 떠난다. 순서가 뒤집히면 replace가 충전 화면 위에서 일어난다.
    void router.replace({ query: { ...route.query, resume: '1' } }).then(openTopup)
    return
  }

  void openTopup()
}

// 흐름을 떠난다. 왔던 길을 되감고, 히스토리가 없을 때(딥링크·PWA 재진입)만
// 약속 목록으로 보낸다.
function leaveFlow(): void {
  if (window.history.length > 1) {
    void router.back()
    return
  }
  void router.push({ name: 'appointment-list' })
}

// 이탈 확인 모달은 폼에 적은 내용을 지키기 위한 것이다. 여정 선택 단계에는
// 아직 적은 것이 없으므로 묻지 않고 바로 떠난다 — 시트 바깥을 한 번 눌렀을
// 뿐인데 "잃어버린다"는 모달이 뜨면 무엇을 잃는지 알 수 없다.
function goBack(): void {
  if (phase.value === 'form') {
    if (createForm.value?.goToPreviousStep()) return
    exitConfirmOpen.value = true
    return
  }
  if (phase.value === 'journeyDate') {
    closeJourneyDate()
    return
  }
  leaveFlow()
}

function cancelExit(): void {
  exitConfirmOpen.value = false
}

function confirmExit(): void {
  exitConfirmOpen.value = false
  leaveFlow()
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
        class="min-w-0 flex-1 truncate font-display text-screen-title text-ink-display"
      >
        {{ t('appointment.create.title') }}
      </h1>
    </header>

    <AppointmentCreateForm
      v-if="phase === 'form' && selectedTripId !== null && selectedVisitDate !== null"
      ref="createForm"
      :item-id="itemId"
      :item-type="itemType"
      :trip-id="selectedTripId"
      :visit-date="selectedVisitDate"
      :pending="createMutation.isPending.value"
      :error-message="errorMessage"
      @submit="submit"
    />

    <AppointmentJourneySelectSheet
      v-if="phase === 'journeySelect'"
      :journeys="journeyListQuery.data.value ?? []"
      :selected-journey-id="selectedTripId"
      :loading="journeyListQuery.isPending.value"
      :error-message="journeySelectErrorMessage"
      @close="leaveFlow"
      @select="selectJourney"
      @create-journey="goToCreateJourney"
    />

    <AppointmentJourneyDateSheet
      v-if="phase === 'journeyDate' && selectedJourney"
      :journey-title="selectedJourney.title"
      :start-date="selectedJourney.startDate"
      :end-date="selectedJourney.endDate"
      :initial-date="selectedVisitDate"
      :loading="dateCheckPending"
      :error-message="dateCheckError"
      @close="closeJourneyDate"
      @confirm="confirmDate"
    />

    <AppointmentJourneyDateSheet
      v-if="dateConflictRetryOpen && selectedJourney"
      :journey-title="selectedJourney.title"
      :start-date="selectedJourney.startDate"
      :end-date="selectedJourney.endDate"
      :initial-date="selectedVisitDate"
      :loading="dateCheckPending"
      :error-message="dateCheckError"
      @close="closeDateConflictRetry"
      @confirm="retryDate"
    />

    <div
      v-if="exitConfirmOpen"
      class="fixed inset-0 z-50 flex items-end justify-center bg-scrim/70 px-screen pb-6"
    >
      <section
        role="dialog"
        aria-modal="true"
        :aria-label="t('appointment.create.exitConfirmTitle')"
        class="w-full max-w-[390px] rounded-card bg-surface-1 p-5 shadow-sheet"
      >
        <h2 class="text-title text-ink-display">{{ t('appointment.create.exitConfirmTitle') }}</h2>
        <p class="mt-2 text-body-sm text-ink-3">
          {{ t('appointment.create.exitConfirmDescription') }}
        </p>
        <div class="mt-5 grid grid-cols-2 gap-3">
          <AppButton
            block
            variant="secondary"
            @click="cancelExit"
          >
            {{ t('appointment.create.exitConfirmStay') }}
          </AppButton>
          <AppButton
            block
            variant="settle"
            @click="confirmExit"
          >
            {{ t('appointment.create.exitConfirmLeave') }}
          </AppButton>
        </div>
      </section>
    </div>

    <InsufficientBalanceDialog
      v-if="topupPromptOpen"
      placement="bottom"
      :title="t('appointment.create.insufficientTitle')"
      :description="
        t('appointment.create.insufficientDescription', { amount: formattedDepositAmount })
      "
      :later-label="t('appointment.create.insufficientLater')"
      :topup-label="t('appointment.create.insufficientTopup')"
      @close="closeTopupPrompt"
      @topup="goToTopup"
    />
  </main>
</template>
