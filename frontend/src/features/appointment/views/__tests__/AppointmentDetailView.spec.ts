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
  joinAppointment: (appointmentId: number, tripId: number) =>
    joinAppointment(appointmentId, tripId),
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

async function mountView({ path = '/appointments/7' } = {}) {
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
      {
        path: '/explore',
        name: 'explore',
        component: { template: '<div>Explore</div>' },
      },
    ],
  })
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  await router.push(path)
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

/**
 * 회원 목록의 내 행에 붙는 나가기 버튼.
 *
 * 확인 모달의 확정 버튼('Leave group')과 섞이지 않도록 목록 안에서만 찾는다.
 */
function leaveButton(wrapper: MountedWrapper) {
  return wrapper.findAll('li button').find((button) => button.text() === 'Leave')
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
    const statusBadge = wrapper.findAll('span').find((element) => element.text() === 'Recruiting')
    expect(statusBadge?.classes()).toContain('bg-canvas/70')
    // 방장이라 모집 중에도 버거 버튼은 뜬다. 다만 시트 안의 항목은 아직 전부
    // 비활성이다.
    expect(wrapper.find('button[aria-label="Open appointment menu"]').exists()).toBe(true)
  })

  it('writes the date once, and both dates when the activity crosses midnight', async () => {
    // 지금 서버는 활동을 visitDate 하루 위에서만 조립하므로 날짜는 하나다. 그래서
    // 같은 날짜를 두 번 적지 않는다 — 반복되는 값이 정작 다른 값인 시각을 묻는다.
    const sameDay = await mountView()
    expect(sameDay.wrapper.text()).toContain('Aug 8, 2099')
    expect(sameDay.wrapper.text()).not.toContain('Aug 8, 2099 ~ Aug 8, 2099')

    // 그 전제가 깨지면 종료 날짜가 조용히 사라져서는 안 된다. 목록 카드와 같은 규칙이다.
    fetchAppointment.mockResolvedValue({
      ...appointment,
      activityStartAt: '2099-08-08T22:00:00',
      activityEndAt: '2099-08-09T02:00:00',
    })
    const overnight = await mountView()

    expect(overnight.wrapper.text()).toContain('Aug 8, 2099 ~ Aug 9, 2099')
  })

  it('orders members as host, me, then the order they joined', async () => {
    // 서버가 방장 먼저·참여 순으로 내려준다. 여기서 확인하는 것은 "나"가 방장
    // 바로 뒤로 올라오고 나머지 참여 순서는 서버 것 그대로 남는다는 것이다.
    const laterMember = {
      appointmentMemberId: 4,
      memberId: 14,
      displayName: 'Sora Han',
      profileImageUrl: null,
      preferredLanguage: 'en' as const,
      membershipStatus: 'ACTIVE' as const,
      attendanceStatus: 'PENDING' as const,
      isHost: false,
    }
    fetchAppointmentMembers.mockResolvedValue([...members, laterMember])
    fetchMyAppointmentParticipation.mockResolvedValue({
      ...memberParticipation,
      appointmentMemberId: 4,
    })
    const { wrapper } = await mountView()

    expect(wrapper.findAll('li h3').map((name) => name.text())).toEqual([
      'Mina Park',
      'Sora Han',
      'Alex Kim',
    ])
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

  it('leaves for Explore from the bottom action, without stacking the detail', async () => {
    // 하단 CTA는 더 이상 참여가 아니다. 이 화면을 떠나는 동작이라 상세를 히스토리에
    // 남기지 않는다(replace) — 남기면 탐색에서 뒤로 갔을 때 방금 떠난 약속으로 돌아온다.
    const { wrapper, router } = await mountView()
    const replace = vi.spyOn(router, 'replace')

    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Go home')
      ?.trigger('click')
    await flushPromises()

    expect(replace).toHaveBeenCalledWith({ name: 'explore' })
    expect(router.currentRoute.value.name).toBe('explore')
  })

  it('offers no way to join from the detail screen', async () => {
    // 참여 진입점은 약속 목록 카드의 Join 하나다. 상세에 남아 있던 중복 흐름
    // (여정 선택 시트·보증금 시트·충전 프롬프트)은 함께 걷어냈다.
    fetchMyAppointmentParticipation.mockResolvedValue(notJoinedParticipation)
    const { wrapper } = await mountView()

    expect(
      wrapper.findAll('button').find((button) => button.text() === 'Join appointment'),
    ).toBeUndefined()
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
    expect(joinAppointment).not.toHaveBeenCalled()
  })

  it('renders the member cards directly without a View all action', async () => {
    const { wrapper, router } = await mountView()

    expect(wrapper.text()).not.toContain('View all')
    // 기본 participation은 방장이다. 방장 본인 행은 버튼이 없고 나머지 한 명만 Visit이다.
    expect(wrapper.findAll('button').filter((button) => button.text() === 'Visit')).toHaveLength(1)

    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Visit')
      ?.trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('appointment-member-profile')
  })

  it('opens the attendance screen once the activity is over', async () => {
    // 활동 종료 판정은 서버 몫이다 — 종료 후 확정 전이면 appointmentStatus가
    // AWAITING_ATTENDANCE로 온다.
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
  })

  it('keeps leaving out of the burger menu', async () => {
    // 나가기는 회원 목록의 내 행으로 옮겼다. 시트는 "약속"을 대상으로 하는 자리라
    // 자기 참여를 취소하는 것인지 약속을 없애는 것인지 구분되지 않았다.
    fetchMyAppointmentParticipation.mockResolvedValue(memberParticipation)
    const { wrapper } = await mountView()

    expect(leaveButton(wrapper)).toBeDefined()

    await wrapper.get('button[aria-label="Open appointment menu"]').trigger('click')

    expect(menuItem(wrapper)('Leave group')).toBeUndefined()
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

  it('still shows the menu to someone who never joined, with its item disabled', async () => {
    fetchMyAppointmentParticipation.mockResolvedValue(notJoinedParticipation)
    const { wrapper } = await mountView()

    // 참여하지 않았으면 목록에 내 행이 없어 나가기 버튼이 놓일 자리도 없다.
    expect(leaveButton(wrapper)).toBeUndefined()

    await wrapper.get('button[aria-label="Open appointment menu"]').trigger('click')
    const item = menuItem(wrapper)

    expect(item('Attendance')).toBeUndefined()
    expect(item('Reviews')?.attributes('disabled')).toBeDefined()
  })

  it('does not blame the participation check on the screen the user can act on', async () => {
    // 조회가 실패하면 목록에서 어느 행이 내 것인지 알 수 없어 나가기 버튼을 붙일
    // 자리가 없다. 그 사실을 화면 본문에서 말하지는 않는다 — 이제 하단 CTA는 참여가
    // 아니라 이동이라 「참여 상태를 확인하지 못했다」를 걸어 둘 자리가 아니다.
    // 그 사유가 필요한 곳은 버거 메뉴의 출석·후기 항목이고, 아래 테스트들이 맡는다.
    fetchMyAppointmentParticipation.mockRejectedValue(new Error('network error'))
    const { wrapper } = await mountView()

    expect(leaveButton(wrapper)).toBeUndefined()
    expect(wrapper.text()).not.toContain('We could not check your participation status.')
    expect(wrapper.text()).not.toContain('the leave button is unavailable')
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

  it('gives the host no action on their own row', async () => {
    // 방장은 어떤 상태에서도 자기 참여를 취소할 수 없고(APPOINTMENT-007) 자기
    // 프로필을 방문할 일도 없다. 놓을 버튼이 없어 그 칸은 비운다.
    const { wrapper } = await mountView()

    expect(leaveButton(wrapper)).toBeUndefined()
    const hostRow = wrapper.findAll('li').find((row) => row.text().includes('Mina Park'))
    expect(hostRow?.findAll('button')).toHaveLength(0)

    await wrapper.get('button[aria-label="Open appointment menu"]').trigger('click')

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

    await leaveButton(wrapper)?.trigger('click')
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
      'You left this appointment. 10,000 P has been refunded to your wallet.',
    )
  })

  it('lets a member leave during the activity, spelling out the no-show forfeit', async () => {
    // 활동 중(IN_PROGRESS) 탈퇴는 막지 않는다 — 대신 노쇼로 굳어 보증금이
    // 몰수된다는 것을 확인 모달이 예고하고, 완료 토스트도 환급이 아니라
    // 몰수를 말한다.
    fetchAppointment.mockResolvedValueOnce({
      ...appointment,
      appointmentStatus: 'IN_PROGRESS',
    })
    fetchMyAppointmentParticipation.mockResolvedValue(memberParticipation)
    cancelAppointmentParticipation.mockResolvedValue(undefined)
    const { wrapper } = await mountView()

    const leave = leaveButton(wrapper)
    expect(leave?.attributes('disabled')).toBeUndefined()

    await leave?.trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('leaving now counts as a no-show')
    expect(wrapper.text()).not.toContain('will be refunded to your wallet')

    await wrapper
      .findAll('[role="dialog"] button')
      .find((button) => button.text() === 'Leave and forfeit')
      ?.trigger('click')
    await flushPromises()

    expect(cancelAppointmentParticipation).toHaveBeenCalledWith(7)
    expect(toasts.value[toasts.value.length - 1]?.message).toContain('forfeited as a no-show')
  })

  it('says why leaving is blocked only once the button is pressed', async () => {
    // 이유를 회원 이름 옆에 상시로 적어 두면 목록이 안내문으로 찬다. 버튼은 언제나
    // 눌리고, 막히는 이유는 누른 자리에서 한 번만 말한다.
    fetchAppointment.mockResolvedValueOnce({
      ...appointment,
      appointmentStatus: 'AWAITING_ATTENDANCE',
    })
    fetchMyAppointmentParticipation.mockResolvedValue(memberParticipation)
    const { wrapper } = await mountView()

    const leave = leaveButton(wrapper)
    expect(leave?.attributes('disabled')).toBeUndefined()
    expect(wrapper.text()).not.toContain('The activity has ended, so you can no longer leave.')

    await leave?.trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain("You can't leave this appointment")
    expect(wrapper.text()).toContain('The activity has ended, so you can no longer leave.')
    // 확인 모달은 열리지 않는다 — 나가는 흐름 자체가 시작되지 않는다.
    expect(wrapper.text()).not.toContain('Leave this appointment?')
    expect(cancelAppointmentParticipation).not.toHaveBeenCalled()
  })

  it('blames neither the clock nor the cancellation when I am no longer a member', async () => {
    // 버튼은 회원 목록(ACTIVE 행)에서 그려지고 이유는 참여 조회에서 고른다. 두
    // 응답이 어긋나면 이미 나간 사람에게 버튼이 남는데, 그때 "활동이 끝났다"고
    // 하면 이 PR이 참여 쪽에서 고친 것과 같은 거짓말이 나가기 쪽에 남는다.
    fetchMyAppointmentParticipation.mockResolvedValue({
      ...memberParticipation,
      membershipStatus: 'LEFT' as const,
    })
    const { wrapper } = await mountView()

    await leaveButton(wrapper)?.trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('You already left this appointment.')
    expect(wrapper.text()).not.toContain('The activity has ended, so you can no longer leave.')
    expect(wrapper.text()).not.toContain('This appointment was canceled.')
    expect(cancelAppointmentParticipation).not.toHaveBeenCalled()
  })

  it('blames the cancellation, not the clock, on a canceled appointment', async () => {
    fetchAppointment.mockResolvedValueOnce({ ...appointment, appointmentStatus: 'CANCELLED' })
    fetchMyAppointmentParticipation.mockResolvedValue(memberParticipation)
    const { wrapper } = await mountView()

    await leaveButton(wrapper)?.trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('This appointment was canceled.')
    expect(wrapper.text()).not.toContain('The activity has ended, so you can no longer leave.')
  })

  it('refreshes the list and my-appointments caches after leaving', async () => {
    fetchMyAppointmentParticipation.mockResolvedValue(memberParticipation)
    cancelAppointmentParticipation.mockResolvedValue(undefined)
    const { wrapper, queryClient } = await mountView()
    const invalidate = vi.spyOn(queryClient, 'invalidateQueries')

    await leaveButton(wrapper)?.trigger('click')
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
    ['FULL', true, 'PENDING'],
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

  // 폴링만 가짜 시계에 올린다. flushPromises는 setTimeout·setImmediate를 쓰므로
  // 전부 가짜로 만들면 이 테스트가 영영 끝나지 않는다.
  it('follows the server while the detail stays open', async () => {
    vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval'] })

    try {
      const { wrapper } = await mountView()
      const item = menuItem(wrapper)

      expect(wrapper.text()).toContain('Recruiting')
      expect(wrapper.text()).toContain('Alex Kim')

      // 시트를 연 채로 둔다. 상태가 넘어가면 항목의 활성 여부도 같이 따라와야 한다.
      await wrapper.get('button[aria-label="Open appointment menu"]').trigger('click')
      expect(item('Attendance')?.attributes('disabled')).toBeDefined()

      fetchAppointment.mockResolvedValue({
        ...appointment,
        appointmentStatus: 'AWAITING_ATTENDANCE',
      })
      fetchAppointmentMembers.mockResolvedValue([members[0], leftMember])

      vi.advanceTimersByTime(5_000)
      await flushPromises()

      expect(wrapper.text()).toContain('Awaiting attendance')
      expect(wrapper.text()).not.toContain('Alex Kim')
      expect(item('Attendance')?.attributes('disabled')).toBeUndefined()
    } finally {
      vi.useRealTimers()
    }
  })

  it('keeps the detail on screen when a refresh fails', async () => {
    vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval'] })

    try {
      const { wrapper } = await mountView()
      await wrapper.get('button[aria-label="Open appointment menu"]').trigger('click')

      fetchAppointment.mockRejectedValue(new Error('offline'))
      fetchAppointmentMembers.mockRejectedValue(new Error('offline'))
      fetchMyAppointmentParticipation.mockRejectedValue(new Error('offline'))

      vi.advanceTimersByTime(5_000)
      await flushPromises()

      // 세 쿼리 모두 retry를 쓰지 않아 한 번 끊기면 곧바로 실패한다. 그렇다고
      // 보고 있던 상세·회원 목록을 지우거나, 이미 알던 참여 정보를 "확인하지
      // 못했다"로 바꾸지 않는다. 다음 폴링이 성공하면 조용히 되돌아온다.
      expect(wrapper.text()).toContain('Seongsu K-Beauty Tour')
      expect(wrapper.text()).toContain('Alex Kim')
      expect(wrapper.text()).not.toContain('Appointment details could not be loaded')
      expect(wrapper.text()).not.toContain('Members could not be loaded')
      expect(wrapper.text()).not.toContain('We could not check your participation status')
    } finally {
      vi.useRealTimers()
    }
  })
})
