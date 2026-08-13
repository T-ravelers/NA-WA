import { describe, expect, it } from 'vitest'

import {
  filterJourneysByStatus,
  formatJourneyDate,
  getJourneyStatus,
  getKoreaToday,
  getMillisecondsUntilKoreaMidnight,
} from '../journeyStatus'

describe('journeyStatus', () => {
  it('uses Korea local date when determining today', () => {
    expect(getKoreaToday(new Date('2026-08-09T14:59:59.000Z'))).toBe('2026-08-09')
    expect(getKoreaToday(new Date('2026-08-09T15:00:00.000Z'))).toBe('2026-08-10')
  })

  it('marks only dates before today as past', () => {
    expect(getJourneyStatus('2026-08-08', '2026-08-09')).toBe('past')
    expect(getJourneyStatus('2026-08-09', '2026-08-09')).toBe('ongoing')
    expect(getJourneyStatus('2026-08-10', '2026-08-09')).toBe('ongoing')
  })

  it('calculates the next Korea midnight from UTC time', () => {
    expect(getMillisecondsUntilKoreaMidnight(new Date('2026-08-09T14:59:59.000Z'))).toBe(1_000)
  })

  it('filters journeys into the selected tab', () => {
    const journeys = [
      {
        tripId: 1,
        title: 'Past journey',
        startDate: '2026-08-01',
        endDate: '2026-08-08',
        eventCount: 2,
        placeCount: 1,
      },
      {
        tripId: 2,
        title: 'Current journey',
        startDate: '2026-08-09',
        endDate: '2026-08-09',
        eventCount: 0,
        placeCount: 0,
      },
    ]

    expect(filterJourneysByStatus(journeys, 'past', '2026-08-09')).toEqual([journeys[0]])
    expect(filterJourneysByStatus(journeys, 'ongoing', '2026-08-09')).toEqual([journeys[1]])
  })

  it('formats API date-only values without timezone conversion', () => {
    expect(formatJourneyDate('2026-08-09', 'en')).toBe('Aug 9, 2026')
  })
})
