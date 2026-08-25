<script setup lang="ts">
import { computed, ref } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { IconCoin, IconSettings, IconUserPlus } from '@tabler/icons-vue'
import { useI18n } from 'vue-i18n'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import { NormalizedApiError } from '@/shared/api/apiError'
import { spendingCategoryLabelKey, toSpendingCategory } from '@/shared/lib/spendingCategory'
import SegmentedControl from '@/shared/ui/SegmentedControl.vue'
import StateEmpty from '@/shared/ui/StateEmpty.vue'
import StateError from '@/shared/ui/StateError.vue'
import StateLoading from '@/shared/ui/StateLoading.vue'

import JourneySummary from '../components/JourneySummary.vue'
import JourneyDialog from '../components/JourneyDialog.vue'
import JourneyTimelineList from '../components/JourneyTimelineList.vue'
import { deleteJourneyItem, type JourneyTimelineItem } from '../api/journeyApi'
import { journeyErrorMessageKey, isJourneyForbidden } from '../model/journeyErrors'
import { journeyKeys as journeyListKeys } from '../model/journeyKeys'
import { getJourneyStatus } from '../model/journeyStatus'
import {
  journeyDetailQueryOptions,
  journeyKeys,
  journeyTimelineQueryOptions,
} from '../model/journeyQueries'
import { useJourneyReportIntegration } from '../model/reportIntegration'

const i18n = useI18n()
const { locale, t } = i18n
const route = useRoute()
const router = useRouter()
const queryClient = useQueryClient()
const hasMessage = (key: string): boolean => i18n.te(key)

const tripId = computed(() => {
  const rawTripId = Array.isArray(route.params.tripId)
    ? route.params.tripId[0]
    : route.params.tripId
  const parsedTripId = Number(rawTripId)

  return Number.isSafeInteger(parsedTripId) && parsedTripId > 0 ? parsedTripId : null
})

const detailQuery = useQuery({
  ...journeyDetailQueryOptions(tripId),
  enabled: computed(() => tripId.value !== null),
  retry: false,
})

const timelineQuery = useQuery({
  ...journeyTimelineQueryOptions(tripId, locale),
  enabled: computed(() => tripId.value !== null),
  retry: false,
})

const requestError = computed(() => detailQuery.error.value ?? timelineQuery.error.value)
const forbidden = computed(() => isJourneyForbidden(requestError.value))
const requestErrorDescription = computed(() =>
  t(journeyErrorMessageKey(requestError.value, hasMessage)),
)
const selectedItem = ref<JourneyTimelineItem | null>(null)
const activeSection = ref<'itinerary' | 'spending'>('itinerary')
const removeDialog = ref<'confirm' | 'blocked' | 'unavailable' | null>(null)
const removeError = ref<string | null>(null)

const removeMutation = useMutation({
  mutationFn: async () => {
    if (tripId.value === null || selectedItem.value === null) {
      throw new Error('A valid journey item is required.')
    }
    await deleteJourneyItem(tripId.value, selectedItem.value.tripItemId)
  },
  onSuccess: async () => {
    const id = tripId.value
    removeDialog.value = null
    selectedItem.value = null
    if (id === null) return
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: journeyListKeys.list() }),
      queryClient.invalidateQueries({ queryKey: journeyKeys.detail(id) }),
      queryClient.invalidateQueries({ queryKey: journeyKeys.timeline(id, locale.value) }),
    ])
  },
  onError: (error) => {
    if (error instanceof NormalizedApiError && error.code === 'JOURNEY-011') {
      removeDialog.value = 'blocked'
      return
    }
    if (
      error instanceof NormalizedApiError &&
      (error.code === 'JOURNEY-010' || error.code === 'APPOINTMENT-007')
    ) {
      removeDialog.value = 'unavailable'
      return
    }
    removeError.value = t(journeyErrorMessageKey(error, hasMessage))
  },
})

const { useReportSummariesQuery, useReportExpenseCandidatesQuery } = useJourneyReportIntegration()
const reportsQuery = useReportSummariesQuery()
const expenseCandidatesQuery = useReportExpenseCandidatesQuery(tripId)
const expenseCandidates = computed(() => expenseCandidatesQuery.data.value?.candidates ?? [])
const itemCount = computed(() =>
  (timelineQuery.data.value?.timeline ?? []).reduce((sum, day) => sum + day.items.length, 0),
)
const sectionOptions = computed(() => [
  { value: 'itinerary', label: t('journey.detail.timeline') },
  { value: 'spending', label: t('journey.detail.spending') },
])
const spendingRows = computed(() => {
  const totals = new Map<string, number>()
  for (const candidate of expenseCandidates.value) {
    const category = toSpendingCategory(candidate.category)
    const amount = Number(candidate.amount)
    if (!Number.isFinite(amount) || amount < 0) continue
    totals.set(category, (totals.get(category) ?? 0) + amount)
  }

  return [...totals.entries()].sort((first, second) => second[1] - first[1])
})
const journeyEnded = computed(() => {
  const journey = detailQuery.data.value
  return journey !== undefined && getJourneyStatus(journey.endDate) === 'past'
})
const matchingReport = computed(() => {
  const journey = detailQuery.data.value
  if (journey === undefined) {
    return null
  }

  return reportsQuery.data.value?.find((report) => report.tripId === journey.tripId) ?? null
})

