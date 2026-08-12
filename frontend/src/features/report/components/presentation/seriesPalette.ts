/**
 * Report 차트 계열 색.
 *
 * **`shared/ui`의 `CATEGORIES`·`CategoryDot`을 쓰지 않는다.** 그쪽 4종은 Explore의
 * 소비영역(beauty·shopping·show·food)을 뜻하는 고정 의미고, Report의 지출 카테고리는
 * Wallet `spendingCategory`에서 오는 임의 문자열이라 의미가 겹치지 않는다. 그래서 여기서는
 * 카테고리 이름이 아니라 **정렬된 순번**으로 색을 배정한다. 같은 목록을 다시 그리면 같은
 * 색이 나오지만, 특정 색이 특정 카테고리를 뜻하지는 않는다.
 *
 * 값은 전부 `app/styles/tokens.css`에 이미 있는 토큰이고 새 토큰을 만들지 않는다. 6번째
 * 이후는 앞에서부터 다시 돈다. 색이 겹쳐도 범례가 항상 라벨과 금액을 텍스트로 함께
 * 보여주므로 정보가 색에만 실리지 않는다.
 *
 * Tailwind는 소스에 그대로 적힌 클래스 문자열만 수집하므로, 클래스는 조합하지 않고
 * 아래 두 표에 전부 적어 둔다.
 */
export const REPORT_SERIES_TOKENS = [
  'food',
  'shopping',
  'show',
  'beauty',
  'settlement',
  'gauge',
] as const

export type ReportSeriesToken = (typeof REPORT_SERIES_TOKENS)[number]

/** 면을 채울 때(범례 표식). */
const SERIES_SURFACE_CLASS: Record<ReportSeriesToken, string> = {
  food: 'bg-food',
  shopping: 'bg-shopping',
  show: 'bg-show',
  beauty: 'bg-beauty',
  settlement: 'bg-settlement',
  gauge: 'bg-gauge',
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
  gauge: 'text-gauge',
}

export function seriesTokenAt(index: number): ReportSeriesToken {
  const token = REPORT_SERIES_TOKENS[index % REPORT_SERIES_TOKENS.length]

  // 나머지 연산 결과는 항상 범위 안이지만, noUncheckedIndexedAccess에서는 좁혀지지 않는다.
  return token ?? REPORT_SERIES_TOKENS[0]
}

export function seriesSurfaceClass(index: number): string {
  return SERIES_SURFACE_CLASS[seriesTokenAt(index)]
}

export function seriesInkClass(index: number): string {
  return SERIES_INK_CLASS[seriesTokenAt(index)]
}
