import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'

import { i18n } from '@/app/i18n'

import { useQrRequestDraftStore } from '../../model/qrRequestDraft'
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
  setActivePinia(createPinia())
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

  it('saves the entered amount and memo as the local QR request draft, without a URL query', async () => {
    const { router, wrapper } = await mountView()
    const pushSpy = vi.spyOn(router, 'push')

    await wrapper.get('input[type="text"]:not([inputmode])').setValue('Seoul Food Tour')
    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Create QR')
      ?.trigger('click')
    await flushPromises()

    expect(useQrRequestDraftStore().draft).toEqual({
      amount: 18_500,
      memo: 'Seoul Food Tour',
      payerEntersAmount: false,
    })
    expect(pushSpy).toHaveBeenCalledWith({ name: 'wallet-qr' })
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

    expect(useQrRequestDraftStore().draft).toEqual({
      amount: null,
      memo: 'Seoul Night Tour',
      payerEntersAmount: true,
    })
    expect(pushSpy).toHaveBeenCalledWith({ name: 'wallet-qr' })
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

  it('disables QR creation when the amount exceeds the safe integer range', async () => {
    const { wrapper } = await mountView()

    await wrapper.get('input[inputmode="numeric"]').setValue('99999999999999999')

    expect(
      wrapper
        .findAll('button')
        .find((button) => button.text() === 'Create QR')
        ?.attributes('disabled'),
    ).toBeDefined()
  })

  it('disables QR creation when the amount overflows to Infinity', async () => {
    const { wrapper } = await mountView()

    await wrapper.get('input[inputmode="numeric"]').setValue('9'.repeat(400))

    expect(
      wrapper
        .findAll('button')
        .find((button) => button.text() === 'Create QR')
        ?.attributes('disabled'),
    ).toBeDefined()
  })

  it('disables QR creation when the amount exceeds the DB column max (DECIMAL(19,4))', async () => {
    const { wrapper } = await mountView()

    await wrapper.get('input[inputmode="numeric"]').setValue('1000000000000000')

    expect(
      wrapper
        .findAll('button')
        .find((button) => button.text() === 'Create QR')
        ?.attributes('disabled'),
    ).toBeDefined()
  })

  it('saves a cleared memo as an empty string rather than the placeholder default', async () => {
    const { wrapper } = await mountView()

    await wrapper.get('input[type="text"]:not([inputmode])').setValue('')
    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Create QR')
      ?.trigger('click')
    await flushPromises()

    expect(useQrRequestDraftStore().draft).toEqual({
      amount: 18_500,
      memo: '',
      payerEntersAmount: false,
    })
  })
})