function retryAll(): void {
  void detailQuery.refetch()
  void timelineQuery.refetch()
}

function viewReport(reportId: number): void {
  void router.push({ name: 'report-detail', params: { reportId } })
}

function createReport(): void {
  const journey = detailQuery.data.value
  if (journey === undefined) {
    return
  }

  void router.push({ name: 'report-list', query: { tripId: journey.tripId } })
}

function setActiveSection(value: string): void {
  if (value === 'itinerary' || value === 'spending') activeSection.value = value
}

// 지갑 통화(KRW)와 1:1이라 통화 스타일 대신 자릿수 구분만 로케일 대응으로 하고
// 단위는 P로 직접 붙인다(#333).
function formatExpenseAmount(value: number): string {
  return `${new Intl.NumberFormat(i18n.locale.value, { maximumFractionDigits: 0 }).format(value)} P`
}

function requestRemove(item: JourneyTimelineItem): void {
  selectedItem.value = item
  removeError.value = null
  removeDialog.value = 'confirm'
}

function closeRemoveDialog(): void {
  if (!removeMutation.isPending.value) {
    removeDialog.value = null
    selectedItem.value = null
  }
}
</script>

<template>
  <main class="flex w-full flex-1 flex-col gap-6 px-screen pt-6 pb-8">
    <section
      v-if="tripId === null"
      role="alert"
    >
      <StateEmpty
        :title="t('journey.detail.invalidTitle')"
        :description="t('journey.detail.invalidDescription')"
      />
    </section>

    <StateLoading
      v-else-if="detailQuery.isPending.value"
      :label="t('state.loading')"
    />

    <section
      v-else-if="forbidden"
      role="alert"
    >
      <StateEmpty
        :title="t('journey.detail.accessDeniedTitle')"
        :description="t('journey.detail.accessDeniedDescription')"
      />
    </section>

    <StateError
      v-else-if="detailQuery.isError.value"
      :title="t('journey.detail.loadFailed')"
      :description="requestErrorDescription"
      :action-label="t('journey.detail.retry')"
      @retry="retryAll"
    />

    <template v-else-if="detailQuery.data.value !== undefined">
      <JourneySummary
        :journey="detailQuery.data.value"
        :expenses="expenseCandidates"
        :item-count="itemCount"
      >
        <RouterLink
          :to="{
            name: 'journey-settings',
            params: { tripId: detailQuery.data.value.tripId },
          }"
          :aria-label="t('journey.settings.open')"
          class="flex size-11 shrink-0 items-center justify-center rounded-pill bg-surface-1 text-ink"
        >
          <IconSettings
            :size="20"
            :stroke-width="1.75"
            aria-hidden="true"
          />
        </RouterLink>
      </JourneySummary>

      <div class="flex h-11.5 gap-2.5">
        <button
          v-if="journeyEnded"
          type="button"
          :disabled="reportsQuery.isPending.value || reportsQuery.isError.value"
          class="flex min-w-0 flex-1 items-center justify-center gap-2 rounded-sm border border-paper text-input font-medium text-ink disabled:opacity-50"
          @click="matchingReport === null ? createReport() : viewReport(matchingReport.reportId)"
        >
          <IconUserPlus
            :size="24"
            aria-hidden="true"
          />
          <span class="truncate">
            {{
              reportsQuery.isPending.value
                ? t('journey.detail.reportChecking')
                : reportsQuery.isError.value
                  ? t('journey.detail.reportLoadFailed')
                  : matchingReport === null
                    ? t('journey.detail.createReport')
                    : t('journey.detail.viewReportShort')
            }}
          </span>
        </button>

        <RouterLink
          :to="{ name: 'settlement-new' }"
          class="flex min-w-0 flex-1 items-center justify-center gap-2 rounded-sm bg-paper-fill text-input font-medium text-on-paper"
        >
          <IconCoin
            :size="24"
            aria-hidden="true"
          />
          <span class="truncate">{{ t('journey.detail.splitExpenses') }}</span>
        </RouterLink>
      </div>

      <StateError
        v-if="journeyEnded && reportsQuery.isError.value"
        :title="t('journey.detail.reportLoadFailed')"
        :description="t('journey.detail.reportLoadFailedDescription')"
        :action-label="t('action.retry')"
        @retry="reportsQuery.refetch"
      />

      <SegmentedControl
        :model-value="activeSection"
        :options="sectionOptions"
        :label="t('journey.detail.sectionsLabel')"
        @update:model-value="setActiveSection"
      />

      <section
        v-if="activeSection === 'itinerary'"
        :aria-label="t('journey.detail.timeline')"
      >
        <StateLoading
          v-if="timelineQuery.isPending.value"
          :label="t('journey.detail.timelineLoading')"
        />

        <section
          v-else-if="isJourneyForbidden(timelineQuery.error.value)"
          role="alert"
        >
          <StateEmpty
            :title="t('journey.detail.accessDeniedTitle')"
            :description="t('journey.detail.accessDeniedDescription')"
          />
        </section>

        <StateError
          v-else-if="timelineQuery.isError.value"
          :title="t('journey.detail.loadFailed')"
          :description="requestErrorDescription"
          :action-label="t('journey.detail.retry')"
          @retry="retryAll"
        />

        <JourneyTimelineList
          v-else-if="timelineQuery.data.value !== undefined"
          :days="timelineQuery.data.value.timeline"
          :trip-id="detailQuery.data.value.tripId"
          :start-date="detailQuery.data.value.startDate"
          :end-date="detailQuery.data.value.endDate"
          :removing-trip-item-id="removeMutation.isPending.value ? selectedItem?.tripItemId : null"
          @remove="requestRemove"
        />
      </section>

      <section
        v-else
        class="flex flex-col gap-2"
        :aria-label="t('journey.detail.spending')"
      >
        <StateLoading
          v-if="expenseCandidatesQuery.isPending.value"
          :label="t('journey.detail.spendingLoading')"
        />
        <StateError
          v-else-if="expenseCandidatesQuery.isError.value"
          :title="t('journey.detail.spendingLoadFailed')"
          :description="t('journey.detail.spendingLoadFailedDescription')"
          :action-label="t('action.retry')"
          @retry="expenseCandidatesQuery.refetch"
        />
        <StateEmpty
          v-else-if="spendingRows.length === 0"
          :title="t('journey.detail.spendingEmpty')"
        />
        <template v-else>
          <div
            v-for="row in spendingRows"
            :key="row[0]"
            class="flex items-center justify-between rounded-md bg-surface-1 px-4 py-3"
          >
            <span class="text-body-sm text-ink-2">{{ t(spendingCategoryLabelKey(row[0])) }}</span>
            <strong class="text-title-sm tabular-nums text-ink">{{
              formatExpenseAmount(row[1])
            }}</strong>
          </div>
        </template>
      </section>
    </template>

    <JourneyDialog
      v-if="removeDialog === 'confirm' && selectedItem !== null"
      id="remove-journey-item-dialog"
      :title="t('journey.remove.title')"
      :description="
        selectedItem.status === 'CONFIRMED'
          ? t('journey.remove.confirmedDescription', {
              title: selectedItem.exploreItem.title,
            })
          : t('journey.remove.description', { title: selectedItem.exploreItem.title })
      "
      :confirm-label="t('journey.remove.delete')"
      :cancel-label="t('journey.remove.cancel')"
      :pending="removeMutation.isPending.value"
      destructive
      @confirm="removeMutation.mutate()"
      @cancel="closeRemoveDialog"
    >
      <p
        v-if="removeError !== null"
        class="text-body-sm text-danger"
        role="alert"
      >
        {{ removeError }}
      </p>
    </JourneyDialog>

    <JourneyDialog
      v-else-if="removeDialog === 'blocked'"
      id="remove-journey-item-blocked-dialog"
      :title="t('journey.remove.blockedTitle')"
      :description="t('journey.remove.blockedDescription')"
      :confirm-label="t('action.close')"
      single-action
      @confirm="closeRemoveDialog"
      @cancel="closeRemoveDialog"
    />

    <JourneyDialog
      v-else-if="removeDialog === 'unavailable'"
      id="remove-journey-item-unavailable-dialog"
      :title="t('journey.remove.unavailableTitle')"
      :description="t('journey.remove.unavailableDescription')"
      :confirm-label="t('action.close')"
      single-action
      @confirm="
        () => {
          closeRemoveDialog()
          timelineQuery.refetch()
        }
      "
      @cancel="closeRemoveDialog"
    />
  </main>
</template>
