/**
 * 프레젠테이션 픽스처.
 *
 * **wire DTO가 아니라 `../types`의 프레젠테이션 계약을 쓴다.** 백엔드 응답(#151)의
 * `string | number` 금액과 nullable 카테고리를 여기 형태로 정규화하는 일은 #153의
 * API 어댑터 몫이고, 이 디렉터리는 정규화가 끝난 뒤의 화면만 책임진다.
 *
 * 값은 시안 R4(개인 최종 리포트)의 표시값을 그대로 옮긴 것이다.
 */
import type { ReportCategoryBreakdownItem, ReportDailyTrendPoint, ReportKpiData } from '../types'

export const reportKpiFixture: ReportKpiData = {
  totalSpent: 1_284_500,
  dailyAverage: 142_700,
  currency: 'KRW',
}

/** 모든 금액이 0인 여정. 화면이 나눗셈으로 무너지지 않는지 보는 용도다. */
export const reportKpiZeroFixture: ReportKpiData = {
  totalSpent: 0,
  dailyAverage: 0,
  currency: 'KRW',
}

export const reportCategoryBreakdownFixture: ReportCategoryBreakdownItem[] = [
  { category: 'FOOD', label: 'Food', amount: 539_500, percentage: 42 },
  { category: 'SHOPPING', label: 'Shopping', amount: 398_200, percentage: 31 },
  { category: 'SHOW', label: 'Shows', amount: 218_400, percentage: 17 },
  { category: 'BEAUTY', label: 'Beauty', amount: 128_400, percentage: 10 },
]

/**
 * 카테고리는 잡혔지만 금액이 전부 0인 경우.
 *
 * `category`가 백엔드 enum이 아니라 Wallet에서 온 임의 문자열이라는 점을 함께 보여 준다.
 * 값이 비어 있던 항목을 #153 어댑터가 어떤 문자열로 접든 화면은 그대로 그린다.
 */
export const reportCategoryBreakdownZeroFixture: ReportCategoryBreakdownItem[] = [
  { category: 'FOOD', label: 'Food', amount: 0, percentage: 0 },
  { category: 'late-night-snacks', label: 'Late-night snacks', amount: 0, percentage: 0 },
  { category: 'uncategorized', label: 'Uncategorized', amount: 0, percentage: 0 },
]

export const reportDailyTrendFixture: ReportDailyTrendPoint[] = [
  { date: '2026-03-28', label: 'Mar 28', amount: 262_000 },
  { date: '2026-03-29', label: 'Mar 29', amount: 148_500 },
  { date: '2026-03-30', label: 'Mar 30', amount: 76_000 },
  { date: '2026-03-31', label: 'Mar 31', amount: 178_000 },
  { date: '2026-04-01', label: 'Apr 1', amount: 122_000 },
  { date: '2026-04-02', label: 'Apr 2', amount: 220_000 },
  { date: '2026-04-05', label: 'Apr 5', amount: 108_000 },
]

/** 일자는 있는데 지출이 하나도 없는 여정. */
export const reportDailyTrendZeroFixture: ReportDailyTrendPoint[] = [
  { date: '2026-05-02', label: 'May 2', amount: 0 },
  { date: '2026-05-03', label: 'May 3', amount: 0 },
  { date: '2026-05-04', label: 'May 4', amount: 0 },
]
