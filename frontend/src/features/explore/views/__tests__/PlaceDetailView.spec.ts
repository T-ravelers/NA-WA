import { VueQueryPlugin, QueryClient } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import { i18n } from '@/app/i18n'

const fetchPlaceDetail = vi.fn()
const fetchJourneys = vi.fn()
const addJourneyItem = vi.fn()

vi.mock('../../model/journeyIntegration', async () => {
  const { useQuery } = await import('@tanstack/vue-query')
  const { computed, toValue } = await import('vue')
  return {
    useExploreJourneyIntegration: () => ({
      addJourneyItem: (journeyId: number, request: { itemId: number; visitDate: string }) =>
        addJourneyItem(journeyId, request),
      parseJourneyRouteQuery: () => null,
      readActiveJourneyId: () => null,
      storeActiveJourneyId: vi.fn(),
      useJourneyListQuery: (enabled: import('vue').MaybeRefOrGetter<boolean>) =>
        useQuery({
          queryKey: ['journeys', 'review-test'],
          queryFn: () => fetchJourneys(),
          enabled: computed(() => toValue(enabled)),
          retry: false,
        }),
    }),
  }
})

vi.mock('../../api/exploreApi', () => ({
  fetchPlaceDetail: (placeId: number | string, language: string) =>
    fetchPlaceDetail(placeId, language),
}))

const PlaceDetailView = (await import('../PlaceDetailView.vue')).default

const place = {
  placeId: 42,
  itemId: 42,
  name: 'Seongsu Onsil',
  brand: null,
  branch: null,
  placeKind: 'RESTAURANT',
  thumbnailUrl: null,
  imageUrls: [],
  region1: 'Seoul',
  region2: 'Seongsu',
  region3: null,
  addressRoad: '26-14 Wangsimni-ro 4-gil',
  addressDetail: '2F',
  latitude: 37.54,
  longitude: 127.05,
  sourceUrl: null,
  postalCode: null,
  openingHours: { hours: '11:30 – 21:00' },
  closedDays: ['Seollal'],
  menuSummary: 'Sea urchin pasta',
  tel: '0507-1307-7941',
  activities: [],
  isActive: true,
  viewCount: 10,
  favoriteCount: 2,
  hasParking: true,
  reservable: true,
  takeoutAvailable: true,
  hasRestroom: false,
}

async function mountView() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/explore/places/:placeId',
        name: 'explore-place-detail',
        component: PlaceDetailView,
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

  await router.push('/explore/places/42')
  await router.isReady()

  const wrapper = mount(PlaceDetailView, {
    global: {
      plugins: [i18n, router, [VueQueryPlugin, { queryClient }]],
    },
  })

  await flushPromises()
  return { wrapper, router }
}

describe('PlaceDetailView', () => {
  beforeEach(() => {
    fetchPlaceDetail.mockReset()
    fetchJourneys.mockReset()
    addJourneyItem.mockReset()
    fetchPlaceDetail.mockResolvedValue(place)
    fetchJourneys.mockResolvedValue([
      { tripId: 7, title: 'Seoul weekend', startDate: '2026-08-10', endDate: '2026-08-12' },
    ])
    addJourneyItem.mockResolvedValue({})
  })

  it('renders Place details and keeps Directions disabled', async () => {
    const { wrapper } = await mountView()

    expect(wrapper.text()).toContain('Seongsu Onsil')
    expect(wrapper.text()).toContain('Seoul · Seongsu')
    expect(wrapper.text()).toContain('Sea urchin pasta')
    expect(wrapper.find('button').exists()).toBe(true)
    expect(
      wrapper
        .findAll('button')
        .find((button) => button.text() === 'Directions')
        ?.attributes('disabled'),
    ).toBeDefined()
    expect(
      wrapper
        .findAll('button')
        .find((button) => button.text() === 'Find companions')
        ?.attributes('disabled'),
    ).toBeDefined()
  })

  it('keeps Find companions disabled until the Appointment API is available', async () => {
    const { wrapper, router } = await mountView()

    const button = wrapper.findAll('button').find((button) => button.text() === 'Find companions')

    expect(button?.attributes('disabled')).toBeDefined()
    await button?.trigger('click')

    expect(router.currentRoute.value.name).toBe('explore-place-detail')
  })

  it('opens the journey selector from Add to journey', async () => {
    const { wrapper } = await mountView()

    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Add to journey')
      ?.trigger('click')
    await flushPromises()

    expect(wrapper.get('[role="dialog"]').text()).toContain('Choose a journey')
    expect(fetchJourneys).toHaveBeenCalledOnce()
  })

  it('adds the Place to the selected journey for the chosen date', async () => {
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
})
