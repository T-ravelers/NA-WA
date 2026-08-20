import { formatGroupedDecimal } from '@/shared/lib/money'

/**
 * 서버가 내려준 원장 문자열을 정밀도 손실 없이 묶는다.
 *
 * 통화 단위는 여기서 붙이지 않는다. 단위 위치가 로케일마다 다르므로 `settlement.points`
 * 보간 문구가 금액과 단위를 함께 소유한다. `useSettlementPoints`를 쓴다.
 *
 * `minimumFractionDigits`는 화면이 직접 계산한 금액을 서버 금액과 나란히 놓을 때 쓴다.
 * 계산 결과는 뒤의 0이 떨어져 나오므로, 채워 주지 않으면 25.00과 25가 같은 줄에 놓여
 * 같은 금액인지 알아볼 수 없다. 자릿수를 줄이지는 않으므로 값이 뭉개지지 않는다.
 */
export function formatSettlementAmount(amount: string, minimumFractionDigits = 0): string {
  return formatGroupedDecimal(amount, 'en-US', { minimumFractionDigits }) || amount
}
