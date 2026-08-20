import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { i18n } from '@/app/i18n'
import { NormalizedApiError } from '@/shared/api/apiError'

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

  it('does not claim a payment before the server answers', async () => {
    let answer: (value: unknown) => void = () => {}
    getDetail.mockImplementationOnce(() => new Promise((resolve) => (answer = resolve)))
    const { wrapper } = await mountComplete()

    expect(wrapper.text()).not.toContain('Payment sent')
    expect(wrapper.text()).toContain('Sending your payment')

    answer(detail)
    await flushPromises()
    expect(wrapper.text()).toContain('Payment sent')
  })

  it('does not claim a payment it could not verify', async () => {
    // 조회가 실패하면 되돌려보내는 판정이 서지 않는다. 성공을 그리면 결제된 적 없는
    // 정산에 완료 화면이 그대로 남는다.
    getDetail.mockRejectedValueOnce(new NormalizedApiError('SETTLEMENT-001', 404, 'missing'))
    const { wrapper, router } = await mountComplete()

    expect(wrapper.text()).not.toContain('Payment sent')
    expect(wrapper.find('[role="alert"]').exists()).toBe(true)
    expect(router.currentRoute.value.name).toBe('settlement-pay-complete')
  })
})
