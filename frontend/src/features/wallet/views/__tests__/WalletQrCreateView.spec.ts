import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'

import { i18n } from '@/app/i18n'

import WalletQrCreateView from '../WalletQrCreateView.vue'

function createTestRouter(): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/wallet', name: 'wallet', component: { template: '<div />' } },
      { path: '/wallet/qr', name: 'wallet-qr', component: { template: '<div />' } },
      {
        path: '/wallet/qr/create',
        name: 'wallet-qr-create',
        component: { template: '<div />' },
      },
    ],
  })
}

async function mountView(): Promise<{ router: Router; wrapper: ReturnType<typeof mount> }> {
  const router = createTestRouter()
  await router.push('/wallet/qr/create')
  await router.isReady()

  const wrapper = mount(WalletQrCreateView, {
    global: { plugins: [i18n, router] },
  })

  return { router, wrapper }
}

describe('WalletQrCreateView', () => {
  it('shows the local QR request form', async () => {
    const { wrapper } = await mountView()

    expect(wrapper.get('h1').text()).toBe('CREATE PAYMENT QR')
    expect(wrapper.text()).toContain('Create a payment request')
    expect(wrapper.text()).toContain('Let the payer enter the amount')
    expect(wrapper.text()).toContain('Create QR')
  })

  it('passes the entered amount and memo to the local QR result route', async () => {
    const { router, wrapper } = await mountView()
    const pushSpy = vi.spyOn(router, 'push')

    await wrapper.get('input[type="text"]:not([inputmode])').setValue('Seoul Food Tour')
    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Create QR')
      ?.trigger('click')
    await flushPromises()

    expect(pushSpy).toHaveBeenCalledWith({
      name: 'wallet-qr',
      query: { amount: '18500', amountMode: 'fixed', memo: 'Seoul Food Tour' },
    })
  })

  it('supports a request where the payer enters the amount', async () => {
    const { router, wrapper } = await mountView()
    const pushSpy = vi.spyOn(router, 'push')

    await wrapper.get('input[type="checkbox"]').setValue(true)
    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Create QR')
      ?.trigger('click')
    await flushPromises()

    expect(pushSpy).toHaveBeenCalledWith({
      name: 'wallet-qr',
      query: { amount: 'payer', amountMode: 'payer', memo: 'Seoul Night Tour' },
    })
  })

  it('disables QR creation when a fixed amount is empty', async () => {
    const { wrapper } = await mountView()

    await wrapper.get('input[inputmode="numeric"]').setValue('')

    expect(
      wrapper
        .findAll('button')
        .find((button) => button.text() === 'Create QR')
        ?.attributes('disabled'),
    ).toBeDefined()
  })
})
