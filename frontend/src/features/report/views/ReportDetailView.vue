<script setup lang="ts">
import { IconArrowLeft } from '@tabler/icons-vue'
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'

import AppCard from '@/shared/ui/AppCard.vue'
import IconOrb from '@/shared/ui/IconOrb.vue'
import StateError from '@/shared/ui/StateError.vue'
import StateLoading from '@/shared/ui/StateLoading.vue'

import ReportCategoryBreakdown from '../components/presentation/ReportCategoryBreakdown.vue'
import ReportDailyTrend from '../components/presentation/ReportDailyTrend.vue'
import ReportKpiCard from '../components/presentation/ReportKpiCard.vue'
import type {
  ReportCategoryBreakdownItem,
  ReportDailyTrendPoint,
  ReportKpiData,
} from '../components/presentation/types'
import { useReportDetailQuery } from '../composables/useReportQueries'
import { isReportForbidden, isReportNotFound, reportErrorMessageKey } from '../model/reportErrors'
import { formatReportDate, isZeroAmount, parsePositiveRouteId } from '../model/reportModel'

const route = useRoute()
const router = useRouter()
const i18n = useI18n()
const { t } = i18n
const hasMessage = (key: string): boolean => i18n.te(key)

const reportId = computed(() => parsePositiveRouteId(route.params.reportId))
const reportQuery = useReportDetailQuery(reportId)
const report = computed(() => reportQuery.data.value ?? null)
const isForbidden = computed(() => isReportForbidden(reportQuery.error.value))
const isNotFound = computed(() => isReportNotFound(reportQuery.error.value))
const errorDescription = computed(() =>
  t(reportErrorMessageKey(reportQuery.error.value, hasMessage)),
)
const isZeroSpending = computed(
  () => report.value?.analytics !== null && isZeroAmount(report.value?.analytics.totalSpent ?? '0'),
)
const reportKpi = computed<ReportKpiData | null>(() => {
  const analytics = report.value?.analytics

  if (analytics === null || analytics === undefined) {
    return null
  }

  return {
    totalSpent: Number(analytics.totalSpent),
    dailyAverage: Number(analytics.dailyAverage),
    currency: 'KRW',
  }
})
const reportCategories = computed<ReportCategoryBreakdownItem[]>(() =>
  (report.value?.analytics?.categoryBreakdown ?? []).map((row) => ({
    category: row.category,
    label: row.category,
    amount: Number(row.amount),
    percentage: Number(row.percentage),
  })),
)
const reportTrend = computed<ReportDailyTrendPoint[]>(() =>
  (report.value?.analytics?.dailyTrend ?? []).map((row) => ({
    date: row.date,
    label: formatReportDate(row.date),
    amount: Number(row.amount),
  })),
)

function goBack(): void {
  void router.push({ name: 'report-list' })
}

function retry(): void {
  void reportQuery.refetch()
}
</script>

