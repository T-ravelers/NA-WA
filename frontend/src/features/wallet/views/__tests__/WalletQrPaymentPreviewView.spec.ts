import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'

import { i18n } from '@/app/i18n'

import WalletQrPaymentPreviewView from '../WalletQrPaymentPreviewView.vue'

function createTestRouter(): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/wallet', name: 'wallet', component: { template: '<div />' } },
      { path: '/wallet/qr/scan', name: 'wallet-qr-scan', component: { template: '<div />' } },
      {
        path: '/wallet/qr/payment/preview',
        name: 'wallet-qr-payment-preview',
        component: { template: '<div />' },
      },
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
  await router.push('/wallet/qr/payment/preview')
  await router.isReady()

  const wrapper = mount(WalletQrPaymentPreviewView, {
    global: { plugins: [i18n, router] },
  })

  return { router, wrapper }
}

describe('WalletQrPaymentPreviewView', () => {
  it('shows the payment details and defaults to a personal expense', async () => {
    const { wrapper } = await mountView()

    expect(wrapper.get('h1').text()).toBe('PAYMENT PREVIEW')
    expect(wrapper.text()).toContain('Seoul Night Tour')
    expect(wrapper.text()).toContain('₩18,500')
    expect(wrapper.text()).toContain('₩110,000')
    expect(wrapper.get('[role="radio"][aria-checked="true"]').text()).toBe('Personal')
    expect(wrapper.text()).not.toContain('Current appointments')
  })

  it('requires an active appointment for a shared expense', async () => {
    const { wrapper } = await mountView()
    const spendingOptions = wrapper.findAll('[role="radio"]')
    const sharedOption = spendingOptions.find((option) => option.text() === 'Shared')

    await sharedOption?.trigger('click')

    expect(wrapper.text()).toContain('Current appointments')
    expect(wrapper.text()).toContain('Select an appointment to continue.')
    expect(
      wrapper
        .findAll('button')
        .find((button) => button.text() === 'Pay')
        ?.attributes('disabled'),
    ).toBeDefined()

    await wrapper.get('input[value="seoul-night-tour"]').setValue(true)

    expect(wrapper.text()).not.toContain('Select an appointment to continue.')
    expect(
      wrapper
        .findAll('button')
        .find((button) => button.text() === 'Pay')
        ?.attributes('disabled'),
    ).toBe(undefined)
  })

  it('opens the completion screen with the selected expense context', async () => {
    const { router, wrapper } = await mountView()
    const pushSpy = vi.spyOn(router, 'push')

    await wrapper
      .findAll('[role="radio"]')
      .find((option) => option.text() === 'Shared')
      ?.trigger('click')
    await wrapper.get('input[value="seoul-night-tour"]').setValue(true)
    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Pay')
      ?.trigger('click')

    expect(pushSpy).toHaveBeenCalledWith({
      name: 'wallet-qr-payment-complete',
      query: { scope: 'shared', appointment: 'seoul-night-tour' },
    })
  })
})
