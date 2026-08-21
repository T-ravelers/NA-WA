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

/**
 * 품목 한 줄을 채운다.
 *
 * 품목·수량·단가는 서버로 보내지 않는다. 합계만 QR에 실리므로 여기서 확인할 것은
 * 입력이 합계로 이어지는지다.
 */
async function fillItem(
  wrapper: Awaited<ReturnType<typeof mountView>>['wrapper'],
  index: number,
  quantity: string,
  unitPrice: string,
) {
  const row = wrapper.findAll('li')[index]

  if (row === undefined) {
    throw new Error(`품목 ${index}번 줄이 없다`)
  }

  // DOM 순서가 아니라 id로 고른다. 순서로 고르면 수량과 단가가 뒤바뀌어도 곱셈 결과가
  // 같아 테스트가 통과해 버린다.
  await row.get('input[id^="merchant-qty-"]').setValue(quantity)
  await row.get('input[id^="merchant-price-"]').setValue(unitPrice)
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
    // Welcome에 링크가 생겨 손님도 이 화면에 닿는다. 되돌릴 수 없다는 것을 미리 알려야 한다.
    expect(wrapper.text()).toContain('This cannot be undone.')
    expect(wrapper.text()).not.toContain('Charge a customer')
    expect(fetchMerchantIncome).not.toHaveBeenCalled()
  })

  it('registers the store and refreshes the cached profile', async () => {
    vi.mocked(fetchMerchantAccount).mockResolvedValue(account('TRAVELER'))
    vi.mocked(registerAsMerchant).mockResolvedValue(account('MERCHANT'))

    const { wrapper, queryClient } = await mountView()
    const removed = vi.spyOn(queryClient, 'removeQueries')

    await wrapper.find('input[type="text"]').setValue('Blue Bottle Hongdae')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(registerAsMerchant).toHaveBeenCalledWith('Blue Bottle Hongdae')

    // 등록에 성공하면 화면이 곧바로 가맹점 모드로 바뀌어야 한다. 캐시만 비우고 이 화면이
    // 다시 그려지지 않으면 사용자는 등록 폼을 계속 보고, 다시 누르면 MEMBER-009를 받는다.
    expect(wrapper.text()).toContain('Charge a customer')
    expect(wrapper.text()).not.toContain('Set up your store')

    // guard가 보는 회원 프로필 캐시가 TRAVELER로 남으면 등록 직후 손님 화면으로 나갈 수 있다.
    expect(removed).toHaveBeenCalled()
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

  it('adds up every item into the total', async () => {
    vi.mocked(fetchMerchantAccount).mockResolvedValue(account('MERCHANT'))

    const { wrapper } = await mountView()

    await fillItem(wrapper, 0, '2', '4500')
    await wrapper.get('button[type="button"].w-full').trigger('click')
    await fillItem(wrapper, 1, '1', '3000')

    expect(wrapper.text()).toContain('12,000 P')
  })

  it('keeps the QR button unavailable until an item has a quantity and a price', async () => {
    vi.mocked(fetchMerchantAccount).mockResolvedValue(account('MERCHANT'))

    const { wrapper } = await mountView()

    const submit = wrapper.get('button[type="submit"]')

    expect(submit.attributes('disabled')).toBeDefined()
    expect(wrapper.text()).toContain('Add at least one item')

    await fillItem(wrapper, 0, '1', '4500')

    expect(submit.attributes('disabled')).toBeUndefined()
  })

  it('steps the quantity with the plus and minus buttons', async () => {
    vi.mocked(fetchMerchantAccount).mockResolvedValue(account('MERCHANT'))

    const { wrapper } = await mountView()

    const row = wrapper.get('li')
    const quantity = row.get('input[id^="merchant-qty-"]')

    await row.get('input[id^="merchant-price-"]').setValue('4500')

    // 한 개를 파는 경우가 가장 흔해 새 줄은 1에서 시작한다.
    expect((quantity.element as HTMLInputElement).value).toBe('1')
    expect(wrapper.text()).toContain('4,500 P')

    await row.get('button[aria-label^="Increase quantity"]').trigger('click')

    expect((quantity.element as HTMLInputElement).value).toBe('2')
    expect(wrapper.text()).toContain('9,000 P')

    await row.get('button[aria-label^="Decrease quantity"]').trigger('click')

    expect((quantity.element as HTMLInputElement).value).toBe('1')
  })

  it('stops the minus button at zero', async () => {
    vi.mocked(fetchMerchantAccount).mockResolvedValue(account('MERCHANT'))

    const { wrapper } = await mountView()

    const row = wrapper.get('li')
    const decrease = row.get('button[aria-label^="Decrease quantity"]')

    await decrease.trigger('click')

    expect((row.get('input[id^="merchant-qty-"]').element as HTMLInputElement).value).toBe('0')
    expect(decrease.attributes('disabled')).toBeDefined()
  })

  it('clears text that is not a number out of the price field', async () => {
    vi.mocked(fetchMerchantAccount).mockResolvedValue(account('MERCHANT'))

    const { wrapper } = await mountView()

    const price = wrapper.get('li').get('input[id^="merchant-price-"]')

    await price.setValue('abc')

    // 파싱 결과(null)가 직전과 같아 Vue는 다시 그리지 않는다. 되돌려 주지 않으면 합계는
    // 0인데 칸에는 `abc`가 남아, 사용자는 금액이 아니라 지워지지 않는 글자를 보게 된다.
    expect((price.element as HTMLInputElement).value).toBe('')
    expect(wrapper.text()).toContain('Add at least one item')
  })

  it('snaps the quantity field back to the cap when the typed value exceeds it', async () => {
    vi.mocked(fetchMerchantAccount).mockResolvedValue(account('MERCHANT'))

    const { wrapper } = await mountView()

    const quantity = wrapper.get('li').get('input[id^="merchant-qty-"]')

    await quantity.setValue('9999')
    // 이미 상한이라 파싱 결과가 그대로다 — 되돌려 주지 않으면 `99999`가 칸에 남는다.
    await quantity.setValue('99999')

    expect((quantity.element as HTMLInputElement).value).toBe('9999')
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

    await fillItem(wrapper, 0, '2', '4500')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    // 품목이 아니라 계산된 합계만 서버로 간다.
    expect(createMerchantQr).toHaveBeenCalledWith(9000, null)
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

    await fillItem(wrapper, 0, '1', '4500')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain('This code expired.')
    expect(wrapper.text()).toContain('Create a new one')
  })
})
