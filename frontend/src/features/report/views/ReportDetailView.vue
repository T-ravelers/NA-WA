<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'

import AppButton from '@/shared/ui/AppButton.vue'
import AppCard from '@/shared/ui/AppCard.vue'
import StateError from '@/shared/ui/StateError.vue'
import StateLoading from '@/shared/ui/StateLoading.vue'

import { useReportDetailQuery } from '../composables/useReportQueries'
import { isReportForbidden, isReportNotFound, reportErrorMessageKey } from '../model/reportErrors'
import {
  formatKrwAmount,
  formatPercentage,
  formatReportDate,
  isZeroAmount,
  parsePositiveRouteId,
} from '../model/reportModel'

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

function goBack(): void {
  void router.push({ name: 'report-list' })
}

function retry(): void {
  void reportQuery.refetch()
}
</script>

<template>
  <main class="flex w-full flex-col gap-6 px-screen py-8">
    <header class="flex flex-col gap-3">
      <AppButton
        variant="tertiary"
        class="self-start"
        @click="goBack"
      >
        {{ t('report.detail.back') }}
      </AppButton>
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
      <AppCard>
        <section
          class="flex flex-col gap-3"
          aria-labelledby="report-journey-title"
        >
          <div>
            <p class="text-label text-ink-3">{{ t('report.detail.journeySnapshot') }}</p>
            <h2
              id="report-journey-title"
              class="mt-1 text-title text-ink"
            >
              {{ report.reportContent.journey.title }}
            </h2>
          </div>
          <p class="text-body-sm text-ink-3">
            {{ formatReportDate(report.reportContent.journey.startDate) }}–{{
              formatReportDate(report.reportContent.journey.endDate)
            }}
          </p>
          <p class="text-body-sm text-ink-3">
            {{ t('report.detail.status', { status: report.generationStatus }) }}
          </p>
        </section>
      </AppCard>

      <section
        class="flex flex-col gap-3"
        aria-labelledby="report-itinerary-title"
      >
        <h2
          id="report-itinerary-title"
          class="text-section-header text-ink"
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

      <section
        class="flex flex-col gap-4"
        aria-labelledby="report-analysis-title"
      >
        <h2
          id="report-analysis-title"
          class="text-section-header text-ink"
        >
          {{ t('report.detail.analysis') }}
        </h2>

        <AppCard v-if="report.analytics === null">
          <h3 class="text-title text-ink">{{ t('report.detail.legacyTitle') }}</h3>
          <p class="mt-2 text-body-sm text-ink-3">
            {{ t('report.detail.legacyDescription') }}
          </p>
        </AppCard>

        <template v-else>
          <AppCard tone="paper">
            <dl class="grid grid-cols-2 gap-4">
              <div>
                <dt class="text-label">{{ t('report.detail.totalSpent') }}</dt>
                <dd class="mt-1 text-title">{{ formatKrwAmount(report.analytics.totalSpent) }}</dd>
              </div>
              <div>
                <dt class="text-label">{{ t('report.detail.dailyAverage') }}</dt>
                <dd class="mt-1 text-title">
                  {{ formatKrwAmount(report.analytics.dailyAverage) }}
                </dd>
              </div>
            </dl>
          </AppCard>

          <AppCard v-if="isZeroSpending">
            <h3 class="text-title text-ink">{{ t('report.detail.zeroTitle') }}</h3>
            <p class="mt-2 text-body-sm text-ink-3">
              {{ t('report.detail.zeroDescription') }}
            </p>
          </AppCard>

          <section
            class="flex flex-col gap-3"
            aria-labelledby="report-category-title"
          >
            <h3
              id="report-category-title"
              class="text-title text-ink"
            >
              {{ t('report.detail.categoryTitle') }}
            </h3>
            <p
              v-if="report.analytics.categoryBreakdown.length === 0"
              class="text-body-sm text-ink-3"
            >
              {{ t('report.detail.categoryEmpty') }}
            </p>
            <div
              v-else
              class="overflow-x-auto"
            >
              <table class="w-full border-collapse text-left text-body-sm">
                <thead>
                  <tr class="border-b border-hairline text-ink-3">
                    <th class="p-2 font-medium">{{ t('report.detail.category') }}</th>
                    <th class="p-2 text-right font-medium">{{ t('report.detail.amount') }}</th>
                    <th class="p-2 text-right font-medium">{{ t('report.detail.share') }}</th>
                  </tr>
                </thead>
                <tbody>
                  <tr
                    v-for="row in report.analytics.categoryBreakdown"
                    :key="row.category"
                    class="border-b border-hairline"
                  >
                    <th
                      scope="row"
                      class="p-2 font-medium text-ink"
                    >
                      {{ row.category }}
                    </th>
                    <td class="p-2 text-right text-ink">
                      {{ formatKrwAmount(row.amount) }}
                    </td>
                    <td class="p-2 text-right text-ink">
                      {{ formatPercentage(row.percentage) }}
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </section>

          <section
            class="flex flex-col gap-3"
            aria-labelledby="report-trend-title"
          >
            <h3
              id="report-trend-title"
              class="text-title text-ink"
            >
              {{ t('report.detail.trendTitle') }}
            </h3>
            <p
              v-if="report.analytics.dailyTrend.length === 0"
              class="text-body-sm text-ink-3"
            >
              {{ t('report.detail.trendEmpty') }}
            </p>
            <div
              v-else
              class="overflow-x-auto"
            >
              <table class="w-full border-collapse text-left text-body-sm">
                <thead>
                  <tr class="border-b border-hairline text-ink-3">
                    <th class="p-2 font-medium">{{ t('report.detail.date') }}</th>
                    <th class="p-2 text-right font-medium">{{ t('report.detail.amount') }}</th>
                  </tr>
                </thead>
                <tbody>
                  <tr
                    v-for="row in report.analytics.dailyTrend"
                    :key="row.date"
                    class="border-b border-hairline"
                  >
                    <th
                      scope="row"
                      class="p-2 font-medium text-ink"
                    >
                      {{ formatReportDate(row.date) }}
                    </th>
                    <td class="p-2 text-right text-ink">
                      {{ formatKrwAmount(row.amount) }}
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </section>
        </template>
      </section>
    </template>
  </main>
</template>
