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
  saved: false,
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

const RETURN_CONTEXT_KEY = 'nawa.explore.returnContext'
/* Event 개최 기간(2026-08-10 ~ 2026-08-12) 안이어야 담기 시트가 그대로 프리필한다. */
const CARRIED_DATE = '2026-08-11'

/** Journey 날짜에서 넘어온 복귀 맥락을 심는다. store가 마운트 시점에 읽어간다. */
function seedReturnContext(): void {
  sessionStorage.setItem(
    RETURN_CONTEXT_KEY,
    JSON.stringify({
      journeyId: 7,
      visitDate: CARRIED_DATE,
      returnTo: { name: 'journey-detail', params: { tripId: '7' } },
    }),
  )
}

function readReturnContext(): unknown {
  const stored = sessionStorage.getItem(RETURN_CONTEXT_KEY)
  return stored === null ? null : JSON.parse(stored)
}

/** 담기 버튼 → 여정 선택 → 날짜 확인까지 한 번에 진행한다. */
async function runAddToJourney(
  wrapper: Awaited<ReturnType<typeof mountView>>['wrapper'],
): Promise<void> {
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
}

describe('EventDetailView', () => {
  beforeEach(() => {
    sessionStorage.clear()
    fetchEventDetail.mockReset()
    addJourneyItem.mockReset()
    fetchEventDetail.mockResolvedValue(event)
    addJourneyItem.mockResolvedValue({})
  })

  it('mounts with the app-provided Journey integration', async () => {
    const { wrapper } = await mountView()

    expect(wrapper.text()).toContain('Seoul concert')
    expect(wrapper.text()).toContain('Seoul · Seocho-gu')
    expect(
      wrapper
        .findAll('button')
        .find((button) => button.text() === 'Google Maps')
        ?.attributes('disabled'),
    ).toBeUndefined()
  })

  it('hides map buttons when the Event has no coordinates', async () => {
    fetchEventDetail.mockResolvedValue({ ...event, latitude: null, longitude: null })

    const { wrapper } = await mountView()

    const buttonLabels = wrapper.findAll('button').map((button) => button.text())
    expect(buttonLabels).not.toContain('Google Maps')
    expect(buttonLabels).not.toContain('Directions')
  })

  it('translates operational region values and hides the raw hours key', async () => {
    fetchEventDetail.mockResolvedValue({
      ...event,
      region1: '서울',
      region2: '성수',
      operatingHours: { raw: 'Every day 10:00 – 20:00' },
    })

    const { wrapper } = await mountView()

    expect(wrapper.text()).toContain('Seoul · Seongsu')
    expect(wrapper.text()).toContain('Every day 10:00 – 20:00')
    expect(wrapper.text()).not.toContain('raw:')
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

  it('adds the Event on the date the Journey entry carried and returns there', async () => {
    seedReturnContext()
    const { wrapper, router } = await mountView()

    await runAddToJourney(wrapper)

    expect(addJourneyItem).toHaveBeenCalledWith(7, { itemId: 42, visitDate: CARRIED_DATE })
    expect(router.currentRoute.value.name).toBe('journey-detail')
    expect(router.currentRoute.value.params.tripId).toBe('7')
  })

  it('spends the carried date once so a later direct entry does not reuse it', async () => {
    seedReturnContext()
    const first = await mountView()
    await runAddToJourney(first.wrapper)

    expect(readReturnContext()).toEqual({ journeyId: 7, visitDate: null, returnTo: null })

    addJourneyItem.mockClear()
    const second = await mountView()
    await runAddToJourney(second.wrapper)

    expect(addJourneyItem).toHaveBeenCalledTimes(1)
    expect(addJourneyItem.mock.calls[0]?.[1].visitDate).not.toBe(CARRIED_DATE)
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
