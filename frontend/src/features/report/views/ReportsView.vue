<script setup lang="ts">
import { useQueryClient } from '@tanstack/vue-query'
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'

import AppButton from '@/shared/ui/AppButton.vue'
import StateEmpty from '@/shared/ui/StateEmpty.vue'
import StateError from '@/shared/ui/StateError.vue'
import StateLoading from '@/shared/ui/StateLoading.vue'
import ScreenHeader from '@/shared/ui/ScreenHeader.vue'

import type { ReportExpenseCandidate } from '../api/reportApi'
import ReportJourneyCard from '../components/ReportJourneyCard.vue'
import {
  useCreateReportMutation,
  useReportExpenseCandidatesQuery,
  useReportJourneysQuery,
  useReportSummariesQuery,
} from '../composables/useReportQueries'
import { isReportConflict, reportErrorMessageKey } from '../model/reportErrors'
import { reportKeys } from '../model/reportKeys'
import {
  buildReportJourneyOptions,
  formatReportAmount,
  formatReportDate,
  getKoreaToday,
  parsePositiveRouteId,
} from '../model/reportModel'

const i18n = useI18n()
const { t } = i18n
const route = useRoute()
const router = useRouter()
const queryClient = useQueryClient()
const hasMessage = (key: string): boolean => i18n.te(key)

const selectedTripId = ref<number | null>(null)
const selectedTransferIds = ref<Set<number>>(new Set())
const initializedCandidateTripId = ref<number | null>(null)
const hasUnresolvedConflict = ref(false)

const journeysQuery = useReportJourneysQuery()
const reportsQuery = useReportSummariesQuery()
const candidatesQuery = useReportExpenseCandidatesQuery(selectedTripId)
const createMutation = useCreateReportMutation()

const options = computed(() =>
  buildReportJourneyOptions(
    journeysQuery.data.value ?? [],
    reportsQuery.data.value ?? [],
    getKoreaToday(),
  ),
)
const selectedOption = computed(
  () => options.value.find((option) => option.tripId === selectedTripId.value) ?? null,
)
const candidates = computed(() => candidatesQuery.data.value?.candidates ?? [])
const selectedIds = computed(() => [...selectedTransferIds.value].sort((a, b) => a - b))
const isListPending = computed(() => journeysQuery.isPending.value || reportsQuery.isPending.value)
const listError = computed(() => journeysQuery.error.value ?? reportsQuery.error.value)
const isListReady = computed(() => !isListPending.value && listError.value === null)
const listErrorDescription = computed(() => t(reportErrorMessageKey(listError.value, hasMessage)))
const candidateErrorDescription = computed(() =>
  t(reportErrorMessageKey(candidatesQuery.error.value, hasMessage)),
)
const createErrorDescription = computed(() =>
  t(reportErrorMessageKey(createMutation.error.value, hasMessage)),
)

watch(
  () => candidatesQuery.data.value,
  (response) => {
    if (
      response === undefined ||
      response.tripId !== selectedTripId.value ||
      initializedCandidateTripId.value === response.tripId
    ) {
      return
    }

    selectedTransferIds.value = new Set(
      response.candidates
        .filter((candidate) => candidate.selected)
        .map(({ transferId }) => transferId),
    )
    initializedCandidateTripId.value = response.tripId
  },
  { immediate: true },
)

function chooseJourney(tripId: number): void {
  selectedTripId.value = tripId
  selectedTransferIds.value = new Set()
  initializedCandidateTripId.value = null
  hasUnresolvedConflict.value = false
  createMutation.reset()
}

let hasAppliedRouteTripId = false

watch(
  isListReady,
  (ready) => {
    if (!ready || hasAppliedRouteTripId) {
      return
    }

    hasAppliedRouteTripId = true
    const tripId = parsePositiveRouteId(route.query.tripId)
    const option = options.value.find((candidate) => candidate.tripId === tripId)

    if (option === undefined) {
      return
    }

    if (option.report !== null) {
      void router.replace({ name: 'report-detail', params: { reportId: option.report.reportId } })
      return
    }

    chooseJourney(option.tripId)
  },
  { immediate: true },
)

function toggleCandidate(candidate: ReportExpenseCandidate, event: Event): void {
  const input = event.target as HTMLInputElement
  const next = new Set(selectedTransferIds.value)

  if (input.checked) {
    next.add(candidate.transferId)
  } else {
    next.delete(candidate.transferId)
  }

  selectedTransferIds.value = next
}

function viewReport(reportId: number): void {
  void router.push({ name: 'report-detail', params: { reportId } })
}

function retryList(): void {
  void Promise.all([journeysQuery.refetch(), reportsQuery.refetch()])
}

function retryCandidates(): void {
  void candidatesQuery.refetch()
}

async function refreshReports(): Promise<void> {
  hasUnresolvedConflict.value = false
  const refreshed = await reportsQuery.refetch()
  const existing = refreshed.data?.find((report) => report.tripId === selectedTripId.value)

  if (existing !== undefined) {
    viewReport(existing.reportId)
  }
}

