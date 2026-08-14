import { describe, expect, it, vi } from 'vitest'

import { settlementGateway } from '../settlementGateway'

const { get } = vi.hoisted(() => ({ get: vi.fn() }))
vi.mock('@/shared/api/httpClient', () => ({
  httpClient: { get: (...args: unknown[]) => get(...args), post: vi.fn() },
}))

describe('settlement gateway', () => {
  it('maps candidate appointment-member data at the http boundary', async () => {
    get.mockResolvedValueOnce({
      data: [
        {
          transferId: 7,
          appointmentId: 9,
          payerAppointmentMemberId: 12,
          journeyName: 'Seoul',
          gatheringName: 'Dinner',
          merchantName: 'Cafe',
          amount: '25.00',
          paidAt: '2026-08-12T10:00:00',
          payerName: 'Alex',
          participants: [{ id: 12, name: 'Alex', initials: 'AL' }],
        },
      ],
    })

    await expect(settlementGateway.getCandidates()).resolves.toMatchObject([
      { payerAppointmentMemberId: '12' },
    ])
  })

  it('exposes only creation and payment mutations', () => {
    expect(settlementGateway).toEqual(
      expect.objectContaining({ create: expect.any(Function), pay: expect.any(Function) }),
    )
    expect(settlementGateway).not.toHaveProperty('cancel')
    expect(settlementGateway).not.toHaveProperty('request')
    expect(settlementGateway).not.toHaveProperty('analyzeReceipt')
  })
})
