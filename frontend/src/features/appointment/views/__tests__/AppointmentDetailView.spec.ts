import { VueQueryPlugin, QueryClient } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import { i18n } from '@/app/i18n'
import { useToasts } from '@/shared/ui/toast'

const fetchAppointment = vi.fn()
const fetchAppointmentMembers = vi.fn()
const fetchMyAppointmentParticipation = vi.fn()
const joinAppointment = vi.fn()
const cancelAppointmentParticipation = vi.fn()

vi.mock('../../api/appointmentApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../api/appointmentApi')>()),
  fetchAppointment: (appointmentId: number) => fetchAppointment(appointmentId),
  fetchAppointmentMembers: (appointmentId: number) => fetchAppointmentMembers(appointmentId),
  fetchMyAppointmentParticipation: (appointmentId: number) =>
    fetchMyAppointmentParticipation(appointmentId),
  joinAppointment: (appointmentId: number) => joinAppointment(appointmentId),
  cancelAppointmentParticipation: (appointmentId: number) =>
    cancelAppointmentParticipation(appointmentId),
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
      {
        path: '/appointments',
        name: 'appointment-list',
        component: { template: '<div>List</div>' },
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
  return { wrapper, router, queryClient }
}

const notJoinedParticipation = {
  joined: false,
  appointmentMemberId: null,
  membershipStatus: null,
  attendanceStatus: null,
  host: false,
}

const hostParticipation = {
  joined: true,
  appointmentMemberId: 1,
  membershipStatus: 'ACTIVE' as const,
  attendanceStatus: 'ATTENDED' as const,
  host: true,
}

const memberParticipation = {
  joined: true,
  appointmentMemberId: 2,
  membershipStatus: 'ACTIVE' as const,
  attendanceStatus: 'ATTENDED' as const,
  host: false,
}

type MountedWrapper = Awaited<ReturnType<typeof mountView>>['wrapper']

/** 시트 안에서 라벨로 항목 버튼을 찾는다. 라벨 아래에 설명·비활성 사유가 붙는다. */
function menuItem(wrapper: MountedWrapper) {
  return (label: string) =>
    wrapper.findAll('[role="dialog"] button').find((button) => button.text().startsWith(label))
}

const toasts = useToasts()

