import { VueQueryPlugin, QueryClient } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'

import { i18n } from '@/app/i18n'
import { appointmentMemberIntegrationKey } from '../../model/memberIntegration'

const fetchAppointment = vi.fn()
const fetchAppointmentMembers = vi.fn()
const fetchMyAppointmentParticipation = vi.fn()
const joinAppointment = vi.fn()
const cancelAppointmentParticipation = vi.fn()
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
    profileQuery.data.value = { memberId: 11 }
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

  it('disables Join appointment and shows an already-joined notice for the host', async () => {
    const { wrapper } = await mountView()

    const joinButton = wrapper
      .findAll('button')
      .find((button) => button.text() === 'Join appointment')
    const notice = wrapper
      .findAll('p')
      .find((p) => p.text() === 'You have already joined this appointment.')

    // 버튼 title 툴팁은 비활성 버튼·모바일에서 뜨지 않으므로, 이유는 상시
    // 텍스트로 보여준다. 완료된 약속에서도 보일 수 있어 경고색(text-danger)이
    // 아니라 중립색(text-ink-3)이어야 한다.
    expect(notice).toBeDefined()
    expect(notice?.classes()).toContain('text-ink-3')
    expect(notice?.classes()).not.toContain('text-danger')
    expect(joinButton?.attributes('disabled')).toBeDefined()

    await joinButton?.trigger('click')

    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
  })

  it('disables Join appointment when the participation check fails', async () => {
    fetchMyAppointmentParticipation.mockRejectedValue(new Error('network error'))
    const { wrapper } = await mountView()

    const joinButton = wrapper
      .findAll('button')
      .find((button) => button.text() === 'Join appointment')

    expect(wrapper.text()).toContain(
      'We could not check your participation status. Please try again.',
    )
    expect(joinButton?.attributes('disabled')).toBeDefined()
  })

  it('shows a neutral (not red) notice on a normally closed appointment', async () => {
    // 모집 종료(COMPLETED 등)는 정상 상태지 오류가 아니다. 버튼 title
    // 툴팁은 비활성 버튼·모바일에서 뜨지 않으므로 이유는 상시 텍스트로
    // 보여주되, 경고색이 아니라 중립색으로 보여준다.
    fetchAppointment.mockResolvedValue({ ...appointment, appointmentStatus: 'COMPLETED' })
    fetchMyAppointmentParticipation.mockResolvedValue(notJoinedParticipation)
    profileQuery.data.value = { memberId: 99 }
    const { wrapper } = await mountView()

    const joinButton = wrapper
      .findAll('button')
      .find((button) => button.text() === 'Join appointment')
    const notice = wrapper
      .findAll('p')
      .find((p) => p.text() === 'This appointment is not open for joining.')

    expect(notice).toBeDefined()
    expect(notice?.classes()).toContain('text-ink-3')
    expect(notice?.classes()).not.toContain('text-danger')
    expect(joinButton?.attributes('disabled')).toBeDefined()
  })

  it('opens the deposit sheet with an enabled confirm button for a member who has not joined', async () => {
    fetchMyAppointmentParticipation.mockResolvedValue(notJoinedParticipation)
    profileQuery.data.value = { memberId: 99 }
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
    profileQuery.data.value = { memberId: 99 }
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

  it('opens the attendance screen from the detail sheet', async () => {
    fetchAppointment.mockResolvedValueOnce({ ...appointment, appointmentStatus: 'IN_PROGRESS' })
    const { wrapper, router } = await mountView()

    await wrapper.get('button[aria-label="Open appointment menu"]').trigger('click')
    const attendance = menuItem(wrapper)('Attendance')
    expect(attendance?.attributes('disabled')).toBeUndefined()

    await attendance?.trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('appointment-attendance')
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

  it('hides the menu button from someone who never joined', async () => {
    fetchMyAppointmentParticipation.mockResolvedValue(notJoinedParticipation)
    const { wrapper } = await mountView()

    expect(wrapper.find('button[aria-label="Open appointment menu"]').exists()).toBe(false)
  })

  it('hides Leave group from the host', async () => {
    const { wrapper } = await mountView()

    await wrapper.get('button[aria-label="Open appointment menu"]').trigger('click')
    expect(menuItem(wrapper)('Leave group')).toBeUndefined()
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

  // 버거 버튼은 "이 약속의 활성 회원인가"만 본다. 약속 상태나 출석 여부로
  // 갈리지 않는다 — 갈리는 것은 시트 안 항목의 활성 여부다.
  it.each([
    ['COMPLETED', true, 'ATTENDED'],
    ['COMPLETED', false, 'ATTENDED'],
    ['IN_PROGRESS', true, 'PENDING'],
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
