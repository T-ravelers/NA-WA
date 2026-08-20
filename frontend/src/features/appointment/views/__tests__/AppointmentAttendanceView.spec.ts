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
    confirmAppointmentAttendance.mockResolvedValue(undefined)
    fetchAppointment.mockResolvedValue(appointment)
    fetchAppointmentMembers.mockResolvedValue(members)
    profileMemberId.value = 11
    useAppointmentMemberProfileMock.mockReset()
    useAppointmentMemberProfileMock.mockReturnValue(profileQuery)
  })

  function toggleFor(wrapper: Awaited<ReturnType<typeof mountView>>['wrapper'], name: string) {
    return wrapper.find(`button[aria-label="Toggle attendance for ${name}"]`)
  }

  function saveButton(wrapper: Awaited<ReturnType<typeof mountView>>['wrapper']) {
    return wrapper.findAll('button').find((button) => button.text() === 'Confirm attendance')
  }

  it('starts everyone as attended so a no-show is always a deliberate choice', async () => {
    // NO_SHOW는 보증금을 몰수해 참석자에게 나누는 처리라 기본값이 되면 안 된다.
    const { wrapper } = await mountView()

    expect(toggleFor(wrapper, 'Mina Park').attributes('aria-pressed')).toBe('true')
    expect(toggleFor(wrapper, 'Alex Kim').attributes('aria-pressed')).toBe('true')
    expect(saveButton(wrapper)?.attributes('disabled')).toBeUndefined()
  })

  it('toggles a member between attended and no-show', async () => {
    const { wrapper } = await mountView()
    const toggle = toggleFor(wrapper, 'Alex Kim')

    await toggle.trigger('click')
    expect(toggleFor(wrapper, 'Alex Kim').attributes('aria-pressed')).toBe('false')

    await toggleFor(wrapper, 'Alex Kim').trigger('click')
    expect(toggleFor(wrapper, 'Alex Kim').attributes('aria-pressed')).toBe('true')
  })

  it('sends every active member and returns to the detail screen', async () => {
    const { wrapper, router } = await mountView()

    await toggleFor(wrapper, 'Alex Kim').trigger('click')
    await saveButton(wrapper)?.trigger('click')
    await flushPromises()

    expect(confirmAppointmentAttendance).toHaveBeenCalledWith(7, {
      members: [
        { memberId: 11, attendanceStatus: 'ATTENDED' },
        { memberId: 12, attendanceStatus: 'NO_SHOW' },
      ],
    })
    expect(router.currentRoute.value.name).toBe('appointment-detail')
  })

  it('blocks saving when nobody is marked as attended', async () => {
    // 서버가 APPOINTMENT-006으로 거부한다. 나눠 줄 상대가 없어 정산이 성립하지 않는다.
    const { wrapper } = await mountView()

    await toggleFor(wrapper, 'Mina Park').trigger('click')
    await toggleFor(wrapper, 'Alex Kim').trigger('click')

    expect(saveButton(wrapper)?.attributes('disabled')).toBeDefined()
    expect(wrapper.text()).toContain('Mark at least one member as attended.')

    await saveButton(wrapper)?.trigger('click')
    await flushPromises()
    expect(confirmAppointmentAttendance).not.toHaveBeenCalled()
  })

  it('shows the server error code message when confirmation fails', async () => {
    const { NormalizedApiError } = await import('@/shared/api/apiError')
    confirmAppointmentAttendance.mockRejectedValue(
      new NormalizedApiError('APPOINTMENT-004', 403, 'forbidden'),
    )
    const { wrapper, router } = await mountView()

    await saveButton(wrapper)?.trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Only the appointment host can do this.')
    expect(router.currentRoute.value.name).toBe('appointment-attendance')
  })

  it('hides attendance controls from non-host members', async () => {
    profileMemberId.value = 12
    const { wrapper } = await mountView()

    expect(wrapper.text()).toContain('Host access required')
    expect(saveButton(wrapper)).toBeUndefined()
  })

  it('blocks attendance before the activity starts', async () => {
    fetchAppointment.mockResolvedValueOnce({ ...appointment, appointmentStatus: 'RECRUITING' })
    const { wrapper } = await mountView()

    expect(wrapper.text()).toContain('Attendance is not open')
    expect(saveButton(wrapper)).toBeUndefined()
  })
})
