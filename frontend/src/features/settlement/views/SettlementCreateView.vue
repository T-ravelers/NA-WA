<script setup lang="ts">
import { IconX } from '@tabler/icons-vue'
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'

import AppButton from '@/shared/ui/AppButton.vue'
import AppCard from '@/shared/ui/AppCard.vue'

import { settlementGateway } from '../api/settlementGateway'
import SettlementBottomSheet from '../components/SettlementBottomSheet.vue'
import SettlementEmptyState from '../components/SettlementEmptyState.vue'
import SettlementReceiptSheet from '../components/SettlementReceiptSheet.vue'
import SettlementStatusScreen from '../components/SettlementStatusScreen.vue'
import SettlementTransactionCard from '../components/SettlementTransactionCard.vue'
import { useSettlementPoints } from '../composables/useSettlementPoints'
import {
  useSettlementReceiptOcr,
  useSettlementReceiptUpload,
} from '../composables/useSettlementReceipt'
import type {
  ItemizedSettlementItem,
  SettlementCandidate,
  SettlementType,
} from '../model/settlement'
import { resolveSettlementError } from '../model/settlementErrors'
import { groupCandidates } from '../model/settlementGrouping'
import {
  clearSettlementCreateIdempotencyKey,
  resolveSettlementCreateIdempotencyKey,
} from '../model/settlementIdempotency'
import {
  compareItemizedTotal,
  summarizeItemizedShares,
  validateItemizedItems,
} from '../model/settlementRules'

/**
 * 정산 요청서를 만드는 세 단계.
 *
 * 1단계는 여정 → 약속 → 거래를 좁혀 무엇을 정산할지 정하고, 2단계는 방식·참여자·품목을
 * 채우고, 3단계는 읽기 전용으로 확인만 한다. 1/N과 품목별이 같은 단계 수를 갖도록 두
 * 방식의 골격을 맞췄다.
 */
const props = defineProps<{ candidates: SettlementCandidate[] }>()
const emit = defineEmits<{
  complete: [settlementId: string]
  cancel: []
  refreshCandidates: []
  'update:step': [step: number]
  /** v-model이 아니라 부모가 머리말을 감출지 판단하는 신호다. */
  submittingChange: [submitting: boolean]
}>()

const { t } = useI18n()
const points = useSettlementPoints()

const step = ref(1)
const journeyKey = ref<string | null>(null)
const appointmentId = ref<string | null>(null)
const selectedCandidate = ref<SettlementCandidate | null>(null)
const type = ref<SettlementType>('EQUAL')
const selectedParticipantIds = ref<string[]>([])
const items = ref<ItemizedSettlementItem[]>([])
const submitting = ref(false)
const error = ref<unknown>(null)
const validationMessage = ref<string | null>(null)
/** 1단계 위쪽에 뜨는 안내. 2단계 전용인 validationMessage와 자리가 달라 따로 둔다. */
const candidateNotice = ref<string | null>(null)
const receipt = useSettlementReceiptUpload()
const receiptSheetOpen = ref(false)
const ocr = useSettlementReceiptOcr()
const overwriteSheetOpen = ref(false)
/*
 * 방금 인식으로 채웠는지 기억한다.
 *
 * 인식은 누가 무엇을 먹었는지 알려주지 않아 배분이 비어 있는 채로 채워진다. 그 상태가
 * 고장이 아니라 다음에 할 일이 남은 것임을 알려 주는 데만 쓴다.
 */
const loadedFromReceipt = ref(false)

const journeys = computed(() => groupCandidates(props.candidates))
const selectedJourney = computed(
  () => journeys.value.find((journey) => journey.key === journeyKey.value) ?? null,
)
const appointments = computed(() => selectedJourney.value?.appointments ?? [])
const selectedAppointment = computed(
  () =>
    appointments.value.find((appointment) => appointment.appointmentId === appointmentId.value) ??
    null,
)
const transactions = computed(() => selectedAppointment.value?.candidates ?? [])
const journeyLabel = computed(() =>
  selectedJourney.value === null || selectedJourney.value.journeyName === ''
    ? t('settlement.create.unassignedJourney')
    : selectedJourney.value.journeyName,
)

const selectedIds = computed(() => new Set(selectedParticipantIds.value))
const chosenParticipants = computed(
  () =>
    selectedCandidate.value?.participants.filter((entry) => selectedIds.value.has(entry.id)) ?? [],
)
const itemValidation = computed(() => validateItemizedItems(items.value, selectedIds.value))
const hasEnoughParticipants = computed(() => selectedParticipantIds.value.length >= 2)
/*
 * 서버는 품목 금액의 합이 원거래 금액과 정확히 같을 때만 정산을 만든다. 여기서 막지 않으면
 * 사용자가 검토 단계까지 다 채운 뒤 제출에서야 거절당한다.
 */
