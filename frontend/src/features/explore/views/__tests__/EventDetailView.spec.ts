import { VueQueryPlugin, QueryClient } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import { i18n } from '@/app/i18n'

import type {
  ExploreJourneyIntegration,
  ExploreJourneyListQuery,
  ExploreJourneySummary,
} from '../../model/journeyIntegration'
import { exploreJourneyIntegrationKey } from '../../model/journeyIntegration'
import type { EventDetail } from '../../model/eventDetail'

const fetchEventDetail = vi.fn()
const addJourneyItem = vi.fn()

vi.mock('../../api/exploreApi', () => ({
  fetchEventDetail: (eventId: number | string, language: string) =>
    fetchEventDetail(eventId, language),
}))

const EventDetailView = (await import('../EventDetailView.vue')).default

const event: EventDetail = {
  eventId: 42,
  eventType: null,
  eventKind: 'CONCERT',
  title: 'Seoul concert',
  subtitle: null,
  description: 'A test event',
  programText: null,
  thumbnailUrl: null,
  imageUrls: [],
  links: {},
  reservationUrl: null,
  preReservation: null,
  status: 'ONGOING',
  isPermanent: false,
  startDate: '2026-08-10',
  endDate: '2026-08-12',
  operatingHours: { hours: '19:00 – 21:00' },
  openDays: [],
  openWeekend: true,
  opensLate: false,
  venueName: 'Seoul Arts Center',
  region1: 'Seoul',
  region2: 'Seocho-gu',
  region3: null,
  addressRoad: '2406 Nambu-sunhwan-ro',
  latitude: 37.48,
  longitude: 127.01,
  hasPhotoZone: false,
  isExperience: false,
  ageLimit: null,
  isFree: false,
  priceText: '30,000 KRW',
  hasBenefit: false,
  reservable: true,
  contact: null,
  organizer: 'NA-WA',
  activities: [],
}

const journeys: ExploreJourneySummary[] = [
  {
    tripId: 7,
    title: 'Seoul weekend',
    startDate: '2026-08-10',
    endDate: '2026-08-12',
  },
]

function createJourneyIntegration(): ExploreJourneyIntegration {
  const query: ExploreJourneyListQuery = {
    data: ref<ExploreJourneySummary[] | undefined>(journeys),
    isPending: ref(false),
    isError: ref(false),
  }

  return {
    addJourneyItem,
    parseJourneyRouteQuery: () => null,
    useJourneyListQuery: () => query,
  }
}

async function mountView() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/explore',
        name: 'explore',
        component: { template: '<div>Explore</div>' },
      },
      {
        path: '/explore/events/:eventId',
        name: 'explore-event-detail',
        component: EventDetailView,
      },
      {
        path: '/journeys/:tripId',
        name: 'journey-detail',
        component: { template: '<div>Journey detail</div>' },
      },
      {
        path: '/appointments',
        name: 'appointment-list',
        component: { template: '<div>Appointments</div>' },
      },
    ],
  })
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })

  await router.push('/explore/events/42')
  await router.isReady()

  const wrapper = mount(EventDetailView, {
    global: {
      plugins: [i18n, router, createPinia(), [VueQueryPlugin, { queryClient }]],
      provide: {
        [exploreJourneyIntegrationKey as symbol]: createJourneyIntegration(),
      },
    },
  })

  await flushPromises()
  return { wrapper, router }
}

describe('EventDetailView', () => {
  beforeEach(() => {
    fetchEventDetail.mockReset()
    addJourneyItem.mockReset()
    fetchEventDetail.mockResolvedValue(event)
    addJourneyItem.mockResolvedValue({})
  })

  it('mounts with the app-provided Journey integration', async () => {
    const { wrapper } = await mountView()

    expect(wrapper.text()).toContain('Seoul concert')
    expect(wrapper.text()).toContain('Seoul · Seocho-gu')
  })

  it('returns to the Event list from the detail screen', async () => {
    const { wrapper, router } = await mountView()

    await wrapper.get('button[aria-label="Back to events"]').trigger('click')
    await flushPromises()
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('explore')
    expect(router.currentRoute.value.query).toEqual({ tab: 'events' })
  })

  it('adds the Event to the selected journey for the chosen date', async () => {
    const { wrapper, router } = await mountView()

    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Add to journey')
      ?.trigger('click')
    await flushPromises()

    await wrapper
      .get('[role="dialog"]')
      .findAll('button')
      .find((button) => button.text().includes('Seoul weekend'))
      ?.trigger('click')
    await flushPromises()

    await wrapper
      .get('[role="dialog"]')
      .findAll('button')
      .find((button) => button.text().includes('Add to'))
      ?.trigger('click')
    await flushPromises()

    expect(addJourneyItem).toHaveBeenCalledWith(7, {
      itemId: 42,
      visitDate: expect.any(String),
    })
    expect(router.currentRoute.value.name).toBe('journey-detail')
  })

  it('opens the Event appointment list with the Event filter', async () => {
    const { wrapper, router } = await mountView()

    const button = wrapper.findAll('button').find((button) => button.text() === 'Find companions')

    expect(button?.attributes('disabled')).toBeUndefined()
    await button?.trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('appointment-list')
    expect(router.currentRoute.value.query).toEqual({ itemId: '42', itemType: 'EVENT' })
  })
})
