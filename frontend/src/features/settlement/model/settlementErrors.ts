import { NormalizedApiError } from '@/shared/api/apiError'
import { resolveErrorMessageKey } from '@/shared/api/apiResponse'

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
  | 'TOP_UP'
  | 'GO_TO_WALLET'

/*
 * RETAKE_PHOTO와 ENTER_MANUALLY를 보고 동작을 바꾸는 화면은 아직 없다.
 *
 * recovery를 읽는 곳은 SettlementCreateView와 SettlementPayView 둘뿐이다. 결제 화면은
 * BACK_TO_LIST·REFETCH_DETAIL·REFETCH_CANDIDATES에 더해 TOP_UP·GO_TO_WALLET을 본다.
 *
 * 그래도 나머지 값을 두는 이유는 아래 전수 매핑이 "이 코드는 어느 쪽으로 이끄는가"를
 * 한곳에 적어 두기 때문이다. 화면이 사진 다시 찍기나 직접 입력으로 데려가는 버튼을 붙일
 * 때 판단 기준이 이미 서 있게 된다.
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
   * 지급은 지갑 이체를 타므로 정산 코드만 오는 것이 아니다.
   *
   * 이 넷이 여기 없던 동안에는 전부 기본값(RETRY)으로 떨어졌고, 결제 화면의 재시도가
   * 같은 요청을 그대로 다시 보냈다. 잔액도 지갑 상태도 그 사이에 변할 리 없으니 사용자는
   * "Try again"만 무한히 누르게 된다. 새 지갑 코드가 생기면 여기에도 적는다.
   *
   * 지갑이 없거나 잠긴 것은 **상대(원결제자) 쪽일 수도 있다** — 이체는 양쪽 지갑을
   * 확인한다. 그래서 이 둘은 "당신의 지갑" 대신 지갑 화면으로만 데려간다.
   */
  'WALLET-001': 'GO_TO_WALLET',
  'WALLET-014': 'REFETCH_DETAIL',
  'WALLET-015': 'TOP_UP',
  'WALLET-016': 'GO_TO_WALLET',

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

/**
 * 위 매핑에 적힌 코드 전부.
 *
 * 지갑 코드는 문구를 지갑 feature가 갖는다. 코드만 늘리고 그쪽 문구를 빠뜨리면 화면에 키가
 * 그대로 나오는데, 두 feature에 걸쳐 있어 눈으로는 잘 드러나지 않는다. 시험이 이 목록을
 * 훑어 잡는다 — 영수증 코드 목록을 따로 둔 것과 같은 이유다.
 */
export const SETTLEMENT_MAPPED_ERROR_CODES = Object.keys(RECOVERY_BY_CODE)

export function resolveSettlementError(error: unknown): {
  messageKey: string
  recovery: SettlementRecovery
} {
  const code = error instanceof NormalizedApiError ? error.code : undefined
  const recovery = code === undefined ? undefined : RECOVERY_BY_CODE[code]
  if (code === undefined || recovery === undefined) {
    return { messageKey: 'settlement.errorCode.default', recovery: 'RETRY' }
  }

  /*
   * 지갑 코드의 문구는 지갑이 이미 갖고 있다(`wallet.errorCode.WALLET-015` 등). 여기에
   * 다시 적으면 같은 실패가 화면에 따라 다른 말을 하게 되고, 한쪽만 고쳐지는 순간
   * 어긋난다. 코드 접두사로 키를 만드는 규칙은 공용 유틸 하나가 갖는다.
   */
  const messageKey = code.startsWith('SETTLEMENT-')
    ? `settlement.errorCode.${code}`
    : resolveErrorMessageKey(code)

  return { messageKey, recovery }
}
