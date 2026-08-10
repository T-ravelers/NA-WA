import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'

import { i18n } from '@/app/i18n'

import WalletQrScanView from '../WalletQrScanView.vue'

function createTestRouter(): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/wallet', name: 'wallet', component: { template: '<div />' } },
      { path: '/wallet/qr', name: 'wallet-qr', component: { template: '<div />' } },
      { path: '/wallet/qr/scan', name: 'wallet-qr-scan', component: { template: '<div />' } },
      {
        path: '/wallet/qr/payment/preview',
        name: 'wallet-qr-payment-preview',
        component: { template: '<div />' },
      },
    ],
  })
}

async function mountView(): Promise<{ router: Router; wrapper: ReturnType<typeof mount> }> {
  const router = createTestRouter()
  await router.push('/wallet/qr/scan')
  await router.isReady()

  const wrapper = mount(WalletQrScanView, {
    global: { plugins: [i18n, router] },
  })

  return { router, wrapper }
}

describe('WalletQrScanView', () => {
  it('shows the QR scanning frame and guidance', async () => {
    const { wrapper } = await mountView()

    expect(wrapper.get('h1').text()).toBe('QR PAYMENT')
    expect(wrapper.text()).toContain('Frame the QR code inside the box.')
    expect(wrapper.text()).toContain('QR code detected')
    expect(wrapper.get('[role="img"]').attributes('aria-label')).toBe('QR code scanning area')
    expect(wrapper.get('[role="tab"][aria-selected="true"]').text()).toBe('Scan QR')
  })

  it('returns to the wallet from the back button', async () => {
    const { router, wrapper } = await mountView()
    const pushSpy = vi.spyOn(router, 'push')

    await wrapper.get('header button').trigger('click')

    expect(pushSpy).toHaveBeenCalledWith({ name: 'wallet' })
  })

  it('opens my QR from the first tab', async () => {
    const { router, wrapper } = await mountView()
    const pushSpy = vi.spyOn(router, 'push')

    await wrapper.get('[role="tab"][aria-selected="false"]').trigger('click')

    expect(pushSpy).toHaveBeenCalledWith({ name: 'wallet-qr' })
  })

  it('opens the payment preview after a QR code is detected', async () => {
    const { router, wrapper } = await mountView()
    const pushSpy = vi.spyOn(router, 'push')

    await wrapper.get('button:not(header button):not([role="tab"])').trigger('click')

    expect(pushSpy).toHaveBeenCalledWith({ name: 'wallet-qr-payment-preview' })
  })
})
