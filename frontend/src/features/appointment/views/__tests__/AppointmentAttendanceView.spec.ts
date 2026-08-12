import { VueQueryPlugin, QueryClient } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import { computed, ref } from 'vue'

import { i18n } from '@/app/i18n'

const fetchAppointment = vi.fn()
const fetchAppointmentMembers = vi.fn()
const confirmAppointmentAttendance = vi.fn()
const useAppointmentMemberProfileMock = vi.hoisted(() => vi.fn())

vi.mock('../../api/appointmentApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../api/appointmentApi')>()),
  fetchAppointment: (appointmentId: number) => fetchAppointment(appointmentId),
  fetchAppointmentMembers: (appointmentId: number) => fetchAppointmentMembers(appointmentId),
  confirmAppointmentAttendance: (appointmentId: number, request: unknown) =>
    confirmAppointmentAttendance(appointmentId, request),
}))

vi.mock('../../model/memberIntegration', () => ({
  useAppointmentMemberProfile: () => useAppointmentMemberProfileMock(),
}))

const AppointmentAttendanceView = (await import('../AppointmentAttendanceView.vue')).default

const appointment = {
  appointmentId: 7,
  itemId: 42,
  itemType: 'EVENT' as const,
  appointmentName: 'Seongsu K-Beauty Tour',
  languageCode: 'en' as const,
  maxMembers: 4,
  currentMemberCount: 2,
  depositAmount: '10000',
  appointmentStatus: 'IN_PROGRESS' as const,
  meetingPlace: 'Seongsu Beauty Lab',
  activityStartAt: '2026-08-08T18:30:00',
  activityEndAt: '2026-08-08T22:00:00',
  joinDeadline: '2026-08-08T17:30:00',
  hostDisplayName: 'Mina Park',
  meetingAddress: null,
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

const profileMemberId = ref(11)
const profileQuery = {
  data: computed(() => ({ memberId: profileMemberId.value })),
  isPending: ref(false),
  isError: ref(false),
  refetch: vi.fn().mockResolvedValue(undefined),
}

async function mountView() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/appointments/:appointmentId/attendance',
        name: 'appointment-attendance',
        component: AppointmentAttendanceView,
      },
      {
        path: '/appointments/:appointmentId',
        name: 'appointment-detail',
        component: { template: '<div>Detail</div>' },
      },
      {
        path: '/appointments/:appointmentId/reviews',
        name: 'appointment-reviews',
        component: { template: '<div>Reviews</div>' },
      },
    ],
  })
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  await router.push('/appointments/7/attendance')
  await router.isReady()

  const wrapper = mount(AppointmentAttendanceView, {
    global: {
      plugins: [i18n, router, [VueQueryPlugin, { queryClient }]],
    },
  })
  await flushPromises()
  return { wrapper, router }
}

describe('AppointmentAttendanceView', () => {
  beforeEach(() => {
    fetchAppointment.mockReset()
    fetchAppointmentMembers.mockReset()
    confirmAppointmentAttendance.mockReset()
    fetchAppointment.mockResolvedValue(appointment)
    fetchAppointmentMembers.mockResolvedValue(members)
    profileMemberId.value = 11
    useAppointmentMemberProfileMock.mockReset()
    useAppointmentMemberProfileMock.mockReturnValue(profileQuery)
  })

  it('renders attendance controls with a disabled save action until every member is decided', async () => {
    const { wrapper } = await mountView()

    expect(wrapper.text()).toContain('Confirm attendance')
    expect(wrapper.text()).toContain('Mina Park')
    expect(wrapper.text()).toContain('Attended')
    expect(wrapper.text()).toContain('Not attended')
    expect(
      wrapper
        .findAll('button')
        .find((button) => button.text() === 'Attendance checked')
        ?.attributes('disabled'),
    ).toBeDefined()
  })

  it('toggles a pending member to attended locally', async () => {
    const { wrapper } = await mountView()
    const pendingButton = wrapper
      .findAll('button')
      .find((button) => button.text() === 'Not attended')

    await pendingButton?.trigger('click')

    expect(wrapper.text()).toContain('Attended')
  })

  it('keeps the host on the attendance screen until every member is decided', async () => {
    const { wrapper, router } = await mountView()

    expect(router.currentRoute.value.name).toBe('appointment-attendance')
    expect(
      wrapper
        .findAll('button')
        .find((button) => button.text() === 'Attendance checked')
        ?.attributes('disabled'),
    ).toBeDefined()
  })

  it('hides attendance controls from non-host members', async () => {
    profileMemberId.value = 12
    const { wrapper } = await mountView()

    expect(wrapper.text()).toContain('Host access required')
    expect(wrapper.text()).not.toContain('Attendance checked')
  })

  it('blocks attendance before the appointment is in progress', async () => {
    fetchAppointment.mockResolvedValueOnce({ ...appointment, appointmentStatus: 'RECRUITING' })
    const { wrapper } = await mountView()

    expect(wrapper.text()).toContain('Attendance is not available yet')
    expect(wrapper.text()).not.toContain('Attendance checked')
  })
})
