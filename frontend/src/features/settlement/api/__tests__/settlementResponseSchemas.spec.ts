import { describe, expect, it } from 'vitest'

import { settlementReceiptOcrResponseSchema } from '../settlementResponseSchemas'

describe('settlement response schemas', () => {
  it('accepts amounts as numbers or strings so either server shape passes', () => {
    expect(
      settlementReceiptOcrResponseSchema.safeParse({
        items: [{ name: 'Pasta', unitPrice: 10500, quantity: '2' }],
        recognizedTotal: '21000',
      }).success,
    ).toBe(true)
  })

  /** 영수증이 접혔거나 흐리면 이름만 읽히고 금액은 안 읽힌다. 그 줄도 화면까지 올라가야 한다. */
  it('accepts a half-read line and an unread receipt total', () => {
    expect(
      settlementReceiptOcrResponseSchema.safeParse({
        items: [{ name: 'Pasta', unitPrice: null, quantity: null }],
        recognizedTotal: null,
      }).success,
    ).toBe(true)
  })

  it('accepts a receipt with no readable line at all', () => {
    expect(
      settlementReceiptOcrResponseSchema.safeParse({ items: [], recognizedTotal: null }).success,
    ).toBe(true)
  })

  /** 서버가 필드를 더 붙여도 화면이 통째로 실패하면 안 된다. */
  it('keeps passing when the server adds fields', () => {
    expect(
      settlementReceiptOcrResponseSchema.safeParse({
        items: [{ name: 'Pasta', unitPrice: '10500', quantity: '1', lineTotal: '10500' }],
        recognizedTotal: '10500',
        engine: 'clova',
      }).success,
    ).toBe(true)
  })

  /*
   * 여기서 걸러내지 못하면 오류 대신 빈 품목 카드가 조용히 만들어진다. 사용자는 인식이
   * 실패한 것인지 영수증이 흐린 것인지 알 수 없다.
   */
  it('rejects a shape the item cards cannot read', () => {
    expect(
      settlementReceiptOcrResponseSchema.safeParse({ items: null, recognizedTotal: null }).success,
    ).toBe(false)
    expect(
      settlementReceiptOcrResponseSchema.safeParse({
        items: [{ name: 'Pasta', unitPrice: { amount: 10500 }, quantity: '1' }],
        recognizedTotal: null,
      }).success,
    ).toBe(false)
    expect(settlementReceiptOcrResponseSchema.safeParse({ items: [] }).success).toBe(false)
  })
})
