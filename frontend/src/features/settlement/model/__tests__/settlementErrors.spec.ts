import { describe, expect, it } from 'vitest'

import { i18n } from '@/app/i18n'
import { NormalizedApiError } from '@/shared/api/apiError'
import { SUPPORTED_LOCALES, type AppLocale } from '@/shared/i18n/locales'

import {
  SETTLEMENT_MAPPED_ERROR_CODES,
  SETTLEMENT_RECEIPT_ERROR_CODES,
  WALLET_CODES_WITHOUT_A_KNOWN_SIDE,
  resolveSettlementError,
} from '../settlementErrors'

/**
 * "당신의"에 해당하는 말. 로케일마다 형태가 달라 하나의 정규식으로는 잡히지 않는다.
 *
 * en의 `your`는 `your wallet`뿐 아니라 `your balance`도 잡는다. 잔액은 언제나 낸 사람의
 * 것이므로 소유격이 맞고, 그래서 이 검사는 아래의 **양쪽 어디든 원인일 수 있는 코드에만**
 * 건다.
 */
const POSSESSIVE_BY_LOCALE: Record<AppLocale, RegExp> = {
  en: /\byour\b/i,
  ja: /あなたの/,
  'zh-TW': /您的|你的/,
  vi: /của bạn/i,
}

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

  /*
   * 지급은 지갑 이체를 타므로 WALLET-* 코드도 온다. 이것들이 매핑에 없으면 기본값
   * RETRY로 떨어지고, 결제 화면이 같은 요청을 다시 보내 무한 재시도가 된다(#452).
   */
  it.each([
    ['WALLET-001', 'GO_TO_WALLET'],
    ['WALLET-014', 'REFETCH_DETAIL'],
    ['WALLET-015', 'TOP_UP'],
    ['WALLET-016', 'GO_TO_WALLET'],
  ])('routes %s to its own recovery instead of retrying blindly', (code, recovery) => {
    expect(resolveSettlementError(new NormalizedApiError(code, 409, 'server message'))).toEqual({
      messageKey: `wallet.errorCode.${code}`,
      recovery,
    })
  })

  /** 잔액 부족은 다시 눌러도 잔액이 그대로다. 재시도로 분류되면 사용자가 갇힌다. */
  it('never sends an insufficient balance back through a plain retry', () => {
    expect(
      resolveSettlementError(new NormalizedApiError('WALLET-015', 409, 'server message')).recovery,
    ).not.toBe('RETRY')
  })

  /** 지갑 문구는 지갑이 갖는다. 정산 네임스페이스로 복제하면 두 곳이 어긋난다. */
  it('borrows the wallet wording instead of copying it into the settlement namespace', () => {
    expect(
      resolveSettlementError(new NormalizedApiError('WALLET-015', 409, 'server message'))
        .messageKey,
    ).toBe('wallet.errorCode.WALLET-015')
  })

  /*
   * 문구가 없으면 화면에 키가 그대로 나온다. 지갑 코드는 문구를 지갑 feature가 갖고 있어
   * 코드만 늘리고 그쪽을 빠뜨리기 쉬운데, 두 feature에 걸쳐 있어 눈으로는 잘 안 보인다.
   */
  it.each(SETTLEMENT_MAPPED_ERROR_CODES)('has wording for %s wherever it lives', (code) => {
    const { messageKey } = resolveSettlementError(
      new NormalizedApiError(code, 409, 'server message'),
    )

    expect(i18n.global.te(messageKey)).toBe(true)
  })

  /*
   * 지갑이 없거나 잠긴 것은 **원결제자 쪽일 수도 있다.** 이체가 양쪽 지갑을 모두 확인하기
   * 때문이다. 문구가 "당신의 지갑"이라고 단정하면 자기 지갑이 멀쩡한 사용자가 자기 지갑
   * 화면만 들여다보며 원인을 찾지 못한다.
   *
   * 키가 있는지만 보는 위 검사로는 이 어긋남이 드러나지 않는다. 실제 문장을 로케일마다
   * 읽어 본다 — 한 로케일만 소유격으로 되돌아가도 그 언어 사용자에게만 조용히 재발한다.
   */
  describe.each(WALLET_CODES_WITHOUT_A_KNOWN_SIDE)('%s', (code) => {
    it.each(SUPPORTED_LOCALES)('does not blame the reader in %s', (locale) => {
      const { messageKey } = resolveSettlementError(
        new NormalizedApiError(code, 404, 'server message'),
      )

      // 그 로케일에 문구가 없으면 en으로 폴백해 검사가 헛돈다. 번역이 있는지 먼저 본다.
      expect(i18n.global.te(messageKey, locale)).toBe(true)
      expect(i18n.global.t(messageKey, {}, { locale })).not.toMatch(POSSESSIVE_BY_LOCALE[locale])
    })
  })

  it('reuses the attempt for unknown failures so a retry cannot double-charge', () => {
    expect(resolveSettlementError(new Error('network down')).recovery).toBe('RETRY')
  })
})
