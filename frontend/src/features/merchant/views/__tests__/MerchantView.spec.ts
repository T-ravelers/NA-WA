import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

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

/**
 * 고정된 현재 시각. `2026-08-18T08:00:00Z`는 KST로 17:00이다.
 *
 * `expiresAt`은 오프셋 없는 KST 벽시계로 내려오므로, 시각을 고정하지 않으면 테스트
 * 실행 기기의 시간대에 따라 남은 시간이 달라진다.
 */
const NOW = new Date('2026-08-18T08:00:00Z')

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
  afterEach(() => {
    vi.useRealTimers()
  })

  beforeEach(() => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    vi.setSystemTime(NOW)

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
        // 환불 같은 반대 방향 항목이 합계에 더해지면 안 된다.
        {
          transferId: 3,
          transferType: 'QR_PAYMENT',
          entryType: 'DEBIT',
          amount: 5000,
          balanceAfter: 10000,
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
      qrPaymentCodeId: 7,
      qrToken: 'token-abc',
      amount: 4500,
      memo: null,
      status: 'ACTIVE',
      currencyCode: 'KRW',
      // 백엔드 @JsonFormat 그대로. 오프셋이 없는 KST 벽시계이며 NOW로부터 45초 뒤다.
      expiresAt: '2026-08-18T17:00:45',
    })

    const { wrapper } = await mountView()

    await wrapper.find('input[inputmode="numeric"]').setValue('4500')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(createMerchantQr).toHaveBeenCalledWith(4500, null)
    expect(wrapper.find('img').attributes('src')).toBe('data:image/png;base64,stub')
    // 기기 시간대로 해석하면 9시간이 더해져 남은 시간이 전혀 달라진다.
    expect(wrapper.text()).toContain('Expires in 0:45')
  })

  it('marks an already expired code instead of showing a countdown', async () => {
    vi.mocked(fetchMerchantAccount).mockResolvedValue(account('MERCHANT'))
    vi.mocked(createMerchantQr).mockResolvedValue({
      qrPaymentCodeId: 8,
      qrToken: 'token-old',
      amount: 4500,
      memo: null,
      status: 'ACTIVE',
      currencyCode: 'KRW',
      // NOW보다 1초 이르다.
      expiresAt: '2026-08-18T16:59:59',
    })

    const { wrapper } = await mountView()

    await wrapper.find('input[inputmode="numeric"]').setValue('4500')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain('This code expired.')
    expect(wrapper.text()).toContain('Create a new one')
  })
})