const itemsTotal = computed(() =>
  selectedCandidate.value === null
    ? null
    : compareItemizedTotal(items.value, selectedCandidate.value.amount),
)
const totalMatchesSource = computed(() => itemsTotal.value?.matches !== false)
/*
 * 품목별 정산에서만 사람별 금액을 미리 보여준다. 균등 분할은 나머지를 누가 더 낼지가
 * 통화의 최소 단위에 달려 있는데 화면은 그 단위를 모른다. 여기서 어림잡아 보여주면
 * 실제 청구 금액과 1단위 어긋난 숫자를 확정된 것처럼 내보이게 된다.
 */
const itemizedShares = computed(() =>
  type.value !== 'ITEMIZED' || selectedCandidate.value === null
    ? null
    : summarizeItemizedShares(items.value, selectedCandidate.value.payerAppointmentMemberId),
)

/*
 * 원거래 금액이 쓰는 소수 자릿수.
 *
 * 계산한 금액은 뒤의 0을 떼고 나오므로 그대로 두면 25.00과 25가 나란히 놓인다. 같은
 * 금액인데 달라 보이면 합계가 맞는지 눈으로 확인할 수 없다.
 */
const amountFractionDigits = computed(
  () => selectedCandidate.value?.amount.split('.')[1]?.length ?? 0,
)

/** 배분이 없는 사람은 0으로 보여 준다. 빈칸이면 왜 없는지 알 수 없다. */
function shareAmountOf(appointmentMemberId: string): string {
  return (
    itemizedShares.value?.shares.find((share) => share.appointmentMemberId === appointmentMemberId)
      ?.amount ?? '0'
  )
}

/**
 * 지워질 내용이 있는지 본다. 덮어쓰기 전에 물어볼지 정하는 기준이다.
 *
 * 배분도 함께 센다. 품목 칸은 비었는데 누가 얼마를 먹었는지만 적어 둔 경우가 있고, 그것도
 * 사용자가 손으로 넣은 값이라 말 없이 지우면 안 된다.
 */
const hasEnteredItems = computed(() =>
  items.value.some(
    (item) =>
      item.name.trim() !== '' ||
      item.unitPrice.trim() !== '' ||
      item.quantity.trim() !== '' ||
      item.allocations.length > 0,
  ),
)

/*
 * 품목별로 나누기로 했고 사진이 다 올라갔을 때만 읽을 수 있다.
 *
 * 균등 분할에는 품목 자리가 없어 읽어 봐야 넣을 곳이 없고, 인식은 부를 때마다 요금이 나간다.
 */
const canLoadItems = computed(
  () =>
    type.value === 'ITEMIZED' &&
    receipt.receiptId.value !== null &&
    !receipt.pending.value &&
    !ocr.pending.value,
)

/** 배분을 다 채우고 나면 안내를 거둔다. 다 한 일을 계속 시키면 안 된다. */
const showAllocateHint = computed(() => loadedFromReceipt.value && !itemValidation.value.valid)

const canContinueDetails = computed(
  () =>
    hasEnoughParticipants.value &&
    /*
     * 사진이 다 올라가기 전에는 넘기지 않는다.
     *
     * 아직 영수증 번호가 없는 상태로 요청이 나가면 정산은 영수증 없이 만들어지고, 나중에
     * 붙일 수도 없다. 오류도 안내도 없이 방금 찍은 사진만 사라진다.
     */
    !receipt.pending.value &&
    (type.value === 'EQUAL' || (itemValidation.value.valid && totalMatchesSource.value)),
)

/*
 * 잘못된 품목 표시는 "계속"을 누른 뒤부터 켠다. 입력하는 도중에 빨간 표시가 따라다니면
 * 아직 다 적지도 않은 칸을 틀렸다고 말하는 꼴이 된다.
 */
const showItemErrors = ref(false)
const invalidItemIndexes = computed(() =>
  showItemErrors.value ? new Set(itemValidation.value.invalidItemIndexes) : new Set<number>(),
)

watch(submitting, (value) => emit('submittingChange', value))

/**
 * 고른 결제가 목록에서 사라졌는지 지켜본다.
 *
 * selectedCandidate는 목록에서 떼어낸 사본이라 목록이 새로 와도 저절로 맞춰지지 않는다.
 * 그대로 두면 이미 없어진 결제 번호로 요청을 보내게 되므로, 선택을 비우고 왜 그런지 알린 뒤
 * 1단계로 돌려보낸다.
 */
watch(
  () => props.candidates,
  (candidates) => {
    // 제출 중에는 손대지 않는다. 성공 직후 부모의 무효화가 정산된 결제를 뺀 목록을
    // 내려보내는데, 여기서 1단계로 되돌리면 성공 화면 뒤에서 상태가 뒤집힌다.
    // 놓친 변경은 실패 시 REFETCH_CANDIDATES 복구 경로의 재조회가 다시 가져온다.
    if (submitting.value) return
    const selected = selectedCandidate.value
    if (selected === null) return
    if (candidates.some((entry) => entry.transferId === selected.transferId)) return

    selectedCandidate.value = null
    selectedParticipantIds.value = []
    items.value = []
    error.value = null
    validationMessage.value = null
    candidateNotice.value = t('settlement.create.candidateGone')
    step.value = 1
    emit('update:step', step.value)
  },
)

