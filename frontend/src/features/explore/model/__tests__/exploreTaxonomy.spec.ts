import { describe, expect, it } from 'vitest'

import {
  EVENT_ACTIVITY_OPTIONS,
  EVENT_SECTOR_OPTIONS,
  PLACE_ACTIVITY_OPTIONS,
  PLACE_SECTOR_OPTIONS,
} from '../exploreTaxonomy'

describe('explore taxonomy', () => {
  it('uses the operational_v9 sector and activity IDs', () => {
    expect(EVENT_SECTOR_OPTIONS.map((sector) => sector.id)).toEqual([1, 2, 3, 4])
    expect(EVENT_ACTIVITY_OPTIONS.map((activity) => activity.id)).toEqual(
      Array.from({ length: 56 }, (_, index) => index + 1),
    )
    expect(EVENT_SECTOR_OPTIONS.map((sector) => sector.activities.length)).toEqual([8, 8, 21, 19])
  })

  it('reuses the Event taxonomy for Place filters', () => {
    expect(PLACE_SECTOR_OPTIONS).toBe(EVENT_SECTOR_OPTIONS)
    expect(PLACE_ACTIVITY_OPTIONS).toBe(EVENT_ACTIVITY_OPTIONS)
  })
})
