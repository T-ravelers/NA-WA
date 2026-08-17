import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { i18n } from '@/app/i18n'
import { NormalizedApiError } from '@/shared/api/apiError'

import SettlementPayView from '../SettlementPayView.vue'

const { getDetail, pay } = vi.hoisted(() => ({ getDetail: vi.fn(), pay: vi.fn() }))
vi.mock('../../api/settlementGateway', () => ({ settlementGateway: { getDetail, pay } }))

const detail = {
  id: '42',
  type: 'EQUAL' as const,
  totalAmount: '25.00',
  status: 'REQUESTED' as const,
  requestedBy: 'Alex',
  gatheringName: 'Dinner',
  merchantName: 'Dinner',
  paidBy: 'Alex',
  transactionId: undefined,
  viewerItems: [],
  viewer: {
    role: 'PARTICIPANT' as const,
    shareAmount: '12.50',
    payableAmount: '12.50',
    requestStatus: 'PENDING' as const,
    allowedActions: ['PAY'],
  },
}

async function mountPay() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/settlements/:settlementId/pay',
        name: 'settlement-pay',
        component: SettlementPayView,
      },
      {
        path: '/settlements/:settlementId/pay/complete',
        name: 'settlement-pay-complete',
        component: { template: '<div />' },
      },
      {
        path: '/settlements/:settlementId',
        name: 'settlement-detail',
        component: { template: '<div />' },
      },
      { path: '/settlements', name: 'settlements', component: { template: '<div />' } },
    ],
  })
  await router.push('/settlements/42/pay')
  await router.isReady()
  const wrapper = mount(SettlementPayView, {
    global: {
      plugins: [
        i18n,
        router,
        [
          VueQueryPlugin,
          { queryClient: new QueryClient({ defaultOptions: { queries: { retry: false } } }) },
        ],
      ],
    },
  })
  await flushPromises()
  return { wrapper, router }
}

describe('SettlementPayView', () => {
  beforeEach(() => {
    getDetail.mockReset().mockResolvedValue(detail)
    pay.mockReset().mockResolvedValue({})
    sessionStorage.clear()
  })

  it('sends the payment on arrival and moves on to the completion screen', async () => {
    const { router } = await mountPay()

    expect(pay).toHaveBeenCalledWith('42', expect.any(String))
    expect(router.currentRoute.value.name).toBe('settlement-pay-complete')
  })

  it('never pays when the server does not grant the action', async () => {
    getDetail.mockResolvedValue({
      ...detail,
      viewer: { ...detail.viewer, requestStatus: 'PAID' as const, allowedActions: [] },
    })
    const { router } = await mountPay()

    expect(pay).not.toHaveBeenCalled()
    expect(router.currentRoute.value.name).toBe('settlement-detail')
  })

  it('returns to the detail screen instead of retrying an already-processed payment', async () => {
    pay.mockRejectedValue(new NormalizedApiError('SETTLEMENT-014', 409, 'already paid'))
    const { wrapper, router } = await mountPay()

    await wrapper.get('[role="alert"] button').trigger('click')
    await flushPromises()

    expect(pay).toHaveBeenCalledTimes(1)
    expect(router.currentRoute.value.name).toBe('settlement-detail')
  })
})