function resetJourney(): void {
  journeyKey.value = null
  appointmentId.value = null
  selectedCandidate.value = null
  candidateNotice.value = null
}

function resetAppointment(): void {
  appointmentId.value = null
  selectedCandidate.value = null
  candidateNotice.value = null
}

function selectJourney(key: string): void {
  journeyKey.value = key
  appointmentId.value = null
  selectedCandidate.value = null
}

function selectAppointment(id: string): void {
  appointmentId.value = id
  selectedCandidate.value = null
}

function selectTransaction(candidate: SettlementCandidate): void {
  selectedCandidate.value = candidate
  selectedParticipantIds.value = [candidate.payerAppointmentMemberId]
  items.value = []
  /*
   * 앞서 고른 결제에 붙였던 영수증을 반드시 떼어낸다.
   *
   * 남겨 두면 다른 결제의 정산이 엉뚱한 영수증을 달고 만들어지고, 한 번 연결된 영수증은
   * 바꿀 수 없어 되돌릴 방법이 없다.
   */
  receipt.reset()
  ocr.reset()
  loadedFromReceipt.value = false
  error.value = null
  candidateNotice.value = null
}

function goToDetails(): void {
  if (selectedCandidate.value === null) return
  ensureFirstItem()
  step.value = 2
  emit('update:step', step.value)
}

function setType(nextType: SettlementType): void {
  type.value = nextType
  ensureFirstItem()
  validationMessage.value = null
}

/**
 * 결제자인가.
 *
 * 결제자는 정산에서 뺄 수 없다. 그런데 칩은 평범한 버튼이라 눌러도 아무 일이 없고 왜
 * 그런지 알 방법이 없었다(#382). 화면은 이 값으로 `aria-disabled`와 조형을 가른다 —
 * 다른 선택 칩은 테두리와 10% 면이고 결제자만 꽉 찬 면이다. 「선택됨」이 아니라
 * 「고정됨」으로 읽히게 한다.
 */
function isPayer(participantId: string): boolean {
  return participantId === selectedCandidate.value?.payerAppointmentMemberId
}

function toggleParticipant(participantId: string): void {
  const candidate = selectedCandidate.value
  if (candidate === null || participantId === candidate.payerAppointmentMemberId) return

  const removing = selectedIds.value.has(participantId)
  selectedParticipantIds.value = removing
    ? selectedParticipantIds.value.filter((id) => id !== participantId)
    : [...selectedParticipantIds.value, participantId]

  // 배분 입력칸은 선택된 사람만 그리므로 해제하면 화면에서 사라진다. 그런데 값이 남아 있으면
  // 검증은 보이지 않는 수량을 계속 더한다. 그러면 보이는 어떤 편집으로도 통과할 수 없다.
  if (removing) {
    items.value = items.value.map((item) => ({
      ...item,
      allocations: item.allocations.filter(
        (allocation) => allocation.appointmentMemberId !== participantId,
      ),
    }))
  }

  validationMessage.value = null
}

/**
 * 사진을 바꾸면 앞서 읽어낸 결과를 버린다.
 *
 * 그대로 두면 지금 붙어 있는 사진과 다른 영수증의 합계가 화면에 남아, 어떤 사진을 견주는
 * 중인지 알 수 없게 된다.
 */
function selectReceipt(file: File): void {
  ocr.reset()
  loadedFromReceipt.value = false
  void receipt.select(file)
}

/**
 * 영수증에서 품목을 읽어 카드에 채운다.
 *
 * 이미 적어 둔 것이 있으면 **읽기 전에** 묻는다. 인식은 부를 때마다 요금이 나가므로, 사용자가
 * 덮어쓰지 않기로 할 요청을 미리 보낼 이유가 없다.
 */
function loadItems(): void {
  if (!canLoadItems.value) return

  if (hasEnteredItems.value) {
    overwriteSheetOpen.value = true
    return
  }
  void recognizeIntoItems()
}

function confirmOverwrite(): void {
  overwriteSheetOpen.value = false
  void recognizeIntoItems()
}

async function recognizeIntoItems(): Promise<void> {
  const receiptId = receipt.receiptId.value
  if (receiptId === null) return

  const recognized = await ocr.recognize(receiptId)
  if (recognized === null) return

  items.value = recognized.map((item) => ({ ...item, allocations: [] }))
  loadedFromReceipt.value = true
  /*
   * 빨간 표시를 켜지 않는다.
   *
   * 배분이 비어 있어 검증은 아직 통과하지 못하지만 그것은 인식이 실패해서가 아니다. 채우자마자
   * 카드가 전부 빨개지면 방금 읽어 온 값이 잘못된 것처럼 보인다.
   */
  showItemErrors.value = false
  validationMessage.value = null
}

function addItem(): void {
  items.value.push({ name: '', unitPrice: '', quantity: '', allocations: [] })
}

