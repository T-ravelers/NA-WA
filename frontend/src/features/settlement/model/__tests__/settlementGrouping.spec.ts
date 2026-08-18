import { describe, expect, it } from 'vitest'

import type { SettlementCandidate } from '../settlement'
import { groupCandidates, resolveJourneyKey, UNASSIGNED_JOURNEY_KEY } from '../settlementGrouping'

function candidate(overrides: Partial<SettlementCandidate> = {}): SettlementCandidate {
  return {
    transferId: '7',
    appointmentId: '9',
    payerAppointmentMemberId: '12',
    journeyName: 'Seoul',
    gatheringName: 'Dinner',
    merchantName: 'Dinner',
    amount: '25.00',
    paidAt: 'Aug 12, 2026, 7:30 PM',
    payerName: 'Alex',
    participants: [{ id: '12', name: 'Alex', initials: 'AL' }],
    ...overrides,
  }
}

describe('settlementGrouping', () => {
  it('groups payments by journey and appointment while keeping the server order', () => {
    const groups = groupCandidates([
      candidate({ transferId: '1', appointmentId: '9', gatheringName: 'Dinner' }),
      candidate({ transferId: '2', appointmentId: '9', gatheringName: 'Dinner' }),
      candidate({ transferId: '3', appointmentId: '10', gatheringName: 'Cafe' }),
      candidate({ transferId: '4', journeyName: 'Busan', appointmentId: '11' }),
    ])

    expect(groups.map((group) => group.key)).toEqual(['Seoul', 'Busan'])
    expect(groups[0]?.paymentCount).toBe(3)
    expect(groups[0]?.appointments.map((entry) => entry.appointmentId)).toEqual(['9', '10'])
    expect(groups[0]?.appointments[0]?.candidates.map((entry) => entry.transferId)).toEqual([
      '1',
      '2',
    ])
  })

  it('collects payments without a journey into their own bucket', () => {
    expect(resolveJourneyKey(candidate({ journeyName: '' }))).toBe(UNASSIGNED_JOURNEY_KEY)
    expect(resolveJourneyKey(candidate({ journeyName: '   ' }))).toBe(UNASSIGNED_JOURNEY_KEY)

    const groups = groupCandidates([candidate({ journeyName: '' })])

    expect(groups[0]?.key).toBe(UNASSIGNED_JOURNEY_KEY)
    expect(groups[0]?.journeyName).toBe('')
  })

  it('keys a journey by its name until the server exposes a journey id', () => {
    expect(resolveJourneyKey(candidate({ journeyName: 'Seoul' }))).toBe('Seoul')
  })
})
