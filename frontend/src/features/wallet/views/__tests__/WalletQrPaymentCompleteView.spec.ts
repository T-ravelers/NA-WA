import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'

import { i18n } from '@/app/i18n'

import WalletQrPaymentCompleteView from '../WalletQrPaymentCompleteView.vue'

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
    ],
  })
}

async function mountView(): Promise<{ router: Router; wrapper: ReturnType<typeof mount> }> {
  const router = createTestRouter()
  await router.push({
    name: 'wallet-qr-payment-complete',
    query: { scope: 'shared', appointment: 'seoul-night-tour' },
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
})
