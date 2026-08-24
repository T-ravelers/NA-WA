import { VueQueryPlugin, QueryClient } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ref, type Ref } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'

import { i18n } from '@/app/i18n'

import {
  appointmentJourneyIntegrationKey,
  type AppointmentJourneySummary,
} from '../../model/journeyIntegration'

const fetchAppointments = vi.fn()
const joinAppointment = vi.fn()

vi.mock('../../api/appointmentApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../api/appointmentApi')>()),
  fetchAppointments: (filters: unknown) => fetchAppointments(filters),
  joinAppointment: (appointmentId: number, tripId: number) =>
    joinAppointment(appointmentId, tripId),
}))

const AppointmentListView = (await import('../AppointmentListView.vue')).default

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
  activityStartAt: '2026-08-08T18:30:00',
  activityEndAt: '2026-08-08T22:00:00',
  hostDisplayName: 'Mina Park',
}

interface MountOptions {
  /** 진입 주소. 여정 생성·충전에서 돌아온 자리를 재현할 때 쓴다. */
  path?: string
  /**
   * 여정 목록이 담길 ref. 넘기지 않으면 처음부터 채워져 있다.
   *
   * 실제로는 시트가 열려야 조회가 시작되므로 목록은 **늦게** 도착한다. 처음부터
   * 채워 두면 그 시차에서만 드러나는 문제를 테스트가 못 본다.
   */
  journeys?: Ref<AppointmentJourneySummary[] | undefined>
  /** 캐시가 남은 재방문을 흉내낼 때 넘긴다. 앱은 QueryClient 하나를 공유한다. */
  queryClient?: QueryClient
}

async function mountView(options: MountOptions = {}) {
  const journeys =
    options.journeys ??
    ref<AppointmentJourneySummary[] | undefined>([
      {
        tripId: 7,
        title: 'Seoul Foodie Week',
        startDate: '2026-08-01',
        endDate: '2026-08-31',
      },
    ])

  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/appointments',
        name: 'appointment-list',
        component: AppointmentListView,
      },
      {
        path: '/appointments/:appointmentId',
        name: 'appointment-detail',
        component: { template: '<div>Detail</div>' },
      },
      {
        path: '/appointments/new',
        name: 'appointment-create',
        component: { template: '<div>Create</div>' },
      },
      {
        path: '/explore/events/:eventId',
        name: 'explore-event-detail',
        component: { template: '<div>Event</div>' },
      },
      {
        path: '/explore/places/:placeId',
        name: 'explore-place-detail',
        component: { template: '<div>Place</div>' },
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
  const queryClient =
    options.queryClient ?? new QueryClient({ defaultOptions: { queries: { retry: false } } })
  await router.push(options.path ?? '/appointments?itemId=42&itemType=EVENT')
  await router.isReady()

  const wrapper = mount(AppointmentListView, {
    global: {
      plugins: [i18n, router, [VueQueryPlugin, { queryClient }]],
      provide: {
        // 카드의 Join이 여정 선택 시트를 연다. 약속 날짜(2026-08-08)를 담는 여정 하나.
        [appointmentJourneyIntegrationKey as symbol]: {
          useJourneyListQuery: () => ({
            data: journeys,
            isPending: ref(false),
            isError: ref(false),
          }),
          checkAppointmentSlotTaken: vi.fn().mockResolvedValue(false),
        },
      },
    },
  })
  await flushPromises()
  return { wrapper, router, queryClient }
}

const EMPTY_PAGE = {
  content: [],
  page: 0,
  size: 20,
  totalElements: 0,
  totalPages: 0,
  hasNext: false,
}

