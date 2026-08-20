import { describe, expect, it } from 'vitest'

import { NormalizedApiError } from '@/shared/api/apiError'

import { SETTLEMENT_RECEIPT_ERROR_CODES, resolveSettlementError } from '../settlementErrors'

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

  /*
   * 영수증 코드를 하나라도 빠뜨리면 화면이 조용히 기본 문구로 돌아간다. 그러면 다시 눌러도
   * 소용없는 실패에서 "다시 시도"만 반복하게 된다.
   */
  it.each(SETTLEMENT_RECEIPT_ERROR_CODES)('guides %s instead of falling back', (code) => {
    expect(resolveSettlementError(new NormalizedApiError(code, 400, 'server message'))).toEqual({
      messageKey: `settlement.errorCode.${code}`,
      recovery: expect.any(String),
    })
  })

  /** 사진을 바꿔야 풀리는 것과 직접 입력이 나은 것이 갈려 있어야 안내가 달라진다. */
  it.each([
    ['SETTLEMENT-022', 'RETAKE_PHOTO'],
    ['SETTLEMENT-023', 'ENTER_MANUALLY'],
    ['SETTLEMENT-024', 'RETRY'],
    ['SETTLEMENT-025', 'ENTER_MANUALLY'],
  ])('sends %s down its own recovery path', (code, recovery) => {
    expect(
      resolveSettlementError(new NormalizedApiError(code, 503, 'server message')).recovery,
    ).toBe(recovery)
  })

  it('reuses the attempt for unknown failures so a retry cannot double-charge', () => {
    expect(resolveSettlementError(new Error('network down')).recovery).toBe('RETRY')
  })
})
