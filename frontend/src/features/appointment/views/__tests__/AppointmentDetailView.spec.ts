import { VueQueryPlugin, QueryClient } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'

import { i18n } from '@/app/i18n'
import { NormalizedApiError } from '@/shared/api/apiError'

import { appointmentJourneyIntegrationKey } from '../../model/journeyIntegration'
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

async function mountView({ journeys: list = journeys, path = '/appointments/7' } = {}) {
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
        path: '/journeys/new',
        name: 'journey-create',
        component: { template: '<div>Journey create</div>' },
      },
      {
        path: '/wallet/top-up',
        name: 'wallet-top-up',
        component: { template: '<div>Top up</div>' },
      },
    ],
  })
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  await router.push(path)
  await router.isReady()

  const wrapper = mount(AppointmentDetailView, {
    global: {
      plugins: [i18n, router, [VueQueryPlugin, { queryClient }]],
      provide: {
        [appointmentJourneyIntegrationKey as symbol]: {
          // 참여도 여정을 고른다. 약속 날짜(2026-08-31)를 품는 여정 하나를 둔다.
          useJourneyListQuery: () => ({
            data: ref(list),
            isPending: ref(false),
            isError: ref(false),
          }),
          checkJourneyItemExists: vi.fn().mockResolvedValue(false),
        },
      },
    },
  })
  await flushPromises()
  return { wrapper, router, queryClient }
}

