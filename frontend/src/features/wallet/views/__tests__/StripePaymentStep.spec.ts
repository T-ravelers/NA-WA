import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest'

import { i18n } from '@/app/i18n'

import { getStripeTopupStatus } from '../../api/topupApi'
import type { StripeTopupStatusResponse } from '../../model/topup'
import StripePaymentStep from '../StripePaymentStep.vue'

import { loadStripe } from '@stripe/stripe-js'

vi.mock('@stripe/stripe-js', () => ({
  loadStripe: vi.fn(),
}))

vi.mock('../../api/topupApi', () => ({
  getStripeTopupStatus: vi.fn(),
}))

const statusResponse: StripeTopupStatusResponse = {
  topupId: 44,
  transactionId: 99,
  status: 'SUCCESS',
  providerStatus: 'succeeded',
  retryable: false,
  sandboxBalance: '114500',
}

describe('StripePaymentStep', () => {
  const stripeTokens = {
    '--color-success-subtle': '#8ec4b1',
    '--color-surface-1': '#262626',
    '--color-ink': '#fbfaf8',
    '--color-danger': '#ed3423',
  }
  const mountPaymentElement = vi.fn()
  const destroyPaymentElement = vi.fn()
  const createPaymentElement = vi.fn(() => ({
    mount: mountPaymentElement,
    destroy: destroyPaymentElement,
  }))
  const confirmPayment = vi.fn()
  const stripe = {
    elements: vi.fn(() => ({ create: createPaymentElement })),
    confirmPayment,
  }

  beforeEach(() => {
    vi.clearAllMocks()
    for (const [name, value] of Object.entries(stripeTokens)) {
      document.documentElement.style.setProperty(name, value)
    }
    vi.stubEnv('VITE_STRIPE_PUBLISHABLE_KEY', 'pk_test_example')
    vi.mocked(loadStripe).mockResolvedValue(stripe as never)
    confirmPayment.mockResolvedValue({ paymentIntent: { status: 'succeeded' } })
    vi.mocked(getStripeTopupStatus).mockResolvedValue(statusResponse)
  })

  afterEach(() => {
    for (const name of Object.keys(stripeTokens)) {
      document.documentElement.style.removeProperty(name)
    }
    vi.unstubAllEnvs()
  })

  it('mounts Stripe Payment Element and confirms payment before checking wallet status', async () => {
    const wrapper = mount(StripePaymentStep, {
      props: {
        clientSecret: 'pi_test_secret',
        topupId: 44,
        amount: '30000',
      },
      global: { plugins: [i18n] },
    })

    await flushPromises()

    expect(loadStripe).toHaveBeenCalledWith('pk_test_example')
    expect(stripe.elements).toHaveBeenCalledWith(
      expect.objectContaining({
        clientSecret: 'pi_test_secret',
        locale: 'en',
        fonts: [expect.objectContaining({ family: 'Noto Sans' })],
      }),
    )
    expect(mountPaymentElement).toHaveBeenCalled()
    expect(wrapper.get('button[type="submit"]').attributes('disabled')).toBeUndefined()

    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(confirmPayment).toHaveBeenCalledWith(
      expect.objectContaining({ redirect: 'if_required' }),
    )
    expect(getStripeTopupStatus).toHaveBeenCalledWith(44)
    expect(wrapper.emitted('payment-confirmed')?.[0]?.[0]).toEqual(statusResponse)
  })

  it('passes the app i18n locale to Stripe Elements instead of letting it auto-detect', async () => {
    i18n.global.locale.value = 'ja'

    mount(StripePaymentStep, {
      props: {
        clientSecret: 'pi_test_secret',
        topupId: 44,
        amount: '30000',
      },
      global: { plugins: [i18n] },
    })

    await flushPromises()

    expect(stripe.elements).toHaveBeenCalledWith(expect.objectContaining({ locale: 'ja' }))

    i18n.global.locale.value = 'en'
  })

  it('fails closed when a required design token is missing', async () => {
    document.documentElement.style.removeProperty('--color-danger')

    const wrapper = mount(StripePaymentStep, {
      props: {
        clientSecret: 'pi_test_secret',
        topupId: 44,
        amount: '30000',
      },
      global: { plugins: [i18n] },
    })

    await flushPromises()

    expect(stripe.elements).not.toHaveBeenCalled()
    expect(wrapper.get('[role="alert"]').text()).toBe(
      i18n.global.t('wallet.topUp.paymentLoadError'),
    )
  })

  it('removes the Stripe developer-tools frame when the step unmounts', async () => {
    // Stripe.js가 이 프레임을 document.body 바로 아래, Vue 트리 바깥에 직접 붙인다
    // (frontend/src/features/wallet/views/StripePaymentStep.vue의 removeDeveloperToolsFrame
    // 참고). 실제 SDK 없이 재현하려고 같은 title로 더미 iframe을 미리 붙여 둔다.
    const devToolsFrame = document.createElement('iframe')
    devToolsFrame.title = 'Stripe developer tools frame'
    document.body.appendChild(devToolsFrame)

    const wrapper = mount(StripePaymentStep, {
      props: {
        clientSecret: 'pi_test_secret',
        topupId: 44,
        amount: '30000',
      },
      global: { plugins: [i18n] },
    })

    await flushPromises()
    expect(document.querySelector('iframe[title="Stripe developer tools frame"]')).not.toBeNull()

    wrapper.unmount()

    expect(document.querySelector('iframe[title="Stripe developer tools frame"]')).toBeNull()
  })
})
