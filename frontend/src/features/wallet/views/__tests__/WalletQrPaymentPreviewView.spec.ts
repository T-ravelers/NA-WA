import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ref, type Ref } from 'vue'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'

import { i18n } from '@/app/i18n'

import { executeQrPayment, previewQrPayment } from '../../api/qrPaymentApi'
import {
  walletAppointmentIntegrationKey,
  type WalletOngoingAppointment,
} from '../../model/appointmentIntegration'
import { useQrPaymentSessionStore } from '../../model/qrPaymentSession'
import WalletQrPaymentPreviewView from '../WalletQrPaymentPreviewView.vue'

vi.mock('../../api/qrPaymentApi', () => ({
  previewQrPayment: vi.fn(),
  executeQrPayment: vi.fn(),
}))

const previewResponse = {
  qrPaymentId: 1,
  payeeName: 'Jieun',
  amount: 18_500,
  currentBalance: 128_500,
  balanceAfter: 110_000,
  currencyCode: 'KRW',
  spendingScope: 'PERSONAL' as const,
  trip: null,
  appointment: null,
  canPay: true,
  expiresAt: '2026-08-13T14:32:00',
}

const executeResponse = {
  transferId: 77,
  qrPaymentId: 1,
  status: 'COMPLETED',
  amount: 18_500,
  balanceAfter: 110_000,
  currencyCode: 'KRW',
  completedAt: '2026-08-13T12:00:00',
}

const seoulNightTour: WalletOngoingAppointment = {
  appointmentId: 42,
  appointmentName: 'Seoul Night Tour',
  activityStartAt: '2026-08-10T18:00:00',
  activityEndAt: '2026-08-12T22:00:00',
}

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
        path: '/wallet/qr/payment/complete/:transferId',
        name: 'wallet-qr-payment-complete',
        component: { template: '<div />' },
      },
    ],
  })
}

interface OngoingAppointmentsQueryState {
  data: Ref<WalletOngoingAppointment[] | undefined>
  isPending: Ref<boolean>
  isError: Ref<boolean>
}

function ongoingAppointmentsQueryState(
  appointments: WalletOngoingAppointment[] = [seoulNightTour],
): OngoingAppointmentsQueryState {
  return {
    data: ref(appointments),
    isPending: ref(false),
    isError: ref(false),
  }
}

async function mountView(
  appointmentsQuery: OngoingAppointmentsQueryState = ongoingAppointmentsQueryState(),
): Promise<{ router: Router; wrapper: ReturnType<typeof mount> }> {
  const router = createTestRouter()
  setActivePinia(createPinia())
  await router.push('/wallet/qr/payment/preview')
  await router.isReady()

  const wrapper = mount(WalletQrPaymentPreviewView, {
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
      provide: {
        [walletAppointmentIntegrationKey as symbol]: {
          useMyOngoingAppointmentsQuery: () => appointmentsQuery,
        },
      },
    },
  })

  return { router, wrapper }
}

function setFixedAmountSession(): void {
  useQrPaymentSessionStore().setSession({
    qrToken: 'tok-abc',
    resolved: {
      qrPaymentId: 1,
      payeeName: 'Jieun',
      amount: 18_500,
      amountInputRequired: false,
      memo: 'Seoul Food Tour',
      status: 'ACTIVE',
      currencyCode: 'KRW',
      expiresAt: '2026-08-13T14:32:00',
    },
  })
}

async function selectSharedExpense(wrapper: ReturnType<typeof mount>): Promise<void> {
  await wrapper
    .findAll('[role="radio"]')
    .find((option) => option.text() === 'Shared')
    ?.trigger('click')
}

