import type { SpendingCategory } from '@/shared/lib/spendingCategory'
import { toSpendingCategory } from '@/shared/lib/spendingCategory'

/**
 * Report 차트 계열 색.
 *
 * **카테고리마다 색이 정해져 있다.** 같은 화면의 칭호 티켓이 1위 카테고리의 코어색을 쓰므로,
 * 도넛이 순번으로 색을 배정하면 사용자가 한 화면에서 "쇼핑 = 파랑"을 읽고 조금 아래에서
 * "쇼핑 = 주황"을 읽게 된다. 그래서 정렬 순번이 아니라 카테고리 이름으로 색을 정한다.
 *
 * 앞의 네 카테고리는 Explore 소비영역과 같은 어휘를 쓰므로 그 코어색을 그대로 쓴다.
 * 코어색이 없는 세 카테고리에는 **새 토큰을 만들지 않고** 이미 있는 토큰을 배정한다.
 * `OTHER`가 흐린 회색인 것은 분류되지 않은 지출이라는 뜻과 맞다.
 *
 * **색상각이 겹치지 않는 토큰을 고른다.** `gauge`(#5aa3dd)는 `shopping`(#318cd5)과 색상각이
 * 207도로 같아 명도만 다른 같은 파랑이었다(서로 간 대비 1.32:1). 팔레트에서 비어 있던 녹색대를
 * 써서 `TRANSPORT`를 `status-ongoing`(#3bbe7a, 149도)으로 둔다.
 *
 * 값은 전부 `app/styles/tokens.css`에 이미 있는 토큰이다. Tailwind는 소스에 그대로 적힌
 * 클래스 문자열만 수집하므로, 클래스는 조합하지 않고 아래 두 표에 전부 적어 둔다.
 */
export const REPORT_SERIES_TOKENS = [
  'food',
  'shopping',
  'show',
  'beauty',
  'settlement',
  'status-ongoing',
  'ink-3',
] as const

export type ReportSeriesToken = (typeof REPORT_SERIES_TOKENS)[number]

/** 카테고리 → 계열 색. */
const CATEGORY_TOKEN: Record<SpendingCategory, ReportSeriesToken> = {
  FOOD: 'food',
  SHOPPING: 'shopping',
  BEAUTY: 'beauty',
  SHOW: 'show',
  TRANSPORT: 'status-ongoing',
  STAY: 'settlement',
  OTHER: 'ink-3',
}

/** 면을 채울 때(범례 표식). */
const SERIES_SURFACE_CLASS: Record<ReportSeriesToken, string> = {
  food: 'bg-food',
  shopping: 'bg-shopping',
  show: 'bg-show',
  beauty: 'bg-beauty',
  settlement: 'bg-settlement',
  'status-ongoing': 'bg-status-ongoing',
  'ink-3': 'bg-ink-3',
}

/**
 * SVG에서 쓸 때. `stroke`/`fill`을 `currentColor`로 두고 이 클래스로 색을 정한다.
 * 색 유틸리티 중 `text-*`만 쓰면 되어 수집 대상이 하나로 줄어든다.
 */
const SERIES_INK_CLASS: Record<ReportSeriesToken, string> = {
  food: 'text-food',
  shopping: 'text-shopping',
  show: 'text-show',
  beauty: 'text-beauty',
  settlement: 'text-settlement',
  'status-ongoing': 'text-status-ongoing',
  'ink-3': 'text-ink-3',
}

/**
 * 카테고리의 계열 색.
 *
 * 값은 Wallet `spendingCategory`에서 오는 임의 문자열이라 좁히지 못한 값은 `OTHER`로 접힌다.
 */
export function seriesTokenOf(category: string): ReportSeriesToken {
  return CATEGORY_TOKEN[toSpendingCategory(category)]
}

export function seriesSurfaceClass(category: string): string {
  return SERIES_SURFACE_CLASS[seriesTokenOf(category)]
}

export function seriesInkClass(category: string): string {
  return SERIES_INK_CLASS[seriesTokenOf(category)]
}
