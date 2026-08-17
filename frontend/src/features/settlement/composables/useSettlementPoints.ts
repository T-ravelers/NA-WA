import { useI18n } from 'vue-i18n'

import { formatSettlementAmount } from '../model/settlementPresentation'

/**
 * 금액과 통화 단위를 한 문구로 묶어 돌려준다.
 *
 * 템플릿에서 `{{ amount }} P`처럼 단위를 이어 붙이지 않는다. 단위가 숫자 앞에 오는
 * 로케일이 있어서, 금액과 단위는 하나의 번역 문구가 소유해야 한다.
 */
export function useSettlementPoints(): (amount: string) => string {
  const { t } = useI18n()

  return (amount: string) => t('settlement.points', { amount: formatSettlementAmount(amount) })
}
