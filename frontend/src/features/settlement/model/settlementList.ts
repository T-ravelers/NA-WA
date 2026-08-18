import type { SettlementSummary } from './settlement'

/** 코드 식별자는 서버 응답을 따르고, 화면 문구만 To Pay / To Collect로 부른다. */
export type SettlementSide = 'received' | 'sent'

/**
 * 화면 사이로 나르는 토글 위치.
 *
 * 목록·전체 내역·상세·결제가 같은 `side` 쿼리를 주고받아야 To Collect에서 들어간 뒤
 * 되돌아 나올 때 To Pay로 떨어지지 않는다. 값이 없거나 모르는 값이면 기본값으로 읽는다.
 */
export function resolveSide(value: unknown): SettlementSide {
  return value === 'sent' ? 'sent' : 'received'
}

/** 완료 구획에서 미리 보여주는 건수. 나머지는 전체 내역 화면에서 본다. */
export const COMPLETED_PREVIEW_COUNT = 3

export interface SettlementSections {
  ongoing: SettlementSummary[]
  completed: SettlementSummary[]
}

/**
 * 진행 중과 완료를 나눈다.
 *
 * 서버가 최신순으로 정렬해 내려주므로 순서를 다시 매기지 않는다.
 */
export function splitIntoSections(settlements: SettlementSummary[]): SettlementSections {
  const ongoing: SettlementSummary[] = []
  const completed: SettlementSummary[] = []
  for (const settlement of settlements) {
    if (settlement.status === 'REQUESTED') {
      ongoing.push(settlement)
    } else {
      completed.push(settlement)
    }
  }

  return { ongoing, completed }
}

/**
 * 내가 이미 냈는데 다른 참가자가 남아 정산 자체는 진행 중인 상태.
 *
 * 이런 건은 진행 중 구획에 남기되 `Paid` 표식으로 구분한다. 생성자 본인의
 * `requestStatus`는 항상 `NOT_REQUESTED`이므로 To Collect 쪽에서는 신호가 되지 않는다.
 */
export function hasViewerPaid(settlement: SettlementSummary): boolean {
  return settlement.viewer.requestStatus === 'PAID'
}

/** 결제 가능 여부는 서버가 준 허용 동작으로만 판단한다. 금액으로 추론하지 않는다. */
export function canPay(settlement: SettlementSummary): boolean {
  return settlement.viewer.allowedActions.includes('PAY')
}

/**
 * 카드에서 가장 크게 보여줄 금액.
 *
 * 낼 쪽은 내 부담금, 받을 쪽은 남에게 받을 총액이다. 낼 쪽에 `payableAmount`를 쓰면
 * 이미 낸 건이 0으로 보이므로 `shareAmount`를 쓴다.
 */
export function primaryAmount(settlement: SettlementSummary, side: SettlementSide): string {
  return side === 'received' ? settlement.viewer.shareAmount : settlement.receivableAmount
}
