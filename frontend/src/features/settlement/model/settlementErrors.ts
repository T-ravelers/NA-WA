import { NormalizedApiError } from '@/shared/api/apiError'

export type SettlementRecovery =
  'BACK_TO_LIST' | 'REFETCH_CANDIDATES' | 'REFETCH_DETAIL' | 'EDIT_FORM' | 'RETRY'

const RECOVERY_BY_CODE: Record<string, SettlementRecovery> = {
  'SETTLEMENT-001': 'BACK_TO_LIST',
  'SETTLEMENT-002': 'REFETCH_DETAIL',
  'SETTLEMENT-003': 'BACK_TO_LIST',
  'SETTLEMENT-004': 'REFETCH_CANDIDATES',
  'SETTLEMENT-005': 'EDIT_FORM',
  'SETTLEMENT-009': 'EDIT_FORM',
  'SETTLEMENT-010': 'REFETCH_CANDIDATES',
  'SETTLEMENT-014': 'REFETCH_DETAIL',
  'SETTLEMENT-015': 'RETRY',
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
