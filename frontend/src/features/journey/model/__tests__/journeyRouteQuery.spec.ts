import { describe, expect, it } from 'vitest'

import { parseJourneyRouteQuery } from '../journeyRouteQuery'

describe('parseJourneyRouteQuery', () => {
  it('normalizes a route query value', () => {
    expect(parseJourneyRouteQuery('12')).toBe(12)
    expect(parseJourneyRouteQuery(['12'])).toBe(12)
  })

  it('rejects values that are not a positive journey id', () => {
    expect(parseJourneyRouteQuery('not-a-number')).toBeNull()
    expect(parseJourneyRouteQuery('0')).toBeNull()
    expect(parseJourneyRouteQuery('-1')).toBeNull()
    expect(parseJourneyRouteQuery(undefined)).toBeNull()
  })
})
