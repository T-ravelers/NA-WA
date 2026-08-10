import { VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'

import { i18n } from '@/app/i18n'
import { queryClient } from '@/app/query/client'
import { NormalizedApiError } from '@/shared/api/apiError'

import type { WalletHome } from '../../api/walletApi'

function createTestRouter(): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/wallet', name: 'wallet', component: { template: '<div />' } },
      { path: '/wallet/top-up', name: 'wallet-top-up', component: { template: '<div />' } },
      { path: '/wallet/qr', name: 'wallet-qr', component: { template: '<div />' } },
      {
        path: '/wallet/transactions',
        name: 'wallet-transactions',
        component: { template: '<div />' },
      },
    ],
  })
}

const fetchWalletHome = vi.fn()

vi.mock('../../api/walletApi', () => ({
  fetchWalletHome: () => fetchWalletHome(),
}))

const WalletHomeView = (await import('../WalletHomeView.vue')).default

const WALLET: WalletHome = {
  balance: 84500,
  availabilityStatus: 'ACTIVE',
  recentTransactions: [
    {
      transferId: 1,
      transferType: 'QR_PAYMENT',
      entryType: 'DEBIT',
      amount: 18000,
      balanceAfter: 84500,
      createdAt: [2026, 7, 25, 12, 0],
    },
  ],
}

async function mountView(router: Router = createTestRouter()) {
  await router.push('/wallet')
  await router.isReady()

  return mount(WalletHomeView, {
    global: { plugins: [i18n, [VueQueryPlugin, { queryClient }], router] },
  })
}

async function mountLoaded(router?: Router) {
  const wrapper = await mountView(router)

  await flushPromises()

  return wrapper
}

beforeEach(() => {
  queryClient.clear()
  fetchWalletHome.mockReset()
  fetchWalletHome.mockResolvedValue(WALLET)
})

afterEach(() => {
  vi.useRealTimers()
  i18n.global.locale.value = 'en'
})

describe('WalletHomeView', () => {
  it('응답 전에는 로딩 상태를 보여준다', async () => {
    const wrapper = await mountView()

    expect(wrapper.find('[role="status"]').exists()).toBe(true)
  })

  it('잔액과 지갑 상태를 표시한다', async () => {
    const wrapper = await mountLoaded()

    expect(wrapper.text()).toContain('84,500 P')
    expect(wrapper.text()).toContain('Active')
    expect(wrapper.text()).toContain('My wallet')
  })

  it('응답에 없는 계좌명을 지어내지 않는다', async () => {
    const wrapper = await mountLoaded()

    expect(wrapper.text()).not.toContain('Test')
  })

  it('거래를 종류별 문구와 부호로 표시한다', async () => {
    const wrapper = await mountLoaded()

    expect(wrapper.text()).toContain('QR payment')
    expect(wrapper.text()).toContain('-18,000 P')
  })

  it('백엔드에 없는 거래 종류가 와도 항목을 지우지 않는다', async () => {
    fetchWalletHome.mockResolvedValue({
      ...WALLET,
      recentTransactions: [{ ...WALLET.recentTransactions[0]!, transferType: 'DEPOSIT_HOLD' }],
    })

    const wrapper = await mountLoaded()

    expect(wrapper.text()).toContain('Deposit held')
  })

  it('정산 버튼만 아직 비활성이고 QR 버튼은 사용할 수 있다', async () => {
    const wrapper = await mountLoaded()

    const buttons = wrapper.findAll('button')
    const qrButton = buttons.find((button) => button.text() === 'QR')
    const settlementButton = buttons.find((button) => button.text() === 'Settle up')

    expect(qrButton?.attributes('disabled')).toBeUndefined()
    expect(settlementButton?.attributes('disabled')).toBeDefined()
    expect(wrapper.text()).toContain('These become available in a later release.')
  })

  it('QR 버튼을 누르면 내 QR 화면으로 이동한다', async () => {
    const router = createTestRouter()
    const wrapper = await mountLoaded(router)
    const pushSpy = vi.spyOn(router, 'push')

    const qrButton = wrapper.findAll('button').find((button) => button.text() === 'QR')
    await qrButton?.trigger('click')

    expect(pushSpy).toHaveBeenCalledWith({ name: 'wallet-qr' })
  })

  it('충전 버튼을 누르면 충전 화면으로 이동한다', async () => {
    const router = createTestRouter()
    const wrapper = await mountLoaded(router)
    const pushSpy = vi.spyOn(router, 'push')

    const topUpButton = wrapper.findAll('button').find((button) => button.text() === 'Top up')
    expect(topUpButton?.attributes('disabled')).toBeUndefined()

    await topUpButton?.trigger('click')

    expect(pushSpy).toHaveBeenCalledWith({ name: 'wallet-top-up' })
  })

  it('전체보기 버튼을 누르면 거래 내역 화면으로 이동한다', async () => {
    const router = createTestRouter()
    const wrapper = await mountLoaded(router)
    const pushSpy = vi.spyOn(router, 'push')

    const viewAllButton = wrapper.findAll('button').find((button) => button.text() === 'View all')
    expect(viewAllButton?.attributes('disabled')).toBeUndefined()

    await viewAllButton?.trigger('click')

    expect(pushSpy).toHaveBeenCalledWith({ name: 'wallet-transactions' })
  })

  it('거래가 없으면 빈 상태를 보여준다', async () => {
    fetchWalletHome.mockResolvedValue({ ...WALLET, recentTransactions: [] })

    const wrapper = await mountLoaded()

    expect(wrapper.text()).toContain('No activity yet')
  })

  it('실패하면 오류 코드에 해당하는 문구를 덧붙인다', async () => {
    vi.useFakeTimers()
    fetchWalletHome.mockRejectedValue(new NormalizedApiError('WALLET-001', 404, 'not found'))

    const wrapper = await mountView()

    // 일시적 실패는 1회 재시도하므로 대기 시간이 지난 뒤에야 오류 화면이 확정된다.
    await vi.advanceTimersByTimeAsync(5_000)
    await flushPromises()

    expect(wrapper.find('[role="alert"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('We could not find your wallet.')
  })

  /*
   * httpClient가 401에서 이미 갱신을 1회 시도한다. 여기까지 401이 올라왔다면 그 복구가
   * 실패했다는 뜻이라, 다시 부르면 refresh와 세션 만료 처리만 요청 수만큼 더 실행된다.
   */
  it('401은 재시도하지 않는다', async () => {
    fetchWalletHome.mockRejectedValue(new NormalizedApiError('AUTH-003', 401, 'unauthorized'))

    const wrapper = await mountLoaded()

    expect(fetchWalletHome).toHaveBeenCalledTimes(1)
    expect(wrapper.find('[role="alert"]').exists()).toBe(true)
  })

  it('로케일을 바꾸면 문구와 숫자 표기가 함께 따라온다', async () => {
    i18n.global.setLocaleMessage('vi', {
      wallet: { home: { quickActions: { topUp: 'Nạp tiền' } } },
    })

    const wrapper = await mountLoaded()
    expect(wrapper.text()).toContain('84,500 P')

    i18n.global.locale.value = 'vi'
    await flushPromises()

    // 문구는 computed로 다시 계산되고, 숫자는 vi 로케일의 자릿수 구분(.)을 따른다.
    expect(wrapper.text()).toContain('Nạp tiền')
    expect(wrapper.text()).toContain('84.500 P')
  })
})
