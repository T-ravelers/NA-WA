import { describe, expect, it } from 'vitest'

import {
  calculateTotal,
  createEmptyItem,
  isValidTotal,
  itemSubtotal,
  type MerchantQrItem,
} from '../merchantQr'

function item(partial: Partial<MerchantQrItem>): MerchantQrItem {
  return { ...createEmptyItem(), ...partial }
}

describe('itemSubtotal', () => {
  it('multiplies quantity by unit price', () => {
    expect(itemSubtotal(item({ quantity: 3, unitPrice: 4500 }))).toBe(13500)
  })

  /** 입력이 진행 중인 줄이 합계를 흔들지 않아야 한다. */
  it('treats an incomplete row as zero', () => {
    expect(itemSubtotal(item({ quantity: null, unitPrice: 4500 }))).toBe(0)
    expect(itemSubtotal(item({ quantity: 2, unitPrice: null }))).toBe(0)
    expect(itemSubtotal(item({ quantity: 0, unitPrice: 4500 }))).toBe(0)
  })

  it('ignores negative input', () => {
    expect(itemSubtotal(item({ quantity: -1, unitPrice: 4500 }))).toBe(0)
    expect(itemSubtotal(item({ quantity: 2, unitPrice: -100 }))).toBe(0)
  })
})

describe('calculateTotal', () => {
  it('sums every complete row', () => {
    expect(
      calculateTotal([
        item({ quantity: 2, unitPrice: 4500 }),
        item({ quantity: 1, unitPrice: 3000 }),
      ]),
    ).toBe(12000)
  })

  it('is zero when nothing is entered', () => {
    expect(calculateTotal([createEmptyItem()])).toBe(0)
    expect(calculateTotal([])).toBe(0)
  })
})

describe('isValidTotal', () => {
  it('requires a positive amount', () => {
    expect(isValidTotal(0)).toBe(false)
    expect(isValidTotal(-1)).toBe(false)
    expect(isValidTotal(1)).toBe(true)
  })

  /** 백엔드가 정수부 15자리를 넘는 금액을 거절한다. */
  it('rejects an amount the server would refuse', () => {
    expect(isValidTotal(10 ** 15 - 1)).toBe(true)
    expect(isValidTotal(10 ** 15)).toBe(false)
  })
})

describe('createEmptyItem', () => {
  it('gives each row a distinct key', () => {
    expect(createEmptyItem().id).not.toBe(createEmptyItem().id)
  })
})
