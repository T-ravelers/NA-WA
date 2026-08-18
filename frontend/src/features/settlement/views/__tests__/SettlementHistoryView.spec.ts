import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { i18n } from '@/app/i18n'

import type { SettlementStatus, SettlementSummary } from '../../model/settlement'
import SettlementHistoryView from '../SettlementHistoryView.vue'

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
      payableAmount: '0',
      requestStatus: 'PAID',
      allowedActions: [],
    },
  }
}

async function mountHistory(path = '/settlements/history?side=sent') {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/settlements/history',
        name: 'settlement-history',
        component: SettlementHistoryView,
      },
      {
        path: '/settlements/:settlementId',
        name: 'settlement-detail',
        component: { template: '<div />' },
      },
      { path: '/settlements', name: 'settlements', component: { template: '<div />' } },
    ],
  })
  await router.push(path)
  await router.isReady()
  const wrapper = mount(SettlementHistoryView, {
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

describe('SettlementHistoryView', () => {
  beforeEach(() =>
    getSettlements.mockReset().mockResolvedValue({
      received: [summary('1', 'COMPLETED')],
      sent: [summary('2', 'COMPLETED'), summary('3', 'REQUESTED')],
    }),
  )

  it('lists only the completed settlements of the side it was opened for', async () => {
    const { wrapper } = await mountHistory()

    expect(wrapper.text()).toContain('Collected splits')
    expect(wrapper.findAll('[data-settlement-id]')).toHaveLength(1)
    expect(wrapper.find('[data-settlement-id="2"]').exists()).toBe(true)
  })

  it('returns to the side of the list it was opened from', async () => {
    const { wrapper, router } = await mountHistory()

    await wrapper.get('header button').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('settlements')
    expect(router.currentRoute.value.query.side).toBe('sent')
  })

  it('carries the side into the settlement it opens', async () => {
    const { wrapper, router } = await mountHistory()

    await wrapper.get('[data-settlement-id="2"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('settlement-detail')
    expect(router.currentRoute.value.query.side).toBe('sent')
  })
})
