import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

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

  return mount(TopupView, {
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

  it('offers to continue where the caller left off once the top-up completes', async () => {
    // 약속 생성이 잔액 부족으로 보낸 경우. 완료 화면에서 그 화면으로 되돌아가되,
    // 그쪽 query는 그대로 돌려주고 이 화면의 amount·returnRouteName만 뺀 뒤
    // resume=1을 붙인다. 충전 화면들은 히스토리에 남지 않도록 replace로 간다.
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
