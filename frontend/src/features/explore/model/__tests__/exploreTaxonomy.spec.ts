import { describe, expect, it } from 'vitest'

import {
  EVENT_SECTOR_OPTIONS,
  EXPLORE_SECTOR_OPTIONS,
  PLACE_SECTOR_OPTIONS,
} from '../exploreTaxonomy'

describe('shared Explore taxonomy', () => {
  it('keeps the operational_v9 sector and activity IDs contiguous', () => {
    expect(EXPLORE_SECTOR_OPTIONS.map((sector) => sector.id)).toEqual([1, 2, 3, 4])

    const activityIds = EXPLORE_SECTOR_OPTIONS.flatMap((sector) =>
      sector.activities.map((activity) => activity.id),
    )

    expect(activityIds).toEqual(Array.from({ length: 56 }, (_, index) => index + 1))
  })

  it('uses the same taxonomy for Event and Place filters', () => {
    expect(EVENT_SECTOR_OPTIONS).toBe(EXPLORE_SECTOR_OPTIONS)
    expect(PLACE_SECTOR_OPTIONS).toBe(EXPLORE_SECTOR_OPTIONS)
  })
})
