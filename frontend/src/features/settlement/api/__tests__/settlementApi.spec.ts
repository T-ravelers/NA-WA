import { beforeEach, describe, expect, it, vi } from 'vitest'

import {
  createSettlement,
  fetchSettlementCandidates,
  fetchSettlementDetail,
  fetchSettlements,
  paySettlement,
  recognizeSettlementReceipt,
} from '../settlementApi'
import { settlementReceiptOcrResponseSchema } from '../settlementResponseSchemas'

const { get, post } = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }))
vi.mock('@/shared/api/httpClient', () => ({
  httpClient: {
    get: (...args: unknown[]) => get(...args),
    post: (...args: unknown[]) => post(...args),
  },
}))

describe('settlement API contract', () => {
  beforeEach(() => {
    get.mockReset()
    post.mockReset()
  })

  it('uses only the supported collection and detail endpoints', async () => {
    get.mockResolvedValue({ data: [] })

    await fetchSettlementCandidates()
    await fetchSettlements()
    await fetchSettlementDetail('42')

    expect(get).toHaveBeenNthCalledWith(1, '/api/v1/settlements/candidates')
    expect(get).toHaveBeenNthCalledWith(2, '/api/v1/settlements')
    expect(get).toHaveBeenNthCalledWith(3, '/api/v1/settlements/42')
  })

  it('posts an ITEMIZED request unchanged with a creation idempotency key', async () => {
    post.mockResolvedValue({ data: { id: 42 } })
    const request = {
      sourceTransferId: '7',
      type: 'ITEMIZED' as const,
      participantAppointmentMemberIds: ['12', '19'],
      items: [
        {
          name: 'Dinner',
          unitPrice: '12.50',
          quantity: '2',
          allocations: [
            { appointmentMemberId: '12', quantity: '1' },
            { appointmentMemberId: '19', quantity: '1' },
          ],
        },
      ],
    }

    await expect(createSettlement('9', 'create-key', request)).resolves.toEqual({ id: '42' })
    expect(post).toHaveBeenCalledWith('/api/v1/appointments/9/settlements', request, {
      headers: { 'Idempotency-Key': 'create-key' },
    })
  })

  it('pays through the member endpoint with an independent idempotency key', async () => {
    post.mockResolvedValue({
      data: {
        settlementId: 42,
        settlementStatus: 'COMPLETED',
        transferId: 90,
        viewer: {
          role: 'PARTICIPANT',
          shareAmount: '12.50',
          payableAmount: '0',
          requestStatus: 'PAID',
          allowedActions: [],
        },
      },
    })

    await paySettlement('42', 'pay-key')

    expect(post).toHaveBeenCalledWith('/api/v1/settlements/42/members/me/pay', undefined, {
      headers: { 'Idempotency-Key': 'pay-key' },
    })
  })

  /*
   * 읽기만 하는데 POST인 이유는 부를 때마다 요금이 나가서다. GET으로 바꾸면 브라우저나 중간
   * 서버가 임의로 다시 부르거나 응답을 캐시에 담는다.
   */
  it('reads a receipt through POST and checks the response shape', async () => {
    post.mockResolvedValue({ data: { items: [], recognizedTotal: null } })

    await recognizeSettlementReceipt('31')

    expect(post).toHaveBeenCalledWith('/api/v1/settlement-receipts/31/ocr', undefined, {
      responseSchema: settlementReceiptOcrResponseSchema,
    })
  })
})
