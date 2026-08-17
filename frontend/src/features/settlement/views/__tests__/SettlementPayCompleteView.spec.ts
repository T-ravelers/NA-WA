import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { i18n } from '@/app/i18n'

import SettlementPayCompleteView from '../SettlementPayCompleteView.vue'

const { getDetail } = vi.hoisted(() => ({ getDetail: vi.fn() }))
vi.mock('../../api/settlementGateway', () => ({ settlementGateway: { getDetail } }))

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
    payableAmount: '0',
    requestStatus: 'PAID' as const,
    allowedActions: [],
  },
}

async function mountComplete() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/settlements/:settlementId/pay/complete',
        name: 'settlement-pay-complete',
        component: SettlementPayCompleteView,
      },
      {
        path: '/settlements/:settlementId',
        name: 'settlement-detail',
        component: { template: '<div />' },
      },
    ],
  })
  await router.push('/settlements/42/pay/complete')
  await router.isReady()
  const wrapper = mount(SettlementPayCompleteView, {
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

describe('SettlementPayCompleteView', () => {
  beforeEach(() => getDetail.mockReset().mockResolvedValue(detail))

  it('confirms the payment once the server records it', async () => {
    const { wrapper, router } = await mountComplete()

    expect(wrapper.text()).toContain('Payment sent')
    expect(router.currentRoute.value.name).toBe('settlement-pay-complete')
  })

  it('does not claim a payment the server has not recorded', async () => {
    getDetail.mockResolvedValue({
      ...detail,
      viewer: { ...detail.viewer, requestStatus: 'PENDING' as const, allowedActions: ['PAY'] },
    })
    const { router } = await mountComplete()

    expect(router.currentRoute.value.name).toBe('settlement-detail')
  })
})
