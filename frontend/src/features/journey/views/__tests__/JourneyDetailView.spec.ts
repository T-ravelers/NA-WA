import { VueQueryPlugin, QueryClient } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'

import { i18n } from '@/app/i18n'
import { NormalizedApiError } from '@/shared/api/apiError'

import type { JourneyReportIntegration, JourneyReportSummary } from '../../model/reportIntegration'
import { journeyReportIntegrationKey } from '../../model/reportIntegration'

const fetchJourney = vi.fn()
const fetchJourneyTimeline = vi.fn()
const deleteJourneyItem = vi.fn()

vi.mock('../../api/journeyApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../api/journeyApi')>()),
  fetchJourney: (tripId: number) => fetchJourney(tripId),
  fetchJourneyTimeline: (tripId: number) => fetchJourneyTimeline(tripId),
  deleteJourneyItem: (tripId: number, tripItemId: number) => deleteJourneyItem(tripId, tripItemId),
}))

const JourneyDetailView = (await import('../JourneyDetailView.vue')).default

const journey = {
  tripId: 7,
  title: 'Seoul and Busan',
  startDate: '2026-08-10',
  endDate: '2026-08-12',
  budgetAmount: 1500000,
  companionPreference: '2-4',
  regions: [{ regionCode: 'SEOUL', regionName: 'Seoul', displayOrder: 0 }],
}

function oneItemTimeline(status: 'ADDED' | 'CONFIRMED' = 'ADDED') {
  return {
    tripId: 7,
    timeline: [
      {
        visitDate: '2026-08-10',
        items: [
          {
            tripItemId: 31,
            itemId: 91,
            status,
            displayOrder: 0,
            note: null,
            exploreItem: {
              itemType: 'EVENT',
              title: 'Nanta Theatre',
              thumbnailUrl: null,
              imageUrls: [],
              location: {
                region1: 'Seoul',
                region2: null,
                region3: null,
                addressRoad: null,
                addressDetail: null,
                latitude: null,
                longitude: null,
              },
            },
          },
        ],
      },
    ],
  }
}

interface ReportIntegrationOptions {
  reports?: JourneyReportSummary[]
  isPending?: boolean
  isError?: boolean
  refetch?: () => Promise<unknown>
}

function createReportIntegration(options: ReportIntegrationOptions = {}): JourneyReportIntegration {
  const { reports = [], isPending = false, isError = false, refetch = vi.fn() } = options

  return {
    useReportSummariesQuery: () => ({
      data: ref<JourneyReportSummary[] | undefined>(reports),
      isPending: ref(isPending),
      isError: ref(isError),
      refetch,
    }),
  }
}

async function mountWithRouter(path: string, reportOptions: ReportIntegrationOptions = {}) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/journeys/:tripId', name: 'journey-detail', component: JourneyDetailView },
      {
        path: '/journeys/:tripId/settings',
        name: 'journey-settings',
        component: { template: '<div>Settings</div>' },
      },
      { path: '/explore', name: 'explore', component: { template: '<div>Explore</div>' } },
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
        path: '/appointments',
        name: 'appointment-list',
        component: { template: '<div>Appointments</div>' },
      },
      {
        path: '/appointments/:appointmentId',
        name: 'appointment-detail',
        component: { template: '<div>Appointment</div>' },
      },
      { path: '/reports', name: 'report-list', component: { template: '<div>Reports</div>' } },
      {
        path: '/reports/:reportId',
        name: 'report-detail',
        component: { template: '<div>Report detail</div>' },
      },
    ],
  })
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })

  await router.push(path)
  await router.isReady()

  const wrapper = mount(JourneyDetailView, {
    global: {
      plugins: [i18n, router, [VueQueryPlugin, { queryClient }]],
      provide: {
        [journeyReportIntegrationKey as symbol]: createReportIntegration(reportOptions),
      },
    },
  })

  await flushPromises()

  return { wrapper, router }
}

async function mountAt(path: string, reportOptions: ReportIntegrationOptions = {}) {
  return (await mountWithRouter(path, reportOptions)).wrapper
}

