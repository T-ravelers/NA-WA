import { describe, expect, it } from 'vitest'

import {
  MAX_QUANTITY,
  calculateTotal,
  createEmptyItem,
  decreaseQuantity,
  increaseQuantity,
  isValidTotal,
  itemSubtotal,
  parseAmount,
  parseQuantity,
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

  /** 한 개를 파는 경우가 가장 흔하다. 단가만 넣어도 합계가 서야 한다. */
  it('starts at one', () => {
    expect(createEmptyItem().quantity).toBe(1)
    expect(createEmptyItem().unitPrice).toBeNull()
  })
})

describe('increaseQuantity', () => {
  it('adds one', () => {
    expect(increaseQuantity(2)).toBe(3)
  })

  it('treats an empty quantity as zero', () => {
    expect(increaseQuantity(null)).toBe(1)
  })

  it('stops at the maximum', () => {
    expect(increaseQuantity(MAX_QUANTITY)).toBe(MAX_QUANTITY)
  })
})

describe('decreaseQuantity', () => {
  it('subtracts one', () => {
    expect(decreaseQuantity(2)).toBe(1)
  })

  /** 줄을 지우는 것과 수량을 0으로 두는 것은 다르다. */
  it('stops at zero', () => {
    expect(decreaseQuantity(0)).toBe(0)
    expect(decreaseQuantity(null)).toBe(0)
  })
})

describe('parseAmount', () => {
  it('keeps digits only', () => {
    expect(parseAmount('4,500')).toBe(4500)
    expect(parseAmount('₩ 12000')).toBe(12000)
  })

  it('is null when empty', () => {
    expect(parseAmount('')).toBeNull()
  })

  /** 단가는 수량 상한(9999)에 묶이지 않는다. */
  it('is not capped at the quantity maximum', () => {
    expect(parseAmount('50000')).toBe(50000)
  })
})

describe('parseQuantity', () => {
  it('keeps digits only', () => {
    expect(parseQuantity('12개')).toBe(12)
    expect(parseQuantity('3.5')).toBe(35)
  })

  it('is null when empty', () => {
    expect(parseQuantity('')).toBeNull()
    expect(parseQuantity('abc')).toBeNull()
  })

  it('caps at the maximum', () => {
    expect(parseQuantity('999999')).toBe(MAX_QUANTITY)
  })
})
