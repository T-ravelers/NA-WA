import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { i18n } from '@/app/i18n'

import type { SettlementStatus, SettlementSummary } from '../../model/settlement'
import SettlementListView from '../SettlementListView.vue'

const { getSettlements } = vi.hoisted(() => ({ getSettlements: vi.fn() }))
vi.mock('../../api/settlementGateway', () => ({ settlementGateway: { getSettlements } }))

function summary(id: string, status: SettlementStatus): SettlementSummary {
  return {
    id,
    title: `Dinner ${id}`,
    totalAmount: '25.00',
    receivableAmount: '18.00',
    type: 'EQUAL',
    status,
    viewer: {
      role: 'PARTICIPANT',
      shareAmount: '12.50',
      payableAmount: '12.50',
      requestStatus: status === 'COMPLETED' ? 'PAID' : 'PENDING',
      allowedActions: status === 'COMPLETED' ? [] : ['PAY'],
    },
  }
}

function createTestRouter(): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/settlements', name: 'settlements', component: SettlementListView },
      { path: '/settlements/new', name: 'settlement-new', component: { template: '<div />' } },
      {
        path: '/settlements/history',
        name: 'settlement-history',
        component: { template: '<div />' },
      },
      {
        path: '/settlements/:settlementId',
        name: 'settlement-detail',
        component: { template: '<div />' },
      },
      { path: '/wallet', name: 'wallet', component: { template: '<div />' } },
    ],
  })
}

async function mountList(path = '/settlements') {
  const router = createTestRouter()
  await router.push(path)
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
  return { wrapper, router }
}

describe('SettlementListView', () => {
  beforeEach(() => getSettlements.mockReset())

  it('reserves space for the fixed bottom navigation below the create button', async () => {
    getSettlements.mockResolvedValue({ received: [], sent: [] })
    const { wrapper } = await mountList()

    expect(wrapper.get('section').classes()).toContain('pb-32')
  })

  it('shows every ongoing settlement but only a preview of the completed ones', async () => {
    getSettlements.mockResolvedValue({
      received: [
        summary('1', 'REQUESTED'),
        summary('2', 'REQUESTED'),
        summary('3', 'COMPLETED'),
        summary('4', 'COMPLETED'),
        summary('5', 'COMPLETED'),
        summary('6', 'COMPLETED'),
      ],
      sent: [],
    })
    const { wrapper } = await mountList()

    expect(wrapper.findAll('[data-settlement-id]')).toHaveLength(5)
    expect(wrapper.find('[data-settlement-id="6"]').exists()).toBe(false)
  })

  it('opens a settlement in its detail route', async () => {
    getSettlements.mockResolvedValue({ received: [summary('42', 'REQUESTED')], sent: [] })
    const { wrapper, router } = await mountList()

    expect(wrapper.text()).toContain('12.50 P')
    await wrapper.get('[data-settlement-id="42"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('settlement-detail')
  })

  it('highlights the viewer share when paying and the receivable when collecting', async () => {
    getSettlements.mockResolvedValue({
      received: [summary('1', 'REQUESTED')],
      sent: [summary('2', 'REQUESTED')],
    })
    const { wrapper } = await mountList()
    expect(wrapper.text()).toContain('You pay')
    expect(wrapper.text()).toContain('12.50 P')

    const toCollect = wrapper.findAll('[role="radio"]').find((tab) => tab.text() === 'To Collect')
    await toCollect?.trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('You collect')
    expect(wrapper.text()).toContain('18.00 P')
  })

  it('opens the full history for the side that is currently shown', async () => {
    getSettlements.mockResolvedValue({ received: [], sent: [summary('2', 'COMPLETED')] })
    const { wrapper, router } = await mountList('/settlements?side=sent')

    await wrapper.get('[data-action="view-all"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('settlement-history')
    expect(router.currentRoute.value.query.side).toBe('sent')
  })
})
