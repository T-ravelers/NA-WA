import { VueQueryPlugin, QueryClient } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'

import { i18n } from '@/app/i18n'
import { appointmentMemberIntegrationKey } from '../../model/memberIntegration'

const fetchAppointment = vi.fn()
const fetchAppointmentMembers = vi.fn()
const joinAppointment = vi.fn()
const profileQuery = {
  data: ref({ memberId: 11 }),
  isPending: ref(false),
  isError: ref(false),
  refetch: vi.fn().mockResolvedValue(undefined),
}

vi.mock('../../api/appointmentApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../api/appointmentApi')>()),
  fetchAppointment: (appointmentId: number) => fetchAppointment(appointmentId),
  fetchAppointmentMembers: (appointmentId: number) => fetchAppointmentMembers(appointmentId),
  joinAppointment: (appointmentId: number) => joinAppointment(appointmentId),
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
  activityStartAt: '2099-08-08T18:30:00',
  activityEndAt: '2099-08-08T22:00:00',
  joinDeadline: '2099-08-08T17:30:00',
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
    attendanceStatus: 'ATTENDED' as const,
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

const leftMember = {
  appointmentMemberId: 3,
  memberId: 13,
  displayName: 'Jamie Lee',
  profileImageUrl: null,
  preferredLanguage: 'vi' as const,
  membershipStatus: 'LEFT' as const,
  attendanceStatus: 'NO_SHOW' as const,
  isHost: false,
}

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
        path: '/appointments/:appointmentId/members/:memberId',
        name: 'appointment-member-profile',
        component: { template: '<div>Profile</div>' },
      },
      {
        path: '/appointments/:appointmentId/attendance',
        name: 'appointment-attendance',
        component: { template: '<div>Attendance</div>' },
      },
      {
        path: '/appointments/:appointmentId/reviews',
        name: 'appointment-reviews',
        component: { template: '<div>Reviews</div>' },
      },
    ],
  })
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  await router.push('/appointments/7')
  await router.isReady()

  const wrapper = mount(AppointmentDetailView, {
    global: {
      plugins: [i18n, router, [VueQueryPlugin, { queryClient }]],
      provide: {
        [appointmentMemberIntegrationKey as symbol]: {
          useMemberProfile: () => profileQuery,
        },
      },
    },
  })
  await flushPromises()
  return { wrapper, router }
}

describe('AppointmentDetailView', () => {
  beforeEach(() => {
    fetchAppointment.mockReset()
    fetchAppointmentMembers.mockReset()
    joinAppointment.mockReset()
    fetchAppointment.mockResolvedValue(appointment)
    fetchAppointmentMembers.mockResolvedValue([...members, leftMember])
    joinAppointment.mockResolvedValue(members[1])
  })

  it('renders appointment details, members, and opens deposit confirmation', async () => {
    const { wrapper } = await mountView()

    expect(wrapper.text()).toContain('Seongsu K-Beauty Tour')
    expect(wrapper.text()).toContain('Seongsu Beauty Lab')
    expect(wrapper.text()).toContain('Mina Park')
    expect(wrapper.text()).toContain('Alex Kim')
    expect(wrapper.text()).not.toContain('Not attended')
    expect(wrapper.text()).toContain('Host')
    expect(wrapper.text()).not.toContain('Jamie Lee')
    expect(wrapper.find('button[aria-label="Open appointment menu"]').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('Confirm attendance')
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
    ).toBeUndefined()
    await wrapper
      .get('[role="dialog"]')
      .findAll('button')
      .find((button) => button.text().includes('Pay'))
      ?.trigger('click')
    expect(joinAppointment).toHaveBeenCalledWith(7)
  })

  it('renders the member cards directly without a View all action', async () => {
    const { wrapper, router } = await mountView()

    expect(wrapper.text()).not.toContain('View all')
    expect(wrapper.findAll('button').filter((button) => button.text() === 'Visit')).toHaveLength(2)

    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Visit')
      ?.trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('appointment-member-profile')
  })

  it('opens the attendance screen from the appointment detail', async () => {
    fetchAppointment.mockResolvedValueOnce({ ...appointment, appointmentStatus: 'IN_PROGRESS' })
    const { wrapper, router } = await mountView()

    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Confirm attendance')
      ?.trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('appointment-attendance')
  })

  it('opens reviews from the detail menu after completion', async () => {
    fetchAppointment.mockResolvedValueOnce({ ...appointment, appointmentStatus: 'COMPLETED' })
    const { wrapper, router } = await mountView()

    await wrapper.get('button[aria-label="Open appointment menu"]').trigger('click')
    expect(wrapper.get('[role="menu"]').text()).toContain('Reviews')
    expect(wrapper.get('[role="menu"]').text()).not.toContain('Attendance')

    await wrapper.get('[role="menuitem"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('appointment-reviews')
  })

  it('does not expose attendance from a completed appointment', async () => {
    fetchAppointment.mockResolvedValueOnce({ ...appointment, appointmentStatus: 'COMPLETED' })
    const { wrapper } = await mountView()

    expect(wrapper.find('button[aria-label="Open appointment menu"]').exists()).toBe(true)
    await wrapper.get('button[aria-label="Open appointment menu"]').trigger('click')
    expect(wrapper.get('[role="menu"]').text()).not.toContain('Attendance')
    expect(wrapper.text()).not.toContain('Confirm attendance')
  })
})
