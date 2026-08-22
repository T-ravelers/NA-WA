import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'

import { i18n } from '@/app/i18n'

import { createStripeIntent, getTopupMethods, previewTopup } from '../../api/topupApi'
import StripePaymentStep from '../StripePaymentStep.vue'
import TopupView from '../TopupView.vue'

vi.mock('../../api/topupApi', () => ({
  createStripeIntent: vi.fn(),
  getTopupMethods: vi.fn(),
  previewTopup: vi.fn(),
}))

const methodsResponse = {
  methods: [
    {
      type: 'STRIPE_CARD',
      displayName: 'International card top-up',
      testMode: true,
      enabled: true,
    },
  ],
  guideMessage: 'Sandbox mode',
}

const previewResponse = {
  amount: '30000',
  fee: '0',
  currency: 'KRW',
  sandboxBalance: '84500',
  expectedSandboxBalance: '114500',
  warning: null,
}

const stripeIntentResponse = {
  topupId: 44,
  clientSecret: 'pi_test_secret',
  providerPaymentId: 'pi_test_44',
  amount: '30000',
  currency: 'KRW',
  status: 'READY',
  paymentMode: 'SANDBOX',
}

function globalOptions(router: Router) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  // 튜플로 못박는다. 인라인이 아니면 [플러그인, 옵션]이 그냥 배열로 추론돼 어긋난다.
  const vueQuery: [typeof VueQueryPlugin, { queryClient: QueryClient }] = [
    VueQueryPlugin,
    { queryClient },
  ]
  return { plugins: [i18n, router, vueQuery] }
}

const mountTopup = async (initialPath = '/wallet/top-up') => {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/wallet', name: 'wallet', component: { template: '<div />' } },
      { path: '/wallet/top-up', name: 'wallet-top-up', component: { template: '<div />' } },
    ],
  })

  await router.push(initialPath)
  await router.isReady()

  return mount(TopupView, { global: globalOptions(router) })
}

// 되감기는 "히스토리 엔트리를 소비하는 것"까지가 동작이라, 이 화면만 손으로 마운트하면
// 재현되지 않는다. 라우터가 화면을 직접 갈아끼우게 띄우고 실제 동선대로 엔트리를 쌓는다.
// 활동 상세 → 약속 생성(충전으로 떠나기 전 자기 자리를 resume=1로 바꿔 둔다) → 충전.
const mountTopupInFlow = async () => {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/events/:eventId',
        name: 'event-detail',
        component: { template: '<div>Event</div>' },
      },
      {
        path: '/appointments/new',
        name: 'appointment-create',
        component: { template: '<div>Appointment create</div>' },
      },
      { path: '/wallet', name: 'wallet', component: { template: '<div />' } },
      { path: '/wallet/top-up', name: 'wallet-top-up', component: TopupView },
    ],
  })

  await router.push('/events/42')
  await router.push('/appointments/new?itemId=42&itemType=EVENT&resume=1')
  await router.push(
    '/wallet/top-up?amount=10000&returnRouteName=appointment-create&itemId=42&itemType=EVENT&tripId=7',
  )
  await router.isReady()

  const wrapper = mount({ template: '<RouterView />' }, { global: globalOptions(router) })
  await flushPromises()
  return { wrapper, router }
}