describe('AppointmentDetailView', () => {
  beforeEach(() => {
    fetchAppointment.mockReset()
    fetchAppointmentMembers.mockReset()
    fetchMyAppointmentParticipation.mockReset()
    joinAppointment.mockReset()
    cancelAppointmentParticipation.mockReset()
    fetchAppointment.mockResolvedValue(appointment)
    fetchAppointmentMembers.mockResolvedValue([...members, leftMember])
    fetchMyAppointmentParticipation.mockResolvedValue(hostParticipation)
  })

  it('renders appointment details and members', async () => {
    const { wrapper } = await mountView()

    expect(wrapper.text()).toContain('Seongsu K-Beauty Tour')
    expect(wrapper.text()).toContain('Seongsu Beauty Lab')
    expect(wrapper.text()).toContain('Mina Park')
    expect(wrapper.text()).toContain('Alex Kim')
    expect(wrapper.text()).not.toContain('Not attended')
    expect(wrapper.text()).toContain('Host')
    expect(wrapper.text()).not.toContain('Jamie Lee')
    // 방장이라 모집 중에도 버거 버튼은 뜬다. 다만 시트 안의 항목은 아직 전부
    // 비활성이다.
    expect(wrapper.find('button[aria-label="Open appointment menu"]').exists()).toBe(true)
  })

  it('goes back to the appointment list for this item when there is no history', async () => {
    // 딥링크·PWA 재진입처럼 되감을 것이 없을 때만 목적지를 정해 보낸다.
    const { wrapper, router } = await mountView()

    await wrapper.get('button[aria-label="Go back"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('appointment-list')
    expect(router.currentRoute.value.query).toEqual({ itemId: '42', itemType: 'EVENT' })
  })

  it('goes back the way you came when there is history', async () => {
    // 여정 타임라인에서 들어왔으면 타임라인으로 돌아가야 한다. 목적지를 고정하면
    // 어디서 왔든 약속 목록으로 튄다.
    const { wrapper, router } = await mountView()
    const back = vi.spyOn(router, 'back').mockImplementation(() => {})
    const historyLength = vi.spyOn(window.history, 'length', 'get').mockReturnValue(3)

    await wrapper.get('button[aria-label="Go back"]').trigger('click')
    await flushPromises()

    expect(back).toHaveBeenCalledOnce()
    expect(router.currentRoute.value.name).toBe('appointment-detail')

    historyLength.mockRestore()
    back.mockRestore()
  })

  it('tells an existing member they are already in, instead of opening the deposit sheet', async () => {
    const { wrapper } = await mountView()
    const joinButton = wrapper
      .findAll('button')
      .find((button) => button.text() === 'Join appointment')

    // 사유는 누르기 전부터 버튼 위에 떠 있다. 눌러 봐야 아는 화면은 제일 큰 CTA를
    // "눌리기는 하는데 아무 일도 없는 버튼"으로 만든다. 비활성으로 두지 않는 것은
    // 모바일이라 hover가 없어 비활성 버튼이 이유를 말할 방법이 없어서다.
    expect(joinButton?.attributes('disabled')).toBeUndefined()
    expect(
      wrapper.findAll('p').find((p) => p.text() === 'You have already joined this appointment.'),
    ).toBeDefined()

    await joinButton?.trigger('click')
    await flushPromises()

    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
  })

  it('says up front that recruiting is over', async () => {
    // 아직 참여하지 않은 사람이어야 모집 종료가 이유로 잡힌다. 이미 참여한
    // 사람에게는 "이미 참여 중"이 먼저 걸린다.
    fetchAppointment.mockResolvedValueOnce({ ...appointment, appointmentStatus: 'CLOSED' })
    fetchMyAppointmentParticipation.mockResolvedValue(notJoinedParticipation)
    const { wrapper } = await mountView()
    const joinButton = wrapper
      .findAll('button')
      .find((button) => button.text() === 'Join appointment')

    expect(joinButton?.attributes('disabled')).toBeUndefined()

    const notice = wrapper
      .findAll('p')
      .find((p) => p.text() === 'This appointment is not open for joining.')

    // 모집 종료는 사용자 잘못이 아닌 정상 상태라 경고색이 아니라 중립색이어야 한다.
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
    expect(notice?.classes()).toContain('text-ink-3')
    expect(notice?.classes()).not.toContain('text-danger')
  })

  it('says up front that the participation check failed', async () => {
    fetchMyAppointmentParticipation.mockRejectedValue(new Error('network error'))
    const { wrapper } = await mountView()

    expect(wrapper.text()).toContain(
      'We could not check your participation status. Please try again.',
    )

    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Join appointment')
      ?.trigger('click')
    await flushPromises()

    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
  })

  it('shows no notice when joining is actually possible', async () => {
    // 사유를 상시로 띄우는 만큼, 막힐 이유가 없을 때 아무 말도 하지 않는 것이
    // 같은 규칙의 반대쪽이다.
    fetchMyAppointmentParticipation.mockResolvedValue(notJoinedParticipation)
    const { wrapper } = await mountView()

    expect(wrapper.text()).not.toContain('This appointment is not open for joining.')
    expect(wrapper.text()).not.toContain('You have already joined this appointment.')
    expect(wrapper.text()).not.toContain('We could not check your participation status.')
  })

  it('shows a neutral (not red) notice on a normally closed appointment', async () => {
    // 모집 종료(COMPLETED 등)는 정상 상태지 오류가 아니다. 버튼 위에 이유는
    // 적되 경고색이 아니라 중립색으로 보여준다.
    fetchAppointment.mockResolvedValue({ ...appointment, appointmentStatus: 'COMPLETED' })
    fetchMyAppointmentParticipation.mockResolvedValue(notJoinedParticipation)
    const { wrapper } = await mountView()

    const notice = wrapper
      .findAll('p')
      .find((p) => p.text() === 'This appointment is not open for joining.')

    expect(notice).toBeDefined()
    expect(notice?.classes()).toContain('text-ink-3')
    expect(notice?.classes()).not.toContain('text-danger')
  })

  it('opens the deposit sheet with an enabled confirm button for a member who has not joined', async () => {
    fetchMyAppointmentParticipation.mockResolvedValue(notJoinedParticipation)
    const { wrapper } = await mountView()

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
  })

  it('joins the appointment and closes the sheet once confirmed', async () => {
    fetchMyAppointmentParticipation.mockResolvedValue(notJoinedParticipation)
    joinAppointment.mockResolvedValue({
      appointmentMemberId: 4,
      memberId: 99,
      displayName: 'Jordan Lee',
      profileImageUrl: null,
      preferredLanguage: 'en',
      membershipStatus: 'PENDING',
      attendanceStatus: 'PENDING',
      isHost: false,
    })
    const { wrapper } = await mountView()

    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Join appointment')
      ?.trigger('click')
    await wrapper
      .get('[role="dialog"]')
      .findAll('button')
      .find((button) => button.text().includes('Pay'))
      ?.trigger('click')
    await flushPromises()

    expect(joinAppointment).toHaveBeenCalledWith(7)
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
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

  it('opens the attendance screen once the activity is over', async () => {
    // 활동 종료 판정은 서버 몫이다 — 종료 후 확정 전이면 appointmentStatus가
    // 표시 전용 AWAITING_ATTENDANCE로 온다.
    fetchAppointment.mockResolvedValueOnce({
      ...appointment,
      appointmentStatus: 'AWAITING_ATTENDANCE',
      activityStartAt: '2020-01-01T10:00:00',
      activityEndAt: '2020-01-01T12:00:00',
    })
    const { wrapper, router } = await mountView()

    await wrapper.get('button[aria-label="Open appointment menu"]').trigger('click')
    const attendance = menuItem(wrapper)('Attendance')
    expect(attendance?.attributes('disabled')).toBeUndefined()

    await attendance?.trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('appointment-attendance')
  })

  it('hides Attendance from a member who is not the host', async () => {
    // 출석 확정은 방장만 할 수 있어(APPOINTMENT-004) 영영 켜지지 않는다.
    fetchMyAppointmentParticipation.mockResolvedValue(memberParticipation)
    const { wrapper } = await mountView()

    await wrapper.get('button[aria-label="Open appointment menu"]').trigger('click')

    expect(menuItem(wrapper)('Attendance')).toBeUndefined()
    expect(menuItem(wrapper)('Reviews')).toBeDefined()
    expect(menuItem(wrapper)('Leave group')).toBeDefined()
  })

  it('opens reviews from the detail sheet after completion', async () => {
    fetchAppointment.mockResolvedValueOnce({ ...appointment, appointmentStatus: 'COMPLETED' })
    const { wrapper, router } = await mountView()

    await wrapper.get('button[aria-label="Open appointment menu"]').trigger('click')
    const item = menuItem(wrapper)
    expect(item('Reviews')?.attributes('disabled')).toBeUndefined()

    await item('Reviews')?.trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('appointment-reviews')
  })

  it('keeps attendance visible but disabled once the appointment is completed', async () => {
    fetchAppointment.mockResolvedValueOnce({ ...appointment, appointmentStatus: 'COMPLETED' })
    const { wrapper } = await mountView()

    await wrapper.get('button[aria-label="Open appointment menu"]').trigger('click')
    const attendance = menuItem(wrapper)('Attendance')

    expect(attendance?.attributes('disabled')).toBeDefined()
    expect(attendance?.text()).toContain('Attendance has already been confirmed.')
  })

  it('still shows the menu to someone who never joined, with every item disabled', async () => {
    fetchMyAppointmentParticipation.mockResolvedValue(notJoinedParticipation)
    const { wrapper } = await mountView()

    await wrapper.get('button[aria-label="Open appointment menu"]').trigger('click')
    const item = menuItem(wrapper)

    expect(item('Attendance')).toBeUndefined()
    expect(item('Reviews')?.attributes('disabled')).toBeDefined()
    expect(item('Leave group')?.text()).toContain('You are not a member of this appointment.')
  })

  it('does not claim you are not a member when the participation check failed', async () => {
    // isActiveMember는 조회 실패와 "회원이 아님"을 구분하지 못한다. 못 읽었을
    // 뿐인데 단정하면 사용자는 자기가 회원이 아니라고 믿는다.
    fetchMyAppointmentParticipation.mockRejectedValue(new Error('network error'))
    const { wrapper } = await mountView()

    await wrapper.get('button[aria-label="Open appointment menu"]').trigger('click')
    const leave = menuItem(wrapper)('Leave group')

    expect(leave?.text()).toContain('We could not check your participation status.')
    expect(leave?.text()).not.toContain('You are not a member of this appointment.')
  })

  it('does not claim you were absent when the participation check failed', async () => {
    fetchAppointment.mockResolvedValueOnce({ ...appointment, appointmentStatus: 'COMPLETED' })
    fetchMyAppointmentParticipation.mockRejectedValue(new Error('network error'))
    const { wrapper } = await mountView()

    await wrapper.get('button[aria-label="Open appointment menu"]').trigger('click')
    const reviews = menuItem(wrapper)('Reviews')

    expect(reviews?.text()).toContain('We could not check your participation status.')
    expect(reviews?.text()).not.toContain('Only members confirmed as attended can write reviews.')
  })

  it('keeps Attendance in the sheet when the host check could not be read', async () => {
    // 조회가 실패하면 isHost가 false에 머문다. 그걸로 항목을 감추면 정작
    // 방장에게서 출석 확정이 통째로 사라진다.
    fetchAppointment.mockResolvedValueOnce({
      ...appointment,
      appointmentStatus: 'AWAITING_ATTENDANCE',
      activityStartAt: '2020-01-01T10:00:00',
      activityEndAt: '2020-01-01T12:00:00',
    })
    fetchMyAppointmentParticipation.mockRejectedValue(new Error('network error'))
    const { wrapper } = await mountView()

    await wrapper.get('button[aria-label="Open appointment menu"]').trigger('click')
    const attendance = menuItem(wrapper)('Attendance')

    expect(attendance).toBeDefined()
    expect(attendance?.attributes('disabled')).toBeDefined()
    expect(attendance?.text()).toContain('We could not check your participation status.')
  })

  it('hides Leave group from the host', async () => {
    // 방장은 어떤 상태에서도 자기 참여를 취소할 수 없어(APPOINTMENT-007) 비활성으로
    // 둬도 영영 켜지지 않는다.
    const { wrapper } = await mountView()

    await wrapper.get('button[aria-label="Open appointment menu"]').trigger('click')

    expect(menuItem(wrapper)('Leave group')).toBeUndefined()
    expect(menuItem(wrapper)('Attendance')).toBeDefined()
    expect(menuItem(wrapper)('Reviews')).toBeDefined()
  })

  it('keeps attendance disabled while the activity is still running', async () => {
    fetchAppointment.mockResolvedValueOnce({
      ...appointment,
      appointmentStatus: 'IN_PROGRESS',
      activityStartAt: '2020-01-01T10:00:00',
      activityEndAt: '2099-01-01T12:00:00',
    })
    const { wrapper } = await mountView()

    await wrapper.get('button[aria-label="Open appointment menu"]').trigger('click')
    const attendance = menuItem(wrapper)('Attendance')

    expect(attendance?.attributes('disabled')).toBeDefined()
    expect(attendance?.text()).toContain('Opens once the activity ends.')
  })

  it('leaves the appointment after confirming, and refunds are spelled out', async () => {
    fetchMyAppointmentParticipation.mockResolvedValue(memberParticipation)
    cancelAppointmentParticipation.mockResolvedValue(undefined)
    const { wrapper } = await mountView()

    await wrapper.get('button[aria-label="Open appointment menu"]').trigger('click')
    await menuItem(wrapper)('Leave group')?.trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Leave this appointment?')
    expect(wrapper.text()).toContain('will be refunded to your wallet')

    await wrapper
      .findAll('[role="dialog"] button')
      .find((button) => button.text() === 'Leave group')
      ?.trigger('click')
    await flushPromises()

    expect(cancelAppointmentParticipation).toHaveBeenCalledWith(7)
    // 모달만 조용히 닫히면 나간 것인지, 보증금이 돌아왔는지 알 수 없다.
    expect(toasts.value[toasts.value.length - 1]?.message).toBe(
      'You left this appointment. ₩10,000 has been refunded to your wallet.',
    )
  })

  it('stops saying you already joined once you have left', async () => {
    // 나갔는데 안내가 남으면 같은 화면에서 "이미 참여했다"와 환급 토스트가
    // 서로 반대되는 말을 한다.
    fetchMyAppointmentParticipation.mockResolvedValue(memberParticipation)
    cancelAppointmentParticipation.mockImplementation(() => {
      fetchMyAppointmentParticipation.mockResolvedValue(notJoinedParticipation)
      return Promise.resolve()
    })
    const { wrapper } = await mountView()

    expect(wrapper.text()).toContain('You have already joined this appointment.')

    await wrapper.get('button[aria-label="Open appointment menu"]').trigger('click')
    await menuItem(wrapper)('Leave group')?.trigger('click')
    await flushPromises()
    await wrapper
      .findAll('[role="dialog"] button')
      .find((button) => button.text() === 'Leave group')
      ?.trigger('click')
    await flushPromises()

    expect(wrapper.text()).not.toContain('You have already joined this appointment.')
  })

  it('disables Leave group once the join deadline has passed', async () => {
    fetchAppointment.mockResolvedValueOnce({
      ...appointment,
      appointmentStatus: 'IN_PROGRESS',
      joinDeadline: '2020-01-01T00:00:00',
    })
    fetchMyAppointmentParticipation.mockResolvedValue(memberParticipation)
    const { wrapper } = await mountView()

    await wrapper.get('button[aria-label="Open appointment menu"]').trigger('click')
    const leave = menuItem(wrapper)('Leave group')

    expect(leave?.attributes('disabled')).toBeDefined()
    expect(leave?.text()).toContain('The join deadline has passed')
  })

  it('refreshes the list and my-appointments caches after leaving', async () => {
    fetchMyAppointmentParticipation.mockResolvedValue(memberParticipation)
    cancelAppointmentParticipation.mockResolvedValue(undefined)
    const { wrapper, queryClient } = await mountView()
    const invalidate = vi.spyOn(queryClient, 'invalidateQueries')

    await wrapper.get('button[aria-label="Open appointment menu"]').trigger('click')
    await menuItem(wrapper)('Leave group')?.trigger('click')
    await flushPromises()
    await wrapper
      .findAll('[role="dialog"] button')
      .find((button) => button.text() === 'Leave group')
      ?.trigger('click')
    await flushPromises()

    // 상세만 고치면 목록 카드의 인원 수와 지갑 QR 결제의 약속 선택이 어긋난 채 남는다.
    const invalidated = invalidate.mock.calls.map((call) => {
      const filters = typeof call[0] === 'function' ? call[0]() : call[0]
      return JSON.stringify(filters?.queryKey)
    })
    expect(invalidated).toContain(JSON.stringify(['appointments', 'list']))
    expect(invalidated).toContain(JSON.stringify(['appointments', 'mine']))
  })

  // 버거 버튼은 약속 상세를 받았으면 언제나 뜬다. 상태·방장 여부·참여 여부로
  // 갈리지 않는다 — 갈리는 것은 시트 안 항목의 활성 여부다.
  it.each([
    ['COMPLETED', true, 'ATTENDED'],
    ['COMPLETED', false, 'ATTENDED'],
    ['IN_PROGRESS', true, 'PENDING'],
    ['AWAITING_ATTENDANCE', true, 'PENDING'],
    ['CLOSED', true, 'PENDING'],
    ['RECRUITING', false, 'PENDING'],
    ['CANCELLED', false, 'PENDING'],
  ] as const)(
    'shows the menu button on a %s appointment (host=%s, attendance=%s)',
    async (appointmentStatus, host, attendanceStatus) => {
      fetchAppointment.mockResolvedValueOnce({ ...appointment, appointmentStatus })
      fetchMyAppointmentParticipation.mockResolvedValue({
        joined: true,
        appointmentMemberId: 1,
        membershipStatus: 'ACTIVE' as const,
        attendanceStatus,
        host,
      })
      const { wrapper } = await mountView()

      expect(wrapper.find('button[aria-label="Open appointment menu"]').exists()).toBe(true)

      await wrapper.get('button[aria-label="Open appointment menu"]').trigger('click')
      expect(wrapper.find('[role="dialog"]').exists()).toBe(true)
    },
  )

  it('does not show the menu button while the detail is still loading', async () => {
    // 버튼만 먼저 뜨면 눌러도 시트가 열리지 않는다. 시트는 약속 이름과 보증금이
    // 필요해 상세를 받은 뒤에만 렌더된다.
    let resolveDetail: (value: unknown) => void = () => {}
    fetchAppointment.mockReturnValueOnce(
      new Promise((resolve) => {
        resolveDetail = resolve
      }),
    )
    const { wrapper } = await mountView()

    expect(wrapper.find('button[aria-label="Open appointment menu"]').exists()).toBe(false)

    resolveDetail(appointment)
    await flushPromises()

    expect(wrapper.find('button[aria-label="Open appointment menu"]').exists()).toBe(true)
  })

  it('closes the menu sheet on Escape', async () => {
    const { wrapper } = await mountView()

    await wrapper.get('button[aria-label="Open appointment menu"]').trigger('click')
    expect(wrapper.find('[role="dialog"]').exists()).toBe(true)

    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    await flushPromises()

    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
  })
})
