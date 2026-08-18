<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'

import AppButton from '@/shared/ui/AppButton.vue'
import AppCard from '@/shared/ui/AppCard.vue'

import { settlementGateway } from '../api/settlementGateway'
import SettlementStatusScreen from '../components/SettlementStatusScreen.vue'
import SettlementTransactionCard from '../components/SettlementTransactionCard.vue'
import { useSettlementPoints } from '../composables/useSettlementPoints'
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
import { validateItemizedItems } from '../model/settlementRules'

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
const canContinueDetails = computed(
  () => hasEnoughParticipants.value && (type.value === 'EQUAL' || itemValidation.value.valid),
)

watch(submitting, (value) => emit('submittingChange', value))

function resetJourney(): void {
  journeyKey.value = null
  appointmentId.value = null
  selectedCandidate.value = null
}

function resetAppointment(): void {
  appointmentId.value = null
  selectedCandidate.value = null
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
  error.value = null
}

function goToDetails(): void {
  if (selectedCandidate.value === null) return
  step.value = 2
  emit('update:step', step.value)
}

function setType(nextType: SettlementType): void {
  type.value = nextType
  validationMessage.value = null
}

function toggleParticipant(participantId: string): void {
  const candidate = selectedCandidate.value
  if (candidate === null || participantId === candidate.payerAppointmentMemberId) return
  selectedParticipantIds.value = selectedIds.value.has(participantId)
    ? selectedParticipantIds.value.filter((id) => id !== participantId)
    : [...selectedParticipantIds.value, participantId]
  validationMessage.value = null
}

function addItem(): void {
  items.value.push({ name: '', unitPrice: '', quantity: '', allocations: [] })
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
  if (!canContinueDetails.value) {
    validationMessage.value = t('settlement.create.allocationIncomplete')
    return
  }

  validationMessage.value = null
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
      <template v-if="selectedJourney === null">
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
      />

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
          class="min-h-12 rounded-sm px-3 text-left"
          :class="selectedIds.has(participant.id) ? 'bg-surface-1' : 'bg-surface-2 text-ink-2'"
          @click="toggleParticipant(participant.id)"
        >
          {{ participant.name
          }}<span
            v-if="participant.id === selectedCandidate?.payerAppointmentMemberId"
            class="ml-1 text-caption"
            >{{ t('settlement.create.payer') }}</span
          >
        </button>
      </div>

      <template v-if="type === 'ITEMIZED'">
        <div class="mt-8 flex items-center justify-between gap-3">
          <h3 class="text-title">{{ t('settlement.create.items') }}</h3>
          <AppButton
            data-action="add-item"
            dense
            variant="secondary"
            @click="addItem"
            >{{ t('settlement.create.addItem') }}</AppButton
          >
        </div>
        <p class="mt-2 text-body-sm text-ink-2">{{ t('settlement.create.itemsHint') }}</p>
        <div
          v-for="(item, index) in items"
          :key="index"
          class="mt-4 rounded-sm bg-surface-1 p-4"
        >
          <label
            class="block text-caption text-ink-2"
            :for="`item-name-${index}`"
            >{{ t('settlement.create.itemName') }}</label
          >
          <input
            :id="`item-name-${index}`"
            :data-item-name="index"
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
      />

      <AppCard class="mt-4">
        <dl class="space-y-3 text-body-sm">
          <div class="flex justify-between gap-3">
            <dt class="text-ink-3">{{ t('settlement.create.method') }}</dt>
            <dd>{{ t(`settlement.type.${type}`) }}</dd>
          </div>
          <div class="flex justify-between gap-3">
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
            t('settlement.create.request', { amount: points(selectedCandidate?.amount ?? '0') })
          }}</AppButton
        >
      </div>
    </div>
  </div>
</template>
