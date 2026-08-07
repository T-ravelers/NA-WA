import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import { i18n } from '@/app/i18n'

import { getTransactions } from '../../api/walletApi'
import TransactionsView from '../TransactionsView.vue'

vi.mock('../../api/walletApi', () => ({
  getTransactions: vi.fn(),
}))

const transactionsResponse = {
  transactions: [
    {
      transferId: 101,
      transferType: 'TOPUP',
      entryType: 'CREDIT',
      amount: '30000',
      balanceAfter: '114500',
      createdAt: '2026-07-25T12:00:00',
    },
  ],
  nextCursor: null,
  appliedFilters: {
    type: null,
    status: null,
    from: null,
    to: null,
  },
}

const mountTransactions = async () => {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/wallet', name: 'wallet', component: { template: '<div />' } },
      {
        path: '/wallet/transactions',
        name: 'wallet-transactions',
        component: { template: '<div />' },
      },
    ],
  })

  await router.push('/wallet/transactions')
  await router.isReady()

  return mount(TransactionsView, {
    global: {
      plugins: [
        i18n,
        router,
        [
          VueQueryPlugin,
          {
            queryClient: new QueryClient({
              defaultOptions: {
                queries: { retry: false },
              },
            }),
          },
        ],
      ],
    },
  })
}

describe('TransactionsView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(getTransactions).mockResolvedValue(transactionsResponse)
  })

  it('renders the transaction list and filter controls', async () => {
    const wrapper = await mountTransactions()

    await flushPromises()

    expect(wrapper.get('h1').text()).toBe('TRANSACTIONS')
    expect(wrapper.text()).toContain('Point top-up')
    expect(wrapper.text()).toContain('+30,000 P')
    expect(wrapper.text()).toContain('Balance after')
    expect(vi.mocked(getTransactions)).toHaveBeenCalledWith({
      cursor: undefined,
      size: 20,
    })
  })

  it('requests transactions with the selected filters', async () => {
    const wrapper = await mountTransactions()

    await flushPromises()
    await wrapper.get('select[aria-label="Transaction type"]').setValue('TOPUP')
    await wrapper.get('select[aria-label="Status"]').setValue('COMPLETED')
    await wrapper.get('input[aria-label="From"]').setValue('2026-07-01')
    await wrapper.get('input[aria-label="To"]').setValue('2026-07-31')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(vi.mocked(getTransactions)).toHaveBeenLastCalledWith({
      type: 'TOPUP',
      status: 'COMPLETED',
      from: '2026-07-01',
      to: '2026-07-31',
      cursor: undefined,
      size: 20,
    })
  })

  it('rejects an invalid date range before requesting filtered data', async () => {
    const wrapper = await mountTransactions()

    await flushPromises()
    await wrapper.get('input[aria-label="From"]').setValue('2026-08-01')
    await wrapper.get('input[aria-label="To"]').setValue('2026-07-01')
    await wrapper.get('form').trigger('submit')

    expect(wrapper.get('[role="alert"]').text()).toContain(
      'The start date must be before the end date.',
    )
    expect(vi.mocked(getTransactions)).toHaveBeenCalledTimes(1)
  })
})
