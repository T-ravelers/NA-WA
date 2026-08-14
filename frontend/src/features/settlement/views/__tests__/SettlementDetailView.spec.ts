import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { i18n } from '@/app/i18n'
import { NormalizedApiError } from '@/shared/api/apiError'

import SettlementDetailView from '../SettlementDetailView.vue'

const { getDetail, pay } = vi.hoisted(() => ({ getDetail: vi.fn(), pay: vi.fn() }))
vi.mock('../../api/settlementGateway', () => ({ settlementGateway: { getDetail, pay } }))

const detail = {
  id: '42',
  type: 'ITEMIZED' as const,
  totalAmount: '25.00',
  status: 'REQUESTED' as const,
  requestedBy: 'Alex',
  gatheringName: 'Dinner',
  merchantName: 'Cafe',
  paidBy: 'Alex',
  transactionId: undefined,
  viewerItems: [{ id: '1', name: 'Dinner', allocatedQuantity: '1', allocatedAmount: '12.50' }],
  viewer: {
    role: 'PARTICIPANT' as const,
    shareAmount: '12.50',
    payableAmount: '12.50',
    requestStatus: 'PENDING' as const,
    allowedActions: ['PAY'],
  },
}

async function mountDetail() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/settlements/:settlementId',
        name: 'settlement-detail',
        component: SettlementDetailView,
      },
      { path: '/settlements', name: 'settlements', component: { template: '<div />' } },
    ],
  })
  await router.push('/settlements/42')
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
  return wrapper
}

describe('SettlementDetailView', () => {
  beforeEach(() => {
    getDetail.mockReset().mockResolvedValue(detail)
    pay.mockReset().mockResolvedValue({})
  })

  it('shows payment only when the server grants PAY and renders viewer ITEMIZED data', async () => {
    const wrapper = await mountDetail()
    expect(wrapper.text()).toContain('Dinner')
    expect(wrapper.text()).toContain('12.50 P')
    expect(wrapper.get('[data-action="pay"]')).toBeTruthy()

    await wrapper.get('[data-action="pay"]').trigger('click')
    await flushPromises()
    expect(pay).toHaveBeenCalledWith('42', expect.any(String))
  })

  it('does not infer a payment action from a positive payable amount', async () => {
    getDetail.mockResolvedValue({ ...detail, viewer: { ...detail.viewer, allowedActions: [] } })
    const wrapper = await mountDetail()
    expect(wrapper.find('[data-action="pay"]').exists()).toBe(false)
  })

  it('refetches detail instead of retrying payment after an already-processed payment conflict', async () => {
    pay.mockRejectedValue(new NormalizedApiError('SETTLEMENT-014', 409, 'already paid'))
    const wrapper = await mountDetail()
    await wrapper.get('[data-action="pay"]').trigger('click')
    await flushPromises()
    await wrapper.get('[role="alert"] button').trigger('click')
    await flushPromises()

    expect(getDetail).toHaveBeenCalledTimes(2)
    expect(pay).toHaveBeenCalledTimes(1)
  })
})
