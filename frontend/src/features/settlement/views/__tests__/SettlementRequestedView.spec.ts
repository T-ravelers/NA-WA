import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { i18n } from '@/app/i18n'
import { NormalizedApiError } from '@/shared/api/apiError'

import SettlementRequestedView from '../SettlementRequestedView.vue'

const { getDetail } = vi.hoisted(() => ({ getDetail: vi.fn() }))
vi.mock('../../api/settlementGateway', () => ({ settlementGateway: { getDetail } }))

const detail = {
  id: '42',
  type: 'EQUAL' as const,
  totalAmount: '25.00',
  status: 'REQUESTED' as const,
  requestedBy: 'You',
  gatheringName: 'Dinner',
  merchantName: 'Dinner',
  paidBy: 'You',
  transactionId: undefined,
  viewerItems: [],
  viewer: {
    role: 'CREATOR' as const,
    shareAmount: '12.50',
    payableAmount: '0',
    requestStatus: 'NOT_REQUESTED' as const,
    allowedActions: [],
  },
}

async function mountRequested() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/settlements/:settlementId/requested',
        name: 'settlement-requested',
        component: SettlementRequestedView,
      },
      {
        path: '/settlements/:settlementId',
        name: 'settlement-detail',
        component: { template: '<div />' },
      },
      { path: '/settlements', name: 'settlements', component: { template: '<div />' } },
    ],
  })
  await router.push('/settlements/42/requested')
  await router.isReady()
  const wrapper = mount(SettlementRequestedView, {
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

describe('SettlementRequestedView', () => {
  beforeEach(() => getDetail.mockReset().mockResolvedValue(detail))

  it('confirms the request once the server records the viewer as its creator', async () => {
    const { wrapper, router } = await mountRequested()

    expect(wrapper.text()).toContain('Request sent')
    expect(router.currentRoute.value.name).toBe('settlement-requested')
  })

  it('sends the viewer to the collect side of the list', async () => {
    const { wrapper, router } = await mountRequested()

    await wrapper.get('[data-action="status-action"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('settlements')
    expect(router.currentRoute.value.query.side).toBe('sent')
  })

  it('does not claim a request the viewer did not send', async () => {
    getDetail.mockResolvedValue({ ...detail, viewer: { ...detail.viewer, role: 'PARTICIPANT' } })
    const { router } = await mountRequested()

    expect(router.currentRoute.value.name).toBe('settlement-detail')
  })

  it('does not claim a request it could not verify', async () => {
    getDetail.mockRejectedValueOnce(new NormalizedApiError('SETTLEMENT-001', 404, 'missing'))
    const { wrapper, router } = await mountRequested()

    expect(wrapper.text()).not.toContain('Request sent')
    expect(wrapper.find('[role="alert"]').exists()).toBe(true)
    expect(router.currentRoute.value.name).toBe('settlement-requested')
  })
})
