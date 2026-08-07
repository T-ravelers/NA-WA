import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import { i18n } from '@/app/i18n'

import { getTransactionDetail } from '../../api/walletApi'
import TransactionDetailView from '../TransactionDetailView.vue'

vi.mock('../../api/walletApi', () => ({
  getTransactionDetail: vi.fn(),
}))

const transactionDetail = {
  amount: '30000',
  occurredAt: '2026-07-25T12:00:00',
  counterparty: { type: 'EXTERNAL', name: 'Stripe' },
  status: 'COMPLETED',
  receipt: {
    transactionNumber: 'ST-0727-0001',
    memo: 'Wallet top-up',
    spendingCategory: null,
  },
  transactionNumber: 'ST-0727-0001',
  fx: null,
}

const mountTransactionDetail = async () => {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/wallet/transactions/:transactionId',
        name: 'wallet-transaction-detail',
        component: TransactionDetailView,
      },
      {
        path: '/wallet/transactions',
        name: 'wallet-transactions',
        component: { template: '<div />' },
      },
    ],
  })

  await router.push('/wallet/transactions/101')
  await router.isReady()

  const wrapper = mount(TransactionDetailView, {
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

  return { router, wrapper }
}

describe('TransactionDetailView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(getTransactionDetail).mockResolvedValue(transactionDetail)
  })

  it('loads and renders transaction details', async () => {
    const { wrapper } = await mountTransactionDetail()

    await flushPromises()

    expect(wrapper.get('h1').text()).toBe('TRANSACTION DETAILS')
    expect(wrapper.text()).toContain('30,000 P')
    expect(wrapper.text()).toContain('Stripe')
    expect(wrapper.text()).toContain('ST-0727-0001')
    expect(vi.mocked(getTransactionDetail)).toHaveBeenCalledWith(101)
  })
})
