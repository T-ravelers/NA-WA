import { VueQueryPlugin, QueryClient } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import { i18n } from '@/app/i18n'
import { NormalizedApiError } from '@/shared/api/apiError'

const fetchJourney = vi.fn()
const fetchJourneyTimeline = vi.fn()

vi.mock('../../api/journeyApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../api/journeyApi')>()),
  fetchJourney: (tripId: number) => fetchJourney(tripId),
  fetchJourneyTimeline: (tripId: number) => fetchJourneyTimeline(tripId),
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

async function mountWithRouter(path: string) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/journeys/:tripId', name: 'journey-detail', component: JourneyDetailView }],
  })
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })

  await router.push(path)
  await router.isReady()

  const wrapper = mount(JourneyDetailView, {
    global: {
      plugins: [i18n, router, [VueQueryPlugin, { queryClient }]],
    },
  })

  await flushPromises()

  return { wrapper, router }
}

async function mountAt(path: string) {
  return (await mountWithRouter(path)).wrapper
}

describe('JourneyDetailView', () => {
  beforeEach(() => {
    fetchJourney.mockReset()
    fetchJourneyTimeline.mockReset()
  })

  it('shows journey details and the empty itinerary state', async () => {
    fetchJourney.mockResolvedValue(journey)
    fetchJourneyTimeline.mockResolvedValue({ tripId: 7, timeline: [] })

    const wrapper = await mountAt('/journeys/7')

    expect(fetchJourney).toHaveBeenCalledWith(7)
    expect(fetchJourneyTimeline).toHaveBeenCalledWith(7)
    expect(wrapper.get('h1').text()).toBe('Seoul and Busan')
    expect(wrapper.text()).not.toContain('Visit regions')
    expect(wrapper.text()).not.toContain('No visit regions were added.')
    expect(wrapper.text()).toContain('No itinerary yet')
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
