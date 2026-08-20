import { NormalizedApiError } from '@/shared/api/apiError'

/**
 * `RETRY`는 같은 멱등성 키로 다시 보내 중복 처리를 막는다. 키 자체가 거부된 경우에만
 * `RETRY_NEW_KEY`로 키를 버리고 다시 만든다. 둘을 섞으면 네트워크 실패 재시도까지 새 키로
 * 나가 중복 이체를 막지 못한다.
 */
export type SettlementRecovery =
  | 'BACK_TO_LIST'
  | 'REFETCH_CANDIDATES'
  | 'REFETCH_DETAIL'
  | 'EDIT_FORM'
  | 'RETRY'
  | 'RETRY_NEW_KEY'
  | 'RETAKE_PHOTO'
  | 'ENTER_MANUALLY'

/*
 * RETAKE_PHOTO와 ENTER_MANUALLY를 보고 동작을 바꾸는 화면은 아직 없다.
 *
 * recovery를 읽는 곳은 SettlementCreateView와 SettlementPayView 둘뿐이고, 둘 다
 * BACK_TO_LIST·REFETCH_DETAIL·REFETCH_CANDIDATES만 본다. 지금 사용자 안내를 실제로
 * 갈라 주는 것은 이 값이 아니라 코드마다 다른 문구다.
 *
 * 그래도 값을 두는 이유는 아래 전수 매핑이 "이 코드는 어느 쪽으로 이끄는가"를 한곳에
 * 적어 두기 때문이다. 화면이 사진 다시 찍기나 직접 입력으로 데려가는 버튼을 붙일 때
 * 판단 기준이 이미 서 있게 된다.
 */

/**
 * 영수증 관련 서버 코드 전부.
 *
 * 목록을 따로 두는 이유는, 새 코드가 늘었을 때 매핑을 빠뜨리면 화면이 조용히 기본 문구로
 * 돌아가기 때문이다. 시험이 이 목록을 훑어 빠진 것을 잡는다.
 */
export const SETTLEMENT_RECEIPT_ERROR_CODES = [
  'SETTLEMENT-016',
  'SETTLEMENT-017',
  'SETTLEMENT-018',
  'SETTLEMENT-019',
  'SETTLEMENT-020',
  'SETTLEMENT-021',
  'SETTLEMENT-022',
  'SETTLEMENT-023',
  'SETTLEMENT-024',
  'SETTLEMENT-025',
] as const

const RECOVERY_BY_CODE: Record<string, SettlementRecovery> = {
  'SETTLEMENT-001': 'BACK_TO_LIST',
  'SETTLEMENT-002': 'REFETCH_DETAIL',
  'SETTLEMENT-003': 'BACK_TO_LIST',
  'SETTLEMENT-004': 'REFETCH_CANDIDATES',
  'SETTLEMENT-005': 'EDIT_FORM',
  'SETTLEMENT-009': 'EDIT_FORM',
  'SETTLEMENT-010': 'REFETCH_CANDIDATES',
  'SETTLEMENT-014': 'REFETCH_DETAIL',
  'SETTLEMENT-015': 'RETRY_NEW_KEY',

  /*
   * 영수증 관련 코드는 사용자가 다음에 할 일로 갈린다.
   *
   * 사진을 바꿔야 풀리는 것과, 다시 눌러 봐야 소용없어 직접 입력이 나은 것과, 그대로 다시
   * 시도할 만한 것이 섞여 있다. 한 갈래로 묶으면 화면이 셋 다 "Try again"이라고 말한다.
   */
  'SETTLEMENT-016': 'RETAKE_PHOTO',
  'SETTLEMENT-017': 'RETAKE_PHOTO',
  'SETTLEMENT-018': 'RETAKE_PHOTO',
  'SETTLEMENT-019': 'RETRY',
  'SETTLEMENT-020': 'RETAKE_PHOTO',
  'SETTLEMENT-021': 'RETAKE_PHOTO',
  'SETTLEMENT-022': 'RETAKE_PHOTO',
  'SETTLEMENT-023': 'ENTER_MANUALLY',
  'SETTLEMENT-024': 'RETRY',
  'SETTLEMENT-025': 'ENTER_MANUALLY',
}

export function resolveSettlementError(error: unknown): {
  messageKey: string
  recovery: SettlementRecovery
} {
  const code = error instanceof NormalizedApiError ? error.code : undefined
  if (code !== undefined && RECOVERY_BY_CODE[code] !== undefined) {
    return { messageKey: `settlement.errorCode.${code}`, recovery: RECOVERY_BY_CODE[code] }
  }
  return { messageKey: 'settlement.errorCode.default', recovery: 'RETRY' }
}