/*
 * 품목별 정산으로 들어오면 빈 품목 한 장을 미리 깔아 둔다.
 *
 * 품목이 하나도 없는 품목별 정산은 어차피 다음 단계로 넘어가지 못한다. 빈 자리를 두고
 * "추가"부터 누르게 하면 무엇을 적는 화면인지 보여주지도 못한 채 한 번 더 두드리게 만든다.
 * 이미 적어 둔 품목이 있으면 건드리지 않아서, 방식을 오갔다 돌아와도 값이 그대로 남는다.
 */
function ensureFirstItem(): void {
  if (type.value === 'ITEMIZED' && items.value.length === 0) addItem()
}

/*
 * 마지막 한 장은 지우지 않는다.
 *
 * 품목이 0개면 진행할 수 없는데, 그 상태에서는 왜 막혔는지 짚어 줄 카드조차 화면에 남지
 * 않는다. 그래서 마지막 한 장에서는 버튼 자체를 감춘다. 내용을 비우려면 칸을 지우면 된다.
 */
function removeItem(index: number): void {
  if (items.value.length <= 1) return
  items.value.splice(index, 1)
  validationMessage.value = null
}

function updateItem(index: number, field: 'name' | 'unitPrice' | 'quantity', value: string): void {
  const item = items.value[index]
  if (item !== undefined) item[field] = value
}

function allocationValue(index: number, participantId: string): string {
  return (
    items.value[index]?.allocations.find(
      (allocation) => allocation.appointmentMemberId === participantId,
    )?.quantity ?? ''
  )
}

function updateAllocation(index: number, participantId: string, quantity: string): void {
  const item = items.value[index]
  if (item === undefined) return
  const current = item.allocations.find(
    (allocation) => allocation.appointmentMemberId === participantId,
  )
  if (quantity.trim() === '') {
    item.allocations = item.allocations.filter(
      (allocation) => allocation.appointmentMemberId !== participantId,
    )
  } else if (current === undefined) {
    item.allocations.push({ appointmentMemberId: participantId, quantity })
  } else {
    current.quantity = quantity
  }
}

function goToReview(): void {
  if (!hasEnoughParticipants.value) {
    validationMessage.value = t('settlement.create.participantsTooFew')
    return
  }
  if (receipt.pending.value) {
    validationMessage.value = t('settlement.receipt.uploading')
    return
  }
  if (!canContinueDetails.value) {
    // 합계가 어긋난 것과 품목 입력이 덜 된 것은 고쳐야 할 곳이 달라 문구를 나눈다.
    validationMessage.value =
      itemValidation.value.valid && !totalMatchesSource.value
        ? t('settlement.create.totalMismatch')
        : t('settlement.create.allocationIncomplete')
    showItemErrors.value = true
    return
  }

  validationMessage.value = null
  showItemErrors.value = false
  step.value = 3
  emit('update:step', step.value)
}

async function create(): Promise<void> {
  const candidate = selectedCandidate.value
  if (candidate === null || !canContinueDetails.value || submitting.value) return
  const request = {
    sourceTransferId: candidate.transferId,
    type: type.value,
    participantAppointmentMemberIds: [...selectedParticipantIds.value].sort(),
    ...(type.value === 'ITEMIZED' ? { items: items.value } : {}),
    ...(receipt.receiptId.value !== null ? { receiptId: receipt.receiptId.value } : {}),
  }
  submitting.value = true
  error.value = null
  try {
    const key = resolveSettlementCreateIdempotencyKey(candidate.appointmentId, request)
    const result = await settlementGateway.create(candidate.appointmentId, key, request)
    clearSettlementCreateIdempotencyKey(candidate.transferId)
    // 성공 뒤에는 풀지 않는다. 부모가 다음 화면으로 넘길 때까지 이 화면이 살아 있는데,
    // 검토 화면으로 되돌아가면 멱등키가 이미 지워져 다시 누르는 순간 새 키로 두 번째
    // 요청이 나간다.
    emit('complete', result.id)
  } catch (reason) {
    submitting.value = false
    error.value = reason
    if (resolveSettlementError(reason).recovery === 'REFETCH_CANDIDATES') {
      emit('refreshCandidates')
    }
  }
}

function back(): void {
  if (submitting.value) return
  if (step.value === 1) {
    if (selectedAppointment.value !== null) resetAppointment()
    else if (selectedJourney.value !== null) resetJourney()
    else emit('cancel')
    return
  }

  step.value -= 1
  emit('update:step', step.value)
}

defineExpose({ back })
</script>

