import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { i18n } from '@/app/i18n'

import type { SettlementStatus, SettlementSummary } from '../../model/settlement'
import SettlementHistoryView from '../SettlementHistoryView.vue'

const { getSettlements } = vi.hoisted(() => ({ getSettlements: vi.fn() }))
vi.mock('../../api/settlementGateway', () => ({ settlementGateway: { getSettlements } }))

function summary(
  id: string,
  status: SettlementStatus,
  completedAt = '2026-08-12T19:00:00',
): SettlementSummary {
  return {
    id,
    title: `Dinner ${id}`,
    totalAmount: '25.00',
    receivableAmount: '18.00',
    type: 'EQUAL',
    status,
    createdAt: '2026-08-12T18:00:00',
    completedAt: status === 'COMPLETED' ? completedAt : '',
    viewer: {
      role: 'PARTICIPANT',
      shareAmount: '12.50',
      payableAmount: '0',
      requestStatus: 'PAID',
      allowedActions: [],
    },
  }
}

/** 시트에서 글자로 버튼을 찾는다. 자리로 찾으면 시트에 줄 하나만 늘어도 테스트가 깨진다. */
async function clickByText(wrapper: VueWrapper, label: string): Promise<void> {
  const button = wrapper.findAll('[role="dialog"] button').find((entry) => entry.text() === label)

  await button?.trigger('click')
  await flushPromises()
}

/** 달력에서 이번 달 날짜만 고른다. 앞뒤 달을 채운 칸은 눌러도 아무 일이 없다. */
async function pickDays(wrapper: VueWrapper, ...indexes: number[]): Promise<void> {
  const days = wrapper
    .findAll('[role="dialog"] .grid-cols-7 button')
    .filter((day) => day.attributes('disabled') === undefined)

  for (const index of indexes) {
    await days[index]?.trigger('click')
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

  it('shows the day each settlement was completed', async () => {
    const { wrapper } = await mountHistory()

    expect(wrapper.get('[data-settlement-id="2"]').text()).toContain('Aug 12, 2026')
  })

  it('keeps only the settlements completed inside the period in the address', async () => {
    getSettlements.mockResolvedValue({
      received: [],
      sent: [
        summary('2', 'COMPLETED', '2026-08-12T19:00:00'),
        summary('4', 'COMPLETED', '2026-09-20T19:00:00'),
      ],
    })

    const { wrapper } = await mountHistory(
      '/settlements/history?side=sent&from=2026-08-01&to=2026-08-31',
    )

    expect(wrapper.findAll('[data-settlement-id]')).toHaveLength(1)
    expect(wrapper.find('[data-settlement-id="2"]').exists()).toBe(true)
    expect(wrapper.get('[data-testid="period-filter"]').text()).toContain(
      'Aug 1, 2026 – Aug 31, 2026',
    )
  })

  it('writes the chosen period into the address so it survives a reload', async () => {
    const { wrapper, router } = await mountHistory()

    await wrapper.get('[data-testid="period-filter"]').trigger('click')
    await flushPromises()
    await pickDays(wrapper, 4, 9)
    await clickByText(wrapper, 'Apply')

    expect(router.currentRoute.value.query.from).toMatch(/^\d{4}-\d{2}-\d{2}$/)
    expect(router.currentRoute.value.query.to).toMatch(/^\d{4}-\d{2}-\d{2}$/)
    expect(router.currentRoute.value.query.side).toBe('sent')
  })

  it('opens the calendar on the period already chosen so it can be narrowed', async () => {
    const { wrapper, router } = await mountHistory(
      '/settlements/history?side=sent&from=2026-08-01&to=2026-08-31',
    )

    await wrapper.get('[data-testid="period-filter"]').trigger('click')
    await flushPromises()
    await pickDays(wrapper, 4, 9)
    await clickByText(wrapper, 'Apply')

    expect(router.currentRoute.value.query.from).toBe('2026-08-05')
    expect(router.currentRoute.value.query.to).toBe('2026-08-10')
  })

  it('drops the period from the address when the viewer asks for every date again', async () => {
    const { wrapper, router } = await mountHistory(
      '/settlements/history?side=sent&from=2026-08-01&to=2026-08-31',
    )

    await wrapper.get('[data-testid="period-filter"]').trigger('click')
    await flushPromises()
    await clickByText(wrapper, 'Any date')

    expect(router.currentRoute.value.query.from).toBeUndefined()
    expect(router.currentRoute.value.query.to).toBeUndefined()
    expect(wrapper.findAll('[data-settlement-id]')).toHaveLength(1)
  })

  it('drops the period straight from the list when the chip beside the filter is tapped', async () => {
    const { wrapper, router } = await mountHistory(
      '/settlements/history?side=sent&from=2026-01-01&to=2026-01-31',
    )

    await wrapper.get('[data-testid="period-filter-clear"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.query.from).toBeUndefined()
    expect(router.currentRoute.value.query.to).toBeUndefined()
    expect(wrapper.find('[data-testid="period-filter-clear"]').exists()).toBe(false)
  })

  it('separates an empty period from having no completed settlements at all', async () => {
    const { wrapper } = await mountHistory(
      '/settlements/history?side=sent&from=2026-01-01&to=2026-01-31',
    )

    expect(wrapper.text()).toContain('Nothing in this period')
    expect(wrapper.text()).not.toContain('No completed splits')

    getSettlements.mockResolvedValue({ received: [], sent: [] })
    const empty = await mountHistory()

    expect(empty.wrapper.text()).toContain('No completed splits')
    expect(empty.wrapper.find('[data-testid="period-filter"]').exists()).toBe(false)
  })
})
