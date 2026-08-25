import { VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'

import { i18n } from '@/app/i18n'
import { queryClient } from '@/app/query/client'
import { NormalizedApiError } from '@/shared/api/apiError'

import { walletNotificationIntegrationKey } from '../../model/notificationIntegration'
import { walletKeys } from '../../model/walletHome'

import type { WalletHome } from '../../api/walletApi'

function createTestRouter(): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/wallet', name: 'wallet', component: { template: '<div />' } },
      { path: '/wallet/top-up', name: 'wallet-top-up', component: { template: '<div />' } },
      { path: '/wallet/qr', name: 'wallet-qr', component: { template: '<div />' } },
      {
        path: '/wallet/qr/create',
        name: 'wallet-qr-create',
        component: { template: '<div />' },
      },
      {
        path: '/wallet/transactions',
        name: 'wallet-transactions',
        component: { template: '<div />' },
      },
      { path: '/settlements', name: 'settlements', component: { template: '<div />' } },
      { path: '/notifications', name: 'notifications', component: { template: '<div />' } },
    ],
  })
}

const fetchWalletHome = vi.fn()
const animateBalance = vi.fn()
const reducedMotion = ref(false)

vi.mock('../../api/walletApi', () => ({
  fetchWalletHome: () => fetchWalletHome(),
}))

