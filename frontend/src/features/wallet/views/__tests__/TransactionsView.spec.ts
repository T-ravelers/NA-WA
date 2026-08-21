import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import { i18n } from '@/app/i18n'

import { getTransactions } from '../../api/walletApi'
import { TRANSFER_TYPES } from '../../model/walletHome'
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

/**
 * 고정된 오늘. 달력은 오늘이 속한 달부터 보여주므로 시각을 고정하지 않으면 어떤 달이
 * 열릴지 알 수 없다.
 */
const NOW = new Date('2026-07-15T00:00:00Z')

/** 달력에서 날짜를 고른다. 셀 이름은 앱 로케일(en)로 붙는다. */
const pickDate = async (
  wrapper: Awaited<ReturnType<typeof mountTransactions>>['wrapper'],
  label: string,
) => {
  await wrapper.get(`button[aria-label="Select ${label}"]`).trigger('click')
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
      {
        path: '/wallet/transactions/:transactionId',
        name: 'wallet-transaction-detail',
        component: { template: '<div />' },
      },
    ],
  })

  await router.push('/wallet/transactions')
  await router.isReady()

  return {
    router,
    wrapper: mount(TransactionsView, {
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
    }),
  }
}

describe('TransactionsView', () => {
  afterEach(() => {
    vi.useRealTimers()
  })

  beforeEach(() => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    vi.setSystemTime(NOW)
    vi.clearAllMocks()
    vi.mocked(getTransactions).mockResolvedValue(transactionsResponse)
  })

  it('renders the transaction list and filter controls', async () => {
    const { wrapper } = await mountTransactions()

    await flushPromises()

    expect(wrapper.get('h1').text()).toBe('TRANSACTIONS')
    expect(wrapper.text()).toContain('Point top-up')
    expect(wrapper.text()).toContain('+30,000 P')
    expect(wrapper.text()).toContain('Balance after')
    expect(wrapper.text()).toContain('Details')
    expect(vi.mocked(getTransactions)).toHaveBeenCalledWith({
      cursor: undefined,
      size: 20,
    })
  })

  it('offers every transfer type in TRANSFER_TYPES as a labeled filter option', async () => {
    const { wrapper } = await mountTransactions()

    await flushPromises()

    const options = wrapper
      .get('select[aria-label="Transaction type"]')
      .findAll('option')
      .filter((option) => option.attributes('value') !== '')

    expect(options.map((option) => option.attributes('value')).sort()).toEqual(
      [...TRANSFER_TYPES].sort(),
    )

    for (const option of options) {
      expect(option.text(), `${option.attributes('value')}의 필터 문구가 없다`).not.toContain(
        'wallet.transactions.',
      )
    }
  })

  it('labels each row from the shared activity copy instead of the unknown fallback', async () => {
    const row = (transferId: number, transferType: string) => ({
      transferId,
      transferType,
      entryType: 'CREDIT',
      amount: '25000',
      balanceAfter: '109500',
      createdAt: '2026-07-26T10:30:00',
    })

    vi.mocked(getTransactions).mockResolvedValue({
      ...transactionsResponse,
      transactions: [row(201, 'DEPOSIT_NO_SHOW_DISTRIBUTION'), row(202, 'REVERSAL')],
    })

    const { wrapper } = await mountTransactions()

    await flushPromises()

    expect(wrapper.text()).toContain('No-show deposit shared out')
    expect(wrapper.text()).toContain('Transaction reversed')
    expect(wrapper.text()).not.toContain('Wallet transaction')
  })

  it('requests transactions with the selected filters', async () => {
    const { wrapper } = await mountTransactions()

    await flushPromises()
    await wrapper.get('select[aria-label="Transaction type"]').setValue('TOPUP')
    await wrapper.get('select[aria-label="Status"]').setValue('COMPLETED')
    await pickDate(wrapper, 'July 1, 2026')
    await pickDate(wrapper, 'July 31, 2026')
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

  /**
   * 네이티브 날짜 입력을 쓰던 때는 시작일이 종료일보다 늦을 수 있어 오류를 띄웠다.
   * 달력은 두 번째로 고른 날짜가 이르면 두 값을 뒤집어 그 상태를 만들지 않는다.
   */
  it('swaps the range when the second date comes first', async () => {
    const { wrapper } = await mountTransactions()

    await flushPromises()
    await pickDate(wrapper, 'July 31, 2026')
    await pickDate(wrapper, 'July 1, 2026')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(vi.mocked(getTransactions)).toHaveBeenLastCalledWith(
      expect.objectContaining({ from: '2026-07-01', to: '2026-07-31' }),
    )
  })

  it('shows the chosen range and clears it', async () => {
    const { wrapper } = await mountTransactions()

    await flushPromises()
    expect(wrapper.text()).toContain('Any date')

    await pickDate(wrapper, 'July 1, 2026')
    await pickDate(wrapper, 'July 31, 2026')

    expect(wrapper.text()).toContain('Jul 1, 2026 – Jul 31, 2026')

    await wrapper.get('button[type="button"].underline').trigger('click')

    expect(wrapper.text()).toContain('Any date')
  })

  it('opens the transaction detail route when a transaction is selected', async () => {
    const { router, wrapper } = await mountTransactions()

    await flushPromises()
    await wrapper.get('li > button').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('wallet-transaction-detail')
    expect(router.currentRoute.value.params.transactionId).toBe('101')
  })
})
