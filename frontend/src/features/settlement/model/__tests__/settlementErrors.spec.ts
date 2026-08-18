import { describe, expect, it } from 'vitest'

import { NormalizedApiError } from '@/shared/api/apiError'

import { resolveSettlementError } from '../settlementErrors'

describe('settlement errors', () => {
  it.each([
    ['SETTLEMENT-001', 'BACK_TO_LIST'],
    ['SETTLEMENT-002', 'REFETCH_DETAIL'],
    ['SETTLEMENT-003', 'BACK_TO_LIST'],
    ['SETTLEMENT-004', 'REFETCH_CANDIDATES'],
    ['SETTLEMENT-005', 'EDIT_FORM'],
    ['SETTLEMENT-009', 'EDIT_FORM'],
    ['SETTLEMENT-010', 'REFETCH_CANDIDATES'],
    ['SETTLEMENT-014', 'REFETCH_DETAIL'],
    ['SETTLEMENT-015', 'RETRY_NEW_KEY'],
  ])('uses %s for its code-based recovery flow', (code, recovery) => {
    expect(resolveSettlementError(new NormalizedApiError(code, 409, 'server message'))).toEqual({
      messageKey: `settlement.errorCode.${code}`,
      recovery,
    })
  })

  it('reuses the attempt for unknown failures so a retry cannot double-charge', () => {
    expect(resolveSettlementError(new Error('network down')).recovery).toBe('RETRY')
  })
})
