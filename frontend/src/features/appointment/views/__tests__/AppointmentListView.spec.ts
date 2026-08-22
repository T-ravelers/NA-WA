import { VueQueryPlugin, QueryClient } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'

import { i18n } from '@/app/i18n'

import { appointmentJourneyIntegrationKey } from '../../model/journeyIntegration'

const fetchAppointments = vi.fn()

vi.mock('../../api/appointmentApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../api/appointmentApi')>()),
  fetchAppointments: (filters: unknown) => fetchAppointments(filters),
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

async function mountView() {
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
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  await router.push('/appointments?itemId=42&itemType=EVENT')
  await router.isReady()

  const wrapper = mount(AppointmentListView, {
    global: {
      plugins: [i18n, router, [VueQueryPlugin, { queryClient }]],
      provide: {
        // 카드의 Join이 여정 선택 시트를 연다. 약속 날짜(2026-08-08)를 담는 여정 하나.
        [appointmentJourneyIntegrationKey as symbol]: {
          useJourneyListQuery: () => ({
            data: ref([
              {
                tripId: 7,
                title: 'Seoul Foodie Week',
                startDate: '2026-08-01',
                endDate: '2026-08-31',
              },
            ]),
            isPending: ref(false),
            isError: ref(false),
          }),
          checkJourneyItemExists: vi.fn().mockResolvedValue(false),
        },
      },
    },
  })
  await flushPromises()
  return { wrapper, router }
}

describe('AppointmentListView', () => {
  beforeEach(() => {
    fetchAppointments.mockReset()
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
      language: undefined,
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

  // 끝난 약속은 참여할 수도, 새로 할 일도 없다. 목록에 남으면 지금 갈 수 있는 약속이
  // 그만큼 밀린다.
  it('hides completed appointments', async () => {
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

    expect(wrapper.text()).not.toContain('Seongsu K-Beauty Tour')
    expect(wrapper.text()).toContain('Hongdae Night Market')
    // 개수도 걸러낸 뒤 기준이어야 한다. 서버가 센 값을 그대로 쓰면 보이는 카드와 다르다.
    expect(wrapper.text()).toContain('1 appointments')
  })

  it('opens the detail when the card itself is pressed', async () => {
    const { wrapper, router } = await mountView()

    await wrapper.get('[role="link"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('appointment-detail')
    expect(router.currentRoute.value.params.appointmentId).toBe('7')
  })

  // Join은 상세로 보내지 않는다. 목록에 선 채로 상세와 같은 참여 흐름을 연다.
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