describe('AppointmentListView', () => {
  beforeEach(() => {
    fetchAppointments.mockReset()
    joinAppointment.mockReset()
    fetchAppointments.mockResolvedValue({
      content: [appointment],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
      hasNext: false,
    })
  })

  it('requests appointments with the item context and opens the selected detail', async () => {
    const { wrapper, router } = await mountView()

    expect(fetchAppointments).toHaveBeenCalledWith({
      itemId: 42,
      itemType: 'EVENT',
      keyword: undefined,
      // 기본 언어 칩은 회원이 고른 언어(테스트 로케일은 en)다. 목록 전체를 보여주면
      // 대부분이 못 알아듣는 언어로 채워진다.
      language: 'en',
      page: 0,
      size: 20,
    })

    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'View')
      ?.trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('appointment-detail')
    expect(router.currentRoute.value.params.appointmentId).toBe('7')
  })

  it('starts from the member language again on the next visit', async () => {
    const { wrapper } = await mountView()

    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Japanese')
      ?.trigger('click')
    await flushPromises()

    expect(fetchAppointments).toHaveBeenLastCalledWith(expect.objectContaining({ language: 'ja' }))

    // 고른 칩은 그 화면에 머무는 동안만 유지된다. 다시 들어오면 회원 언어다.
    const revisited = await mountView()
    await flushPromises()

    expect(fetchAppointments).toHaveBeenLastCalledWith(expect.objectContaining({ language: 'en' }))
    expect(
      revisited.wrapper
        .findAll('button')
        .find((button) => button.text() === 'English')
        ?.attributes('aria-pressed'),
    ).toBe('true')
  })

  // 사용자가 고르지 않은 조건으로 빈 화면을 보여주면, 약속이 없는 것인지 걸러진
  // 것인지 구분되지 않는다.
  it('falls back to every language when the member language has no appointments', async () => {
    fetchAppointments.mockResolvedValueOnce(EMPTY_PAGE)

    const { wrapper } = await mountView()
    await flushPromises()

    expect(fetchAppointments).toHaveBeenLastCalledWith(
      expect.objectContaining({ language: undefined }),
    )
    expect(
      wrapper
        .findAll('button')
        .find((button) => button.text() === 'All')
        ?.attributes('aria-pressed'),
    ).toBe('true')
  })

  // 되돌림은 watch가 값의 **변화**를 볼 때 돈다. 앱은 QueryClient 하나를 공유하므로
  // 회원 언어로 건 0건 결과가 캐시에 남은 재방문에서는 마운트 시점에 이미
  // isSuccess=true·count=0이라 값이 변하지 않는다 — watch가 한 번도 돌지 않는다.
  // `{ immediate: true }`가 그 첫 판정을 대신한다. 목록 → 상세 → 뒤로가 바로 이 동선이다.
  it('falls back to every language again when the empty result came from the cache', async () => {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    // 회원 언어(en)는 언제나 0건, 전체(language 없음)는 1건.
    fetchAppointments.mockImplementation((filters: { language?: string }) =>
      Promise.resolve(
        filters.language === undefined
          ? {
              content: [appointment],
              page: 0,
              size: 20,
              totalElements: 1,
              totalPages: 1,
              hasNext: false,
            }
          : EMPTY_PAGE,
      ),
    )

    const first = await mountView({ queryClient })
    first.wrapper.unmount()

    const { wrapper } = await mountView({ queryClient })
    await flushPromises()

    expect(
      wrapper
        .findAll('button')
        .find((button) => button.text() === 'All')
        ?.attributes('aria-pressed'),
    ).toBe('true')
  })

  // 직접 고른 조건을 화면이 임의로 풀면 방금 누른 칩과 목록이 어긋난다.
  it('keeps a chosen language even when it has no appointments', async () => {
    const { wrapper } = await mountView()
    fetchAppointments.mockResolvedValue(EMPTY_PAGE)

    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Japanese')
      ?.trigger('click')
    await flushPromises()

    expect(fetchAppointments).toHaveBeenLastCalledWith(expect.objectContaining({ language: 'ja' }))
  })

  // 끝난 약속을 빼는 것은 서버가 LIMIT 앞에서 한다(APPOINTMENT_API.md). 받은 쪽에서
  // 다시 거르면 정렬이 activity_start_at ASC라 지난 약속이 앞에 서고, 지난 약속이 한
  // 페이지를 채우는 항목에서는 다음 페이지에 모집 중 약속이 있어도 화면이 0건이 된다.
  it('shows every appointment the server returned, filtering none of them out', async () => {
    fetchAppointments.mockResolvedValue({
      content: [
        { ...appointment, appointmentStatus: 'COMPLETED' as const },
        { ...appointment, appointmentId: 8, appointmentName: 'Hongdae Night Market' },
      ],
      page: 0,
      size: 20,
      totalElements: 2,
      totalPages: 1,
      hasNext: false,
    })

    const { wrapper } = await mountView()

    expect(wrapper.text()).toContain('Seongsu K-Beauty Tour')
    expect(wrapper.text()).toContain('Hongdae Night Market')
    expect(wrapper.text()).toContain('2 appointments')
  })

  // 개수는 서버가 센 값이다. 받은 페이지의 길이를 세면 size에서 멈춰, 다음 페이지가
  // 남아 있는 항목에서 "몇 개인지"를 잃는다.
  it('shows the count the server reported, not the length of one page', async () => {
    fetchAppointments.mockResolvedValue({
      content: [appointment, { ...appointment, appointmentId: 8 }],
      page: 0,
      size: 20,
      totalElements: 37,
      totalPages: 2,
      hasNext: true,
    })

    const { wrapper } = await mountView()

    expect(wrapper.text()).toContain('37 appointments')
  })

  it('opens the detail when the card itself is pressed', async () => {
    const { wrapper, router } = await mountView()

    await wrapper.get('article').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('appointment-detail')
    expect(router.currentRoute.value.params.appointmentId).toBe('7')
  })

  // Join을 누르는 것만으로는 목록을 떠나지 않는다. 여정 선택 시트가 목록 위에 열린다.
  it('opens the journey sheet from the card Join button, without leaving the list', async () => {
    const { wrapper, router } = await mountView()

    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Join')
      ?.trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('appointment-list')
    expect(wrapper.find('[role="dialog"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('Seoul Foodie Week')
  })

  // 참여가 끝나면 그 약속 상세로 데려간다. 시트만 닫으면 목록에 남는데, 바뀌는 것이
  // 카드의 인원수 한 자리뿐이라 참여가 됐는지 확신할 수 없다. push라 뒤로 가면 목록이다.
  it('lands on the appointment detail once the deposit is confirmed', async () => {
    joinAppointment.mockResolvedValue(undefined)
    const { wrapper, router } = await mountView()

    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Join')
      ?.trigger('click')
    await flushPromises()

    await wrapper
      .get('[role="dialog"]')
      .findAll('button')
      .find((button) => button.text().includes('Seoul Foodie Week'))
      ?.trigger('click')
    await flushPromises()

    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('Pay'))
      ?.trigger('click')
    await flushPromises()

    expect(joinAppointment).toHaveBeenCalledWith(7, 7)
    expect(router.currentRoute.value.name).toBe('appointment-detail')
    expect(router.currentRoute.value.params.appointmentId).toBe('7')
  })

  // 참여 요청이 도는 동안에도 보증금 시트는 닫힌다(확정 버튼만 비활성이다). 그 길로
  // 다른 카드의 Join을 열면 「지금 만지는 약속」이 바뀌는데, 응답이 그때 도착해 그 값을
  // 다시 읽으면 7번에 참여하고 8번 상세로 간다. 요청에 실어 보낸 식별자를 써야 한다.
  it('goes to the appointment it actually joined, not the one opened while waiting', async () => {
    fetchAppointments.mockResolvedValue({
      content: [appointment, { ...appointment, appointmentId: 8, appointmentName: 'Second' }],
      page: 0,
      size: 20,
      totalElements: 2,
      totalPages: 1,
      hasNext: false,
    })
    let finishJoin: (() => void) | undefined
    joinAppointment.mockImplementation(
      () => new Promise<void>((resolve) => (finishJoin = () => resolve())),
    )
    const { wrapper, router } = await mountView()

    const joinButtons = () => wrapper.findAll('button').filter((button) => button.text() === 'Join')

    // 7번을 확정하고 응답을 기다린다.
    await joinButtons()[0]?.trigger('click')
    await flushPromises()
    await wrapper
      .get('[role="dialog"]')
      .findAll('button')
      .find((button) => button.text().includes('Seoul Foodie Week'))
      ?.trigger('click')
    await flushPromises()
    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('Pay'))
      ?.trigger('click')
    await flushPromises()

    expect(joinAppointment).toHaveBeenCalledWith(7, 7)

    // 응답 전에 시트를 닫고 8번 카드의 Join을 연다.
    await wrapper
      .findAll('[role="dialog"] button')
      .find((button) => button.text() === 'Cancel')
      ?.trigger('click')
    await flushPromises()
    await joinButtons()[1]?.trigger('click')
    await flushPromises()

    // 이제 7번 요청이 뒤늦게 성공한다.
    finishJoin?.()
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('appointment-detail')
    expect(router.currentRoute.value.params.appointmentId).toBe('7')
  })

  // 여정을 만들거나 충전하고 돌아오면 시트를 다시 열어 **그 여정을 골라 둔 채로**
  // 보여 준다. 여정 목록은 시트가 열려야 조회를 시작하므로 시트를 여는 시점에는
  // 아직 없다 — 늦게 도착한 목록에서 그 여정을 집어내는 것까지가 이 흐름이다.
  it('preselects the journey it came back with, even though the list arrives late', async () => {
    const journeys = ref<AppointmentJourneySummary[] | undefined>(undefined)
    const { wrapper } = await mountView({
      path: '/appointments?itemId=42&itemType=EVENT&joinAppointmentId=7&tripId=7',
      journeys,
    })

    expect(wrapper.find('[role="dialog"]').exists()).toBe(true)

    journeys.value = [
      { tripId: 7, title: 'Seoul Foodie Week', startDate: '2026-08-01', endDate: '2026-08-31' },
    ]
    await flushPromises()

    const selected = wrapper.get('[role="dialog"]').findAll('[aria-pressed="true"]')
    expect(selected).toHaveLength(1)
    expect(selected[0]?.text()).toContain('Seoul Foodie Week')
  })

  // 여정을 만들지 않고 뒤로 오면 tripId 없이 표시만 돌아온다. 이어서 열 것이 없으니
  // 주소에 남겨 두지 않는다 — 남으면 resume 조건(둘 다 필요)에 걸려 영영 안 지워진다.
  it('clears a join marker that came back without a journey', async () => {
    const { wrapper, router } = await mountView({
      path: '/appointments?itemId=42&itemType=EVENT&joinAppointmentId=7',
    })

    expect(router.currentRoute.value.query.joinAppointmentId).toBeUndefined()
    expect(router.currentRoute.value.query.itemId).toBe('42')
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
  })

  it('returns to the Event detail from the contextual list', async () => {
    const { wrapper, router } = await mountView()
    await wrapper.find('header button').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('explore-event-detail')
    expect(router.currentRoute.value.params.eventId).toBe('42')
  })

  // 폴링만 가짜 시계에 올린다. flushPromises는 setTimeout·setImmediate를 쓰므로
  // 전부 가짜로 만들면 이 테스트가 영영 끝나지 않는다.
  it('follows the server while the list stays open', async () => {
    vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval'] })

    try {
      const { wrapper } = await mountView()
      expect(wrapper.text()).toContain('Recruiting')

      fetchAppointments.mockResolvedValue({
        content: [
          { ...appointment, appointmentStatus: 'FULL' as const, currentMemberCount: 4 },
          { ...appointment, appointmentId: 8, appointmentName: 'Hongdae Night Market' },
        ],
        page: 0,
        size: 20,
        totalElements: 2,
        totalPages: 1,
        hasNext: false,
      })

      vi.advanceTimersByTime(5_000)
      await flushPromises()

      expect(fetchAppointments).toHaveBeenCalledTimes(2)
      // 새로 등록된 약속이 카드로 들어오고, 이미 있던 카드의 상태도 함께 바뀐다.
      expect(wrapper.text()).toContain('Hongdae Night Market')
      expect(wrapper.text()).toContain('Fully booked')
    } finally {
      vi.useRealTimers()
    }
  })

  it('keeps the cards on screen when a refresh fails', async () => {
    vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval'] })

    try {
      const { wrapper } = await mountView()
      fetchAppointments.mockRejectedValue(new Error('offline'))

      vi.advanceTimersByTime(5_000)
      await flushPromises()

      // 5초마다 조회하면 실패할 기회도 5초마다 생긴다. 신호가 한 번 끊겼다고
      // 보고 있던 목록을 오류 화면으로 바꾸지 않는다.
      expect(wrapper.text()).toContain('Seongsu K-Beauty Tour')
      expect(wrapper.text()).not.toContain('Appointments could not be loaded')
    } finally {
      vi.useRealTimers()
    }
  })
})
