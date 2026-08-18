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

/** 기본값은 상세의 Pay 버튼으로 들어온 경우다. 주소로 열린 진입은 `confirmed: false`. */
async function mountPay({ confirmed = true } = {}) {
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
  await router.push(
    confirmed
      ? { name: 'settlement-pay', params: { settlementId: '42' }, state: { confirmed: true } }
      : '/settlements/42/pay',
  )
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

  it('asks before paying when it was not opened from the split detail', async () => {
    const { wrapper } = await mountPay({ confirmed: false })

    // 공유된 링크·북마크·주소창으로 열어도 이체가 무클릭으로 나가면 안 된다.
    expect(pay).not.toHaveBeenCalled()
    expect(wrapper.get('[data-action="confirm-pay"]').text()).toBe('Pay 12.50 P')

    await wrapper.get('[data-action="confirm-pay"]').trigger('click')
    await flushPromises()

    expect(pay).toHaveBeenCalledWith('42', expect.any(String))
  })

  it('never asks to confirm a payment the server does not grant', async () => {
    getDetail.mockResolvedValue({
      ...detail,
      viewer: { ...detail.viewer, requestStatus: 'PAID' as const, allowedActions: [] },
    })
    const { wrapper, router } = await mountPay({ confirmed: false })

    expect(wrapper.find('[data-action="confirm-pay"]').exists()).toBe(false)
    expect(router.currentRoute.value.name).toBe('settlement-detail')
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

  it('retries a rejected idempotency key with a new one', async () => {
    pay.mockRejectedValueOnce(new NormalizedApiError('SETTLEMENT-015', 400, 'invalid key'))
    const { wrapper, router } = await mountPay()

    await wrapper.get('[role="alert"] button').trigger('click')
    await flushPromises()

    expect(pay).toHaveBeenCalledTimes(2)
    // 서버가 키 자체를 거부했으므로 같은 키로 다시 보내면 영원히 같은 오류가 난다.
    expect(pay.mock.calls[1]?.[1]).not.toBe(pay.mock.calls[0]?.[1])
    expect(router.currentRoute.value.name).toBe('settlement-pay-complete')
  })

  it('retries an unknown failure with the same key so it cannot double-charge', async () => {
    pay.mockRejectedValueOnce(new Error('network down'))
    const { wrapper } = await mountPay()

    await wrapper.get('[role="alert"] button').trigger('click')
    await flushPromises()

    expect(pay).toHaveBeenCalledTimes(2)
    expect(pay.mock.calls[1]?.[1]).toBe(pay.mock.calls[0]?.[1])
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
