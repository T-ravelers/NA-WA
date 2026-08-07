import { beforeEach, describe, expect, it } from 'vitest'

import {
  clearActiveJourneyId,
  parseJourneyRouteQuery,
  readActiveJourneyId,
  storeActiveJourneyId,
} from '../activeJourney'

describe('activeJourney', () => {
  beforeEach(() => {
    sessionStorage.clear()
  })

  it('stores and reads a positive journey id', () => {
    storeActiveJourneyId(12)

    expect(readActiveJourneyId()).toBe(12)
  })

  it('ignores invalid journey ids', () => {
    sessionStorage.setItem('nawa.activeJourneyId', '0')
    expect(readActiveJourneyId()).toBeNull()

    storeActiveJourneyId(-1)
    expect(readActiveJourneyId()).toBeNull()
  })

  it('clears the selected journey', () => {
    storeActiveJourneyId(12)
    clearActiveJourneyId()

    expect(readActiveJourneyId()).toBeNull()
  })

  it('normalizes a route query value', () => {
    expect(parseJourneyRouteQuery('12')).toBe(12)
    expect(parseJourneyRouteQuery(['12'])).toBe(12)
    expect(parseJourneyRouteQuery('not-a-number')).toBeNull()
  })
})
