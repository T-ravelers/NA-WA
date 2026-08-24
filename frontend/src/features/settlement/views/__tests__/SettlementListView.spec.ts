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
    createdAt: '2026-08-01T19:00:00',
    completedAt: status === 'COMPLETED' ? '2026-08-02T19:00:00' : '',
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

  /*
   * 이 단언은 원래 `pb-32`(128px)를 지켰고 이름도 「고정 하단 내비게이션 아래 자리를
   * 비운다」였는데, 둘 다 사실이 아니었다(#489 실측). 정산 화면은 라우트가 전부
   * `hideBottomNav: true`라 하단 탭이 없고, 이 화면에는 `fixed`도 `sticky`도 없다.
   * 탭을 감추기 전에 쓰인 여백이 근거를 잃은 채 남아 있었다.
   */
  it('keeps the standard bottom padding — this screen has neither a tab bar nor a fixed CTA', async () => {
    getSettlements.mockResolvedValue({ received: [], sent: [] })
    const { wrapper } = await mountList()

    const classes = wrapper.get('section').classes()
    expect(classes).toContain('pb-8')
    expect(classes).not.toContain('pb-32')
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
    expect(router.currentRoute.value.query.side).toBe('received')
  })

  it('highlights the viewer share when paying and the receivable when collecting', async () => {
    getSettlements.mockResolvedValue({
      received: [summary('1', 'REQUESTED')],
      sent: [summary('2', 'REQUESTED')],
    })
    const { wrapper, router } = await mountList()
    expect(wrapper.text()).toContain('You pay')
    expect(wrapper.text()).toContain('12.50 P')

    const toCollect = wrapper.findAll('[role="radio"]').find((tab) => tab.text() === 'To Collect')
    await toCollect?.trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('You collect')
    expect(wrapper.text()).toContain('18.00 P')
    // 토글이 주소에 남아야 상세·전체 내역에서 돌아올 때 같은 쪽이 열린다.
    expect(router.currentRoute.value.query.side).toBe('sent')
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
