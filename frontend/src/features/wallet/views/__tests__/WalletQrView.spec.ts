import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'

import { i18n } from '@/app/i18n'

import { fetchWalletHome } from '../../api/walletApi'
import { listActiveQrPayments } from '../../api/qrPaymentApi'
import WalletQrView from '../WalletQrView.vue'

vi.mock('../../api/walletApi', () => ({
  fetchWalletHome: vi.fn(),
}))

vi.mock('../../api/qrPaymentApi', () => ({
  listActiveQrPayments: vi.fn(),
}))

const seoulFoodTour = {
  qrPaymentCodeId: 1,
  qrToken: 'tok-abc',
  amount: 18_500,
  memo: 'Seoul Food Tour',
  status: 'ACTIVE',
  currencyCode: 'KRW',
  expiresAt: '2026-08-13T14:32:00',
}

const payerEntersAmount = {
  qrPaymentCodeId: 2,
  qrToken: 'tok-def',
  amount: null,
  memo: null,
  status: 'ACTIVE',
  currencyCode: 'KRW',
  expiresAt: '2026-08-13T14:33:00',
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
    ],
  })
}

async function mountView(): Promise<{ router: Router; wrapper: ReturnType<typeof mount> }> {
  const router = createTestRouter()
  await router.push('/wallet/qr')
  await router.isReady()

  const wrapper = mount(WalletQrView, {
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

describe('WalletQrView', () => {
  beforeEach(() => {
    vi.mocked(fetchWalletHome).mockReset()
    vi.mocked(fetchWalletHome).mockResolvedValue({
      balance: 128500,
      availabilityStatus: 'ACTIVE',
      recentTransactions: [],
    })
    vi.mocked(listActiveQrPayments).mockReset()
    vi.mocked(listActiveQrPayments).mockResolvedValue([])
  })

  it('shows an empty state and the current balance when no QR is active', async () => {
    const { wrapper } = await mountView()
    await flushPromises()

    expect(wrapper.get('h1').text()).toBe('QR PAYMENT')
    expect(wrapper.text()).toContain('No QR code yet')
    expect(wrapper.find('img').exists()).toBe(false)
  })

  it('shows a loading state while the active QR list is being fetched', async () => {
    const { wrapper } = await mountView()

    expect(wrapper.text()).not.toContain('No QR code yet')
  })

  it('shows an error state and can retry when the active QR list fails to load', async () => {
    vi.mocked(listActiveQrPayments).mockRejectedValue(new Error('network down'))

    const { wrapper } = await mountView()
    await flushPromises()

    expect(wrapper.text()).toContain('We could not load your active QR codes.')

    vi.mocked(listActiveQrPayments).mockResolvedValue([seoulFoodTour])
    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Try again')
      ?.trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Seoul Food Tour')
  })

  it('shows the most recent active QR image and its real amount, memo, and expiry countdown', async () => {
    vi.mocked(listActiveQrPayments).mockResolvedValue([seoulFoodTour])

    const { wrapper } = await mountView()
    await flushPromises()
    await vi.waitFor(() => {
      if (!wrapper.find('img').exists()) throw new Error('QR image not rendered yet')
    })

    expect(wrapper.text()).toContain('Payment request QR')
    expect(wrapper.text()).toContain('₩18,500')
    expect(wrapper.text()).toContain('Seoul Food Tour')
    expect(wrapper.text()).toMatch(/NA-WA · Expires in \d+:\d{2}/)
    expect(wrapper.text()).toContain('Available balance 128,500 P')

    const image = wrapper.get('img')
    expect(image.attributes('src')).toMatch(/^data:image\/png;base64,/)
  })

  it('shows that the payer can enter an unset amount, and no memo instead of a placeholder', async () => {
    vi.mocked(listActiveQrPayments).mockResolvedValue([payerEntersAmount])

    const { wrapper } = await mountView()
    await flushPromises()

    expect(wrapper.text()).toContain('Amount entered by payer')
    expect(wrapper.text()).toContain('No memo')
  })

  it('lists other active QR codes and switches the detail card when one is tapped', async () => {
    vi.mocked(listActiveQrPayments).mockResolvedValue([seoulFoodTour, payerEntersAmount])

    const { wrapper } = await mountView()
    await flushPromises()

    expect(wrapper.text()).toContain('Seoul Food Tour')
    expect(wrapper.text()).toContain('Other active QR requests')

    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('Amount entered by payer'))
      ?.trigger('click')
    await flushPromises()

    const detailAmount = wrapper.get('dl').text()
    expect(detailAmount).toContain('Amount entered by payer')
  })

  it('opens the QR creation screen from the empty state', async () => {
    const { router, wrapper } = await mountView()
    await flushPromises()
    const pushSpy = vi.spyOn(router, 'push')

    await wrapper
      .findAll('button')
      .find((button) => button.text() === '+ Create QR')
      ?.trigger('click')

    expect(pushSpy).toHaveBeenCalledWith({ name: 'wallet-qr-create' })
  })

  it('opens the QR creation screen from the My QR tab when a QR already exists', async () => {
    vi.mocked(listActiveQrPayments).mockResolvedValue([seoulFoodTour])

    const { router, wrapper } = await mountView()
    await flushPromises()
    const pushSpy = vi.spyOn(router, 'push')

    await wrapper
      .findAll('button')
      .find((button) => button.text() === '+ Create QR')
      ?.trigger('click')

    expect(pushSpy).toHaveBeenCalledWith({ name: 'wallet-qr-create' })
  })

  it('returns to the wallet when the back button is pressed', async () => {
    const { router, wrapper } = await mountView()
    const pushSpy = vi.spyOn(router, 'push')

    await wrapper.get('header button').trigger('click')

    expect(pushSpy).toHaveBeenCalledWith({ name: 'wallet' })
  })

  it('opens the QR scan screen from the scan tab', async () => {
    const { router, wrapper } = await mountView()

    await wrapper.get('a[href="/wallet/qr/scan"]').trigger('click')
    await flushPromises()
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('wallet-qr-scan')
    expect(wrapper.find('[role="tab"]').exists()).toBe(false)
  })
})