async function generateReport(): Promise<void> {
  if (selectedTripId.value === null || createMutation.isPending.value) {
    return
  }

  hasUnresolvedConflict.value = false

  try {
    const created = await createMutation.mutateAsync({
      tripId: selectedTripId.value,
      transferIds: selectedIds.value,
    })
    await queryClient.invalidateQueries({ queryKey: reportKeys.list() })
    viewReport(created.reportId)
  } catch (error) {
    if (!isReportConflict(error)) {
      return
    }

    const refreshed = await reportsQuery.refetch()
    const existing = refreshed.data?.find((report) => report.tripId === selectedTripId.value)

    if (existing !== undefined) {
      viewReport(existing.reportId)
      return
    }

    hasUnresolvedConflict.value = true
  }
}
</script>

<template>
  <main class="flex w-full flex-col gap-6 px-screen flex-1 pt-6 pb-8">
    <ScreenHeader
      variant="root"
      :title="t('report.list.title')"
    >
      <template #description>
        {{ t('report.list.description') }}
      </template>
    </ScreenHeader>

    <StateLoading
      v-if="isListPending"
      :label="t('state.loading')"
    />

    <StateError
      v-else-if="journeysQuery.isError.value || reportsQuery.isError.value"
      :title="t('report.list.loadFailed')"
      :description="listErrorDescription"
      :action-label="t('action.retry')"
      @retry="retryList"
    />

    <StateEmpty
      v-else-if="options.length === 0"
      :title="t('report.list.emptyTitle')"
      :description="t('report.list.emptyDescription')"
    />

    <ul
      v-else
      class="flex flex-col gap-3"
    >
      <ReportJourneyCard
        v-for="option in options"
        :key="option.tripId"
        :option="option"
        :selected="selectedTripId === option.tripId"
        :pending="createMutation.isPending.value"
        @view-report="viewReport"
        @choose-journey="chooseJourney"
      />
    </ul>

    <section
      v-if="selectedOption !== null"
      aria-labelledby="report-expense-title"
      class="flex flex-col gap-4 border-t border-hairline pt-6"
    >
      <div>
        <h2
          id="report-expense-title"
          class="text-section-header text-ink"
        >
          {{ t('report.generate.title') }}
        </h2>
        <p class="mt-2 text-body-sm text-ink-3">
          {{ selectedOption.title }} · {{ t('report.generate.description') }}
        </p>
      </div>

      <StateLoading
        v-if="candidatesQuery.isPending.value"
        :label="t('report.generate.loading')"
        :lines="2"
      />

      <StateError
        v-else-if="candidatesQuery.isError.value"
        :title="t('report.generate.loadFailed')"
        :description="candidateErrorDescription"
        :action-label="t('action.retry')"
        @retry="retryCandidates"
      />

      <form
        v-else
        class="flex flex-col gap-4"
        @submit.prevent="generateReport"
      >
        <StateEmpty
          v-if="candidates.length === 0"
          :title="t('report.generate.emptyTitle')"
          :description="t('report.generate.emptyDescription')"
        />

        <fieldset
          v-else
          class="flex flex-col gap-3"
          :disabled="createMutation.isPending.value"
        >
          <legend class="sr-only">{{ t('report.generate.selectionLabel') }}</legend>
          <label
            v-for="candidate in candidates"
            :key="candidate.transferId"
            class="flex min-h-12 cursor-pointer items-start gap-3 rounded-sm border border-hairline p-3"
          >
            <input
              type="checkbox"
              class="mt-1 size-5"
              :checked="selectedTransferIds.has(candidate.transferId)"
              @change="toggleCandidate(candidate, $event)"
            />
            <span class="flex min-w-0 flex-1 flex-col gap-1">
              <span class="flex justify-between gap-3 text-title-sm text-ink">
                <span>{{ candidate.category }}</span>
                <span>{{ formatReportAmount(candidate.amount) }}</span>
              </span>
              <span class="text-body-sm text-ink-3">
                {{ formatReportDate(candidate.occurredDate) }} ·
                {{ candidate.displayMemo ?? t('report.generate.memoUnavailable') }}
              </span>
            </span>
          </label>
        </fieldset>

        <div
          v-if="hasUnresolvedConflict"
          class="rounded-sm border border-hairline p-4"
          role="alert"
        >
          <p class="text-title-sm text-ink">{{ t('report.generate.conflictTitle') }}</p>
          <p class="mt-1 text-body-sm text-ink-3">
            {{ t('report.generate.conflictDescription') }}
          </p>
          <AppButton
            class="mt-3"
            variant="secondary"
            @click="refreshReports"
          >
            {{ t('report.generate.refresh') }}
          </AppButton>
        </div>

        <p
          v-else-if="createMutation.isError.value"
          class="text-body-sm text-ink-3"
          role="alert"
        >
          {{ t('report.generate.failed') }} {{ createErrorDescription }}
        </p>

        <p
          v-if="createMutation.isPending.value"
          class="text-body-sm text-ink-3"
          role="status"
        >
          {{ t('report.generate.pending') }}
        </p>

        <AppButton
          type="submit"
          block
          :loading="createMutation.isPending.value"
        >
          {{
            selectedIds.length === 0
              ? t('report.generate.submitEmpty')
              : t('report.generate.submit')
          }}
        </AppButton>
      </form>
    </section>
  </main>
</template>
