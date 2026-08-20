import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { i18n } from '@/app/i18n'
import { NormalizedApiError } from '@/shared/api/apiError'

import SettlementDetailView from '../SettlementDetailView.vue'

const { getDetail, getReceipt } = vi.hoisted(() => ({ getDetail: vi.fn(), getReceipt: vi.fn() }))
vi.mock('../../api/settlementGateway', () => ({ settlementGateway: { getDetail, getReceipt } }))

/** jsdom에는 미리보기 주소를 만드는 기능이 없어 대역을 둔다. */
Object.defineProperty(URL, 'createObjectURL', { value: () => 'blob:receipt', writable: true })
Object.defineProperty(URL, 'revokeObjectURL', { value: () => {}, writable: true })

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
    getReceipt.mockReset()
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

  it('fetches the receipt only when asked, then opens it', async () => {
    getReceipt.mockResolvedValue(new Blob(['x'], { type: 'image/png' }))
    const { wrapper } = await mountDetail()

    // 영수증은 최대 8MB라 상세를 열 때 미리 받지 않는다.
    expect(getReceipt).not.toHaveBeenCalled()

    await wrapper.get('[data-action="add-receipt"]').trigger('click')
    await flushPromises()

    expect(getReceipt).toHaveBeenCalledTimes(1)
    expect(wrapper.find('[role="dialog"]').exists()).toBe(true)

    // 이미 받아 둔 사진을 다시 받지 않는다.
    await wrapper.get('[data-action="add-receipt"]').trigger('click')
    await flushPromises()
    expect(getReceipt).toHaveBeenCalledTimes(1)
  })

  it('tells an expired receipt apart from one that was never attached', async () => {
    getReceipt.mockRejectedValue(new NormalizedApiError('SETTLEMENT-020', 410, 'gone'))
    const { wrapper } = await mountDetail()

    await wrapper.get('[data-action="add-receipt"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('kept for one year')
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
  })

  it('shows an empty notice when no receipt was attached', async () => {
    getReceipt.mockRejectedValue(new NormalizedApiError('SETTLEMENT-018', 404, 'missing'))
    const { wrapper } = await mountDetail()

    await wrapper.get('[data-action="add-receipt"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('No receipt was attached')
  })

  it('does not carry a receipt over to the next settlement', async () => {
    getReceipt.mockResolvedValue(new Blob(['x'], { type: 'image/png' }))
    const { wrapper, router } = await mountDetail()

    await wrapper.get('[data-action="add-receipt"]').trigger('click')
    await flushPromises()
    expect(getReceipt).toHaveBeenCalledTimes(1)

    // 화면이 그대로 붙어 있어도 다른 정산을 보는 중이면 앞 사진은 남의 것이다.
    await router.push('/settlements/43')
    await flushPromises()

    await wrapper.get('[data-action="add-receipt"]').trigger('click')
    await flushPromises()

    expect(getReceipt).toHaveBeenCalledTimes(2)
    expect(getReceipt).toHaveBeenLastCalledWith('43')
  })
})
