import { VueQueryPlugin, QueryClient } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import { i18n } from '@/app/i18n'

const fetchAppointment = vi.fn()
const fetchAppointmentMembers = vi.fn()
const confirmAppointmentAttendance = vi.fn()
const fetchMyAppointmentParticipation = vi.fn()

vi.mock('../../api/appointmentApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../api/appointmentApi')>()),
  fetchAppointment: (appointmentId: number) => fetchAppointment(appointmentId),
  fetchAppointmentMembers: (appointmentId: number) => fetchAppointmentMembers(appointmentId),
  confirmAppointmentAttendance: (appointmentId: number, request: unknown) =>
    confirmAppointmentAttendance(appointmentId, request),
  fetchMyAppointmentParticipation: (appointmentId: number) =>
    fetchMyAppointmentParticipation(appointmentId),
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
  // 활동이 끝나 출석 확정을 기다리는 약속. 서버가 판정해 내려주는 상태라, 화면은
  // 시각을 다시 재지 않고 이 값으로 게이트를 연다.
  appointmentStatus: 'AWAITING_ATTENDANCE' as const,
  meetingPlace: 'Seongsu Beauty Lab',
  activityStartAt: '2026-08-08T18:30:00',
  activityEndAt: '2026-08-08T22:00:00',
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

const hostParticipation = {
  joined: true,
  appointmentMemberId: 1,
  membershipStatus: 'ACTIVE' as const,
  attendanceStatus: 'PENDING' as const,
  host: true,
}

