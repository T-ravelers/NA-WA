import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { getWalletHome } from '@/features/wallet/api/walletApi'
import WalletHomeView from '@/features/wallet/views/WalletHomeView.vue'
import { i18n } from '@/app/i18n'

vi.mock('@/features/wallet/api/walletApi', () => ({
  getWalletHome: vi.fn(),
}))

const walletHomeResponse = {
  balance: '84500',
  availabilityStatus: 'ACTIVE',
  recentTransactions: [
    {
      transferId: 1,
      transferType: 'QR_PAYMENT',
      entryType: 'DEBIT',
      amount: '18000',
      balanceAfter: '84500',
      createdAt: '2026-07-25T12:00:00',
    },
  ],
}

const mountWalletHome = () =>
  mount(WalletHomeView, {
    global: {
      plugins: [
        i18n,
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

describe('WalletHomeView', () => {
  beforeEach(() => {
    vi.mocked(getWalletHome).mockResolvedValue(walletHomeResponse)
  })

  it('shows a loading state before the API response arrives', () => {
    const wrapper = mountWalletHome()

    expect(wrapper.get('[role="status"]').text()).toContain('지갑 정보를 불러오는 중입니다.')
  })

  it('renders the wallet balance and quick actions after the API response arrives', async () => {
    const wrapper = mountWalletHome()

    await flushPromises()

    expect(wrapper.get('h1').text()).toBe('WALLET')
    expect(wrapper.text()).toContain('84,500 P')
    expect(wrapper.findAll('button').some((button) => button.text() === '충전하기')).toBe(true)
    expect(wrapper.text()).not.toContain('보내기')
  })

  it('announces the selected quick action', async () => {
    const wrapper = mountWalletHome()

    await flushPromises()
    const topUpButton = wrapper.findAll('button').find((button) => button.text() === '충전하기')
    expect(topUpButton).toBeDefined()

    await topUpButton?.trigger('click')

    expect(wrapper.get('[aria-live="polite"]').text()).toContain('충전하기 버튼을 선택했습니다.')
  })
})
