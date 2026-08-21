import { describe, expect, it } from 'vitest'

import type { ReportSummary } from '../../api/reportApi'
import { reportKeys } from '../reportKeys'
import {
  buildReportJourneyOptions,
  formatPercentage,
  formatReportAmount,
  getKoreaToday,
  isZeroAmount,
  parsePositiveRouteId,
} from '../reportModel'

const report: ReportSummary = {
  reportId: 100,
  tripId: 7,
  title: 'Busan Weekender',
  startDate: '2020-08-10',
  endDate: '2020-08-12',
  generationStatus: 'COMPLETED',
  locale: 'en',
  generatedAt: '2020-08-13T10:00:00',
  createdAt: '2020-08-13T10:00:00',
}

describe('reportModel', () => {
  it('joins reports to ended journeys and keeps deterministic newest-first order', () => {
    const options = buildReportJourneyOptions(
      [
        {
          tripId: 42,
          title: 'Future',
          startDate: '2098-08-10',
          endDate: '2098-08-12',
          eventCount: 1,
          placeCount: 2,
        },
        {
          tripId: 7,
          title: 'Busan Weekender',
          startDate: '2020-08-10',
          endDate: '2020-08-12',
          eventCount: 3,
          placeCount: 4,
        },
        {
          tripId: 9,
          title: 'Jeju Island',
          startDate: '2021-07-18',
          endDate: '2021-07-27',
          eventCount: 5,
          placeCount: 6,
        },
      ],
      [report],
      '2026-08-11',
    )

    expect(options.map(({ tripId }) => tripId)).toEqual([9, 7])
    expect(options[0]?.report).toBeNull()
    expect(options[1]?.report?.reportId).toBe(100)
  })

  it('uses Korea calendar dates at the UTC boundary', () => {
    expect(getKoreaToday(new Date('2026-08-10T15:00:00.000Z'))).toBe('2026-08-11')
  })

  it('parses only safe positive integer route ids', () => {
    expect(parsePositiveRouteId('100')).toBe(100)
    expect(parsePositiveRouteId('0')).toBeNull()
    expect(parsePositiveRouteId('-1')).toBeNull()
    expect(parsePositiveRouteId('1.5')).toBeNull()
    expect(parsePositiveRouteId(['1'])).toBeNull()
    expect(parsePositiveRouteId('9007199254740993')).toBeNull()
  })

  it('formats decimal strings without binary floating-point conversion', () => {
    expect(formatReportAmount('1284500.0000')).toBe('1,284,500 P')
    expect(formatReportAmount('10.2500')).toBe('10.25 P')
    expect(formatPercentage('42.00')).toBe('42%')
    expect(formatPercentage('12.50')).toBe('12.5%')
    expect(isZeroAmount('0.0000')).toBe(true)
    expect(isZeroAmount('0.0100')).toBe(false)
  })

  it('builds feature-owned query keys', () => {
    expect(reportKeys.candidates(7)).toEqual(['reports', 'candidates', 7])
    expect(reportKeys.detail(100)).toEqual(['reports', 'detail', 100])
  })
})
