import { VueQueryPlugin, QueryClient } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'

import { i18n } from '@/app/i18n'
import { NormalizedApiError } from '@/shared/api/apiError'

import { appointmentJourneyIntegrationKey } from '../../model/journeyIntegration'

const createAppointment = vi.fn()
const checkJourneyItemExists = vi.fn()

vi.mock('../../api/appointmentApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../api/appointmentApi')>()),
  createAppointment: (request: unknown) => createAppointment(request),
}))

const AppointmentCreateView = (await import('../AppointmentCreateView.vue')).default

const journeys = [
  { tripId: 7, title: 'Seoul Foodie Week', startDate: '2026-08-01', endDate: '2026-08-31' },
]

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
        path: '/journeys/new',
        name: 'journey-create',
        component: { template: '<div>Journey create</div>' },
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
      provide: {
        [appointmentJourneyIntegrationKey as symbol]: {
          useJourneyListQuery: () => ({
            data: ref(journeys),
            isPending: ref(false),
            isError: ref(false),
          }),
          checkJourneyItemExists,
        },
      },
    },
  })
  await flushPromises()
  return { wrapper, router }
}

async function completeJourneySelection(wrapper: ReturnType<typeof mount>): Promise<void> {
  await buttonByText(wrapper, 'Seoul Foodie Week').trigger('click')
  await flushPromises()
  await buttonByText(wrapper, 'Continue with').trigger('click')
  await flushPromises()
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
  beforeEach(() => {
    createAppointment.mockReset()
    checkJourneyItemExists.mockReset()
    checkJourneyItemExists.mockResolvedValue(false)
  })

  it('opens the journey select sheet on entry and hides the form', async () => {
    const { wrapper } = await mountView()

    expect(wrapper.text()).toContain('Choose a journey')
    expect(wrapper.text()).toContain('Seoul Foodie Week')
    expect(wrapper.find('form').exists()).toBe(false)
  })

  it('moves to the date sheet after selecting a journey, then to the form after choosing a date', async () => {
    const { wrapper } = await mountView()

    await buttonByText(wrapper, 'Seoul Foodie Week').trigger('click')
    expect(wrapper.text()).toContain('Which day?')

    await buttonByText(wrapper, 'Continue with').trigger('click')
    await flushPromises()

    expect(checkJourneyItemExists).toHaveBeenCalledWith(7, 42, expect.any(String))
    expect(wrapper.text()).toContain('Start with your appointment details')
  })

  it('shows an error and keeps the date sheet open when the combination already exists', async () => {
    checkJourneyItemExists.mockResolvedValueOnce(true)
    const { wrapper } = await mountView()

    await buttonByText(wrapper, 'Seoul Foodie Week').trigger('click')
    await buttonByText(wrapper, 'Continue with').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain(
      'This activity is already linked to an appointment on this day.',
    )
    expect(wrapper.text()).toContain('Which day?')
    expect(wrapper.find('form').exists()).toBe(false)
  })

  it('returns to the journey select sheet when the date sheet is closed', async () => {
    const { wrapper } = await mountView()

    await buttonByText(wrapper, 'Seoul Foodie Week').trigger('click')
    expect(wrapper.text()).toContain('Which day?')

    await wrapper.get('button[aria-label="Go back"]').trigger('click')

    expect(wrapper.text()).toContain('Choose a journey')
  })

  it('confirms and leaves the flow when the journey select sheet is closed', async () => {
    const { wrapper, router } = await mountView()

    await wrapper.get('button[aria-label="Close journey selector"]').trigger('click')
    expect(wrapper.text()).toContain('Leave without creating?')

    await buttonByText(wrapper, 'Leave').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('appointment-list')
  })

  it('navigates to journey creation with a return route when there are no journeys', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/appointments/new', name: 'appointment-create', component: AppointmentCreateView },
        { path: '/journeys/new', name: 'journey-create', component: { template: '<div />' } },
      ],
    })
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    await router.push('/appointments/new?itemId=42&itemType=EVENT')
    await router.isReady()

    const wrapper = mount(AppointmentCreateView, {
      global: {
        plugins: [i18n, router, [VueQueryPlugin, { queryClient }]],
        provide: {
          [appointmentJourneyIntegrationKey as symbol]: {
            useJourneyListQuery: () => ({
              data: ref([]),
              isPending: ref(false),
              isError: ref(false),
            }),
            checkJourneyItemExists,
          },
        },
      },
    })
    await flushPromises()

    await buttonByText(wrapper, 'Create a journey').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('journey-create')
    expect(router.currentRoute.value.query).toMatchObject({
      returnRouteName: 'appointment-create',
      itemId: '42',
      itemType: 'EVENT',
    })
  })

  it('moves to the previous form step, then opens an exit confirmation on the first step', async () => {
    const { wrapper, router } = await mountView()
    await completeJourneySelection(wrapper)

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

    await wrapper.find('header button').trigger('click')
    expect(wrapper.text()).toContain('Leave without creating?')
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
    await completeJourneySelection(wrapper)

    await fillAndConfirm(wrapper)
    await flushPromises()

    expect(createAppointment).toHaveBeenCalledOnce()
    expect(router.currentRoute.value.name).toBe('appointment-detail')
    expect(router.currentRoute.value.params.appointmentId).toBe('42')
  })

  it('shows a generic error message and stays on the form when creation fails', async () => {
    createAppointment.mockRejectedValueOnce(new Error('network error'))
    const { wrapper, router } = await mountView()
    await completeJourneySelection(wrapper)

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
    await completeJourneySelection(wrapper)

    await fillAndConfirm(wrapper)
    await flushPromises()

    expect(wrapper.text()).toContain('Your wallet balance is too low for this transfer.')
    expect(router.currentRoute.value.name).toBe('appointment-create')
  })
})
