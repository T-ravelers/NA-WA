/**
 * Report 프레젠테이션 전용 숫자 표기.
 *
 * 로케일을 인자로 받는다. 컴포넌트가 `useI18n`을 부르면 상위(#153)의 i18n 등록에
 * 묶여서 단독으로 렌더되지 않기 때문이다. 기본값은 앱의 폴백 로케일과 같은 `en`이다.
 */
import type { MoneyValue, ReportKpiData } from './types'

export type ReportCurrency = ReportKpiData['currency']

export function formatMoney(amount: MoneyValue, currency: ReportCurrency, locale = 'en'): string {
  return new Intl.NumberFormat(locale, {
    style: 'currency',
    currency,
    maximumFractionDigits: 0,
  }).format(amount)
}

/** 입력은 0–100 스케일이다. `Intl`은 0–1을 받으므로 여기서 나눈다. */
export function formatPercent(percentage: number, locale = 'en'): string {
  return new Intl.NumberFormat(locale, {
    style: 'percent',
    maximumFractionDigits: 0,
  }).format(percentage / 100)
}
