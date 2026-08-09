import { describe, expect, it } from 'vitest'

import { EVENT_SECTOR_OPTIONS, PLACE_SECTOR_OPTIONS } from '../exploreTaxonomy'

describe('explore taxonomy', () => {
  it('keeps the existing Event sector and activity IDs stable', () => {
    expect(EVENT_SECTOR_OPTIONS.map((sector) => sector.id)).toEqual([1, 2, 3, 4])
    expect(EVENT_SECTOR_OPTIONS[0]?.activities.map((activity) => activity.id)).toEqual([
      101, 102, 103, 104, 105, 106,
    ])
  })

  it('uses a separate Place activity ID range', () => {
    const eventActivityIds = new Set(
      EVENT_SECTOR_OPTIONS.flatMap((sector) => sector.activities).map((activity) => activity.id),
    )
    const placeActivityIds = PLACE_SECTOR_OPTIONS.flatMap((sector) => sector.activities).map(
      (activity) => activity.id,
    )

    expect(PLACE_SECTOR_OPTIONS[0]?.activities[0]?.id).toBe(9)
    expect(placeActivityIds.every((id) => !eventActivityIds.has(id))).toBe(true)
  })
})
