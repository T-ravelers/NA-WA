/**
 * Report 프레젠테이션 전용 숫자 표기.
 *
 * 로케일을 인자로 받는다. 컴포넌트가 `useI18n`을 부르면 상위(#153)의 i18n 등록에
 * 묶여서 단독으로 렌더되지 않기 때문이다. 기본값은 앱의 폴백 로케일과 같은 `en`이다.
 */
import type { MoneyValue } from './types'

/** 소비 금액을 P로 표시한다(#333). 지갑 통화(KRW)와 1:1이라 통화 스타일 대신 자릿수 구분만 한다. */
export function formatMoney(amount: MoneyValue, locale = 'en'): string {
  return `${new Intl.NumberFormat(locale, { maximumFractionDigits: 0 }).format(amount)} P`
}

/** 입력은 0–100 스케일이다. `Intl`은 0–1을 받으므로 여기서 나눈다. */
export function formatPercent(percentage: number, locale = 'en'): string {
  return new Intl.NumberFormat(locale, {
    style: 'percent',
    maximumFractionDigits: 0,
  }).format(percentage / 100)
}
