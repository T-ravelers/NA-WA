import { describe, expect, it } from 'vitest'

import type { SettlementSummary } from '../settlement'
import {
  filterByCompletedDate,
  resolveDateRange,
  settlementCompletedDate,
} from '../settlementHistoryFilter'

function summary(id: string, createdAt: string, completedAt: string): SettlementSummary {
  return {
    id,
    title: `Dinner ${id}`,
    totalAmount: '25.00',
    receivableAmount: '12.50',
    type: 'EQUAL',
    status: 'COMPLETED',
    createdAt,
    completedAt,
    viewer: {
      role: 'PARTICIPANT',
      shareAmount: '12.50',
      payableAmount: '0',
      requestStatus: 'PAID',
      allowedActions: [],
    },
  }
}

describe('settlementCompletedDate', () => {
  it('reads a late evening completion as the server day instead of the browser day', () => {
    expect(
      settlementCompletedDate(summary('1', '2026-08-01T09:00:00', '2026-08-20T23:30:00')),
    ).toBe('2026-08-20')
  })

  it('falls back to the created time for settlements finished before the server recorded one', () => {
    expect(settlementCompletedDate(summary('1', '2026-07-04T18:00:00', ''))).toBe('2026-07-04')
  })

  it('reports no date when neither time can be read', () => {
    expect(settlementCompletedDate(summary('1', '', ''))).toBe('')
  })
})

describe('resolveDateRange', () => {
  it('has no range until the address carries one', () => {
    expect(resolveDateRange(undefined, undefined)).toBeNull()
  })

  it('keeps the chosen period', () => {
    expect(resolveDateRange('2026-08-01', '2026-08-31')).toEqual({
      from: '2026-08-01',
      to: '2026-08-31',
    })
  })

  it('reads a single chosen day as a one day period', () => {
    expect(resolveDateRange('2026-08-01', undefined)).toEqual({
      from: '2026-08-01',
      to: '2026-08-01',
    })
    expect(resolveDateRange(undefined, '2026-08-01')).toEqual({
      from: '2026-08-01',
      to: '2026-08-01',
    })
  })

  it('turns a hand edited address around instead of showing an empty period', () => {
    expect(resolveDateRange('2026-08-31', '2026-08-01')).toEqual({
      from: '2026-08-01',
      to: '2026-08-31',
    })
  })

  it('ignores values that are not a date', () => {
    expect(resolveDateRange('yesterday', '2026-13-45')).toBeNull()
    expect(resolveDateRange(['2026-08-01'], null)).toEqual({
      from: '2026-08-01',
      to: '2026-08-01',
    })
  })
})

describe('filterByCompletedDate', () => {
  const settlements = [
    summary('1', '2026-07-01T10:00:00', '2026-07-31T10:00:00'),
    summary('2', '2026-08-01T10:00:00', '2026-08-01T10:00:00'),
    summary('3', '2026-08-15T10:00:00', ''),
    summary('4', '2026-09-01T10:00:00', '2026-09-01T10:00:00'),
  ]

  it('keeps every settlement while no period is chosen', () => {
    expect(filterByCompletedDate(settlements, null)).toHaveLength(4)
  })

  it('includes both ends of the chosen period', () => {
    const kept = filterByCompletedDate(settlements, { from: '2026-07-31', to: '2026-09-01' })

    expect(kept.map((entry) => entry.id)).toEqual(['1', '2', '3', '4'])
  })

  it('drops what was completed outside the period', () => {
    const kept = filterByCompletedDate(settlements, { from: '2026-08-01', to: '2026-08-31' })

    expect(kept.map((entry) => entry.id)).toEqual(['2', '3'])
  })

  it('leaves out a settlement with no readable date, since it cannot be claimed for the period', () => {
    const kept = filterByCompletedDate([...settlements, summary('5', '', '')], {
      from: '2026-01-01',
      to: '2026-12-31',
    })

    expect(kept.map((entry) => entry.id)).toEqual(['1', '2', '3', '4'])
  })
})
