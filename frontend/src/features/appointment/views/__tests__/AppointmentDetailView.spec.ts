import { VueQueryPlugin, QueryClient } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import { i18n } from '@/app/i18n'

const fetchAppointment = vi.fn()
const fetchAppointmentMembers = vi.fn()

vi.mock('../../api/appointmentApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../api/appointmentApi')>()),
  fetchAppointment: (appointmentId: number) => fetchAppointment(appointmentId),
  fetchAppointmentMembers: (appointmentId: number) => fetchAppointmentMembers(appointmentId),
}))

const AppointmentDetailView = (await import('../AppointmentDetailView.vue')).default

const appointment = {
  appointmentId: 7,
  itemId: 42,
  itemType: 'EVENT' as const,
  appointmentName: 'Seongsu K-Beauty Tour',
  languageCode: 'en' as const,
  maxMembers: 4,
  currentMemberCount: 2,
  depositAmount: '10000',
  appointmentStatus: 'RECRUITING' as const,
  meetingPlace: 'Seongsu Beauty Lab',
  meetingAddress: 'Seongsu-ro 12',
  activityStartAt: '2026-08-08T18:30:00',
  activityEndAt: '2026-08-08T22:00:00',
  joinDeadline: '2026-08-08T17:30:00',
  hostDisplayName: 'Mina Park',
  description: null,
  members: [],
}

const members = [
  {
    appointmentMemberId: 1,
    memberId: 11,
    displayName: 'Mina Park',
    profileImageUrl: null,
    preferredLanguage: 'en' as const,
    membershipStatus: 'ACTIVE' as const,
    attendanceStatus: 'PENDING' as const,
    isHost: true,
  },
  {
    appointmentMemberId: 2,
    memberId: 12,
    displayName: 'Alex Kim',
    profileImageUrl: null,
    preferredLanguage: 'ja' as const,
    membershipStatus: 'ACTIVE' as const,
    attendanceStatus: 'PENDING' as const,
    isHost: false,
  },
]

async function mountView() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/appointments/:appointmentId',
        name: 'appointment-detail',
        component: AppointmentDetailView,
      },
      {
        path: '/appointments/:appointmentId/members',
        name: 'appointment-members',
        component: { template: '<div>Members</div>' },
      },
    ],
  })
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  await router.push('/appointments/7')
  await router.isReady()

  const wrapper = mount(AppointmentDetailView, {
    global: {
      plugins: [i18n, router, [VueQueryPlugin, { queryClient }]],
    },
  })
  await flushPromises()
  return { wrapper, router }
}

describe('AppointmentDetailView', () => {
  beforeEach(() => {
    fetchAppointment.mockReset()
    fetchAppointmentMembers.mockReset()
    fetchAppointment.mockResolvedValue(appointment)
    fetchAppointmentMembers.mockResolvedValue(members)
  })

  it('renders appointment details, members, and opens deposit confirmation', async () => {
    const { wrapper } = await mountView()

    expect(wrapper.text()).toContain('Seongsu K-Beauty Tour')
    expect(wrapper.text()).toContain('Seongsu Beauty Lab')
    expect(wrapper.text()).toContain('Mina Park')
    expect(wrapper.text()).toContain('Host')
    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Join appointment')
      ?.trigger('click')

    expect(wrapper.get('[role="dialog"]').text()).toContain('Confirm participation')
    expect(
      wrapper
        .get('[role="dialog"]')
        .findAll('button')
        .find((button) => button.text().includes('Pay'))
        ?.attributes('disabled'),
    ).toBeDefined()
  })

  it('opens the full member screen from the detail view', async () => {
    const { wrapper, router } = await mountView()

    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'View all')
      ?.trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('appointment-members')
  })
})
