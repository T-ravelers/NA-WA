<script setup lang="ts">
import { IconArrowLeft } from '@tabler/icons-vue'
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'

import { spendingCategoryLabelKey, toSpendingCategory } from '@/shared/lib/spendingCategory'
import AppCard from '@/shared/ui/AppCard.vue'
import IconOrb from '@/shared/ui/IconOrb.vue'
import StateError from '@/shared/ui/StateError.vue'
import StateLoading from '@/shared/ui/StateLoading.vue'
import type { Category } from '@/shared/ui/category'

import ReportCategoryBreakdown from '../components/presentation/ReportCategoryBreakdown.vue'
import ReportDailyTrend from '../components/presentation/ReportDailyTrend.vue'
import { formatPercent } from '../components/presentation/format'
import ReportKpiCard from '../components/presentation/ReportKpiCard.vue'
import ReportPersonaTicket from '../components/presentation/ReportPersonaTicket.vue'
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
    label: t(spendingCategoryLabelKey(row.category)),
    amount: Number(row.amount),
    percentage: Number(row.percentage),
  })),
)

/**
 * 칭호 티켓 색.
 *
 * 시안 R4에서 티켓 배경은 도넛 1위 조각과 같은 색이다. 1위 카테고리는 언제나 정렬 순번 0이고
 * `seriesTokenAt(0)`이 `food`이므로, 티켓 색도 그 하나로 정해진다.
 *
 * **카테고리 이름으로 색을 정하지 않는다.** `seriesPalette`가 "특정 색이 특정 카테고리를
 * 뜻하지는 않는다"고 못 박아 두었고, 카테고리마다 코어색을 주면 같은 화면의 도넛과 어긋난다.
 * 순번 0의 토큰이 바뀌면 `ReportDetailView.spec`이 잡는다.
 */
const PERSONA_TONE: Category = 'food'

/**
 * 소비 성향 칭호.
 *
 * 가장 많이 쓴 카테고리 하나로 정한다. 백엔드가 금액 내림차순으로 정렬해 내려주므로
 * 첫 항목이 1위이고, 같은 리포트에서는 항상 같은 칭호가 나온다.
 *
 * 문구는 백엔드가 만들지 않는다. 그래서 리포트 스냅샷에 `locale`을 실을 필요가 없고,
 * 이미 만들어진 리포트도 사용자가 언어를 바꾸면 그 언어로 보인다.
 *
 * 지출이 없으면 칭호를 주지 않는다 — 근거 없는 성향이 된다.
 */
const reportPersona = computed<{
  title: string
  description: string
  share: string
  categoryLabel: string
  tone: Category | 'paper'
} | null>(() => {
  const top = reportCategories.value[0]

  if (top === undefined || isZeroSpending.value) {
    return null
  }

  const category = toSpendingCategory(top.category)
  const share = formatPercent(top.percentage, i18n.locale.value)

  return {
    title: t(`report.detail.persona.${category}.title`),
    description: t(`report.detail.persona.${category}.description`, { share }),
    share,
    categoryLabel: top.label,
    tone: PERSONA_TONE,
  }
})
/**
 * 도넛 가운데 숫자 — 스냅샷에 저장된 이벤트 수.
 *
 * 스냅샷 항목은 `EVENT`와 `PLACE` 둘이고 목록 카드가 둘을 나눠 보여준다
 * (`ReportJourneyCard.vue`). 여기서 합계를 세면 목록이 `5 events · 9 places`인 여정의
 * 상세에 `14 events`가 떠서 같은 여정의 숫자가 두 화면에서 어긋난다.
 */
const reportEventCount = computed(() =>
  (report.value?.reportContent.days ?? []).reduce(
    (count, day) => count + day.items.filter((item) => item.itemType === 'EVENT').length,
    0,
  ),
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
      <h1 class="font-display text-screen-title font-bold uppercase text-ink-display">
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
        <ReportPersonaTicket
          v-if="reportPersona !== null"
          :heading="t('report.detail.persona.sectionTitle')"
          :label="t('report.detail.persona.heading')"
          :title="reportPersona.title"
          :description="reportPersona.description"
          :stamp-value="reportPersona.share"
          :stamp-label="reportPersona.categoryLabel"
          :tone="reportPersona.tone"
        />

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
          :center-value="String(reportEventCount)"
          :center-label="t('report.detail.categoryCenterLabel')"
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
