import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'

import { i18n } from '@/app/i18n'

import WalletQrPaymentCompleteView from '../WalletQrPaymentCompleteView.vue'

type CompletionQuery = {
  scope: string
  appointment?: string
}

function createTestRouter(): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/wallet', name: 'wallet', component: { template: '<div />' } },
      {
        path: '/wallet/qr/payment/complete',
        name: 'wallet-qr-payment-complete',
        component: { template: '<div />' },
      },
      {
        path: '/wallet/qr/payment/preview',
        name: 'wallet-qr-payment-preview',
        component: { template: '<div />' },
      },
    ],
  })
}

async function mountView(
  query: CompletionQuery = { scope: 'shared', appointment: 'seoul-night-tour' },
): Promise<{ router: Router; wrapper: ReturnType<typeof mount> }> {
  const router = createTestRouter()
  await router.push({
    name: 'wallet-qr-payment-complete',
    query,
  })
  await router.isReady()

  const wrapper = mount(WalletQrPaymentCompleteView, {
    global: { plugins: [i18n, router] },
  })

  return { router, wrapper }
}

describe('WalletQrPaymentCompleteView', () => {
  it('shows the completed payment and selected shared expense', async () => {
    const { wrapper } = await mountView()

    expect(wrapper.get('h1').text()).toBe('Payment complete')
    expect(wrapper.text()).toContain('-₩18,500')
    expect(wrapper.text()).toContain('Remaining balance ₩110,000')
    expect(wrapper.text()).toContain('Shared expense · Seoul Night Tour')
    expect(wrapper.get('[role="img"]').attributes('aria-label')).toBe('Payment completed')
  })

  it('returns to the wallet', async () => {
    const { router, wrapper } = await mountView()
    const pushSpy = vi.spyOn(router, 'push')

    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Back to wallet')
      ?.trigger('click')

    expect(pushSpy).toHaveBeenCalledWith({ name: 'wallet' })
  })

  it.each([{ scope: 'shared' }, { scope: 'shared', appointment: 'unknown-appointment' }])(
    'does not treat an invalid shared context as a personal expense',
    async (query) => {
      const { wrapper } = await mountView(query)

      expect(wrapper.text()).toContain('Payment context unavailable')
      expect(wrapper.text()).toContain('Return to payment preview')
      expect(wrapper.text()).not.toContain('Payment complete')
      expect(wrapper.text()).not.toContain('Personal expense')
    },
  )

  it('returns to the payment preview when the context is invalid', async () => {
    const { router, wrapper } = await mountView({ scope: 'shared' })
    const pushSpy = vi.spyOn(router, 'push')

    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Return to payment preview')
      ?.trigger('click')

    expect(pushSpy).toHaveBeenCalledWith({ name: 'wallet-qr-payment-preview' })
  })
})
