import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'

import { i18n } from '@/app/i18n'

import { resolvePaymentQr } from '../../api/qrPaymentApi'
import { useQrPaymentSessionStore } from '../../model/qrPaymentSession'
import WalletQrScanView from '../WalletQrScanView.vue'

vi.mock('../../api/qrPaymentApi', () => ({
  resolvePaymentQr: vi.fn(),
}))

vi.mock('vue-qrcode-reader', () => ({
  QrcodeStream: {
    name: 'QrcodeStream',
    props: ['paused', 'formats'],
    emits: ['detect', 'error', 'camera-on', 'camera-off'],
    template: '<div data-testid="qrcode-stream" />',
  },
}))

const resolvedResponse = {
  qrPaymentId: 1,
  payeeName: 'Jieun',
  amount: 18_500,
  amountInputRequired: false,
  memo: 'Seoul Food Tour',
  status: 'ACTIVE',
  currencyCode: 'KRW',
  expiresAt: '2026-08-13T14:32:00',
}

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
  setActivePinia(createPinia())
  await router.push('/wallet/qr/scan')
  await router.isReady()

  const wrapper = mount(WalletQrScanView, {
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

describe('WalletQrScanView', () => {
  beforeEach(() => {
    vi.mocked(resolvePaymentQr).mockReset()
  })

  it('shows the QR scanning frame and guidance', async () => {
    const { wrapper } = await mountView()

    expect(wrapper.get('h1').text()).toBe('QR PAYMENT')
    expect(wrapper.text()).toContain('Frame the QR code inside the box.')
    expect(wrapper.get('[role="img"]').attributes('aria-label')).toBe('QR code scanning area')
    expect(wrapper.find('[data-testid="qrcode-stream"]').exists()).toBe(true)
    expect(wrapper.get('a[href="/wallet/qr/scan"]').text()).toBe('Scan QR')
    expect(wrapper.find('[role="tab"]').exists()).toBe(false)
  })

  it('returns to the wallet from the back button', async () => {
    const { router, wrapper } = await mountView()
    const pushSpy = vi.spyOn(router, 'push')

    await wrapper.get('header button').trigger('click')

    expect(pushSpy).toHaveBeenCalledWith({ name: 'wallet' })
  })

  it('opens my QR from the first tab', async () => {
    const { router, wrapper } = await mountView()

    await wrapper.get('a[href="/wallet/qr"]').trigger('click')
    await flushPromises()
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('wallet-qr')
  })

  it('resolves a detected QR code, shows the payee, and continues to the preview', async () => {
    vi.mocked(resolvePaymentQr).mockResolvedValue(resolvedResponse)

    const { router, wrapper } = await mountView()
    const pushSpy = vi.spyOn(router, 'push')

    await wrapper
      .findComponent({ name: 'QrcodeStream' })
      .vm.$emit('detect', [{ rawValue: 'tok-abc' }])
    await flushPromises()

    expect(vi.mocked(resolvePaymentQr).mock.calls[0]?.[0]).toEqual('tok-abc')
    expect(wrapper.text()).toContain('Jieun')
    expect(useQrPaymentSessionStore().session).toEqual({
      qrToken: 'tok-abc',
      resolved: resolvedResponse,
    })

    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Continue')
      ?.trigger('click')

    expect(pushSpy).toHaveBeenCalledWith({ name: 'wallet-qr-payment-preview' })
  })

  it('shows an error and lets the user rescan when resolving fails', async () => {
    vi.mocked(resolvePaymentQr).mockRejectedValue(new Error('expired'))

    const { wrapper } = await mountView()

    await wrapper
      .findComponent({ name: 'QrcodeStream' })
      .vm.$emit('detect', [{ rawValue: 'tok-abc' }])
    await flushPromises()

    expect(wrapper.text()).toContain('We could not read this QR code. Please try again.')

    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Scan again')
      ?.trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="qrcode-stream"]').exists()).toBe(true)
    expect(wrapper.findComponent({ name: 'QrcodeStream' }).props('paused')).toBe(false)
  })

  it('shows a camera permission fallback when access is denied', async () => {
    const { wrapper } = await mountView()

    await wrapper
      .findComponent({ name: 'QrcodeStream' })
      .vm.$emit('error', { name: 'NotAllowedError', message: 'denied' })
    await flushPromises()

    expect(wrapper.text()).toContain('Camera access was denied')
    expect(wrapper.find('[data-testid="qrcode-stream"]').exists()).toBe(false)
  })

  it('shows a generic camera fallback for unsupported browsers', async () => {
    const { wrapper } = await mountView()

    await wrapper
      .findComponent({ name: 'QrcodeStream' })
      .vm.$emit('error', { name: 'StreamApiNotSupportedError', message: 'unsupported' })
    await flushPromises()

    expect(wrapper.text()).toContain('Camera scanning is not available on this device or browser.')
  })
})
