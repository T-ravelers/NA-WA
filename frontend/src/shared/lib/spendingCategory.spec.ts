import { describe, expect, it } from 'vitest'

import {
  SPENDING_CATEGORIES,
  isSpendingCategory,
  spendingCategoryLabelKey,
  toSpendingCategory,
} from './spendingCategory'

describe('spendingCategory', () => {
  it('keeps every allowed value', () => {
    SPENDING_CATEGORIES.forEach((category) => {
      expect(toSpendingCategory(category)).toBe(category)
    })
  })

  it('normalizes case and whitespace', () => {
    expect(toSpendingCategory('food')).toBe('FOOD')
    expect(toSpendingCategory('  Food  ')).toBe('FOOD')
  })

  // 이 기능 이전에 만들어진 거래는 컬럼이 비어 있다. 화면에 코드가 날것으로 보이는 것보다
  // OTHER로 접는 편이 낫다.
  it('folds unknown and missing values into OTHER', () => {
    expect(toSpendingCategory(null)).toBe('OTHER')
    expect(toSpendingCategory(undefined)).toBe('OTHER')
    expect(toSpendingCategory('')).toBe('OTHER')
    expect(toSpendingCategory('CAFE')).toBe('OTHER')
  })

  it('narrows only allowed values', () => {
    expect(isSpendingCategory('FOOD')).toBe(true)
    expect(isSpendingCategory('food')).toBe(false)
    expect(isSpendingCategory('CAFE')).toBe(false)
  })

  it('builds the label key from the folded value', () => {
    expect(spendingCategoryLabelKey('TRANSPORT')).toBe('spendingCategory.TRANSPORT')
    expect(spendingCategoryLabelKey(null)).toBe('spendingCategory.OTHER')
  })
})