<template>
  <SettlementStatusScreen
    v-if="submitting"
    state="processing"
    :title="t('settlement.create.requesting')"
    :description="t('settlement.create.requestingHint')"
  />

  <div
    v-else
    class="flex flex-1 flex-col"
  >
    <div
      v-if="step === 1"
      class="flex flex-1 flex-col"
    >
      <p
        v-if="candidateNotice !== null"
        class="mt-4 text-body-sm text-danger"
        role="alert"
      >
        {{ candidateNotice }}
      </p>
      <SettlementEmptyState
        v-if="candidates.length === 0"
        class="flex-1"
        data-testid="settlement-no-payments"
        :title="t('settlement.create.noPaymentsTitle')"
        :description="t('settlement.create.noPaymentsDescription')"
      />
      <template v-else-if="selectedJourney === null">
        <h2 class="mt-8 text-section-header">{{ t('settlement.create.journeys') }}</h2>
        <p class="mt-2 text-body-sm text-ink-2">{{ t('settlement.create.selectJourney') }}</p>
        <ul class="mt-5 space-y-3">
          <li
            v-for="journey in journeys"
            :key="journey.key"
          >
            <button
              type="button"
              :data-journey-key="journey.key"
              class="flex min-h-14 w-full items-center justify-between gap-3 rounded-sm bg-surface-1 p-4 text-left"
              @click="selectJourney(journey.key)"
            >
              <span class="min-w-0 truncate">{{
                journey.journeyName === ''
                  ? t('settlement.create.unassignedJourney')
                  : journey.journeyName
              }}</span>
              <span class="shrink-0 text-caption text-ink-3">{{
                t(
                  'settlement.create.paymentCount',
                  { count: journey.paymentCount },
                  journey.paymentCount,
                )
              }}</span>
            </button>
          </li>
        </ul>
      </template>

      <template v-else>
        <dl class="mt-8 space-y-3">
          <div class="flex items-center justify-between gap-3">
            <div class="min-w-0">
              <dt class="text-caption text-ink-3">{{ t('settlement.create.journeys') }}</dt>
              <dd class="truncate text-body">{{ journeyLabel }}</dd>
            </div>
            <button
              type="button"
              data-action="change-journey"
              class="min-h-11 shrink-0 text-body-sm text-ink-2 underline underline-offset-4"
              @click="resetJourney"
            >
              {{ t('settlement.create.changeJourney') }}
            </button>
          </div>
          <div
            v-if="selectedAppointment !== null"
            class="flex items-center justify-between gap-3"
          >
            <div class="min-w-0">
              <dt class="text-caption text-ink-3">{{ t('settlement.create.appointments') }}</dt>
              <dd class="truncate text-body">{{ selectedAppointment.gatheringName }}</dd>
            </div>
            <button
              type="button"
              data-action="change-appointment"
              class="min-h-11 shrink-0 text-body-sm text-ink-2 underline underline-offset-4"
              @click="resetAppointment"
            >
              {{ t('settlement.create.changeAppointment') }}
            </button>
          </div>
        </dl>

        <template v-if="selectedAppointment === null">
          <h2 class="mt-8 text-section-header">{{ t('settlement.create.appointments') }}</h2>
          <p class="mt-2 text-body-sm text-ink-2">
            {{ t('settlement.create.selectAppointment') }}
          </p>
          <ul class="mt-5 space-y-3">
            <li
              v-for="appointment in appointments"
              :key="appointment.appointmentId"
            >
              <button
                type="button"
                :data-appointment-id="appointment.appointmentId"
                class="flex min-h-14 w-full items-center justify-between gap-3 rounded-sm bg-surface-1 p-4 text-left"
                @click="selectAppointment(appointment.appointmentId)"
              >
                <span class="min-w-0 truncate">{{ appointment.gatheringName }}</span>
                <span class="shrink-0 text-caption text-ink-3">{{
                  t(
                    'settlement.create.paymentCount',
                    { count: appointment.candidates.length },
                    appointment.candidates.length,
                  )
                }}</span>
              </button>
            </li>
          </ul>
        </template>

        <template v-else>
          <h2 class="mt-8 text-section-header">{{ t('settlement.create.transactions') }}</h2>
          <p class="mt-2 text-body-sm text-ink-2">
            {{ t('settlement.create.selectTransaction') }}
          </p>
          <ul class="mt-5 space-y-3">
            <li
              v-for="candidate in transactions"
              :key="candidate.transferId"
            >
              <button
                type="button"
                :data-payment-id="candidate.transferId"
                class="w-full rounded-sm bg-surface-1 p-4 text-left"
                :class="
                  selectedCandidate?.transferId === candidate.transferId
                    ? 'border border-ink'
                    : 'border border-transparent'
                "
                :aria-pressed="selectedCandidate?.transferId === candidate.transferId"
                @click="selectTransaction(candidate)"
              >
                <span class="block text-title">{{ points(candidate.amount) }}</span>
                <span class="mt-1 block text-body-sm text-ink-2">{{ candidate.payerName }}</span>
                <span class="mt-1 block text-caption text-ink-3">{{ candidate.paidAt }}</span>
              </button>
            </li>
          </ul>
        </template>
      </template>

      <AppButton
        data-action="next"
        class="mt-auto"
        block
        variant="settle"
        :disabled="selectedCandidate === null"
        @click="goToDetails"
        >{{ t('settlement.continue') }}</AppButton
      >
    </div>

    <div
      v-else-if="step === 2"
      class="flex flex-1 flex-col"
    >
      <h2 class="mt-8 text-section-header">{{ t('settlement.create.method') }}</h2>
      <div
        class="mt-5 grid grid-cols-2 gap-2"
        role="radiogroup"
        :aria-label="t('settlement.create.method')"
      >
        <button
          v-for="option in ['EQUAL', 'ITEMIZED'] as SettlementType[]"
          :key="option"
          type="button"
          role="radio"
          :data-type="option"
          :aria-checked="type === option"
          class="min-h-11 rounded-pill px-3 text-body-sm"
          :class="type === option ? 'bg-settlement text-on-paper' : 'bg-surface-1 text-ink-2'"
          @click="setType(option)"
        >
          {{ t(`settlement.type.${option}`) }}
        </button>
      </div>

      <SettlementTransactionCard
        v-if="selectedCandidate !== null"
        class="mt-6"
        :gathering-name="selectedCandidate.gatheringName"
        :amount="selectedCandidate.amount"
        :paid-at="selectedCandidate.paidAt"
        :payer-name="selectedCandidate.payerName"
        :receipt-url="receipt.previewUrl.value"
        :receipt-pending="receipt.pending.value"
        @receipt-select="selectReceipt"
      />
      <p
        v-if="receipt.errorKey.value !== null"
        class="mt-2 text-caption text-danger"
        role="alert"
      >
        {{ t(receipt.errorKey.value) }}
      </p>
      <p
        v-else-if="selectedCandidate !== null"
        class="mt-2 text-caption text-ink-3"
      >
        {{ t('settlement.receipt.hint') }}
      </p>

      <h3 class="mt-8 text-title">{{ t('settlement.create.participants') }}</h3>
      <p class="mt-2 text-body-sm text-ink-2">{{ t('settlement.create.participantsHint') }}</p>
      <p class="mt-1 text-caption text-ink-3">{{ t('settlement.create.payerRequired') }}</p>
      <div class="mt-3 grid grid-cols-2 gap-2">
        <button
          v-for="participant in selectedCandidate?.participants"
          :key="participant.id"
          type="button"
          :data-participant-id="participant.id"
          :aria-pressed="selectedIds.has(participant.id)"
          :aria-disabled="isPayer(participant.id) || undefined"
          class="min-h-12 rounded-sm border px-3 text-left"
          :class="[
            isPayer(participant.id)
              ? 'cursor-default border-settlement bg-settlement text-on-paper'
              : selectedIds.has(participant.id)
                ? 'border-settlement bg-settlement/10 text-settlement'
                : 'border-hairline-strong text-ink-2',
          ]"
          @click="toggleParticipant(participant.id)"
        >
          {{ participant.name
          }}<span
            v-if="isPayer(participant.id)"
            class="ml-1 text-caption"
            >{{ t('settlement.create.payer') }}</span
          >
        </button>
      </div>

      <template v-if="type === 'ITEMIZED'">
        <div class="mt-8 flex items-center justify-between gap-3">
          <h3 class="text-title">{{ t('settlement.create.items') }}</h3>
          <div class="flex items-center gap-2">
            <AppButton
              v-if="receipt.receiptId.value !== null"
              data-action="load-items"
              dense
              variant="secondary"
              :disabled="!canLoadItems"
              :loading="ocr.pending.value"
              @click="loadItems"
              >{{ t('settlement.create.loadItems') }}</AppButton
            >
            <AppButton
              data-action="add-item"
              dense
              variant="secondary"
              @click="addItem"
              >{{ t('settlement.create.addItem') }}</AppButton
            >
          </div>
        </div>
        <p class="mt-2 text-body-sm text-ink-2">{{ t('settlement.create.itemsHint') }}</p>
        <p
          v-if="ocr.errorKey.value !== null"
          data-testid="ocr-error"
          class="mt-2 text-caption text-danger"
          role="alert"
        >
          {{ t(ocr.errorKey.value) }}
        </p>
        <p
          v-else-if="showAllocateHint"
          data-testid="allocate-hint"
          role="status"
          class="mt-2 text-caption text-ink-3"
        >
          {{ t('settlement.create.allocateAfterLoad') }}
        </p>
        <!--
          영수증에 찍힌 합계는 보여주지 않는다.

          사진을 반듯하게 찍지 않으면 합계부터 틀리게 읽힌다. 사용자가 손댈 수 없는 그 숫자를
          결제 금액과 나란히 놓으면 멀쩡한 영수증을 두고 뭔가 잘못됐다는 인상만 남는다.
          인식이 하는 일은 아래 품목 카드를 대신 채워 주는 것 하나다.

          아래 "품목 합계"는 성격이 다르다. 사용자가 카드에 확정한 값이고, 서버가 결제 금액과
          정확히 같을 때만 정산을 만들기 때문에 여기서 알려주지 않으면 제출에서야 거절당한다.
        -->
        <div
          v-if="itemsTotal !== null"
          data-testid="items-total"
          class="mt-3 flex items-baseline justify-between gap-3 text-body-sm"
          :class="itemsTotal.matches ? 'text-ink-2' : 'text-danger'"
        >
          <span>{{ t('settlement.create.itemsTotal') }}</span>
          <span
            >{{ points(itemsTotal.total, amountFractionDigits) }} /
            {{ points(selectedCandidate?.amount ?? '0') }}</span
          >
        </div>
        <p
          v-if="itemsTotal !== null && !itemsTotal.matches"
          data-testid="items-gap"
          class="mt-2 text-caption text-danger"
        >
          {{
            t(
              itemsTotal.exceedsPayment
                ? 'settlement.create.itemsOverPayment'
                : 'settlement.create.itemsUnderPayment',
              { amount: points(itemsTotal.difference, amountFractionDigits) },
            )
          }}
        </p>
        <div
          v-for="(item, index) in items"
          :key="index"
          :data-item-invalid="invalidItemIndexes.has(index) ? 'true' : undefined"
          class="mt-4 rounded-sm bg-surface-1 p-4"
          :class="{ 'ring-1 ring-danger': invalidItemIndexes.has(index) }"
        >
          <div class="mb-3 flex items-center justify-between gap-3">
            <p class="text-caption text-ink-3">
              {{ t('settlement.create.itemNumber', { number: index + 1 }) }}
            </p>
            <button
              v-if="items.length > 1"
              type="button"
              :data-remove-item="index"
              class="-my-2 -mr-2 grid size-11 shrink-0 place-items-center rounded-sm text-ink-3"
              :aria-label="t('settlement.create.removeItem', { number: index + 1 })"
              @click="removeItem(index)"
            >
              <IconX
                :size="18"
                :stroke-width="1.8"
                aria-hidden="true"
              />
            </button>
          </div>
          <p
            v-if="invalidItemIndexes.has(index)"
            :id="`item-error-${index}`"
            class="mb-3 text-caption text-danger"
            role="alert"
          >
            {{ t('settlement.create.itemInvalid') }}
          </p>
          <label
            class="block text-caption text-ink-2"
            :for="`item-name-${index}`"
            >{{ t('settlement.create.itemName') }}</label
          >
          <input
            :id="`item-name-${index}`"
            :data-item-name="index"
            :aria-invalid="invalidItemIndexes.has(index) ? 'true' : undefined"
            :aria-describedby="invalidItemIndexes.has(index) ? `item-error-${index}` : undefined"
            :value="item.name"
            class="mt-1 min-h-11 w-full rounded-sm bg-surface-2 px-3 text-body-sm"
            @input="updateItem(index, 'name', ($event.target as HTMLInputElement).value)"
          />
          <div class="mt-3 grid grid-cols-2 gap-2">
            <label class="text-caption text-ink-2"
              >{{ t('settlement.create.unitPrice')
              }}<input
                :data-item-unit-price="index"
                :value="item.unitPrice"
                inputmode="decimal"
                class="mt-1 min-h-11 w-full rounded-sm bg-surface-2 px-3 text-body-sm"
                @input="
                  updateItem(index, 'unitPrice', ($event.target as HTMLInputElement).value)
                " /></label
            ><label class="text-caption text-ink-2"
              >{{ t('settlement.create.quantity')
              }}<input
                :data-item-quantity="index"
                :value="item.quantity"
                inputmode="decimal"
                class="mt-1 min-h-11 w-full rounded-sm bg-surface-2 px-3 text-body-sm"
                @input="updateItem(index, 'quantity', ($event.target as HTMLInputElement).value)"
            /></label>
          </div>
          <p class="mt-4 text-caption text-ink-3">{{ t('settlement.create.allocations') }}</p>
          <label
            v-for="participant in chosenParticipants"
            :key="participant.id"
            class="mt-2 flex min-h-11 items-center justify-between text-body-sm"
            ><span>{{ participant.name }}</span
            ><input
              :data-allocation-quantity="`${index}:${participant.id}`"
              :value="allocationValue(index, participant.id)"
              inputmode="decimal"
              class="w-24 rounded-sm bg-surface-2 px-3 py-2 text-right"
              @input="
                updateAllocation(index, participant.id, ($event.target as HTMLInputElement).value)
              "
          /></label>
        </div>
      </template>

      <p
        v-if="validationMessage !== null"
        class="mt-4 text-body-sm text-danger"
        role="alert"
      >
        {{ validationMessage }}
      </p>
      <div class="mt-auto pt-8">
        <AppButton
          data-action="next"
          block
          variant="settle"
          @click="goToReview"
          >{{ t('settlement.continue') }}</AppButton
        >
      </div>
    </div>

    <div
      v-else
      class="flex flex-1 flex-col"
    >
      <h2 class="mt-8 text-section-header">{{ t('settlement.create.overview') }}</h2>
      <p class="mt-2 text-body-sm text-ink-2">{{ t('settlement.create.overviewHint') }}</p>

      <SettlementTransactionCard
        v-if="selectedCandidate !== null"
        class="mt-5"
        :gathering-name="selectedCandidate.gatheringName"
        :amount="selectedCandidate.amount"
        :paid-at="selectedCandidate.paidAt"
        :payer-name="selectedCandidate.payerName"
        :receipt-mode="receipt.previewUrl.value === null ? 'empty' : 'view'"
        :receipt-url="receipt.previewUrl.value"
        @receipt-open="receiptSheetOpen = true"
      />

      <AppCard class="mt-4">
        <dl class="space-y-3 text-body-sm">
          <div class="flex justify-between gap-3">
            <dt class="text-ink-3">{{ t('settlement.create.method') }}</dt>
            <dd>{{ t(`settlement.type.${type}`) }}</dd>
          </div>
          <div
            v-if="itemizedShares === null"
            class="flex justify-between gap-3"
          >
            <dt class="text-ink-3">{{ t('settlement.create.breakdown') }}</dt>
            <dd class="text-right">
              {{ chosenParticipants.map((participant) => participant.name).join(', ') }}
            </dd>
          </div>
          <div class="flex justify-between gap-3">
            <dt class="text-ink-3">{{ t('settlement.total') }}</dt>
            <dd>{{ points(selectedCandidate?.amount ?? '0') }}</dd>
          </div>
        </dl>
      </AppCard>

      <template v-if="itemizedShares !== null">
        <h3 class="mt-6 text-title">{{ t('settlement.create.sharesTitle') }}</h3>
        <AppCard class="mt-3">
          <dl class="space-y-3 text-body-sm">
            <div
              v-for="participant in chosenParticipants"
              :key="participant.id"
              :data-share-for="participant.id"
              class="flex justify-between gap-3"
            >
              <dt class="text-ink-3">
                {{ participant.name
                }}<span
                  v-if="participant.id === selectedCandidate?.payerAppointmentMemberId"
                  class="ml-1 text-caption"
                  >{{ t('settlement.create.payerShare') }}</span
                >
              </dt>
              <dd>{{ points(shareAmountOf(participant.id), amountFractionDigits) }}</dd>
            </div>
          </dl>
          <div
            class="mt-4 flex justify-between gap-3 border-t border-hairline pt-3 text-title-sm"
            data-testid="request-total"
          >
            <span>{{ t('settlement.create.requestTotal') }}</span>
            <span>{{ points(itemizedShares.requested, amountFractionDigits) }}</span>
          </div>
        </AppCard>
      </template>

      <p
        v-else-if="type === 'EQUAL'"
        class="mt-4 text-body-sm text-ink-2"
      >
        {{ t('settlement.create.evenSplitNote', { count: chosenParticipants.length }) }}
      </p>

      <ul
        v-if="type === 'ITEMIZED'"
        class="mt-4 space-y-2"
      >
        <li
          v-for="(item, index) in items"
          :key="index"
          class="rounded-sm bg-surface-1 p-3 text-body-sm"
        >
          <span class="block">{{ item.name }}</span>
          <span class="mt-1 block text-caption text-ink-3">
            {{ item.quantity }} × {{ points(item.unitPrice) }}
          </span>
        </li>
      </ul>

      <p
        v-if="error !== null"
        class="mt-4 text-body-sm text-danger"
        role="alert"
      >
        {{ t(resolveSettlementError(error).messageKey) }}
      </p>
      <div class="mt-auto pt-8">
        <AppButton
          data-action="create"
          block
          variant="settle"
          @click="create"
          >{{
            itemizedShares === null
              ? t('settlement.create.send')
              : t('settlement.create.request', {
                  amount: points(itemizedShares.requested, amountFractionDigits),
                })
          }}</AppButton
        >
      </div>
    </div>

    <SettlementBottomSheet
      v-if="overwriteSheetOpen"
      :label="t('settlement.create.overwriteItemsTitle')"
      @close="overwriteSheetOpen = false"
    >
      <h2 class="font-display text-section-header text-ink-display uppercase">
        {{ t('settlement.create.overwriteItemsTitle') }}
      </h2>
      <p class="mt-2 text-body-sm text-ink-2">
        {{ t('settlement.create.overwriteItemsDescription') }}
      </p>

      <div class="mt-6 flex flex-col gap-2">
        <AppButton
          data-action="overwrite-items-confirm"
          block
          variant="settle"
          @click="confirmOverwrite"
          >{{ t('settlement.create.overwriteItemsConfirm') }}</AppButton
        >
        <AppButton
          data-action="overwrite-items-cancel"
          block
          variant="secondary"
          @click="overwriteSheetOpen = false"
          >{{ t('settlement.create.overwriteItemsCancel') }}</AppButton
        >
      </div>
    </SettlementBottomSheet>

    <SettlementReceiptSheet
      v-if="receiptSheetOpen && receipt.previewUrl.value !== null"
      :url="receipt.previewUrl.value"
      @close="receiptSheetOpen = false"
    />
  </div>
</template>
