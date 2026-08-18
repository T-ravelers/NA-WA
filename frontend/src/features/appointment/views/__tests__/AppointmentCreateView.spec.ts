import { VueQueryPlugin, QueryClient } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import { i18n } from '@/app/i18n'
import { NormalizedApiError } from '@/shared/api/apiError'

const createAppointment = vi.fn()

vi.mock('../../api/appointmentApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../api/appointmentApi')>()),
  createAppointment: (request: unknown) => createAppointment(request),
}))

const AppointmentCreateView = (await import('../AppointmentCreateView.vue')).default

function buttonByText(wrapper: ReturnType<typeof mount>, text: string) {
  const button = wrapper.findAll('button').find((candidate) => candidate.text().includes(text))
  if (button === undefined) throw new Error(`Button not found: ${text}`)
  return button
}

async function mountView() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/appointments/new',
        name: 'appointment-create',
        component: AppointmentCreateView,
      },
      {
        path: '/appointments/:appointmentId',
        name: 'appointment-detail',
        component: { template: '<div>Detail</div>' },
      },
      {
        path: '/appointments',
        name: 'appointment-list',
        component: { template: '<div>List</div>' },
      },
    ],
  })
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  await router.push('/appointments/new?itemId=42&itemType=EVENT')
  await router.isReady()

  const wrapper = mount(AppointmentCreateView, {
    global: {
      plugins: [i18n, router, [VueQueryPlugin, { queryClient }]],
    },
  })
  await flushPromises()
  return { wrapper, router }
}

async function fillAndConfirm(wrapper: ReturnType<typeof mount>): Promise<void> {
  await wrapper
    .find('input[placeholder="e.g. Seongsu K-Beauty Tour"]')
    .setValue('Seongsu K-Beauty Tour')
  await wrapper.get('form').trigger('submit')

  await wrapper.find('input[inputmode="numeric"]').setValue('10000')
  await wrapper.find('input[placeholder="e.g. Seongsu Beauty Lab"]').setValue('Seongsu Beauty Lab')
  await wrapper.get('form').trigger('submit')

  await wrapper.find('input[type="datetime-local"]').setValue('2026-08-08T18:30')
  await wrapper.findAll('input[type="datetime-local"]')[1]?.setValue('2026-08-08T22:00')
  await wrapper.findAll('input[type="datetime-local"]')[2]?.setValue('2026-08-08T17:30')
  await wrapper.get('form').trigger('submit')
  await buttonByText(wrapper, 'Confirm').trigger('click')
}

describe('AppointmentCreateView', () => {
  it('moves to the previous form step before leaving the route', async () => {
    const { wrapper, router } = await mountView()

    await wrapper
      .find('input[placeholder="e.g. Seongsu K-Beauty Tour"]')
      .setValue('Seongsu K-Beauty Tour')
    await wrapper.get('form').trigger('submit')

    await wrapper.find('input[inputmode="numeric"]').setValue('10000')
    await wrapper
      .find('input[placeholder="e.g. Seongsu Beauty Lab"]')
      .setValue('Seongsu Beauty Lab')
    await wrapper.get('form').trigger('submit')

    expect(wrapper.text()).toContain('Set the appointment schedule')

    await wrapper.find('header button').trigger('click')
    expect(wrapper.text()).toContain('Set your appointment details')
    expect(router.currentRoute.value.name).toBe('appointment-create')

    await wrapper.find('header button').trigger('click')
    expect(wrapper.text()).toContain('Start with your appointment details')
    expect(router.currentRoute.value.name).toBe('appointment-create')
  })

  it('creates the appointment and navigates to its detail page on success', async () => {
    createAppointment.mockResolvedValueOnce({
      appointmentId: 42,
      itemId: 42,
      itemType: 'EVENT',
      appointmentName: 'Seongsu K-Beauty Tour',
      languageCode: 'en',
      maxMembers: 4,
      currentMemberCount: 1,
      depositAmount: '10000',
      appointmentStatus: 'RECRUITING',
      meetingPlace: 'Seongsu Beauty Lab',
      activityStartAt: '2026-08-08T18:30:00',
      activityEndAt: '2026-08-08T22:00:00',
      joinDeadline: '2026-08-08T17:30:00',
      hostDisplayName: 'Mina Park',
      meetingAddress: null,
      description: null,
      members: [],
    })
    const { wrapper, router } = await mountView()

    await fillAndConfirm(wrapper)
    await flushPromises()

    expect(createAppointment).toHaveBeenCalledOnce()
    expect(router.currentRoute.value.name).toBe('appointment-detail')
    expect(router.currentRoute.value.params.appointmentId).toBe('42')
  })

  it('shows a generic error message and stays on the form when creation fails', async () => {
    createAppointment.mockRejectedValueOnce(new Error('network error'))
    const { wrapper, router } = await mountView()

    await fillAndConfirm(wrapper)
    await flushPromises()

    expect(wrapper.text()).toContain('Appointment could not be created')
    expect(router.currentRoute.value.name).toBe('appointment-create')
  })

  it('shows an insufficient-balance message when the deposit hold fails', async () => {
    createAppointment.mockRejectedValueOnce(
      new NormalizedApiError('WALLET-015', 409, '지갑 잔액이 부족합니다.'),
    )
    const { wrapper, router } = await mountView()

    await fillAndConfirm(wrapper)
    await flushPromises()

    expect(wrapper.text()).toContain('Your wallet balance is too low for this transfer.')
    expect(router.currentRoute.value.name).toBe('appointment-create')
  })
})
