<script setup lang="ts">
import { useQueryClient } from '@tanstack/vue-query'
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'

import AppButton from '@/shared/ui/AppButton.vue'
import AppCard from '@/shared/ui/AppCard.vue'
import StateEmpty from '@/shared/ui/StateEmpty.vue'
import StateError from '@/shared/ui/StateError.vue'
import StateLoading from '@/shared/ui/StateLoading.vue'

import type { ReportExpenseCandidate } from '../api/reportApi'
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
  formatKrwAmount,
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
  isListPending,
  (pending) => {
    if (pending || hasAppliedRouteTripId) {
      return
    }

    hasAppliedRouteTripId = true
    const tripId = parsePositiveRouteId(route.query.tripId)

    if (tripId !== null && options.value.some((option) => option.tripId === tripId)) {
      chooseJourney(tripId)
    }
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
  <main class="flex w-full flex-col gap-6 px-screen py-8">
    <header class="flex flex-col gap-2">
      <h1 class="font-display text-screen-title font-bold text-ink-display">
        {{ t('report.list.title') }}
      </h1>
      <p class="text-body-sm text-ink-3">
        {{ t('report.list.description') }}
      </p>
    </header>

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
      <li
        v-for="option in options"
        :key="option.tripId"
      >
        <AppCard>
          <article class="flex flex-col gap-4">
            <div class="flex items-start justify-between gap-4">
              <div class="min-w-0">
                <h2 class="truncate text-title text-ink">
                  {{ option.title }}
                </h2>
                <p class="mt-1 text-body-sm text-ink-3">
                  {{ formatReportDate(option.startDate) }}–{{ formatReportDate(option.endDate) }}
                </p>
              </div>
              <span class="text-label text-ink-3">{{ t('report.list.ended') }}</span>
            </div>

            <p class="text-body-sm text-ink-3">
              {{ option.report === null ? t('report.list.notCreated') : t('report.list.ready') }}
            </p>

            <AppButton
              v-if="option.report !== null"
              variant="secondary"
              block
              @click="viewReport(option.report.reportId)"
            >
              {{ t('report.list.view') }}
            </AppButton>
            <AppButton
              v-else
              variant="secondary"
              block
              :disabled="createMutation.isPending.value"
              @click="chooseJourney(option.tripId)"
            >
              {{
                selectedTripId === option.tripId
                  ? t('report.list.choosingExpenses')
                  : t('report.list.chooseExpenses')
              }}
            </AppButton>
          </article>
        </AppCard>
      </li>
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
                <span>{{ formatKrwAmount(candidate.amount) }}</span>
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
