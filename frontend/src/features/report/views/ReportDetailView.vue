<script setup lang="ts">
import { IconArrowLeft, IconChevronRight, IconShare2 } from '@tabler/icons-vue'
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'

import {
  SPENDING_CATEGORIES,
  spendingCategoryLabelKey,
  toSpendingCategory,
} from '@/shared/lib/spendingCategory'
import AppButton from '@/shared/ui/AppButton.vue'
import AppCard from '@/shared/ui/AppCard.vue'
import IconOrb from '@/shared/ui/IconOrb.vue'
import StateError from '@/shared/ui/StateError.vue'
import StateLoading from '@/shared/ui/StateLoading.vue'
import type { Category } from '@/shared/ui/category'
import { showToast } from '@/shared/ui/toast'

import ReportCategoryBreakdown from '../components/presentation/ReportCategoryBreakdown.vue'
import ReportComparisonBars from '../components/presentation/ReportComparisonBars.vue'
import ReportDailyTrend from '../components/presentation/ReportDailyTrend.vue'
import ReportRadarChart from '../components/presentation/ReportRadarChart.vue'
import ReportRankTiles from '../components/presentation/ReportRankTiles.vue'
import { formatMoney, formatPercent } from '../components/presentation/format'
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
const comparisonQuery = useReportComparisonQuery(
  reportId,
  'GROUP',
  computed(
    () =>
      report.value?.analytics !== null &&
      report.value?.analytics !== undefined &&
      !isZeroSpending.value,
  ),
)
const comparison = computed(() => comparisonQuery.data.value ?? null)
const hasPeers = computed(() => (comparison.value?.peers.length ?? 0) > 0)
/** `LIVE`는 여정 기간의 결제를 지금 다시 합산한 값이라 위 ANALYSIS(스냅샷)와 다를 수 있다. */
const isLiveComparison = computed(() => comparison.value?.basis === 'LIVE')

const comparisonMe = computed<ReportComparisonBarRow>(() => ({
  id: comparison.value?.me.memberId ?? 0,
  label: t('report.detail.comparison.you'),
  amount: Number(comparison.value?.me.totalSpent ?? 0),
}))
const comparisonPeers = computed<ReportComparisonBarRow[]>(() =>
  (comparison.value?.peers ?? []).map((peer) => ({
    id: peer.memberId,
    label: peer.displayName,
    amount: Number(peer.totalSpent),
  })),
)

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

const comparisonTiles = computed<ReportRankTile[]>(() =>
  (comparison.value?.ranks ?? []).slice(0, 4).map((row) => ({
    key: row.category,
    label: t(spendingCategoryLabelKey(row.category)),
    rankText: rankText(row.rank),
    tone: spendingCategoryTone(row.category) ?? 'surface',
  })),
)

function retryComparison(): void {
  void comparisonQuery.refetch()
}

/**
 * 헤더 아이콘과 하단 `Confirm & Share`가 보내는 리포트 요약.
 *
 * 리포트 상세는 작성자만 열 수 있다. 링크를 보내면 받는 쪽은 아무것도 볼 수 없으므로
 * 링크 대신 문장을 보낸다 — 여정·기간에 칭호·총지출·1위 비중을 붙인다. 칭호가 없는
 * 리포트(지출 0원·구 리포트)는 여정과 기간만 보낸다.
 */
const shareSummary = computed<string | null>(() => {
  const current = report.value

  if (current === null) {
    return null
  }

  const journey = current.reportContent.journey
  const period = `${formatReportDate(journey.startDate)}–${formatReportDate(journey.endDate)}`
  const persona = reportPersona.value
  const kpi = reportKpi.value

  if (persona === null || kpi === null) {
    return t('report.detail.sharing.summaryPlain', { journey: journey.title, period })
  }

  return t('report.detail.sharing.summary', {
    journey: journey.title,
    period,
    hashtag: persona.title,
    total: formatMoney(kpi.totalSpent, i18n.locale.value),
    share: persona.share,
    category: persona.categoryLabel,
  })
})