describe('JourneyDetailView', () => {
  beforeEach(() => {
    fetchJourney.mockReset()
    fetchJourneyTimeline.mockReset()
    deleteJourneyItem.mockReset()
  })

  it('shows journey details and a day skeleton for an empty itinerary', async () => {
    fetchJourney.mockResolvedValue(journey)
    fetchJourneyTimeline.mockResolvedValue({ tripId: 7, timeline: [] })

    const wrapper = await mountAt('/journeys/7')

    expect(fetchJourney).toHaveBeenCalledWith(7)
    expect(fetchJourneyTimeline).toHaveBeenCalledWith(7)
    expect(wrapper.get('h1').text()).toBe('Seoul and Busan')
    expect(wrapper.text()).not.toContain('Visit regions')
    expect(wrapper.text()).not.toContain('No visit regions were added.')

    // 2026-08-10 ~ 2026-08-12. 항목이 하나도 없어도 날짜가 전부 보인다.
    // `time`은 JourneySummary도 쓰므로 날짜 블록은 추가 버튼 수로 센다.
    expect(wrapper.text()).toContain('Day 1')
    expect(wrapper.text()).toContain('Day 3')
    expect(wrapper.text()).not.toContain('Day 4')
    expect(wrapper.findAll('a[aria-label^="Add event on"]')).toHaveLength(3)
    expect(wrapper.findAll('a[aria-label^="Add place on"]')).toHaveLength(3)
    expect(wrapper.get('a[aria-label="Journey settings"]').attributes('href')).toBe(
      '/journeys/7/settings',
    )
  })

  it('shows no report entry for an ongoing journey', async () => {
    fetchJourney.mockResolvedValue({ ...journey, startDate: '2098-08-10', endDate: '2098-08-12' })
    fetchJourneyTimeline.mockResolvedValue({ tripId: 7, timeline: [] })

    const wrapper = await mountAt('/journeys/7')

    expect(wrapper.text()).not.toContain('View final report')
    expect(wrapper.text()).not.toContain('Create final report')
  })

  it('offers to create a final report for an ended journey without one', async () => {
    fetchJourney.mockResolvedValue({ ...journey, startDate: '2020-08-10', endDate: '2020-08-12' })
    fetchJourneyTimeline.mockResolvedValue({ tripId: 7, timeline: [] })

    const { router, wrapper } = await mountWithRouter('/journeys/7')

    const createButton = wrapper
      .findAll('button')
      .find((button) => button.text() === 'Create final report')
    expect(createButton).toBeDefined()
    expect(wrapper.text()).not.toContain('View final report')

    await createButton?.trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.fullPath).toBe('/reports?tripId=7')
  })

  it('links to the existing final report for an ended journey', async () => {
    fetchJourney.mockResolvedValue({ ...journey, startDate: '2020-08-10', endDate: '2020-08-12' })
    fetchJourneyTimeline.mockResolvedValue({ tripId: 7, timeline: [] })

    const { router, wrapper } = await mountWithRouter('/journeys/7', {
      reports: [{ tripId: 7, reportId: 55 }],
    })

    const viewButton = wrapper
      .findAll('button')
      .find((button) => button.text() === 'View final report')
    expect(viewButton).toBeDefined()
    expect(wrapper.text()).not.toContain('Create final report')

    await viewButton?.trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.fullPath).toBe('/reports/55')
  })

  it('shows a loading state instead of a CTA while the report list is pending', async () => {
    fetchJourney.mockResolvedValue({ ...journey, startDate: '2020-08-10', endDate: '2020-08-12' })
    fetchJourneyTimeline.mockResolvedValue({ tripId: 7, timeline: [] })

    const wrapper = await mountAt('/journeys/7', { isPending: true })

    expect(wrapper.text()).toContain('Checking final report status')
    expect(wrapper.text()).not.toContain('View final report')
    expect(wrapper.text()).not.toContain('Create final report')
  })

  it('shows a retryable error instead of a CTA when the report list fails to load', async () => {
    fetchJourney.mockResolvedValue({ ...journey, startDate: '2020-08-10', endDate: '2020-08-12' })
    fetchJourneyTimeline.mockResolvedValue({ tripId: 7, timeline: [] })

    const refetch = vi.fn()
    const wrapper = await mountAt('/journeys/7', { isError: true, refetch })

    expect(wrapper.text()).toContain('Report status unavailable')
    expect(wrapper.text()).not.toContain('View final report')
    expect(wrapper.text()).not.toContain('Create final report')

    await wrapper.get('button').trigger('click')

    expect(refetch).toHaveBeenCalledTimes(1)
  })

  it('renders EVENT and PLACE timeline entries', async () => {
    fetchJourney.mockResolvedValue(journey)
    fetchJourneyTimeline.mockResolvedValue({
      tripId: 7,
      timeline: [
        {
          visitDate: '2026-08-10',
          items: [
            {
              tripItemId: 1,
              itemId: 10,
              status: 'ADDED',
              displayOrder: 0,
              note: null,
              exploreItem: {
                itemType: 'EVENT',
                title: 'Night market',
                thumbnailUrl: null,
                imageUrls: [],
                location: {
                  region1: 'Seoul',
                  region2: 'Yeouido',
                  region3: null,
                  addressRoad: null,
                  addressDetail: null,
                  latitude: null,
                  longitude: null,
                },
              },
            },
            {
              tripItemId: 2,
              itemId: 11,
              status: 'CONFIRMED',
              displayOrder: 1,
              note: 'Try the tasting menu',
              exploreItem: {
                itemType: 'PLACE',
                title: 'Gwangjang Market',
                thumbnailUrl: null,
                imageUrls: [],
                location: {
                  region1: 'Seoul',
                  region2: 'Jongno-gu',
                  region3: null,
                  addressRoad: null,
                  addressDetail: null,
                  latitude: null,
                  longitude: null,
                },
              },
              appointment: {
                activityStartAt: '2026-08-10T10:20:00',
                activityEndAt: '2026-08-10T11:20:00',
                appointmentStatus: 'OPEN',
              },
            },
          ],
        },
      ],
    })

    const wrapper = await mountAt('/journeys/7')

    expect(wrapper.text()).toContain('Night market')
    expect(wrapper.text()).toContain('Event')
    expect(wrapper.text()).toContain('Gwangjang Market')
    expect(wrapper.text()).toContain('Place')
    expect(wrapper.text()).toContain('Try the tasting menu')
  })

  it('confirms and deletes one itinerary item through its real API action', async () => {
    fetchJourney.mockResolvedValue(journey)
    fetchJourneyTimeline.mockResolvedValue(oneItemTimeline())
    deleteJourneyItem.mockResolvedValue(undefined)
    const wrapper = await mountAt('/journeys/7')

    await wrapper.get('button[aria-label="Remove Nanta Theatre from itinerary"]').trigger('click')
    const dialog = wrapper.get('#remove-journey-item-dialog')
    expect(dialog.text()).toContain('Remove from itinerary?')
    await dialog.get('button.bg-danger').trigger('click')
    await flushPromises()

    expect(deleteJourneyItem).toHaveBeenCalledWith(7, 31)
    expect(wrapper.find('#remove-journey-item-dialog').exists()).toBe(false)
  })

  it('shows the host blocker when the item delete API returns JOURNEY-011', async () => {
    fetchJourney.mockResolvedValue(journey)
    fetchJourneyTimeline.mockResolvedValue(oneItemTimeline('CONFIRMED'))
    deleteJourneyItem.mockRejectedValue(new NormalizedApiError('JOURNEY-011', 409, 'host conflict'))
    const wrapper = await mountAt('/journeys/7')

    await wrapper.get('button[aria-label="Remove Nanta Theatre from itinerary"]').trigger('click')
    await wrapper.get('#remove-journey-item-dialog button.bg-danger').trigger('click')
    await flushPromises()

    expect(wrapper.get('#remove-journey-item-blocked-dialog').text()).toContain(
      'This item cannot be removed',
    )
  })

  it('shows a dedicated forbidden state', async () => {
    fetchJourney.mockRejectedValue(new NormalizedApiError('JOURNEY-002', 403, 'forbidden journey'))
    fetchJourneyTimeline.mockRejectedValue(
      new NormalizedApiError('JOURNEY-002', 403, 'forbidden journey'),
    )

    const wrapper = await mountAt('/journeys/7')

    expect(wrapper.get('[role="alert"]').text()).toContain('This journey is private')
    expect(wrapper.text()).not.toContain('Try again')
  })

  it('shows a retryable API error with the localized error code message', async () => {
    fetchJourney.mockRejectedValue(new NormalizedApiError('JOURNEY-001', 404, 'missing journey'))
    fetchJourneyTimeline.mockResolvedValue({ tripId: 7, timeline: [] })

    const wrapper = await mountAt('/journeys/7')

    expect(wrapper.get('[role="alert"]').text()).toContain('This journey could not be found.')
    expect(wrapper.get('button').text()).toBe('Try again')
  })

  it('announces the loading state while requests are pending', async () => {
    fetchJourney.mockReturnValue(new Promise(() => undefined))
    fetchJourneyTimeline.mockReturnValue(new Promise(() => undefined))

    const wrapper = await mountAt('/journeys/7')

    expect(wrapper.get('[role="status"]').attributes('aria-live')).toBe('polite')
  })

  it('does not request APIs for an invalid trip id', async () => {
    const wrapper = await mountAt('/journeys/not-a-number')

    expect(fetchJourney).not.toHaveBeenCalled()
    expect(fetchJourneyTimeline).not.toHaveBeenCalled()
    expect(wrapper.get('[role="alert"]').text()).toContain('Invalid journey link')
  })

  it('loads the new journey when the route reuses the detail view with another id', async () => {
    fetchJourney.mockImplementation((tripId: number) =>
      Promise.resolve({ ...journey, tripId, title: `Journey ${tripId}` }),
    )
    fetchJourneyTimeline.mockImplementation((tripId: number) =>
      Promise.resolve({ tripId, timeline: [] }),
    )
    const { wrapper, router } = await mountWithRouter('/journeys/7')

    await router.push('/journeys/8')
    await flushPromises()

    expect(fetchJourney).toHaveBeenLastCalledWith(8)
    expect(fetchJourneyTimeline).toHaveBeenLastCalledWith(8)
    expect(wrapper.get('h1').text()).toBe('Journey 8')
  })
})
