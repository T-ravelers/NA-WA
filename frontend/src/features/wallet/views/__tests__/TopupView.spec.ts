import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import { i18n } from '@/app/i18n'

import { createStripeIntent, getTopupMethods, previewTopup } from '../../api/topupApi'
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

const mountTopup = async () => {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/wallet', name: 'wallet', component: { template: '<div />' } },
      { path: '/wallet/top-up', name: 'wallet-top-up', component: { template: '<div />' } },
    ],
  })

  await router.push('/wallet/top-up')
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

    const quickAmount = wrapper.findAll('button').find((button) => button.text() === '+₩30,000')
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
    expect(wrapper.text()).toContain('₩30,000')
    expect(wrapper.text()).toContain('₩114,500')
  })

  it('accumulates quick amount taps instead of replacing the typed amount', async () => {
    const wrapper = await mountTopup()

    await flushPromises()

    await wrapper
      .findAll('button')
      .find((button) => button.text() === '+₩10,000')
      ?.trigger('click')
    await wrapper
      .findAll('button')
      .find((button) => button.text() === '+₩30,000')
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
      .find((button) => button.text() === '+₩10,000')
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

  it('creates a Stripe PaymentIntent before showing the Stripe payment step', async () => {
    const wrapper = await mountTopup()

    await flushPromises()
    await wrapper
      .findAll('button')
      .find((button) => button.text() === '+₩30,000')
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
    expect(wrapper.text()).toContain('Pay ₩30,000')
  })
})