describe('TopupView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    // 로컬 `.env.local`에 실제 Stripe 공개 키가 있어도 이 테스트는 키가 없는 경우를
    // 검증해야 한다. Vite는 테스트 모드에서도 `.env.local`을 읽으므로 명시적으로 비운다.
    vi.stubEnv('VITE_STRIPE_PUBLISHABLE_KEY', '')
    vi.mocked(createStripeIntent).mockResolvedValue(stripeIntentResponse)
    vi.mocked(getTopupMethods).mockResolvedValue(methodsResponse)
    vi.mocked(previewTopup).mockResolvedValue(previewResponse)
  })

  afterEach(() => {
    vi.unstubAllEnvs()
  })

  it('renders the English top-up form and payment method', async () => {
    const wrapper = await mountTopup()

    await flushPromises()

    expect(wrapper.get('h1').text()).toBe('TOP UP')
    expect(wrapper.text()).toContain('Top-up amount')
    expect(wrapper.text()).toContain('Stripe')
    expect(wrapper.text()).toContain('Visa / Mastercard · Test Mode')
    expect(wrapper.get('button[aria-pressed="true"]').text()).toContain('Stripe')
    expect(wrapper.get('button[aria-pressed="true"] img').attributes('src')).toBe(
      '/payment/stripe-mark.svg',
    )
    // 비활성 수단 블록은 V2 시안(충전하기/01)대로 55% 투명도로 가라앉는다.
    expect(wrapper.get('.opacity-55 img').attributes('src')).toBe('/payment/paypal-mark.svg')
  })

  it('requests a preview and shows the preview values', async () => {
    const wrapper = await mountTopup()

    await flushPromises()

    const quickAmount = wrapper.findAll('button').find((button) => button.text() === '+30,000 P')
    expect(quickAmount).toBeDefined()
    await quickAmount?.trigger('click')
    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Next')
      ?.trigger('click')
    await flushPromises()

    expect(vi.mocked(previewTopup).mock.calls[0]?.[0]).toEqual({
      amount: 30000,
      method: 'STRIPE_CARD',
      currency: 'KRW',
    })
    expect(wrapper.get('h1').text()).toBe('TOP UP PREVIEW')
    expect(wrapper.text()).toContain('30,000 P')
    expect(wrapper.text()).toContain('114,500 P')
  })

  it('prefills the amount another screen asked for via the query', async () => {
    // 약속 생성이 보증금을 예치할 잔액이 없을 때 그 금액을 ?amount=로 넘겨 보낸다.
    const wrapper = await mountTopup('/wallet/top-up?amount=10000')
    await flushPromises()

    expect(wrapper.get<HTMLInputElement>('input[inputmode="numeric"]').element.value).toBe('10,000')
  })

  it('ignores a query amount that is not a positive whole number', async () => {
    const wrapper = await mountTopup('/wallet/top-up?amount=abc')
    await flushPromises()

    expect(wrapper.get<HTMLInputElement>('input[inputmode="numeric"]').element.value).toBe('')
  })

  it('accumulates quick amount taps instead of replacing the typed amount', async () => {
    const wrapper = await mountTopup()

    await flushPromises()

    await wrapper
      .findAll('button')
      .find((button) => button.text() === '+10,000 P')
      ?.trigger('click')
    await wrapper
      .findAll('button')
      .find((button) => button.text() === '+30,000 P')
      ?.trigger('click')
    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Next')
      ?.trigger('click')
    await flushPromises()

    expect(vi.mocked(previewTopup).mock.calls[0]?.[0]).toEqual({
      amount: 40000,
      method: 'STRIPE_CARD',
      currency: 'KRW',
    })
  })

  it('adds a quick amount on top of a directly typed amount', async () => {
    const wrapper = await mountTopup()

    await flushPromises()

    await wrapper.get('input[inputmode="numeric"]').setValue('30000')
    await wrapper
      .findAll('button')
      .find((button) => button.text() === '+10,000 P')
      ?.trigger('click')
    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Next')
      ?.trigger('click')
    await flushPromises()

    expect(vi.mocked(previewTopup).mock.calls[0]?.[0]).toEqual({
      amount: 40000,
      method: 'STRIPE_CARD',
      currency: 'KRW',
    })
  })

  it('rewinds to the caller entry instead of stacking the same screen twice', async () => {
    // 모바일 PWA에는 브라우저 뒤로가기가 없어 화면 안 ‹ 가 사실상 유일한 출구다.
    // 여기서 지갑 탭으로 떨어지면 약속을 만들던 흐름이 끊기고, 그 화면이 저장해 둔
    // 초안은 아무도 읽지 않는다. 다만 목적지를 새로 만들어 replace하면 안 된다 —
    // 보낸 화면이 떠나기 전에 자기 자리를 resume=1로 바꿔 뒀으므로 바로 아래 엔트리가
    // 이미 목적지고, 다시 replace하면 같은 화면이 히스토리에 두 번 쌓인다.
    const { wrapper, router } = await mountTopupInFlow()
    const historyLength = vi.spyOn(window.history, 'length', 'get').mockReturnValue(3)

    await wrapper.get('button[aria-label="Back"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('appointment-create')
    // 되감아 도착한 자리는 보낸 화면이 남긴 엔트리 그대로다. 이 화면이 목적지를 새로
    // 만들어 replace했다면 여기에 tripId까지 붙어 있다.
    expect(router.currentRoute.value.query).toEqual({
      itemId: '42',
      itemType: 'EVENT',
      resume: '1',
    })

    // 되살아난 폼에서 한 번 더 뒤로 가면 흐름 이전 화면으로 빠진다. 엔트리가 두 번
    // 쌓였다면 여기서 약속 생성에 다시 머문다.
    router.back()
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('event-detail')

    historyLength.mockRestore()
  })

  it('falls back to the caller target when there is no history to rewind', async () => {
    // 딥링크·PWA 재진입이라 되감을 자리가 없는 경우. 이때만 목적지를 만들어 보낸다 —
    // 완료 후 복귀와 같은 자리(resume=1)다.
    const wrapper = await mountTopup(
      '/wallet/top-up?amount=10000&returnRouteName=appointment-create&itemId=42&itemType=EVENT&tripId=7',
    )
    await flushPromises()
    const router = wrapper.vm.$router
    router.addRoute({
      path: '/appointments/new',
      name: 'appointment-create',
      component: { template: '<div />' },
    })

    await wrapper.get('button[aria-label="Back"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('appointment-create')
    expect(router.currentRoute.value.query).toEqual({
      itemId: '42',
      itemType: 'EVENT',
      tripId: '7',
      resume: '1',
    })
  })

  it('falls back to the wallet tab when no caller asked for a return and there is no history', async () => {
    // 지갑에서 곧장 들어온 평소 경로인데 되감을 자리가 없는 경우(딥링크·PWA 재진입).
    // 돌아갈 곳이 없으면 지금까지처럼 지갑 탭이다.
    const wrapper = await mountTopup('/wallet/top-up')
    await flushPromises()
    const router = wrapper.vm.$router

    await wrapper.get('button[aria-label="Back"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('wallet')
  })

  it('rewinds instead of stacking the wallet tab when the wallet sent the host here', async () => {
    // 지갑 → 충전 → ‹ 도 같은 규칙이다. 지갑 탭을 새로 push하면 히스토리에
    // [지갑, 충전, 지갑]이 남아, 돌아간 지갑에서 뒤로 가기가 충전 화면으로 되튄다.
    const wrapper = await mountTopup('/wallet/top-up')
    await flushPromises()
    const router = wrapper.vm.$router
    const back = vi.spyOn(router, 'back').mockImplementation(() => {})
    const push = vi.spyOn(router, 'push')
    const historyLength = vi.spyOn(window.history, 'length', 'get').mockReturnValue(3)

    await wrapper.get('button[aria-label="Back"]').trigger('click')
    await flushPromises()

    expect(back).toHaveBeenCalledOnce()
    expect(push).not.toHaveBeenCalled()

    historyLength.mockRestore()
    push.mockRestore()
    back.mockRestore()
  })

  it('offers to continue where the caller left off once the top-up completes', async () => {
    // 약속 생성이 잔액 부족으로 보낸 경우. 완료 화면에서 그 화면으로 되돌아간다.
    // 여기서는 되감을 히스토리가 없어(딥링크) 목적지를 만들어 replace하는 쪽이다 —
    // 그쪽 query는 그대로 돌려주고 이 화면의 amount·returnRouteName만 뺀 뒤
    // resume=1을 붙인다. 충전 화면은 히스토리에 남지 않는다.
    const wrapper = await mountTopup(
      '/wallet/top-up?amount=10000&returnRouteName=appointment-create&itemId=42&itemType=EVENT&tripId=7',
    )
    await flushPromises()
    const router = wrapper.vm.$router
    router.addRoute({
      path: '/appointments/new',
      name: 'appointment-create',
      component: { template: '<div />' },
    })
    const replace = vi.spyOn(router, 'replace')

    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Next')
      ?.trigger('click')
    await flushPromises()
    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Execute top-up')
      ?.trigger('click')
    await flushPromises()
    wrapper.findComponent(StripePaymentStep).vm.$emit('payment-confirmed', {
      topupId: 44,
      status: 'SUCCEEDED',
      sandboxBalance: '15000',
    })
    await flushPromises()

    expect(wrapper.text()).toContain('Top-up complete')
    const continueButton = wrapper
      .findAll('button')
      .find((button) => button.text() === 'Continue where you left off')
    expect(continueButton).toBeDefined()
    expect(
      wrapper.findAll('button').find((button) => button.text() === 'Back to wallet'),
    ).toBeDefined()

    await continueButton?.trigger('click')
    await flushPromises()

    expect(replace).toHaveBeenCalledOnce()
    expect(router.currentRoute.value.name).toBe('appointment-create')
    expect(router.currentRoute.value.query).toEqual({
      itemId: '42',
      itemType: 'EVENT',
      tripId: '7',
      resume: '1',
    })
  })

  it('rewinds to the caller entry after the top-up completes, too', async () => {
    // 완료 후 복귀도 ‹ 와 같은 규칙이다. 되감을 자리가 있으면 이 화면의 엔트리를
    // 소비한다 — 목적지를 새로 replace하면 약속 생성이 히스토리에 두 번 쌓여, 되살아난
    // 폼에서 뒤로 가기를 눌러도 흐름을 벗어나지 못하고 (초안은 이미 지운 뒤라) 빈
    // 폼이 열린다.
    const { wrapper, router } = await mountTopupInFlow()
    const historyLength = vi.spyOn(window.history, 'length', 'get').mockReturnValue(3)

    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Next')
      ?.trigger('click')
    await flushPromises()
    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Execute top-up')
      ?.trigger('click')
    await flushPromises()
    wrapper.findComponent(StripePaymentStep).vm.$emit('payment-confirmed', {
      topupId: 44,
      status: 'SUCCEEDED',
      sandboxBalance: '15000',
    })
    await flushPromises()

    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Continue where you left off')
      ?.trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('appointment-create')
    expect(router.currentRoute.value.query).toEqual({
      itemId: '42',
      itemType: 'EVENT',
      resume: '1',
    })

    router.back()
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('event-detail')

    historyLength.mockRestore()
  })

  it('gives up the finished top-up entry when heading to the wallet', async () => {
    // "Back to wallet"은 목적지가 분명하니 되감지 않지만, 끝난 충전 화면을 히스토리에
    // 남기면 안 된다 — 지갑에서 뒤로 갈 때 방금 끝낸 충전 화면이 (폼 단계로) 다시 뜬다.
    const { wrapper, router } = await mountTopupInFlow()
    const historyLength = vi.spyOn(window.history, 'length', 'get').mockReturnValue(3)

    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Next')
      ?.trigger('click')
    await flushPromises()
    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Execute top-up')
      ?.trigger('click')
    await flushPromises()
    wrapper.findComponent(StripePaymentStep).vm.$emit('payment-confirmed', {
      topupId: 44,
      status: 'SUCCEEDED',
      sandboxBalance: '15000',
    })
    await flushPromises()

    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Back to wallet')
      ?.trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('wallet')

    router.back()
    await flushPromises()

    // 충전 화면이 아니라 보낸 화면으로 빠진다.
    expect(router.currentRoute.value.name).toBe('appointment-create')

    historyLength.mockRestore()
  })

  it('rewinds to the wallet it came from instead of stacking it twice', async () => {
    // 지갑에서 곧장 들어온 평소 경로. 바로 아래 엔트리가 이미 지갑이라, 지갑을 새로
    // 얹으면 [지갑, 지갑]이 되어 뒤로 가기가 한 번 헛돈다.
    const wrapper = await mountTopup('/wallet/top-up')
    await flushPromises()
    const router = wrapper.vm.$router

    await wrapper
      .findAll('button')
      .find((button) => button.text() === '+30,000 P')
      ?.trigger('click')
    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Next')
      ?.trigger('click')
    await flushPromises()
    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Execute top-up')
      ?.trigger('click')
    await flushPromises()
    wrapper.findComponent(StripePaymentStep).vm.$emit('payment-confirmed', {
      topupId: 44,
      status: 'SUCCEEDED',
      sandboxBalance: '15000',
    })
    await flushPromises()

    const back = vi.spyOn(router, 'back').mockImplementation(() => {})
    const replace = vi.spyOn(router, 'replace')
    const push = vi.spyOn(router, 'push')
    const historyLength = vi.spyOn(window.history, 'length', 'get').mockReturnValue(3)

    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Back to wallet')
      ?.trigger('click')
    await flushPromises()

    expect(back).toHaveBeenCalledOnce()
    expect(replace).not.toHaveBeenCalled()
    expect(push).not.toHaveBeenCalled()

    historyLength.mockRestore()
    push.mockRestore()
    replace.mockRestore()
    back.mockRestore()
  })

  it('creates a Stripe PaymentIntent before showing the Stripe payment step', async () => {
    const wrapper = await mountTopup()

    await flushPromises()
    await wrapper
      .findAll('button')
      .find((button) => button.text() === '+30,000 P')
      ?.trigger('click')
    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Next')
      ?.trigger('click')
    await flushPromises()
    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Execute top-up')
      ?.trigger('click')
    await flushPromises()

    expect(createStripeIntent).toHaveBeenCalledTimes(1)
    expect(vi.mocked(createStripeIntent).mock.calls[0]?.[0]).toEqual({
      amount: 30000,
      currency: 'KRW',
    })
    expect(vi.mocked(createStripeIntent).mock.calls[0]?.[1]).toEqual(expect.any(String))
    expect(wrapper.get('h1').text()).toBe('PAYMENT')
    expect(wrapper.text()).toContain('Stripe payment form could not be loaded')
    expect(wrapper.text()).toContain('Pay 30,000 P')
  })
})