// 약속의 활동 날짜는 2099-08-08이다. 담을 수 있는 여정과 없는 여정을 함께 둔다 —
// 목록은 둘 다 보여 주고, 담을 수 없는 쪽을 고르면 이유를 알려 준다.
const journeys = [
  { tripId: 7, title: 'Seoul Foodie Week', startDate: '2099-08-01', endDate: '2099-08-31' },
  { tripId: 8, title: 'Busan Winter', startDate: '2099-12-01', endDate: '2099-12-20' },
]

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
    const statusBadge = wrapper.findAll('span').find((element) => element.text() === 'Recruiting')
    expect(statusBadge?.classes()).toContain('bg-canvas/70')
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

  it('says up front that the appointment is full', async () => {
    // 아직 참여하지 않은 사람이어야 정원 충족이 이유로 잡힌다. 이미 참여한
    // 사람에게는 "이미 참여 중"이 먼저 걸린다.
    fetchAppointment.mockResolvedValueOnce({ ...appointment, appointmentStatus: 'FULL' })
    fetchMyAppointmentParticipation.mockResolvedValue(notJoinedParticipation)
    const { wrapper } = await mountView()
    const joinButton = wrapper
      .findAll('button')
      .find((button) => button.text() === 'Join appointment')

    expect(joinButton?.attributes('disabled')).toBeUndefined()

    const notice = wrapper
      .findAll('p')
      .find((p) => p.text() === 'This appointment is not open for joining.')

    // 정원 충족은 사용자 잘못이 아닌 정상 상태라 경고색이 아니라 중립색이어야 한다.
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

    // 참여도 방장처럼 여정을 먼저 고른다. 고른 뒤에 보증금 확인으로 넘어간다.
    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Join appointment')
      ?.trigger('click')
    await flushPromises()
    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('Seoul Foodie Week'))
      ?.trigger('click')
    await flushPromises()

    expect(wrapper.get('[role="dialog"]').text()).toContain('Confirm participation')
    expect(
      wrapper
        .get('[role="dialog"]')
        .findAll('button')
        .find((button) => button.text().includes('Pay'))
        ?.attributes('disabled'),
    ).toBeUndefined()
  })

  it('asks which journey to put the appointment in before taking the deposit', async () => {
    // 참여도 방장처럼 여정을 고른다. 고르지 않으면 서버가 멤버십의 trip_id를 비워 둘
    // 수 없어 참여 자체가 성립하지 않는다.
    fetchMyAppointmentParticipation.mockResolvedValue(notJoinedParticipation)
    const { wrapper } = await mountView()

    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Join appointment')
      ?.trigger('click')
    await flushPromises()

    expect(wrapper.get('[role="dialog"]').text()).toContain('Choose a journey')
    expect(wrapper.text()).not.toContain('Confirm participation')
  })

  it('explains why a journey that cannot hold the activity date was refused', async () => {
    // 목록에서 감추지 않는다 — "내 여정이 왜 없지"로 읽힌다. 고르는 순간 이유를
    // 알려 주고 시트는 열어 둬, 다른 여정을 바로 고를 수 있게 한다.
    fetchMyAppointmentParticipation.mockResolvedValue(notJoinedParticipation)
    const { wrapper } = await mountView()

    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Join appointment')
      ?.trigger('click')
    await flushPromises()

    expect(wrapper.get('[role="dialog"]').text()).toContain('Seoul Foodie Week')
    expect(wrapper.get('[role="dialog"]').text()).toContain('Busan Winter')

    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('Busan Winter'))
      ?.trigger('click')
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toContain('does not cover the appointment date')
    // 보증금 시트로 넘어가지 않고 목록이 그대로 남는다.
    expect(wrapper.text()).not.toContain('Confirm participation')
    expect(wrapper.get('[role="dialog"]').text()).toContain('Seoul Foodie Week')
  })

  it('keeps a way to create a journey even when the list is not empty', async () => {
    // 담을 수 없는 여정도 감추지 않고 보여 주므로, 전부 날짜가 안 맞으면 안내만
    // 반복해서 보고 끝나는 막다른 길이 생긴다. 목록이 있어도 만들 통로를 남긴다.
    fetchMyAppointmentParticipation.mockResolvedValue(notJoinedParticipation)
    const { wrapper } = await mountView()

    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Join appointment')
      ?.trigger('click')
    await flushPromises()

    const sheet = wrapper.get('[role="dialog"]')
    expect(sheet.text()).toContain('Seoul Foodie Week')
    expect(
      sheet.findAll('button').find((button) => button.text() === 'Create a journey'),
    ).toBeDefined()
  })

  it('names joining, not creating, when there is no journey to use', async () => {
    // 참여하려는 사람에게 "이 약속을 만들기 전에"는 어긋난다.
    fetchMyAppointmentParticipation.mockResolvedValue(notJoinedParticipation)
    const { wrapper } = await mountView({ journeys: [] })

    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Join appointment')
      ?.trigger('click')
    await flushPromises()

    expect(wrapper.get('[role="dialog"]').text()).toContain('before joining this appointment')
  })

  it('marks its own entry with the chosen journey before leaving for top-up', async () => {
    // 뒤로가기는 떠날 때의 URL로 되돌린다. 자기 자리에 표시를 안 남기면 충전을
    // 포기했을 뿐인데 고른 여정이 사라져 처음부터 다시 골라야 한다.
    fetchMyAppointmentParticipation.mockResolvedValue(notJoinedParticipation)
    joinAppointment.mockRejectedValue(
      new NormalizedApiError('WALLET-015', 409, '지갑 잔액이 부족합니다.'),
    )
    const { wrapper, router } = await mountView()

    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Join appointment')
      ?.trigger('click')
    await flushPromises()
    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('Seoul Foodie Week'))
      ?.trigger('click')
    await flushPromises()
    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('Pay'))
      ?.trigger('click')
    await flushPromises()
    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Top up')
      ?.trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('wallet-top-up')

    // 충전을 포기하고 뒤로. 떠날 때의 자리에 고른 여정이 남아 있어야 한다.
    router.back()
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('appointment-detail')
    expect(router.currentRoute.value.query.tripId).toBe('7')
  })

  it('sends the host to journey creation when nothing can hold the appointment', async () => {
    // 담을 여정이 하나도 없으면 만들러 보낸다. 자리를 내주고(replace) 보내면 여정
    // 생성이 그 자리를 돌려주므로, 돌아온 뒤 상세가 히스토리에 두 번 쌓이지 않는다.
    fetchMyAppointmentParticipation.mockResolvedValue(notJoinedParticipation)
    const { wrapper, router } = await mountView({ journeys: [] })

    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Join appointment')
      ?.trigger('click')
    await flushPromises()

    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('Create a journey'))
      ?.trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('journey-create')
    // 이 화면은 param 라우트(/appointments/:appointmentId)라 이름만으로는 돌아올 수
    // 없다. 여정 생성이 params로 풀 수 있게 returnParams로 싣는다.
    expect(router.currentRoute.value.query).toMatchObject({
      returnRouteName: 'appointment-detail',
      returnParams: 'appointmentId:7',
    })
  })

  it('offers to top up when the deposit cannot be held', async () => {
    // 빨간 한 줄 대신 "부족하다 + 그만큼 충전할까"를 한 번에 묻는다. 약속 생성과
    // 같은 규칙이다.
    fetchMyAppointmentParticipation.mockResolvedValue(notJoinedParticipation)
    joinAppointment.mockRejectedValue(
      new NormalizedApiError('WALLET-015', 409, '지갑 잔액이 부족합니다.'),
    )
    const { wrapper, router } = await mountView()

    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Join appointment')
      ?.trigger('click')
    await flushPromises()
    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('Seoul Foodie Week'))
      ?.trigger('click')
    await flushPromises()
    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('Pay'))
      ?.trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Not enough balance')

    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Top up')
      ?.trigger('click')
    await flushPromises()

    // 돌아올 곳에 고른 여정을 실어 보낸다 — 돌아오면 그 여정이 골라진 채로 열린다.
    expect(router.currentRoute.value.name).toBe('wallet-top-up')
    expect(router.currentRoute.value.query).toMatchObject({
      amount: '10000',
      returnRouteName: 'appointment-detail',
      returnParams: 'appointmentId:7',
      tripId: '7',
    })
  })

  it('explains a journey conflict instead of failing silently', async () => {
    // 같은 여정·장소·날짜에 다른 약속이 이미 걸려 있으면 서버가 JOURNEY-004로
    // 거절한다. 문구가 없으면 일반 오류로 떨어져 "아무 일도 안 난 것"처럼 보인다.
    fetchMyAppointmentParticipation.mockResolvedValue(notJoinedParticipation)
    joinAppointment.mockRejectedValue(
      new NormalizedApiError('JOURNEY-004', 409, 'duplicate journey item'),
    )
    const { wrapper } = await mountView()

    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Join appointment')
      ?.trigger('click')
    await flushPromises()
    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('Seoul Foodie Week'))
      ?.trigger('click')
    await flushPromises()
    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('Pay'))
      ?.trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('already on that day of your journey')
  })

  it('reopens the journey sheet with the journey the host just created', async () => {
    // 여정을 만들고 ?tripId=로 돌아온 길. 참여를 다시 누르게 하지 않고, 만든 여정을
    // 골라 둔 채로 시트를 열어 확인하고 넘어가게 한다.
    fetchMyAppointmentParticipation.mockResolvedValue(notJoinedParticipation)
    const { wrapper, router } = await mountView({ path: '/appointments/7?tripId=7' })

    const sheet = wrapper.get('[role="dialog"]')
    expect(sheet.text()).toContain('Choose a journey')
    expect(
      sheet
        .findAll('button')
        .find((button) => button.text().includes('Seoul Foodie Week'))
        ?.attributes('aria-pressed'),
    ).toBe('true')
    // 아직 소비하지 않는다 — 충전으로 떠날 때 남겨 둔 표시가 바로 사라지면 안 된다.
    // 사용자가 고르거나 닫으면 그때 지운다.
    expect(router.currentRoute.value.query.tripId).toBe('7')
  })

  it('does not reopen the sheet after the join succeeds', async () => {
    // 참여가 끝나면 상세와 참여 정보를 함께 무효화한다. 상세가 먼저 도착하면 그
    // 순간에는 아직 "참여 안 함"으로 보이므로, ?tripId=가 URL에 남아 있으면 가드를
    // 통과해 시트가 다시 열린다 — 참여는 끝났는데 "Choose a journey"가 뜨는 화면이
    // 된다. 응답 순서에 기대지 않도록 지시를 한 번만 소비한다.
    fetchMyAppointmentParticipation.mockResolvedValue(notJoinedParticipation)
    const { wrapper, router } = await mountView({ path: '/appointments/7?tripId=7' })

    // 골라진 여정을 눌러 보증금 확인으로 넘어간 뒤 참여한다. 고르는 순간 지시가
    // 소비되므로, 참여가 끝난 뒤 상세만 먼저 갱신돼도 시트가 다시 열리지 않는다.
    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('Seoul Foodie Week'))
      ?.trigger('click')
    await flushPromises()

    // 고르는 순간 지시가 소비된다.
    expect(router.currentRoute.value.query.tripId).toBeUndefined()

    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('Pay'))
      ?.trigger('click')
    await flushPromises()

    expect(joinAppointment).toHaveBeenCalledWith(7, 7)

    // 참여 성공 뒤 상세만 먼저 갱신된 상황을 만든다.
    fetchAppointment.mockResolvedValue({ ...appointment, currentMemberCount: 3 })
    await flushPromises()
    await flushPromises()

    expect(wrapper.text()).not.toContain('Choose a journey')
  })

  it('does not reopen the sheet for a member who already joined', async () => {
    // 이미 참여한 사람에게는 열지 않는다. 참여 버튼과 같은 기준이다.
    fetchMyAppointmentParticipation.mockResolvedValue({
      joined: true,
      appointmentMemberId: 4,
      membershipStatus: 'ACTIVE',
      attendanceStatus: 'PENDING',
      host: false,
    })
    const { wrapper } = await mountView({ path: '/appointments/7?tripId=7' })

    expect(wrapper.text()).not.toContain('Choose a journey')
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

    // 참여도 방장처럼 여정을 먼저 고른다. 고른 뒤에 보증금 확인으로 넘어간다.
    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Join appointment')
      ?.trigger('click')
    await flushPromises()
    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('Seoul Foodie Week'))
      ?.trigger('click')
    await flushPromises()
    await wrapper
      .get('[role="dialog"]')
      .findAll('button')
      .find((button) => button.text().includes('Pay'))
      ?.trigger('click')
    await flushPromises()

    expect(joinAppointment).toHaveBeenCalledWith(7, 7)
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
      'You left this appointment. 10,000 P has been refunded to your wallet.',
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

    await wrapper.get('button[aria-label="Open appointment menu"]').trigger('click')
    const leave = menuItem(wrapper)('Leave group')
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

  it('disables Leave group once the activity has ended', async () => {
    fetchAppointment.mockResolvedValueOnce({
      ...appointment,
      appointmentStatus: 'AWAITING_ATTENDANCE',
    })
    fetchMyAppointmentParticipation.mockResolvedValue(memberParticipation)
    const { wrapper } = await mountView()

    await wrapper.get('button[aria-label="Open appointment menu"]').trigger('click')
    const leave = menuItem(wrapper)('Leave group')

    expect(leave?.attributes('disabled')).toBeDefined()
    expect(leave?.text()).toContain('The activity has ended')
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
})