describe('WalletQrPaymentPreviewView', () => {
  beforeEach(() => {
    vi.mocked(previewQrPayment).mockReset()
    vi.mocked(executeQrPayment).mockReset()
    vi.mocked(previewQrPayment).mockResolvedValue(previewResponse)
    vi.mocked(executeQrPayment).mockResolvedValue(executeResponse)
  })

  it('shows an empty state when no QR has been scanned', async () => {
    const { router, wrapper } = await mountView()
    const pushSpy = vi.spyOn(router, 'push')

    expect(wrapper.get('h1').text()).toBe('PAYMENT PREVIEW')
    expect(wrapper.text()).toContain('No QR code scanned yet')

    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Scan again')
      ?.trigger('click')

    expect(pushSpy).toHaveBeenCalledWith({ name: 'wallet-qr-scan' })
  })

  it('requests a live preview for a fixed-amount QR and shows the real balances', async () => {
    const { wrapper } = await mountView()
    setFixedAmountSession()
    await flushPromises()

    expect(vi.mocked(previewQrPayment).mock.calls[0]?.[0]).toEqual({
      qrToken: 'tok-abc',
      amount: 18_500,
      spendingScope: 'PERSONAL',
      appointmentId: null,
    })
    expect(wrapper.text()).toContain('Jieun')
    expect(wrapper.text()).toContain('₩128,500')
    expect(wrapper.text()).toContain('₩110,000')
    expect(
      wrapper
        .findAll('button')
        .find((button) => button.text() === 'Pay')
        ?.attributes('disabled'),
    ).toBeUndefined()
  })

  it('shows an amount input when the QR has no fixed amount and previews once a valid amount is entered', async () => {
    const { wrapper } = await mountView()
    useQrPaymentSessionStore().setSession({
      qrToken: 'tok-abc',
      resolved: {
        qrPaymentId: 1,
        payeeName: 'Jieun',
        amount: null,
        amountInputRequired: true,
        memo: '',
        status: 'ACTIVE',
        currencyCode: 'KRW',
        expiresAt: '2026-08-13T14:32:00',
      },
    })
    await flushPromises()

    expect(vi.mocked(previewQrPayment)).not.toHaveBeenCalled()

    await wrapper.get('input[inputmode="numeric"]').setValue('9000')
    await flushPromises()

    expect(vi.mocked(previewQrPayment).mock.calls[0]?.[0]).toEqual({
      qrToken: 'tok-abc',
      amount: 9_000,
      spendingScope: 'PERSONAL',
      appointmentId: null,
    })
  })

  it('keeps Pay disabled for shared expenses until an appointment is selected', async () => {
    const { wrapper } = await mountView()
    setFixedAmountSession()
    await flushPromises()

    await selectSharedExpense(wrapper)
    await flushPromises()

    expect(vi.mocked(previewQrPayment)).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).toContain('Select an appointment to continue.')
    expect(
      wrapper
        .findAll('button')
        .find((button) => button.text() === 'Pay')
        ?.attributes('disabled'),
    ).toBeDefined()
  })

  it('shows an empty-state message when there are no ongoing appointments to link', async () => {
    const { wrapper } = await mountView(ongoingAppointmentsQueryState([]))
    setFixedAmountSession()
    await flushPromises()

    await selectSharedExpense(wrapper)
    await flushPromises()

    expect(wrapper.text()).toContain('You have no ongoing appointments to link this expense to.')
  })

  it('requests a shared-expense preview with the selected appointment id and enables Pay', async () => {
    const { wrapper } = await mountView()
    setFixedAmountSession()
    await flushPromises()
    vi.mocked(previewQrPayment).mockClear()

    await selectSharedExpense(wrapper)
    await wrapper.get('input[value="42"]').setValue(true)
    await flushPromises()

    expect(vi.mocked(previewQrPayment).mock.calls[0]?.[0]).toEqual({
      qrToken: 'tok-abc',
      amount: 18_500,
      spendingScope: 'SHARED',
      appointmentId: 42,
    })
    expect(
      wrapper
        .findAll('button')
        .find((button) => button.text() === 'Pay')
        ?.attributes('disabled'),
    ).toBeUndefined()
  })

  it('executes the payment and navigates to the complete screen with the transfer id', async () => {
    const { router, wrapper } = await mountView()
    setFixedAmountSession()
    await flushPromises()
    const pushSpy = vi.spyOn(router, 'push')

    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Pay')
      ?.trigger('click')
    await flushPromises()

    expect(vi.mocked(executeQrPayment).mock.calls[0]?.[0]).toEqual({
      qrToken: 'tok-abc',
      amount: 18_500,
      spendingScope: 'PERSONAL',
      appointmentId: null,
    })
    expect(vi.mocked(executeQrPayment).mock.calls[0]?.[1]).toEqual(expect.any(String))
    expect(pushSpy).toHaveBeenCalledWith({
      name: 'wallet-qr-payment-complete',
      params: { transferId: '77' },
    })
    expect(useQrPaymentSessionStore().session).toBeNull()
  })

  it('executes a shared-expense payment with the selected appointment id', async () => {
    const { wrapper } = await mountView()
    setFixedAmountSession()
    await flushPromises()

    await selectSharedExpense(wrapper)
    await wrapper.get('input[value="42"]').setValue(true)
    await flushPromises()

    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Pay')
      ?.trigger('click')
    await flushPromises()

    expect(vi.mocked(executeQrPayment).mock.calls[0]?.[0]).toEqual({
      qrToken: 'tok-abc',
      amount: 18_500,
      spendingScope: 'SHARED',
      appointmentId: 42,
    })
  })

  it('shows an error and keeps the session when execution fails', async () => {
    vi.mocked(executeQrPayment).mockRejectedValue(new Error('insufficient balance'))

    const { router, wrapper } = await mountView()
    setFixedAmountSession()
    await flushPromises()
    const pushSpy = vi.spyOn(router, 'push')

    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Pay')
      ?.trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('We could not complete this payment. Please try again.')
    expect(pushSpy).not.toHaveBeenCalledWith(
      expect.objectContaining({ name: 'wallet-qr-payment-complete' }),
    )
    expect(useQrPaymentSessionStore().session).not.toBeNull()
  })
})
