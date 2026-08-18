import { NormalizedApiError } from '@/shared/api/apiError'

/**
 * `RETRY`는 같은 멱등성 키로 다시 보내 중복 처리를 막는다. 키 자체가 거부된 경우에만
 * `RETRY_NEW_KEY`로 키를 버리고 다시 만든다. 둘을 섞으면 네트워크 실패 재시도까지 새 키로
 * 나가 중복 이체를 막지 못한다.
 */
export type SettlementRecovery =
  'BACK_TO_LIST' | 'REFETCH_CANDIDATES' | 'REFETCH_DETAIL' | 'EDIT_FORM' | 'RETRY' | 'RETRY_NEW_KEY'

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
