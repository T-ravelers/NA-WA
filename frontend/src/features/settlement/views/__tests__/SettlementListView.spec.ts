import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { i18n } from '@/app/i18n'

import SettlementListView from '../SettlementListView.vue'

const { getSettlements } = vi.hoisted(() => ({ getSettlements: vi.fn() }))
vi.mock('../../api/settlementGateway', () => ({ settlementGateway: { getSettlements } }))

describe('SettlementListView', () => {
  beforeEach(() => getSettlements.mockReset())

  it('renders the server amount and opens every supported settlement in its detail route', async () => {
    getSettlements.mockResolvedValue({
      received: [
        {
          id: '42',
          title: 'Dinner',
          totalAmount: '25.00',
          receivableAmount: '12.50',
          type: 'EQUAL',
          status: 'REQUESTED',
          viewer: {
            role: 'PARTICIPANT',
            shareAmount: '12.50',
            payableAmount: '12.50',
            requestStatus: 'PENDING',
            allowedActions: ['PAY'],
          },
        },
      ],
      sent: [],
    })
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/settlements', name: 'settlements', component: SettlementListView },
        { path: '/settlements/new', name: 'settlement-new', component: { template: '<div />' } },
        {
          path: '/settlements/:settlementId',
          name: 'settlement-detail',
          component: { template: '<div />' },
        },
        { path: '/wallet', name: 'wallet', component: { template: '<div />' } },
      ],
    })
    await router.push('/settlements')
    await router.isReady()
    const wrapper = mount(SettlementListView, {
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

    expect(wrapper.text()).toContain('25.00 P')
    await wrapper.get('[data-settlement-id="42"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('settlement-detail')
  })
})