/**
 * 공유 시트가 있으면 시트로, 없으면 클립보드로, 둘 다 없으면 안내만 한다.
 *
 * 시트를 닫아 취소한 것(`AbortError`)만 조용히 넘어간다. 취소가 아닌 거절은 실패이므로
 * 아래 클립보드 경로가 받는다 — `web-share` 권한이 없는 교차 출처 iframe과 인앱 브라우저는
 * `navigator.share`가 있는데도 `NotAllowedError`로 거절한다. 이때 폴백까지 막으면 시트도
 * 토스트도 없이 끝나 버튼이 고장 난 것처럼 보인다.
 *
 * 복사 완료 문구는 무엇을 복사했는지가 달라서 화면이 인자로 준다.
 */
async function shareText(title: string, text: string, copiedMessage: string): Promise<void> {
  if (navigator.share) {
    try {
      await navigator.share({ title, text })
      return
    } catch (error) {
      if (error instanceof DOMException && error.name === 'AbortError') {
        return
      }
    }
  }

  if (!navigator.clipboard) {
    showToast(t('report.detail.sharing.unavailable'))
    return
  }

  try {
    await navigator.clipboard.writeText(text)
    showToast(copiedMessage)
  } catch {
    showToast(t('report.detail.sharing.copyFailed'))
  }
}

function shareReport(): void {
  const summary = shareSummary.value

  if (summary !== null) {
    void shareText(
      t('report.detail.sharing.reportTitle'),
      summary,
      t('report.detail.sharing.copiedReport'),
    )
  }
}

function shareTicket(): void {
  const persona = reportPersona.value

  if (persona !== null) {
    void shareText(
      t('report.detail.sharing.ticketTitle'),
      `${persona.title}\n${persona.description}`,
      t('report.detail.sharing.copiedTicket'),
    )
  }
}

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
      <h1 class="flex-1 font-display text-screen-title font-bold uppercase text-ink-display">
        {{ t('report.detail.title') }}
      </h1>
      <!--
        캐시가 있는 재방문에서 재요청이 실패하면 `data`는 남고 `isError`만 켜진다.
        그러면 본문은 `StateError`인데 헤더에는 공유 아이콘이 남으므로 함께 본다.
      -->
      <IconOrb
        v-if="shareSummary !== null && !reportQuery.isError.value"
        :label="t('report.detail.sharing.report')"
        size="md"
        variant="surface"
        @click="shareReport"
      >
        <IconShare2
          :size="20"
          :stroke-width="1.8"
          aria-hidden="true"
        />
      </IconOrb>
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
          :share-label="t('report.detail.sharing.ticket')"
          @share="shareTicket"
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

        <section
          v-if="!isZeroSpending"
          class="flex flex-col gap-3"
          aria-labelledby="report-comparison-title"
        >
          <h2
            id="report-comparison-title"
            class="font-display text-section-header uppercase text-ink"
          >
            {{ t('report.detail.comparison.heading') }}
          </h2>

          <StateLoading
            v-if="comparisonQuery.isPending.value"
            :label="t('report.detail.comparison.loading')"
          />

          <StateError
            v-else-if="comparisonQuery.isError.value"
            :title="t('report.detail.comparison.loadFailed')"
            :description="t('report.detail.comparison.loadFailedDescription')"
            :action-label="t('action.retry')"
            @retry="retryComparison"
          />

          <AppCard v-else-if="!hasPeers">
            <h3 class="text-title text-ink">{{ t('report.detail.comparison.emptyTitle') }}</h3>
            <p class="mt-2 text-body-sm text-ink-3">
              {{ t('report.detail.comparison.emptyDescription') }}
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
                    :cohort-label="t('report.detail.comparison.groupAvg')"
                    :description="t('report.detail.comparison.radarDescription')"
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

      <AppButton
        block
        @click="shareReport"
      >
        <span class="inline-flex items-center gap-1">
          {{ t('report.detail.sharing.confirm') }}
          <IconChevronRight
            :size="18"
            :stroke-width="2"
            aria-hidden="true"
          />
        </span>
      </AppButton>
    </template>
  </main>
</template>
