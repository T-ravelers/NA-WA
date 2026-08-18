import { formatGroupedDecimal } from '@/shared/lib/money'

/**
 * 서버가 내려준 원장 문자열을 정밀도 손실 없이 묶는다.
 *
 * 통화 단위는 여기서 붙이지 않는다. 단위 위치가 로케일마다 다르므로 `settlement.points`
 * 보간 문구가 금액과 단위를 함께 소유한다. `useSettlementPoints`를 쓴다.
 */
export function formatSettlementAmount(amount: string): string {
  return formatGroupedDecimal(amount, 'en-US') || amount
}
