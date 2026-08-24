import { describe, expect, it } from 'vitest'

import {
  filterJourneysByStatus,
  formatJourneyDate,
  getJourneyStatus,
  isJourneyOnTrip,
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
        coverImageUrl: null,
      },
      {
        tripId: 2,
        title: 'Current journey',
        startDate: '2026-08-09',
        endDate: '2026-08-09',
        eventCount: 0,
        placeCount: 0,
        coverImageUrl: null,
      },
    ]

    expect(filterJourneysByStatus(journeys, 'past', '2026-08-09')).toEqual([journeys[0]])
    expect(filterJourneysByStatus(journeys, 'ongoing', '2026-08-09')).toEqual([journeys[1]])
  })

  it('formats API date-only values without timezone conversion', () => {
    expect(formatJourneyDate('2026-08-09', 'en')).toBe('Aug 9, 2026')
  })
})

describe('isJourneyOnTrip', () => {
  /*
   * 🔴 `getJourneyStatus`의 `ongoing`과 다르다. 그쪽은 `endDate`만 보므로 시작 전 여정도
   * `ongoing`이고, 그것을 그대로 도장 조건으로 쓰면 떠나지도 않은 여정에 `ON TRIP`이
   * 찍힌다 — #533 리뷰가 잡은 것이다.
   */
  it('is false before departure even though the tab calls it ongoing', () => {
    expect(getJourneyStatus('2026-08-30', '2026-08-25')).toBe('ongoing')
    expect(isJourneyOnTrip('2026-08-28', '2026-08-30', '2026-08-25')).toBe(false)
  })

  it('covers both boundary days', () => {
    expect(isJourneyOnTrip('2026-08-25', '2026-08-27', '2026-08-25')).toBe(true)
    expect(isJourneyOnTrip('2026-08-23', '2026-08-25', '2026-08-25')).toBe(true)
  })

  it('is false once the journey has ended', () => {
    expect(isJourneyOnTrip('2026-08-20', '2026-08-24', '2026-08-25')).toBe(false)
  })
})
