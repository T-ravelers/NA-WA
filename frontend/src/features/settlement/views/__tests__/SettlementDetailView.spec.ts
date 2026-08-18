import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { i18n } from '@/app/i18n'

import SettlementDetailView from '../SettlementDetailView.vue'

const { getDetail } = vi.hoisted(() => ({ getDetail: vi.fn() }))
vi.mock('../../api/settlementGateway', () => ({ settlementGateway: { getDetail } }))

const detail = {
  id: '42',
  type: 'ITEMIZED' as const,
  totalAmount: '25.00',
  status: 'REQUESTED' as const,
  requestedBy: 'Alex',
  gatheringName: 'Dinner',
  merchantName: 'Dinner',
  paidBy: 'Alex',
  transactionId: undefined,
  viewerItems: [{ id: '1', name: 'Pasta', allocatedQuantity: '1', allocatedAmount: '12.50' }],
  viewer: {
    role: 'PARTICIPANT' as const,
    shareAmount: '12.50',
    payableAmount: '12.50',
    requestStatus: 'PENDING' as const,
    allowedActions: ['PAY'],
  },
}

async function mountDetail(path = '/settlements/42') {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/settlements/:settlementId',
        name: 'settlement-detail',
        component: SettlementDetailView,
      },
      {
        path: '/settlements/:settlementId/pay',
        name: 'settlement-pay',
        component: { template: '<div />' },
      },
      { path: '/settlements', name: 'settlements', component: { template: '<div />' } },
    ],
  })
  await router.push(path)
  await router.isReady()
  const wrapper = mount(SettlementDetailView, {
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

describe('SettlementDetailView', () => {
  beforeEach(() => {
    getDetail.mockReset().mockResolvedValue(detail)
  })

  it('shows the participant who to send to, how much, and the amount on the pay button', async () => {
    const { wrapper } = await mountDetail()

    expect(wrapper.text()).toContain('Send to')
    expect(wrapper.text()).toContain('Alex')
    expect(wrapper.text()).toContain('Pasta')
    expect(wrapper.get('[data-action="pay"]').text()).toBe('Pay 12.50 P')
  })

  it('starts the payment in its own route instead of paying from the detail screen', async () => {
    const { wrapper, router } = await mountDetail()

    await wrapper.get('[data-action="pay"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('settlement-pay')
    expect(router.currentRoute.value.params.settlementId).toBe('42')
    // 결제 뒤 되돌아온 상세가 스택에 두 번 쌓이지 않도록 대체하며 들어간다.
    expect(router.options.history.state.confirmed).toBe(true)
  })

  it('does not infer a payment action from a positive payable amount', async () => {
    getDetail.mockResolvedValue({
      ...detail,
      viewer: { ...detail.viewer, requestStatus: 'PAID' as const, allowedActions: [] },
    })
    const { wrapper } = await mountDetail()

    expect(wrapper.find('[data-action="pay"]').exists()).toBe(false)
    expect(wrapper.get('[data-action="pay-completed"]').attributes('disabled')).toBeDefined()
  })

  it('shows what is still payable rather than repeating the full share after paying', async () => {
    getDetail.mockResolvedValue({
      ...detail,
      viewer: {
        ...detail.viewer,
        payableAmount: '0',
        requestStatus: 'PAID' as const,
        allowedActions: [],
      },
    })
    const { wrapper } = await mountDetail()

    // 결제를 마친 참여자에게 전액을 보내라고 말하면 "Pay completed" 버튼과 어긋난다.
    expect(wrapper.text()).toContain('Payable now')
    expect(wrapper.text()).toContain('0 P')
    expect(wrapper.text()).toContain('Your share')
  })

  it('keeps the itemized breakdown for the creator too', async () => {
    getDetail.mockResolvedValue({
      ...detail,
      viewer: {
        ...detail.viewer,
        role: 'CREATOR' as const,
        requestStatus: 'NOT_REQUESTED' as const,
        allowedActions: [],
      },
    })
    const { wrapper } = await mountDetail()

    // 서버는 생성자에게도 `viewerItems`를 내려준다. 역할로 가리지 않는다.
    expect(wrapper.text()).toContain('Pasta')
  })

  it('returns to the side of the list it was opened from', async () => {
    const { wrapper, router } = await mountDetail('/settlements/42?side=sent')

    await wrapper.get('header button').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('settlements')
    expect(router.currentRoute.value.query.side).toBe('sent')
  })

  it('shows the creator the participant status placeholder and no payment action', async () => {
    getDetail.mockResolvedValue({
      ...detail,
      viewer: {
        ...detail.viewer,
        role: 'CREATOR' as const,
        requestStatus: 'NOT_REQUESTED' as const,
        allowedActions: [],
      },
    })
    const { wrapper } = await mountDetail()

    expect(wrapper.find('[data-action="pay"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="participant-status-placeholder"]').exists()).toBe(true)
    expect(wrapper.text()).not.toContain('Send to')
  })
})
