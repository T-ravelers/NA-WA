import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'

import { i18n } from '@/app/i18n'

import { fetchWalletHome } from '../../api/walletApi'
import { useQrRequestDraftStore } from '../../model/qrRequestDraft'
import WalletQrView from '../WalletQrView.vue'

vi.mock('../../api/walletApi', () => ({
  fetchWalletHome: vi.fn(),
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
      { path: '/wallet/qr/scan', name: 'wallet-qr-scan', component: { template: '<div />' } },
    ],
  })
}

async function mountView(): Promise<{ router: Router; wrapper: ReturnType<typeof mount> }> {
  const router = createTestRouter()
  setActivePinia(createPinia())
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
  })

  it('shows the wallet QR preview and current balance', async () => {
    const { wrapper } = await mountView()
    await flushPromises()

    expect(wrapper.get('h1').text()).toBe('QR PAYMENT')
    expect(wrapper.text()).toContain('Payment request QR')
    expect(wrapper.text()).toContain('NA-WA · Valid for 05:00')
    expect(wrapper.text()).toContain('Available balance 128,500 P')
    expect(wrapper.find('[role="img"]').exists()).toBe(true)
    expect(wrapper.find('[role="img"]').findAll('span')).toHaveLength(21 * 21)
    expect(wrapper.text()).toContain('Sandbox mode')
  })

  it('shows local payment request values from the create screen', async () => {
    const router = createTestRouter()
    setActivePinia(createPinia())
    await router.push('/wallet/qr')
    await router.isReady()

    useQrRequestDraftStore().setDraft({
      amount: 18_500,
      memo: 'Seoul Food Tour',
      payerEntersAmount: false,
    })

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
    await flushPromises()

    expect(wrapper.text()).toContain('₩18,500')
    expect(wrapper.text()).toContain('Seoul Food Tour')
  })

  it('shows that the payer can enter an unset amount', async () => {
    const router = createTestRouter()
    setActivePinia(createPinia())
    await router.push('/wallet/qr')
    await router.isReady()

    useQrRequestDraftStore().setDraft({
      amount: null,
      memo: '',
      payerEntersAmount: true,
    })

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
    await flushPromises()

    expect(wrapper.text()).toContain('Amount entered by payer')
  })

  it('shows no memo when the create screen submitted a cleared memo, instead of the placeholder default', async () => {
    const router = createTestRouter()
    setActivePinia(createPinia())
    await router.push('/wallet/qr')
    await router.isReady()

    useQrRequestDraftStore().setDraft({
      amount: 18_500,
      memo: '',
      payerEntersAmount: false,
    })

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
    await flushPromises()

    expect(wrapper.text()).toContain('No memo')
    expect(wrapper.text()).not.toContain('Seoul Night Tour')
  })

  it('opens the QR creation screen from the My QR tab', async () => {
    const { router, wrapper } = await mountView()
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
