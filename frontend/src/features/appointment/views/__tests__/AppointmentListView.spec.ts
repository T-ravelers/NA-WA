import { VueQueryPlugin, QueryClient } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import { i18n } from '@/app/i18n'

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
    ],
  })
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  await router.push('/appointments?itemId=42&itemType=EVENT')
  await router.isReady()

  const wrapper = mount(AppointmentListView, {
    global: {
      plugins: [i18n, router, [VueQueryPlugin, { queryClient }]],
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

  it('opens completed appointment details from the list', async () => {
    fetchAppointments.mockResolvedValueOnce({
      content: [{ ...appointment, appointmentStatus: 'COMPLETED' as const }],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
      hasNext: false,
    })

    const { wrapper, router } = await mountView()
    const viewButton = wrapper.findAll('button').find((button) => button.text() === 'View')
    const statusBadge = wrapper.findAll('span').find((element) => element.text() === 'Completed')

    expect(viewButton?.attributes('disabled')).toBeUndefined()
    expect(statusBadge?.classes()).toContain('border-hairline')

    await viewButton?.trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('appointment-detail')
    expect(router.currentRoute.value.params.appointmentId).toBe('7')
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
