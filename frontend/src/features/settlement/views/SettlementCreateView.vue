<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'

import AppButton from '@/shared/ui/AppButton.vue'
import AppCard from '@/shared/ui/AppCard.vue'

import { settlementGateway } from '../api/settlementGateway'
import type {
  ItemizedSettlementItem,
  SettlementCandidate,
  SettlementType,
} from '../model/settlement'
import { resolveSettlementError } from '../model/settlementErrors'
import {
  clearSettlementCreateIdempotencyKey,
  resolveSettlementCreateIdempotencyKey,
} from '../model/settlementIdempotency'
import { formatSettlementAmount } from '../model/settlementPresentation'
import { validateItemizedItems } from '../model/settlementRules'

const props = defineProps<{ candidates: SettlementCandidate[] }>()
const emit = defineEmits<{
  complete: [settlementId: string]
  cancel: []
  refreshCandidates: []
  'update:step': [step: number]
}>()
const { t } = useI18n()

const step = ref(1)
const selectedCandidate = ref<SettlementCandidate | null>(null)
const type = ref<SettlementType>('EQUAL')
const selectedParticipantIds = ref<string[]>([])
const items = ref<ItemizedSettlementItem[]>([])
const submitting = ref(false)
const error = ref<unknown>(null)
const validationMessage = ref<string | null>(null)

const selectedIds = computed(() => new Set(selectedParticipantIds.value))
const itemValidation = computed(() => validateItemizedItems(items.value, selectedIds.value))
const canContinueDetails = computed(() =>
  type.value === 'EQUAL'
    ? selectedParticipantIds.value.length >= 2
    : selectedParticipantIds.value.length >= 2 && itemValidation.value.valid,
)

function selectCandidate(candidate: SettlementCandidate): void {
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
  validationMessage.value = canContinueDetails.value
    ? null
    : t('settlement.create.allocationIncomplete')
  if (canContinueDetails.value) {
    step.value = 3
    emit('update:step', step.value)
  }
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
    emit('complete', result.id)
  } catch (reason) {
    error.value = reason
    if (resolveSettlementError(reason).recovery === 'REFETCH_CANDIDATES') {
      emit('refreshCandidates')
    }
  } finally {
    submitting.value = false
  }
}

function back(): void {
  if (step.value === 1) emit('cancel')
  else {
    step.value -= 1
    emit('update:step', step.value)
  }
}

defineExpose({ back })
</script>

<template>
  <div class="flex flex-1 flex-col">
    <div v-if="step === 1">
      <h2 class="mt-8 text-section-header">{{ t('settlement.paymentSelection') }}</h2>
      <p class="mt-2 text-body-sm text-ink-2">{{ t('settlement.create.selectionHint') }}</p>
      <div class="mt-5 space-y-3">
        <button
          v-for="candidate in props.candidates"
          :key="candidate.transferId"
          type="button"
          :data-payment-id="candidate.transferId"
          class="w-full rounded-sm bg-surface-1 p-4 text-left"
          :class="
            selectedCandidate?.transferId === candidate.transferId
              ? 'border border-ink'
              : 'border border-transparent'
          "
          @click="selectCandidate(candidate)"
        >
          <strong class="block">{{ candidate.gatheringName }}</strong>
          <span class="mt-1 block text-body-sm text-ink-2"
            >{{ candidate.payerName }} · {{ candidate.merchantName }}</span
          >
          <span class="mt-2 block text-title"
            >{{ formatSettlementAmount(candidate.amount) }} P</span
          >
        </button>
      </div>
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

    <div v-else-if="step === 2">
      <h2 class="mt-8 text-section-header">{{ t('settlement.requestDetails') }}</h2>
      <div class="mt-5 grid grid-cols-2 gap-2">
        <button
          v-for="option in ['EQUAL', 'ITEMIZED'] as SettlementType[]"
          :key="option"
          type="button"
          :data-type="option"
          class="min-h-11 rounded-pill px-3 text-body-sm"
          :class="type === option ? 'bg-settlement text-on-paper' : 'bg-surface-1 text-ink-2'"
          @click="setType(option)"
        >
          {{ t(`settlement.type.${option}`) }}
        </button>
      </div>
      <p class="mt-8 text-caption text-ink-3">{{ t('settlement.participants') }}</p>
      <p class="mt-2 text-body-sm text-ink-2">{{ t('settlement.create.payerRequired') }}</p>
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
        <div class="mt-8 flex items-center justify-between">
          <h3 class="text-title">{{ t('settlement.create.items') }}</h3>
          <AppButton
            data-action="add-item"
            dense
            variant="secondary"
            @click="addItem"
            >{{ t('settlement.create.addItem') }}</AppButton
          >
        </div>
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
            v-for="participant in selectedCandidate?.participants.filter((entry) =>
              selectedIds.has(entry.id),
            )"
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
      <AppButton
        data-action="next"
        class="mt-8"
        block
        variant="settle"
        @click="goToReview"
        >{{ t('settlement.continue') }}</AppButton
      >
    </div>

    <div v-else>
      <h2 class="mt-8 text-section-header">{{ t('settlement.finalReview') }}</h2>
      <AppCard class="mt-5"
        ><p class="text-caption text-ink-3">{{ selectedCandidate?.gatheringName }}</p>
        <p class="mt-2 text-title">
          {{ formatSettlementAmount(selectedCandidate?.amount ?? '0') }} P
        </p>
        <p class="mt-3 text-body-sm text-ink-2">
          {{ t(`settlement.type.${type}`) }} · {{ selectedParticipantIds.length }}
          {{ t('settlement.create.people') }}
        </p></AppCard
      >
      <ul
        v-if="type === 'ITEMIZED'"
        class="mt-4 space-y-2"
      >
        <li
          v-for="item in items"
          :key="item.name"
          class="rounded-sm bg-surface-1 p-3 text-body-sm"
        >
          {{ item.name }} · {{ item.quantity }} × {{ item.unitPrice }}
        </li>
      </ul>
      <p
        v-if="error !== null"
        class="mt-4 text-body-sm text-danger"
        role="alert"
      >
        {{ t(resolveSettlementError(error).messageKey) }}
      </p>
      <AppButton
        data-action="create"
        class="mt-auto"
        block
        variant="settle"
        :loading="submitting"
        @click="create"
        >{{ t('settlement.create.create') }}</AppButton
      >
    </div>
  </div>
</template>
