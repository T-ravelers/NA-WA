import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'

import { i18n } from '@/app/i18n'

import { getQrPaymentStatus } from '../../api/qrPaymentApi'
import WalletQrPaymentCompleteView from '../WalletQrPaymentCompleteView.vue'

vi.mock('../../api/qrPaymentApi', () => ({
  getQrPaymentStatus: vi.fn(),
}))

const statusResponse = {
  transferId: 77,
  status: 'COMPLETED',
  amount: 18_500,
  balanceAfter: 110_000,
  currencyCode: 'KRW',
  completedAt: '2026-08-13T12:00:00',
}

function createTestRouter(): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/wallet', name: 'wallet', component: { template: '<div />' } },
      {
        path: '/wallet/qr/payment/complete/:transferId',
        name: 'wallet-qr-payment-complete',
        component: { template: '<div />' },
      },
    ],
  })
}

async function mountView(
  transferId: string,
): Promise<{ router: Router; wrapper: ReturnType<typeof mount> }> {
  const router = createTestRouter()
  await router.push(`/wallet/qr/payment/complete/${transferId}`)
  await router.isReady()

  const wrapper = mount(WalletQrPaymentCompleteView, {
    global: {
      plugins: [
        i18n,
        router,
        [
          VueQueryPlugin,
          {
            queryClient: new QueryClient({
              defaultOptions: { queries: { retry: false } },
            }),
          },
        ],
      ],
    },
  })

  return { router, wrapper }
}

describe('WalletQrPaymentCompleteView', () => {
  beforeEach(() => {
    vi.mocked(getQrPaymentStatus).mockReset()
  })

  it('shows the completed payment amount and remaining balance', async () => {
    vi.mocked(getQrPaymentStatus).mockResolvedValue(statusResponse)

    const { wrapper } = await mountView('77')
    await flushPromises()

    expect(vi.mocked(getQrPaymentStatus)).toHaveBeenCalledWith(77)
    expect(wrapper.get('h1').text()).toBe('Payment complete')
    expect(wrapper.text()).toContain('-₩18,500')
    expect(wrapper.text()).toContain('Remaining balance ₩110,000')
    expect(wrapper.get('[role="img"]').attributes('aria-label')).toBe('Payment completed')
  })

  it('returns to the wallet from the completed state', async () => {
    vi.mocked(getQrPaymentStatus).mockResolvedValue(statusResponse)

    const { router, wrapper } = await mountView('77')
    await flushPromises()
    const pushSpy = vi.spyOn(router, 'push')

    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Back to wallet')
      ?.trigger('click')

    expect(pushSpy).toHaveBeenCalledWith({ name: 'wallet' })
  })

  it('shows an invalid-context state when the transfer id is not a valid number', async () => {
    const { router, wrapper } = await mountView('not-a-number')
    const pushSpy = vi.spyOn(router, 'push')

    expect(getQrPaymentStatus).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('Payment context unavailable')
    expect(wrapper.text()).not.toContain('Payment complete')

    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Back to wallet')
      ?.trigger('click')

    expect(pushSpy).toHaveBeenCalledWith({ name: 'wallet' })
  })

  it('shows an error state when the payment status cannot be loaded', async () => {
    vi.mocked(getQrPaymentStatus).mockRejectedValue(new Error('not found'))

    const { wrapper } = await mountView('77')
    await flushPromises()

    expect(wrapper.text()).toContain('We could not load this payment')
    expect(wrapper.text()).not.toContain('Payment complete')
  })
})