vi.mock('motion-v', async (importOriginal) => ({
  ...(await importOriginal<typeof import('motion-v')>()),
  animate: (...args: unknown[]) => animateBalance(...args),
  useReducedMotion: () => reducedMotion,
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

/*
 * 벨은 알림 feature의 개수 조회를 주입받는다. 테스트는 그 자리에 고정값을 꽂아
 * 지갑 화면만 따로 검증한다 — 폴링이나 서버 호출은 여기 관심사가 아니다.
 */
const unreadCount = ref<number | undefined>(0)

async function mountView(router: Router = createTestRouter()) {
  await router.push('/wallet')
  await router.isReady()

  return mount(WalletHomeView, {
    global: {
      plugins: [i18n, [VueQueryPlugin, { queryClient }], router],
      provide: {
        [walletNotificationIntegrationKey as symbol]: {
          useUnreadNotificationCount: () => ({ data: unreadCount }),
        },
      },
    },
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
  animateBalance.mockReset()
  animateBalance.mockImplementation((...args: unknown[]) => {
    const options = args[2] as { onComplete?: () => void }
    options.onComplete?.()

    return { stop: vi.fn() }
  })
  reducedMotion.value = false
  unreadCount.value = 0
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
    expect(animateBalance).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('Active')
    expect(wrapper.text()).toContain('My wallet')
  })

  it('잔액이 바뀌면 이전 값에서 새 값까지 0.6초 안에 센다', async () => {
    const wrapper = await mountLoaded()
    animateBalance.mockReturnValue({ stop: vi.fn() })

    queryClient.setQueryData(walletKeys.home(), { ...WALLET, balance: 104500 })
    await flushPromises()

    expect(animateBalance).toHaveBeenCalledWith(
      84500,
      104500,
      expect.objectContaining({ duration: 0.6, ease: 'easeOut' }),
    )

    const animationOptions = animateBalance.mock.calls[0]?.[2] as {
      onUpdate: (latest: number) => void
      onComplete: () => void
    }
    animationOptions.onUpdate(94500)
    await flushPromises()
    expect(wrapper.get('[data-testid="wallet-balance"]').text()).toBe('94,500 P')

    animationOptions.onComplete()
    await flushPromises()
    expect(wrapper.get('[data-testid="wallet-balance"]').text()).toBe('104,500 P')
  })

  it('연속 갱신은 현재 표시 중인 값에서 최신 잔액으로 이어서 센다', async () => {
    const wrapper = await mountLoaded()
    const firstStop = vi.fn()
    animateBalance.mockReturnValueOnce({ stop: firstStop }).mockReturnValueOnce({ stop: vi.fn() })

    queryClient.setQueryData(walletKeys.home(), { ...WALLET, balance: 104500 })
    await flushPromises()

    const firstAnimationOptions = animateBalance.mock.calls[0]?.[2] as {
      onUpdate: (latest: number) => void
    }
    firstAnimationOptions.onUpdate(94500)
    await flushPromises()

    queryClient.setQueryData(walletKeys.home(), { ...WALLET, balance: 120000 })
    await flushPromises()

    expect(firstStop).toHaveBeenCalledOnce()
    expect(animateBalance).toHaveBeenNthCalledWith(
      2,
      94500,
      120000,
      expect.objectContaining({ duration: 0.6, ease: 'easeOut' }),
    )
    expect(wrapper.get('[data-testid="wallet-balance"]').text()).toBe('94,500 P')
  })

  it('감소 모션 설정에서는 바뀐 잔액을 즉시 표시한다', async () => {
    reducedMotion.value = true
    const wrapper = await mountLoaded()

    queryClient.setQueryData(walletKeys.home(), { ...WALLET, balance: 104500 })
    await flushPromises()

    expect(animateBalance).not.toHaveBeenCalled()
    expect(wrapper.get('[data-testid="wallet-balance"]').text()).toBe('104,500 P')
  })

  it('fresh 캐시가 남아 있어도 지갑에 다시 들어오면 최신 잔액을 조회한다', async () => {
    const first = await mountLoaded()
    expect(first.text()).toContain('84,500 P')
    first.unmount()

    fetchWalletHome.mockResolvedValue({ ...WALLET, balance: 104500 })
    const second = await mountLoaded()

    expect(fetchWalletHome).toHaveBeenCalledTimes(2)
    expect(second.text()).toContain('104,500 P')
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
      recentTransactions: [{ ...WALLET.recentTransactions![0]!, transferType: 'DEPOSIT_HOLD' }],
    })

    const wrapper = await mountLoaded()

    expect(wrapper.text()).toContain('Deposit held')
  })

  it('정산 거래를 낸 쪽과 받은 쪽으로 갈라 부른다', async () => {
    const base = WALLET.recentTransactions![0]!

    fetchWalletHome.mockResolvedValue({
      ...WALLET,
      recentTransactions: [
        { ...base, transferId: 11, transferType: 'SETTLEMENT', entryType: 'DEBIT' },
        { ...base, transferId: 12, transferType: 'SETTLEMENT', entryType: 'CREDIT' },
      ],
    })

    const wrapper = await mountLoaded()

    expect(wrapper.text()).toContain('Split paid')
    expect(wrapper.text()).toContain('Split collected')
  })

  it('QR과 정산 버튼을 사용할 수 있고 정산 화면으로 이동한다', async () => {
    const router = createTestRouter()
    const wrapper = await mountLoaded(router)
    const pushSpy = vi.spyOn(router, 'push')

    const buttons = wrapper.findAll('button')
    const qrButton = buttons.find((button) => button.text() === 'QR')
    const settlementButton = buttons.find((button) => button.text() === 'Splits')

    expect(qrButton?.attributes('disabled')).toBeUndefined()
    expect(settlementButton?.attributes('disabled')).toBeUndefined()
    expect(wrapper.text()).not.toContain('These become available in a later release.')

    await settlementButton?.trigger('click')
    expect(pushSpy).toHaveBeenCalledWith({ name: 'settlements' })
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
    expect(wrapper.text()).toContain('A wallet needed for this could not be found.')
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

  it('안 읽은 알림이 있으면 벨에 개수를 붙이고, 없으면 배지를 감춘다', async () => {
    unreadCount.value = 3
    const wrapper = await mountLoaded()

    // get은 없으면 던지므로 이 줄이 곧 "개수가 이름에 실렸다"는 단정이다.
    wrapper.get('button[aria-label="3 unread"]')
    expect(wrapper.text()).toContain('3')

    unreadCount.value = 0
    await flushPromises()

    // 배지가 사라져도 벨 자체는 남는다. 알림 화면으로 가는 길이 개수에 따라 없어지면 안 된다.
    expect(wrapper.find('button[aria-label="Notifications"]').exists()).toBe(true)
  })

  it('개수가 아홉을 넘으면 배지를 9+로 줄이되 읽히는 이름에는 실제 개수를 남긴다', async () => {
    unreadCount.value = 12
    const wrapper = await mountLoaded()

    expect(wrapper.text()).toContain('9+')
    expect(wrapper.find('button[aria-label="12 unread"]').exists()).toBe(true)
  })

  it('벨을 누르면 알림 화면으로 간다', async () => {
    const router = createTestRouter()
    const wrapper = await mountLoaded(router)

    await wrapper.get('button[aria-label="Notifications"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('notifications')
  })
})