<template>
  <main class="flex w-full flex-col gap-6 px-screen py-8">
    <header class="flex items-center gap-3">
      <IconOrb
        :label="t('report.detail.back')"
        size="md"
        variant="plain"
        class="-ml-2.5"
        @click="goBack"
      >
        <IconArrowLeft
          :size="24"
          aria-hidden="true"
        />
      </IconOrb>
      <h1 class="font-display text-screen-title font-bold text-ink-display">
        {{ t('report.detail.title') }}
      </h1>
    </header>

    <StateError
      v-if="reportId === null"
      :title="t('report.detail.invalidTitle')"
      :description="t('report.detail.invalidDescription')"
      :action-label="t('report.detail.back')"
      @retry="goBack"
    />

    <StateLoading
      v-else-if="reportQuery.isPending.value"
      :label="t('state.loading')"
    />

    <StateError
      v-else-if="isForbidden"
      :title="t('report.detail.forbiddenTitle')"
      :description="t('report.detail.forbiddenDescription')"
      :action-label="t('report.detail.back')"
      @retry="goBack"
    />

    <StateError
      v-else-if="isNotFound"
      :title="t('report.detail.notFoundTitle')"
      :description="t('report.detail.notFoundDescription')"
      :action-label="t('report.detail.back')"
      @retry="goBack"
    />

    <StateError
      v-else-if="reportQuery.isError.value"
      :title="t('report.detail.loadFailed')"
      :description="errorDescription"
      :action-label="t('action.retry')"
      @retry="retry"
    />

    <template v-else-if="report !== null">
      <section
        v-if="report.analytics === null"
        class="flex flex-col gap-3"
        aria-labelledby="report-analysis-title"
      >
        <h2
          id="report-analysis-title"
          class="text-section-header text-ink"
        >
          {{ t('report.detail.analysis') }}
        </h2>
        <AppCard>
          <h3 class="text-title text-ink">{{ t('report.detail.legacyTitle') }}</h3>
          <p class="mt-2 text-body-sm text-ink-3">
            {{ t('report.detail.legacyDescription') }}
          </p>
        </AppCard>
      </section>

      <template v-else-if="reportKpi !== null">
        <ReportKpiCard
          :heading="t('report.detail.analysis')"
          :data="reportKpi"
          :total-label="t('report.detail.totalSpent')"
          :daily-average-label="t('report.detail.dailyAverage')"
        />

        <AppCard v-if="isZeroSpending">
          <h2 class="text-title text-ink">{{ t('report.detail.zeroTitle') }}</h2>
          <p class="mt-2 text-body-sm text-ink-3">
            {{ t('report.detail.zeroDescription') }}
          </p>
        </AppCard>

        <ReportCategoryBreakdown
          :heading="t('report.detail.categoryTitle')"
          :items="reportCategories"
          currency="KRW"
          :description="t('report.detail.categoryDescription')"
          :empty-title="t('report.detail.categoryTitle')"
          :empty-description="t('report.detail.categoryEmpty')"
        />

        <ReportDailyTrend
          :heading="t('report.detail.trendTitle')"
          :points="reportTrend"
          currency="KRW"
          :description="t('report.detail.trendDescription')"
          :empty-title="t('report.detail.trendTitle')"
          :empty-description="t('report.detail.trendEmpty')"
        />
      </template>

      <section
        class="flex flex-col gap-3"
        aria-labelledby="report-journey-snapshot-title"
      >
        <h2
          id="report-journey-snapshot-title"
          class="font-display text-section-header uppercase text-ink"
        >
          {{ t('report.detail.journeySnapshot') }}
        </h2>
        <AppCard>
          <div
            class="flex flex-col gap-3"
            aria-labelledby="report-journey-title"
          >
            <h3
              id="report-journey-title"
              class="text-title text-ink"
            >
              {{ report.reportContent.journey.title }}
            </h3>
            <p class="text-body-sm text-ink-3">
              {{ formatReportDate(report.reportContent.journey.startDate) }}–{{
                formatReportDate(report.reportContent.journey.endDate)
              }}
            </p>
            <p class="text-body-sm text-ink-3">
              {{ t('report.detail.status', { status: report.generationStatus }) }}
            </p>
          </div>
        </AppCard>
      </section>

      <section
        class="flex flex-col gap-3"
        aria-labelledby="report-itinerary-title"
      >
        <h2
          id="report-itinerary-title"
          class="font-display text-section-header uppercase text-ink"
        >
          {{ t('report.detail.itinerary') }}
        </h2>
        <p
          v-if="report.reportContent.days.length === 0"
          class="text-body-sm text-ink-3"
        >
          {{ t('report.detail.itineraryEmpty') }}
        </p>
        <ol
          v-else
          class="flex flex-col gap-3"
        >
          <li
            v-for="day in report.reportContent.days"
            :key="day.visitDate"
          >
            <AppCard>
              <h3 class="text-title-sm text-ink">{{ formatReportDate(day.visitDate) }}</h3>
              <ul class="mt-2 flex flex-col gap-1 text-body-sm text-ink-3">
                <li
                  v-for="item in day.items"
                  :key="item.tripItemId"
                >
                  {{ item.title }} · {{ item.itemType }} · {{ item.status }}
                </li>
              </ul>
            </AppCard>
          </li>
        </ol>
      </section>
    </template>
  </main>
</template>
