import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { i18n } from '@/app/i18n'
import { NormalizedApiError } from '@/shared/api/apiError'

import SettlementPayView from '../SettlementPayView.vue'

const { getDetail, pay } = vi.hoisted(() => ({ getDetail: vi.fn(), pay: vi.fn() }))
vi.mock('../../api/settlementGateway', () => ({ settlementGateway: { getDetail, pay } }))

const detail = {
  id: '42',
  type: 'EQUAL' as const,
  totalAmount: '25.00',
  status: 'REQUESTED' as const,
  requestedBy: 'Alex',
  gatheringName: 'Dinner',
  merchantName: 'Dinner',
  paidBy: 'Alex',
  transactionId: undefined,
  viewerItems: [],
  viewer: {
    role: 'PARTICIPANT' as const,
    shareAmount: '12.50',
    payableAmount: '12.50',
    requestStatus: 'PENDING' as const,
    allowedActions: ['PAY'],
  },
}

/** 기본값은 상세의 Pay 버튼으로 들어온 경우다. 주소로 열린 진입은 `confirmed: false`. */
async function mountPay({ confirmed = true, query = '' } = {}) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/settlements/:settlementId/pay',
        name: 'settlement-pay',
        component: SettlementPayView,
      },
      {
        path: '/settlements/:settlementId/pay/complete',
        name: 'settlement-pay-complete',
        component: { template: '<div />' },
      },
      {
        path: '/settlements/:settlementId',
        name: 'settlement-detail',
        component: { template: '<div />' },
      },
      { path: '/settlements', name: 'settlements', component: { template: '<div />' } },
      { path: '/wallet', name: 'wallet', component: { template: '<div />' } },
      { path: '/wallet/top-up', name: 'wallet-top-up', component: { template: '<div />' } },
    ],
  })
  await router.push(
    confirmed
      ? {
          name: 'settlement-pay',
          params: { settlementId: '42' },
          query: Object.fromEntries(new URLSearchParams(query)),
          state: { confirmed: true },
        }
      : `/settlements/42/pay${query === '' ? '' : `?${query}`}`,
  )
  await router.isReady()
  const wrapper = mount(SettlementPayView, {
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

describe('SettlementPayView', () => {
  beforeEach(() => {
    getDetail.mockReset().mockResolvedValue(detail)
    pay.mockReset().mockResolvedValue({})
    sessionStorage.clear()
  })

  it('sends the payment on arrival and moves on to the completion screen', async () => {
    const { router } = await mountPay()

    expect(pay).toHaveBeenCalledWith('42', expect.any(String))
    expect(router.currentRoute.value.name).toBe('settlement-pay-complete')
  })

  it('asks before paying when it was not opened from the split detail', async () => {
    const { wrapper } = await mountPay({ confirmed: false })

    // 공유된 링크·북마크·주소창으로 열어도 이체가 무클릭으로 나가면 안 된다.
    expect(pay).not.toHaveBeenCalled()
    expect(wrapper.get('[data-action="confirm-pay"]').text()).toBe('Pay 12.50 P')

    await wrapper.get('[data-action="confirm-pay"]').trigger('click')
    await flushPromises()

    expect(pay).toHaveBeenCalledWith('42', expect.any(String))
  })

  it('never asks to confirm a payment the server does not grant', async () => {
    getDetail.mockResolvedValue({
      ...detail,
      viewer: { ...detail.viewer, requestStatus: 'PAID' as const, allowedActions: [] },
    })
    const { wrapper, router } = await mountPay({ confirmed: false })

    expect(wrapper.find('[data-action="confirm-pay"]').exists()).toBe(false)
    expect(router.currentRoute.value.name).toBe('settlement-detail')
  })

  it('never pays when the server does not grant the action', async () => {
    getDetail.mockResolvedValue({
      ...detail,
      viewer: { ...detail.viewer, requestStatus: 'PAID' as const, allowedActions: [] },
    })
    const { router } = await mountPay()

    expect(pay).not.toHaveBeenCalled()
    expect(router.currentRoute.value.name).toBe('settlement-detail')
  })

  it('retries a rejected idempotency key with a new one', async () => {
    pay.mockRejectedValueOnce(new NormalizedApiError('SETTLEMENT-015', 400, 'invalid key'))
    const { wrapper, router } = await mountPay()

    await wrapper.get('[role="alert"] button').trigger('click')
    await flushPromises()

    expect(pay).toHaveBeenCalledTimes(2)
    // 서버가 키 자체를 거부했으므로 같은 키로 다시 보내면 영원히 같은 오류가 난다.
    expect(pay.mock.calls[1]?.[1]).not.toBe(pay.mock.calls[0]?.[1])
    expect(router.currentRoute.value.name).toBe('settlement-pay-complete')
  })

  it('retries an unknown failure with the same key so it cannot double-charge', async () => {
    pay.mockRejectedValueOnce(new Error('network down'))
    const { wrapper } = await mountPay()

    await wrapper.get('[role="alert"] button').trigger('click')
    await flushPromises()

    expect(pay).toHaveBeenCalledTimes(2)
    expect(pay.mock.calls[1]?.[1]).toBe(pay.mock.calls[0]?.[1])
  })

  it('returns to the detail screen instead of retrying an already-processed payment', async () => {
    pay.mockRejectedValue(new NormalizedApiError('SETTLEMENT-014', 409, 'already paid'))
    const { wrapper, router } = await mountPay()

    await wrapper.get('[role="alert"] button').trigger('click')
    await flushPromises()

    expect(pay).toHaveBeenCalledTimes(1)
    expect(router.currentRoute.value.name).toBe('settlement-detail')
  })

  /*
   * 잔액 부족은 다시 눌러도 잔액이 그대로다. 재시도 버튼만 주면 사용자가 그 화면에
   * 갇힌다 — 약속 보증금처럼 충전으로 이어 준다(#452).
   */
  it('offers to top up instead of retrying when the balance is too low', async () => {
    pay.mockRejectedValue(new NormalizedApiError('WALLET-015', 409, 'not enough'))
    const { wrapper } = await mountPay()

    const dialog = wrapper.get('[role="dialog"]')
    expect(dialog.text()).toContain('Not enough balance')
    expect(dialog.text()).toContain('12.50 P')
    // 팝업과 오류 화면이 겹치면 무엇을 눌러야 할지 흐려진다.
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
  })

  it('carries the share and the way back into the top-up screen', async () => {
    pay.mockRejectedValue(new NormalizedApiError('WALLET-015', 409, 'not enough'))
    const { wrapper, router } = await mountPay({ query: 'side=sent' })

    await wrapper.get('[role="dialog"]').findAll('button')[1]?.trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('wallet-top-up')
    expect(router.currentRoute.value.query).toEqual({
      // 12.50 P가 모자란다. 내림하면 채우고도 다시 모자라므로 올려서 채워 보낸다.
      amount: '13',
      returnRouteName: 'settlement-pay',
      // 경로 변수를 쓰는 화면이라 이름만으로는 주소를 만들 수 없다.
      returnParams: 'settlementId:42',
      // 보고 있던 쪽을 놓치면 돌아온 뒤 뒤로 갈 때 반대편 목록으로 떨어진다.
      side: 'sent',
    })
  })

  it('marks its own entry so the way back is the confirmation step', async () => {
    pay.mockRejectedValue(new NormalizedApiError('WALLET-015', 409, 'not enough'))
    const { wrapper, router } = await mountPay()

    await wrapper.get('[role="dialog"]').findAll('button')[1]?.trigger('click')
    await flushPromises()
    // 충전 화면은 일을 마치든 그냥 나가든 자기 엔트리를 되감아 소비한다.
    router.back()
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('settlement-pay')
    expect(router.currentRoute.value.query.resume).toBe('1')
  })

  it('waits for a tap after the top-up round trip instead of paying on arrival', async () => {
    const { wrapper } = await mountPay({ query: 'resume=1' })

    // 돌아오자마자 돈이 나가면 안 된다. 충전을 그만두고 뒤로 온 경우도 마찬가지다.
    expect(pay).not.toHaveBeenCalled()
    expect(wrapper.get('[data-action="confirm-pay"]').text()).toBe('Pay 12.50 P')

    await wrapper.get('[data-action="confirm-pay"]').trigger('click')
    await flushPromises()

    expect(pay).toHaveBeenCalledTimes(1)
  })

  it('leaves a usable confirmation screen when the top-up offer is declined', async () => {
    pay.mockRejectedValue(new NormalizedApiError('WALLET-015', 409, 'not enough'))
    const { wrapper } = await mountPay()

    await wrapper.get('[role="dialog"]').findAll('button')[0]?.trigger('click')
    await flushPromises()

    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
    // 오류를 지우지 않으면 같은 실패가 오류 화면으로 되살아난다.
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
    expect(wrapper.get('[data-action="confirm-pay"]').text()).toBe('Pay 12.50 P')
  })

  /*
   * 지갑이 없다는 실패는 **원결제자 쪽 지갑 때문일 수도 있다.** 이체가 양쪽 지갑을 모두
   * 확인하기 때문이다(`WalletTransferService.transfer`). 이 화면이 그 문장을 그대로
   * 내보내므로, "your wallet"이라고 단정하면 자기 지갑이 멀쩡한 사용자가 자기 지갑
   * 화면만 들여다보며 원인을 찾지 못한다.
   */
  it('does not blame the reader when a wallet is missing on either side', async () => {
    pay.mockRejectedValue(new NormalizedApiError('WALLET-001', 404, 'not found'))
    const { wrapper } = await mountPay()

    // StateError의 첫 문단이 오류 코드 문구다. 아래 안내문·버튼 라벨은 이 검사 대상이 아니다.
    const message = wrapper.get('[role="alert"] p').text()
    expect(message).toBe('A wallet needed for this could not be found.')
    expect(message).not.toMatch(/\byour\b/i)
  })

  it('sends a locked wallet to the wallet screen rather than a retry', async () => {
    pay.mockRejectedValue(new NormalizedApiError('WALLET-016', 403, 'not active'))
    const { wrapper, router } = await mountPay()

    const action = wrapper.get('[role="alert"] button')
    // 다시 눌러도 같은 답이 온다. 그 버튼에 "Try again"이라고 적혀 있으면 안 된다.
    expect(action.text()).toBe('Go to wallet')

    await action.trigger('click')
    await flushPromises()

    expect(pay).toHaveBeenCalledTimes(1)
    expect(router.currentRoute.value.name).toBe('wallet')
  })
})
