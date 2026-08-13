import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'

import { i18n } from '@/app/i18n'

import { createPaymentQr } from '../../api/qrPaymentApi'
import { qrPaymentKeys } from '../../model/qrPayment'
import WalletQrCreateView from '../WalletQrCreateView.vue'

vi.mock('../../api/qrPaymentApi', () => ({
  createPaymentQr: vi.fn(),
}))

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

async function mountView(): Promise<{
  router: Router
  wrapper: ReturnType<typeof mount>
  queryClient: QueryClient
}> {
  const router = createTestRouter()
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  await router.push('/wallet/qr/create')
  await router.isReady()

  const wrapper = mount(WalletQrCreateView, {
    global: {
      plugins: [i18n, router, [VueQueryPlugin, { queryClient }]],
    },
  })

  return { router, wrapper, queryClient }
}

describe('WalletQrCreateView', () => {
  beforeEach(() => {
    vi.mocked(createPaymentQr).mockReset()
    vi.mocked(createPaymentQr).mockImplementation((request) =>
      Promise.resolve({
        qrPaymentCodeId: 1,
        qrToken: 'tok-abc',
        amount: request.amount,
        memo: request.memo,
        status: 'ACTIVE',
        currencyCode: 'KRW',
        expiresAt: '2026-08-13T12:00:00',
      }),
    )
  })

  it('shows the QR request form', async () => {
    const { wrapper } = await mountView()

    expect(wrapper.get('h1').text()).toBe('CREATE PAYMENT QR')
    expect(wrapper.text()).toContain('Create a payment request')
    expect(wrapper.text()).toContain('Let the payer enter the amount')
    expect(wrapper.text()).toContain('Create QR')
  })

  it('creates the QR via the API, refreshes the active QR list, and returns to My QR', async () => {
    const { router, wrapper, queryClient } = await mountView()
    const pushSpy = vi.spyOn(router, 'push')
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries')

    await wrapper.get('input[type="text"]:not([inputmode])').setValue('Seoul Food Tour')
    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Create QR')
      ?.trigger('click')
    await flushPromises()

    expect(vi.mocked(createPaymentQr).mock.calls[0]?.[0]).toEqual({
      amount: 18_500,
      memo: 'Seoul Food Tour',
    })
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: qrPaymentKeys.active() })
    expect(pushSpy).toHaveBeenCalledWith({ name: 'wallet-qr' })
  })

  it('supports a request where the payer enters the amount', async () => {
    const { wrapper } = await mountView()

    await wrapper.get('input[type="checkbox"]').setValue(true)
    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Create QR')
      ?.trigger('click')
    await flushPromises()

    expect(vi.mocked(createPaymentQr).mock.calls[0]?.[0]).toEqual({
      amount: null,
      memo: 'Seoul Night Tour',
    })
  })

  it('sends a cleared memo as null', async () => {
    const { wrapper } = await mountView()

    await wrapper.get('input[type="text"]:not([inputmode])').setValue('')
    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Create QR')
      ?.trigger('click')
    await flushPromises()

    expect(vi.mocked(createPaymentQr).mock.calls[0]?.[0]).toEqual({
      amount: 18_500,
      memo: null,
    })
  })

  it('shows an error message and keeps the form when QR creation fails', async () => {
    vi.mocked(createPaymentQr).mockRejectedValue(new Error('network down'))

    const { router, wrapper } = await mountView()
    const pushSpy = vi.spyOn(router, 'push')

    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Create QR')
      ?.trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('We could not create this QR code. Please try again.')
    expect(pushSpy).not.toHaveBeenCalled()
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
})
