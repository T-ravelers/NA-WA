<script setup lang="ts">
import { IconArrowLeft, IconSparkles } from '@tabler/icons-vue'
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'

import {
  SPENDING_CATEGORIES,
  spendingCategoryLabelKey,
  toSpendingCategory,
} from '@/shared/lib/spendingCategory'
import AppCard from '@/shared/ui/AppCard.vue'
import IconOrb from '@/shared/ui/IconOrb.vue'
import SegmentedControl from '@/shared/ui/SegmentedControl.vue'
import StateError from '@/shared/ui/StateError.vue'
import StateLoading from '@/shared/ui/StateLoading.vue'
import type { Category } from '@/shared/ui/category'

import type { ReportComparisonScope } from '../api/reportApi'
import ReportCategoryBreakdown from '../components/presentation/ReportCategoryBreakdown.vue'
import ReportComparisonBars from '../components/presentation/ReportComparisonBars.vue'
import ReportDailyTrend from '../components/presentation/ReportDailyTrend.vue'
import ReportRadarChart from '../components/presentation/ReportRadarChart.vue'
import ReportRankTiles from '../components/presentation/ReportRankTiles.vue'
import { seriesInkClass } from '../components/presentation/seriesPalette'
import { formatPercent, formatSignedPercent } from '../components/presentation/format'
import ReportKpiCard from '../components/presentation/ReportKpiCard.vue'
import ReportPersonaTicket from '../components/presentation/ReportPersonaTicket.vue'
import type {
  ReportCategoryBreakdownItem,
  ReportComparisonBarRow,
  ReportDailyTrendPoint,
  ReportKpiData,
  ReportRadarAxis,
  ReportRankTile,
} from '../components/presentation/types'
import { useReportComparisonQuery, useReportDetailQuery } from '../composables/useReportQueries'
import { isReportForbidden, isReportNotFound, reportErrorMessageKey } from '../model/reportErrors'
import {
  formatReportDate,
  isZeroAmount,
  parsePositiveRouteId,
  spendingCategoryTone,
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
    tone: spendingCategoryTone(category) ?? 'paper',
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

/* ── 동료 비교 (#404) ──
 * 스냅샷이 있고 지출이 0이 아닐 때만 부른다. 비교는 여정 기간의 결제를 다시 합산한 값이라
 * 위 ANALYSIS(스냅샷)와 숫자가 다를 수 있다 — 백엔드 문서의 basis 설명. */
const comparisonEnabled = computed(
  () =>
    report.value?.analytics !== null &&
    report.value?.analytics !== undefined &&
    !isZeroSpending.value,
)
const groupQuery = useReportComparisonQuery(reportId, 'GROUP', comparisonEnabled)
// 같은 국적 여행자(SNAPSHOT, #421). 인사이트 문장도 이 코호트를 쓰므로 탭과 상관없이 같이 받는다.
const similarQuery = useReportComparisonQuery(reportId, 'SIMILAR', comparisonEnabled)

const comparisonScope = ref<ReportComparisonScope>('GROUP')
const isSimilarScope = computed(() => comparisonScope.value === 'SIMILAR')
const comparisonQuery = computed(() => (isSimilarScope.value ? similarQuery : groupQuery))
const comparisonPending = computed(() => comparisonQuery.value.isPending.value)
const comparisonFailed = computed(() => comparisonQuery.value.isError.value)
const comparison = computed(() => comparisonQuery.value.data.value ?? null)
/** `LIVE`는 여정 기간의 결제를 지금 다시 합산한 값이라 위 ANALYSIS(스냅샷)와 다를 수 있다. */
const isLiveComparison = computed(() => comparison.value?.basis === 'LIVE')
const comparisonScopeOptions = computed(() => [
  { value: 'GROUP', label: t('report.detail.comparison.scopeGroup') },
  { value: 'SIMILAR', label: t('report.detail.comparison.scopeSimilar') },
])

/** 같은 국적 코호트. 국적이 없거나 같은 국적의 리포트가 없으면 `size 0`으로 온다. */
const similarCohort = computed(() => {
  const cohort = similarQuery.data.value?.cohort

  return cohort !== undefined && cohort.size > 0 ? cohort : null
})
/** 견줄 상대가 있는가 — GROUP은 동료, SIMILAR는 코호트. */
const hasPeers = computed(() =>
  isSimilarScope.value ? similarCohort.value !== null : (comparison.value?.peers.length ?? 0) > 0,
)

function setComparisonScope(value: string): void {
  comparisonScope.value = value === 'SIMILAR' ? 'SIMILAR' : 'GROUP'
}

const comparisonMe = computed<ReportComparisonBarRow>(() => ({
  id: comparison.value?.me.memberId ?? 0,
  label: t('report.detail.comparison.you'),
  amount: Number(comparison.value?.me.totalSpent ?? 0),
}))
/** 막대의 상대. GROUP은 동료들(칩으로 고른다), SIMILAR는 코호트 평균 하나. */
const comparisonPeers = computed<ReportComparisonBarRow[]>(() => {
  const current = comparison.value

  if (current === null) {
    return []
  }

  if (isSimilarScope.value) {
    // 막대 라벨 칸은 48px라 `Travelers avg`가 잘린다. 타일과 같은 `AVG`로 적고 풀네임은 레이더 범례가 맡는다.
    return [
      {
        id: 0,
        label: t('report.detail.comparison.average'),
        amount: Number(current.cohort.avgTotalSpent),
      },
    ]
  }

  return current.peers.map((peer) => ({
    id: peer.memberId,
    label: peer.displayName,
    amount: Number(peer.totalSpent),
  }))
})

/** 레이더 축 — 나와 코호트 중 한쪽이라도 쓴 카테고리, 내 비중 순. 3개가 안 되면 나머지 카테고리로 채운다. */
const comparisonAxes = computed<ReportRadarAxis[]>(() => {
  const current = comparison.value

  if (current === null) {
    return []
  }

  const mine = new Map(
    current.me.categoryBreakdown.map((row) => [row.category, Number(row.percentage)]),
  )
  const cohort = new Map(
    current.cohort.categoryBreakdown.map((row) => [row.category, Number(row.percentage)]),
  )
  const ordered = [...mine.keys(), ...cohort.keys(), ...SPENDING_CATEGORIES].filter(
    (category, index, all) => all.indexOf(category) === index,
  )
  const used = ordered.filter(
    (category) => (mine.get(category) ?? 0) > 0 || (cohort.get(category) ?? 0) > 0,
  )
  const axes = used.length >= 3 ? used : ordered.slice(0, 3)

  return axes.slice(0, 6).map((category) => ({
    key: category,
    label: t(spendingCategoryLabelKey(category)),
    mine: mine.get(category) ?? 0,
    cohort: cohort.get(category) ?? 0,
  }))
})

function rankText(rank: number): string {
  if (rank === 1) return t('report.detail.comparison.rankFirst')
  if (rank === 2) return t('report.detail.comparison.rankSecond')
  if (rank === 3) return t('report.detail.comparison.rankThird')

  return t('report.detail.comparison.rankNth', { rank })
}

/**
 * SIMILAR 타일과 인사이트가 「평균과 비슷하다」고 보는 폭(%p). 시안 VS.SIMILAR의 `AVG` 타일이
 * 이것이다. 내 비중 − 코호트 비중이 이 안이면 `AVG`, 밖이면 `+12%`처럼 부호를 붙인다.
 */
const AVG_BAND_POINTS = 5

function shareDiffText(points: number): string {
  return Math.abs(points) <= AVG_BAND_POINTS
    ? t('report.detail.comparison.average')
    : formatSignedPercent(points, i18n.locale.value)
}

/** 타일 — GROUP은 카테고리별 순위, SIMILAR는 레이더 축 순서대로 코호트 대비 비중 차이. */
const comparisonTiles = computed<ReportRankTile[]>(() => {
  if (isSimilarScope.value) {
    return comparisonAxes.value.slice(0, 4).map((axis) => ({
      key: axis.key,
      label: axis.label,
      rankText: shareDiffText(axis.mine - axis.cohort),
      tone: spendingCategoryTone(axis.key) ?? 'surface',
    }))
  }

  return (comparison.value?.ranks ?? []).slice(0, 4).map((row) => ({
    key: row.category,
    label: t(spendingCategoryLabelKey(row.category)),
    rankText: rankText(row.rank),
    tone: spendingCategoryTone(row.category) ?? 'surface',
  }))
})

function retryComparison(): void {
  void comparisonQuery.value.refetch()
}

/* ── 인사이트 한 줄 (#421) ──
 * 칭호의 1위 카테고리 비중을 같은 국적 여행자 평균과 견준다. 코호트가 없거나 아직 못 받았으면
 * 비교 없이 비중만 말한다 — 비교 부분이 늦게 붙더라도 문장은 처음부터 있다.
 * 카테고리 단어는 그 카테고리 색으로 강조하므로 문장은 `<i18n-t>`로 조립한다. */
type InsightVariant = 'above' | 'same' | 'below' | 'alone'

const reportInsight = computed<{
  variant: InsightVariant
  label: string
  share: string
  cohortShare: string
  inkClass: string
} | null>(() => {
  const top = reportCategories.value[0]
  const persona = reportPersona.value

  if (top === undefined || persona === null) {
    return null
  }

  const locale = i18n.locale.value
  const base = {
    label: top.label.toLocaleLowerCase(locale),
    share: persona.share,
    inkClass: seriesInkClass(top.category),
  }
  const cohort = similarCohort.value

  if (cohort === null) {
    return { ...base, variant: 'alone', cohortShare: '' }
  }

  const cohortShare = Number(
    cohort.categoryBreakdown.find((row) => row.category === top.category)?.percentage ?? 0,
  )
  const diff = top.percentage - cohortShare
  const variant: InsightVariant =
    diff > AVG_BAND_POINTS ? 'above' : diff < -AVG_BAND_POINTS ? 'below' : 'same'

  return { ...base, variant, cohortShare: formatPercent(cohortShare, locale) }
})

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

        <AppCard v-if="reportInsight !== null">
          <p class="flex items-start gap-2 text-body-sm text-ink-2">
            <IconSparkles
              :size="18"
              :stroke-width="1.8"
              aria-hidden="true"
              class="mt-0.5 shrink-0"
              :class="reportInsight.inkClass"
            />
            <i18n-t
              :keypath="`report.detail.insight.${reportInsight.variant}`"
              tag="span"
              scope="global"
            >
              <template #category>
                <span
                  class="font-semibold"
                  :class="reportInsight.inkClass"
                  >{{ reportInsight.label }}</span
                >
              </template>
              <template #share>{{ reportInsight.share }}</template>
              <template #cohortShare>{{ reportInsight.cohortShare }}</template>
            </i18n-t>
          </p>
        </AppCard>

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

        <section
          v-if="!isZeroSpending"
          class="flex flex-col gap-3"
          aria-labelledby="report-comparison-title"
        >
          <h2
            id="report-comparison-title"
            class="font-display text-section-header uppercase text-ink"
          >
            {{
              t(
                isSimilarScope
                  ? 'report.detail.comparison.similarHeading'
                  : 'report.detail.comparison.heading',
              )
            }}
          </h2>

          <SegmentedControl
            :model-value="comparisonScope"
            :label="t('report.detail.comparison.scopeLabel')"
            :options="comparisonScopeOptions"
            @update:model-value="setComparisonScope"
          />

          <StateLoading
            v-if="comparisonPending"
            :label="t('report.detail.comparison.loading')"
          />

          <StateError
            v-else-if="comparisonFailed"
            :title="t('report.detail.comparison.loadFailed')"
            :description="t('report.detail.comparison.loadFailedDescription')"
            :action-label="t('action.retry')"
            @retry="retryComparison"
          />

          <AppCard v-else-if="!hasPeers">
            <h3 class="text-title text-ink">
              {{
                t(
                  isSimilarScope
                    ? 'report.detail.comparison.similarEmptyTitle'
                    : 'report.detail.comparison.emptyTitle',
                )
              }}
            </h3>
            <p class="mt-2 text-body-sm text-ink-3">
              {{
                t(
                  isSimilarScope
                    ? 'report.detail.comparison.similarEmptyDescription'
                    : 'report.detail.comparison.emptyDescription',
                )
              }}
            </p>
          </AppCard>

          <template v-else>
            <AppCard padding="lg">
              <div class="flex flex-col gap-6">
                <div class="flex flex-col gap-2">
                  <ReportComparisonBars
                    :total-label="t('report.detail.comparison.totalSpend')"
                    :chips-label="t('report.detail.comparison.members')"
                    :me="comparisonMe"
                    :peers="comparisonPeers"
                    :chips="!isSimilarScope"
                    :locale="i18n.locale.value"
                  />
                  <p
                    v-if="isLiveComparison"
                    class="text-micro text-ink-3"
                  >
                    {{ t('report.detail.comparison.liveBasisNote') }}
                  </p>
                </div>
                <div class="flex flex-col gap-3">
                  <p class="text-micro uppercase text-ink-3">
                    {{ t('report.detail.comparison.categoryBalance') }}
                  </p>
                  <ReportRadarChart
                    :axes="comparisonAxes"
                    :mine-label="t('report.detail.comparison.you')"
                    :cohort-label="
                      t(
                        isSimilarScope
                          ? 'report.detail.comparison.travelersAvg'
                          : 'report.detail.comparison.groupAvg',
                      )
                    "
                    :description="
                      t(
                        isSimilarScope
                          ? 'report.detail.comparison.similarRadarDescription'
                          : 'report.detail.comparison.radarDescription',
                      )
                    "
                  />
                </div>
              </div>
            </AppCard>

            <ReportRankTiles :tiles="comparisonTiles" />
          </template>
        </section>
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
