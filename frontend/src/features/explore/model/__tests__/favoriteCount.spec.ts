import { describe, expect, it } from 'vitest'

import { formatFavoriteCount } from '../favoriteCount'

describe('formatFavoriteCount', () => {
  it.each([
    [0, ''],
    [7, '7'],
    [999, '999'],
    [1_000, '1K'],
    [1_234, '1.2K'],
    // 반올림하면 2K가 된다. 실제보다 많다고 적지 않도록 버린다.
    [1_950, '1.9K'],
    [12_500, '12.5K'],
    [999_999, '999.9K'],
    [1_250_000, '1.2M'],
  ])('writes %i as "%s"', (count, expected) => {
    expect(formatFavoriteCount(count)).toBe(expected)
  })

  it('hides a missing count', () => {
    expect(formatFavoriteCount(undefined)).toBe('')
    expect(formatFavoriteCount(null)).toBe('')
  })
})
