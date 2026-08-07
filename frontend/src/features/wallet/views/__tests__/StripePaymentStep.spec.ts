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
    vi.stubEnv('VITE_STRIPE_PUBLISHABLE_KEY', 'pk_test_example')
    vi.mocked(loadStripe).mockResolvedValue(stripe as never)
    confirmPayment.mockResolvedValue({ paymentIntent: { status: 'succeeded' } })
    vi.mocked(getStripeTopupStatus).mockResolvedValue(statusResponse)
  })

  afterEach(() => {
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
      expect.objectContaining({ clientSecret: 'pi_test_secret' }),
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
})
