<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useMutation, useQueryClient } from '@tanstack/vue-query'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'

import { NormalizedApiError } from '@/shared/api/apiError'
import AppButton from '@/shared/ui/AppButton.vue'

import {
  createAppointment,
  type AppointmentCreateRequest,
  type AppointmentItemType,
} from '../api/appointmentApi'
import AppointmentCreateForm from '../components/AppointmentCreateForm.vue'
import AppointmentJourneyDateSheet from '../components/AppointmentJourneyDateSheet.vue'
import AppointmentJourneySelectSheet from '../components/AppointmentJourneySelectSheet.vue'
import { appointmentKeys } from '../model/appointmentKeys'
import { useAppointmentJourneyIntegration } from '../model/journeyIntegration'

const route = useRoute()
const router = useRouter()
const queryClient = useQueryClient()
const i18n = useI18n()
const { t } = i18n

const createMutation = useMutation({
  mutationFn: (request: AppointmentCreateRequest) => createAppointment(request),
  onSuccess: async (appointment) => {
    queryClient.setQueryData(appointmentKeys.detail(appointment.appointmentId), appointment)
    await queryClient.invalidateQueries({ queryKey: appointmentKeys.lists() })
    await router.push({
      name: 'appointment-detail',
      params: { appointmentId: appointment.appointmentId },
    })
  },
})

const errorMessage = computed(() => {
  const error = createMutation.error.value
  if (error === null) return undefined

  if (!(error instanceof NormalizedApiError) || !i18n.te(error.messageKey)) {
    return t('appointment.create.loadFailed')
  }
  return t(error.messageKey)
})

function submit(request: AppointmentCreateRequest): void {
  if (!createMutation.isPending.value) {
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

function goToCreateJourney(): void {
  void router.push({
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

async function confirmDate(date: string): Promise<void> {
  if (dateCheckPending.value) return
  if (selectedTripId.value === null || itemId.value === undefined) {
    dateCheckError.value = t('appointment.journeyDate.checkFailed')
    return
  }

  dateCheckPending.value = true
  dateCheckError.value = undefined

  try {
    const exists = await journeyIntegration.checkJourneyItemExists(
      selectedTripId.value,
      itemId.value,
      date,
    )
    if (exists) {
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

type AppointmentCreateFormExposed = {
  goToPreviousStep: () => boolean
}

const createForm = ref<AppointmentCreateFormExposed | null>(null)

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
  exitConfirmOpen.value = true
}

function cancelExit(): void {
  exitConfirmOpen.value = false
}

function confirmExit(): void {
  exitConfirmOpen.value = false
  if (window.history.length > 1) {
    void router.back()
    return
  }
  void router.push({ name: 'appointment-list' })
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
      <h1 class="min-w-0 flex-1 truncate font-display text-screen-title text-ink-display">
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
      @close="exitConfirmOpen = true"
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
  </main>
</template>
