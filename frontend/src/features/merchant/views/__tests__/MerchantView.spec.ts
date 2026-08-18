import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { i18n } from '@/app/i18n'

import {
  createMerchantQr,
  fetchMerchantAccount,
  fetchMerchantIncome,
  registerAsMerchant,
  type MerchantAccount,
} from '../../api/merchantApi'
import MerchantView from '../MerchantView.vue'

vi.mock('../../api/merchantApi', () => ({
  registerAsMerchant: vi.fn(),
  createMerchantQr: vi.fn(),
  fetchMerchantIncome: vi.fn(),
  fetchMerchantAccount: vi.fn(),
}))

vi.mock('qrcode', () => ({
  default: { toDataURL: vi.fn().mockResolvedValue('data:image/png;base64,stub') },
}))

function account(accountType: string): MerchantAccount {
  return {
    accountType,
    displayName: accountType === 'MERCHANT' ? 'Blue Bottle' : 'Traveler',
  }
}

async function mountView() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const wrapper = mount(MerchantView, {
    global: { plugins: [i18n, [VueQueryPlugin, { queryClient }]] },
  })

  await flushPromises()

  return { wrapper, queryClient }
}

describe('MerchantView', () => {
  beforeEach(() => {
    vi.mocked(registerAsMerchant).mockReset()
    vi.mocked(createMerchantQr).mockReset()
    vi.mocked(fetchMerchantIncome).mockReset()
    vi.mocked(fetchMerchantAccount).mockReset()

    vi.mocked(fetchMerchantIncome).mockResolvedValue({
      transactions: [
        {
          transferId: 1,
          transferType: 'QR_PAYMENT',
          entryType: 'CREDIT',
          amount: 12000,
          balanceAfter: 12000,
          createdAt: null,
        },
        {
          transferId: 2,
          transferType: 'QR_PAYMENT',
          entryType: 'CREDIT',
          amount: 3000,
          balanceAfter: 15000,
          createdAt: null,
        },
      ],
      nextCursor: null,
    })
  })

  it('shows the store name form when the account is not a merchant yet', async () => {
    vi.mocked(fetchMerchantAccount).mockResolvedValue(account('TRAVELER'))

    const { wrapper } = await mountView()

    expect(wrapper.text()).toContain('Set up your store')
    expect(wrapper.text()).not.toContain('Charge a customer')
    expect(fetchMerchantIncome).not.toHaveBeenCalled()
  })

  it('registers the store and refreshes the cached profile', async () => {
    vi.mocked(fetchMerchantAccount).mockResolvedValue(account('TRAVELER'))
    vi.mocked(registerAsMerchant).mockResolvedValue(account('MERCHANT'))

    const { wrapper, queryClient } = await mountView()
    const clear = vi.spyOn(queryClient, 'clear')

    await wrapper.find('input[type="text"]').setValue('Blue Bottle Hongdae')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(registerAsMerchant).toHaveBeenCalledWith('Blue Bottle Hongdae')
    // guard가 보는 회원 프로필 캐시가 TRAVELER로 남으면 등록 직후 손님 화면으로 나갈 수 있다.
    expect(clear).toHaveBeenCalled()
  })

  it('shows income and the charge form for a merchant', async () => {
    vi.mocked(fetchMerchantAccount).mockResolvedValue(account('MERCHANT'))

    const { wrapper } = await mountView()

    expect(wrapper.text()).toContain("Today's income")
    expect(wrapper.text()).toContain('15,000 P')
    expect(wrapper.text()).toContain('2 payments')
    expect(wrapper.text()).toContain('Charge a customer')
    expect(wrapper.text()).not.toContain('Set up your store')
  })

  it('creates a QR code and renders it with a countdown', async () => {
    vi.mocked(fetchMerchantAccount).mockResolvedValue(account('MERCHANT'))
    vi.mocked(createMerchantQr).mockResolvedValue({
      qrPaymentId: 7,
      qrToken: 'token-abc',
      amount: 4500,
      memo: null,
      paymentStatus: 'ACTIVE',
      currencyCode: 'KRW',
      expiresAt: new Date(Date.now() + 60_000).toISOString(),
    })

    const { wrapper } = await mountView()

    await wrapper.find('input[inputmode="numeric"]').setValue('4500')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(createMerchantQr).toHaveBeenCalledWith(4500, null)
    expect(wrapper.find('img').attributes('src')).toBe('data:image/png;base64,stub')
    expect(wrapper.text()).toContain('Expires in')
  })

  it('marks an already expired code instead of showing a countdown', async () => {
    vi.mocked(fetchMerchantAccount).mockResolvedValue(account('MERCHANT'))
    vi.mocked(createMerchantQr).mockResolvedValue({
      qrPaymentId: 8,
      qrToken: 'token-old',
      amount: 4500,
      memo: null,
      paymentStatus: 'ACTIVE',
      currencyCode: 'KRW',
      expiresAt: new Date(Date.now() - 1_000).toISOString(),
    })

    const { wrapper } = await mountView()

    await wrapper.find('input[inputmode="numeric"]').setValue('4500')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain('This code expired.')
    expect(wrapper.text()).toContain('Create a new one')
  })
})