async function mountView(initialPath = '/appointments/7/attendance') {
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
      {
        path: '/appointments',
        name: 'appointment-list',
        component: { template: '<div>List</div>' },
      },
    ],
  })
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  await router.push(initialPath)
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
    fetchMyAppointmentParticipation.mockReset()
    fetchMyAppointmentParticipation.mockResolvedValue(hostParticipation)
  })

  function toggleFor(wrapper: Awaited<ReturnType<typeof mountView>>['wrapper'], name: string) {
    return wrapper.find(`button[aria-label="Toggle attendance for ${name}"]`)
  }

  function saveButton(wrapper: Awaited<ReturnType<typeof mountView>>['wrapper']) {
    return wrapper.findAll('button').find((button) => button.text() === 'Confirm attendance')
  }

  /** 확정 버튼은 시트를 열 뿐이다. 실제로 보내는 것은 시트 안의 버튼이다. */
  function confirmInSheet(wrapper: Awaited<ReturnType<typeof mountView>>['wrapper']) {
    return wrapper
      .findAll('[role="dialog"] button')
      .find((button) => button.text() === 'Confirm attendance')
  }

  async function saveThroughSheet(wrapper: Awaited<ReturnType<typeof mountView>>['wrapper']) {
    await saveButton(wrapper)?.trigger('click')
    await confirmInSheet(wrapper)?.trigger('click')
    await flushPromises()
  }

  it('starts everyone as not attended and blocks saving until someone is marked', async () => {
    // 방장이 온 사람만 하나씩 눌러 올린다. 아무도 안 누르면 저장할 수 없다.
    const { wrapper } = await mountView()

    expect(toggleFor(wrapper, 'Mina Park').attributes('aria-pressed')).toBe('false')
    expect(toggleFor(wrapper, 'Alex Kim').attributes('aria-pressed')).toBe('false')
    expect(saveButton(wrapper)?.attributes('disabled')).toBeDefined()
  })

  it('keeps an already confirmed attendance as attended', async () => {
    fetchAppointmentMembers.mockResolvedValueOnce([
      { ...members[0], attendanceStatus: 'ATTENDED' as const },
      members[1],
    ])
    const { wrapper } = await mountView()

    expect(toggleFor(wrapper, 'Mina Park').attributes('aria-pressed')).toBe('true')
    expect(toggleFor(wrapper, 'Alex Kim').attributes('aria-pressed')).toBe('false')
  })

  it('marks the host themselves with a badge, not by replacing their name', async () => {
    // 이름을 통째로 "You"로 바꾸면 토글의 aria-label이 쓰는 실명과 어긋나, 눈으로
    // 보는 이름과 스크린 리더가 읽는 이름이 달라진다. 회원 목록과 같은 방식이다.
    const { wrapper } = await mountView()

    expect(wrapper.text()).toContain('Mina Park')
    expect(wrapper.text()).toContain('You')
    expect(toggleFor(wrapper, 'Mina Park').exists()).toBe(true)
  })

  it('says each status once per row, not twice', async () => {
    // 상태는 오른쪽 토글 버튼이 말한다. 카드가 같은 값을 한 번 더 적으면 한 줄에
    // 같은 말이 두 번 남는다.
    const { wrapper } = await mountView()

    const rows = wrapper.findAll('li')
    expect(rows.length).toBeGreaterThan(0)
    for (const row of rows) {
      expect(row.text().split('No-show').length - 1).toBe(1)
    }
  })

  it('toggles a member between not attended and attended', async () => {
    const { wrapper } = await mountView()

    await toggleFor(wrapper, 'Alex Kim').trigger('click')
    expect(toggleFor(wrapper, 'Alex Kim').attributes('aria-pressed')).toBe('true')

    await toggleFor(wrapper, 'Alex Kim').trigger('click')
    expect(toggleFor(wrapper, 'Alex Kim').attributes('aria-pressed')).toBe('false')
  })

  it('sends every active member and returns to the detail screen', async () => {
    const { wrapper, router } = await mountView()

    // 온 사람만 눌러 올린다. 나머지는 기본값 그대로 NO_SHOW로 나간다.
    await toggleFor(wrapper, 'Mina Park').trigger('click')
    await saveThroughSheet(wrapper)

    expect(confirmAppointmentAttendance).toHaveBeenCalledWith(7, {
      members: [
        { memberId: 11, attendanceStatus: 'ATTENDED' },
        { memberId: 12, attendanceStatus: 'NO_SHOW' },
      ],
    })
    expect(router.currentRoute.value.name).toBe('appointment-detail')
  })

  it('consumes its own history entry so one back press leaves the flow', async () => {
    // 확정을 마친 화면으로 되돌아갈 이유가 없다. push로 나가면 이미 저장한 출석 화면이
    // 다시 열리고, replace로 나가면 상세가 두 번 쌓여 뒤로 가기가 같은 화면에 머문다.
    // 자기 자리를 소비해 되감으면 상세에서 한 번만 눌러도 흐름을 벗어난다.
    // memory history를 쓰는 테스트에서는 window.history.length가 라우터의 히스토리를
    // 반영하지 않는다(실제 브라우저에서는 같은 것을 가리킨다). "되감을 자리가 있다"를
    // 만들어 줘야 되감기 경로를 탄다.
    const historyLength = vi.spyOn(window.history, 'length', 'get').mockReturnValue(3)
    const { wrapper, router } = await mountView('/appointments')
    await router.push('/appointments/7')
    await router.push('/appointments/7/attendance')
    await flushPromises()

    await toggleFor(wrapper, 'Mina Park').trigger('click')
    await saveThroughSheet(wrapper)

    expect(router.currentRoute.value.name).toBe('appointment-detail')

    router.back()
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('appointment-list')

    historyLength.mockRestore()
  })

  it('blocks saving when nobody is marked as attended', async () => {
    // 서버가 APPOINTMENT-006으로 거부한다. 나눠 줄 상대가 없어 정산이 성립하지 않는다.
    const { wrapper } = await mountView()

    expect(saveButton(wrapper)?.attributes('disabled')).toBeDefined()
    expect(wrapper.text()).toContain('Mark at least one member as attended.')

    await saveButton(wrapper)?.trigger('click')
    await flushPromises()
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
    expect(confirmAppointmentAttendance).not.toHaveBeenCalled()
  })

  it('asks once more with the counts before confirming, and sends nothing until then', async () => {
    // 확정에는 되돌리는 상태 전이가 없다. 누르자마자 보내면 실수가 그대로 굳는다.
    const { wrapper } = await mountView()

    await toggleFor(wrapper, 'Alex Kim').trigger('click')
    await saveButton(wrapper)?.trigger('click')

    const sheet = wrapper.get('[role="dialog"]')
    expect(sheet.text()).toContain('1 attended \u00b7 1 no-show')
    expect(sheet.text()).toContain('are forfeited and shared among the members who attended')
    expect(sheet.text()).toContain('Attendance cannot be changed once confirmed.')
    expect(confirmAppointmentAttendance).not.toHaveBeenCalled()
  })

  it('sends nothing when the confirmation is dismissed', async () => {
    const { wrapper } = await mountView()

    await toggleFor(wrapper, 'Alex Kim').trigger('click')
    await saveButton(wrapper)?.trigger('click')
    await wrapper
      .findAll('[role="dialog"] button')
      .find((button) => button.text() === 'Go back')
      ?.trigger('click')
    await flushPromises()

    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
    expect(confirmAppointmentAttendance).not.toHaveBeenCalled()
  })

  it('warns the host when they are about to forfeit their own deposit', async () => {
    // 방장도 기본값이 미참석이다. 자기를 올리지 않고 제출하면 자기 돈이 사라진다.
    const { wrapper } = await mountView()

    await toggleFor(wrapper, 'Alex Kim').trigger('click')
    await saveButton(wrapper)?.trigger('click')

    expect(wrapper.get('[role="dialog"]').text()).toContain(
      'You are marked as no-show, so your own deposit will be forfeited too.',
    )
  })

  it('drops the self no-show warning once the host marks themselves as attended', async () => {
    const { wrapper } = await mountView()

    await toggleFor(wrapper, 'Mina Park').trigger('click')
    await saveButton(wrapper)?.trigger('click')

    const sheet = wrapper.get('[role="dialog"]')
    expect(sheet.text()).toContain('1 attended \u00b7 1 no-show')
    expect(sheet.text()).not.toContain('your own deposit will be forfeited')
  })

  it('shows the server error code message when confirmation fails', async () => {
    const { NormalizedApiError } = await import('@/shared/api/apiError')
    confirmAppointmentAttendance.mockRejectedValue(
      new NormalizedApiError('APPOINTMENT-004', 403, 'forbidden'),
    )
    const { wrapper, router } = await mountView()

    await toggleFor(wrapper, 'Mina Park').trigger('click')
    await saveThroughSheet(wrapper)

    expect(wrapper.text()).toContain('Only the appointment host can do this.')
    expect(router.currentRoute.value.name).toBe('appointment-attendance')
  })

  it('says the failure once, not in two live regions at the same time', async () => {
    // 시트는 오류 뒤에도 닫히지 않아 그 자리에서 다시 시도할 수 있다. 하단 바까지
    // 같은 말을 하면 라이브 리전이 둘이 된다.
    const { NormalizedApiError } = await import('@/shared/api/apiError')
    confirmAppointmentAttendance.mockRejectedValue(
      new NormalizedApiError('APPOINTMENT-004', 403, 'forbidden'),
    )
    const { wrapper } = await mountView()

    await toggleFor(wrapper, 'Mina Park').trigger('click')
    await saveThroughSheet(wrapper)

    const alerts = wrapper
      .findAll('[role="alert"]')
      .filter((node) => node.text().includes('Only the appointment host can do this.'))

    expect(alerts).toHaveLength(1)
    expect(wrapper.find('[role="dialog"]').exists()).toBe(true)
  })

  it('hides attendance controls from non-host members', async () => {
    fetchMyAppointmentParticipation.mockResolvedValue({ ...hostParticipation, host: false })
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
