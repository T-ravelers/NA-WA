import { VueQueryPlugin, QueryClient } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import { i18n } from '@/app/i18n'

const fetchEventList = vi.fn()
const fetchPlaceList = vi.fn()
const scrollToMock = vi.fn()

vi.mock('../../api/exploreApi', () => ({
  fetchEventList: (filters: unknown) => fetchEventList(filters),
  fetchPlaceList: (filters: unknown) => fetchPlaceList(filters),
}))

const ExploreView = (await import('../ExploreView.vue')).default

const place = {
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
  addressRoad: null,
  addressDetail: null,
  latitude: null,
  longitude: null,
  isActive: true,
  viewCount: 10,
  favoriteCount: 2,
  hasParking: true,
  reservable: true,
  takeoutAvailable: false,
  hasRestroom: false,
}

async function mountView(path = '/explore') {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/explore', name: 'explore', component: ExploreView },
      {
        path: '/explore/places/:placeId',
        name: 'explore-place-detail',
        component: { template: '<div>Place detail</div>' },
      },
    ],
  })
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })

  await router.push(path)
  await router.isReady()

  const wrapper = mount(ExploreView, {
    global: {
      plugins: [i18n, router, [VueQueryPlugin, { queryClient }]],
    },
  })

  await flushPromises()
  return { wrapper, router }
}

describe('ExploreView Place branch', () => {
  beforeEach(() => {
    scrollToMock.mockReset()
    vi.stubGlobal('scrollTo', scrollToMock)
    vi.stubGlobal(
      'matchMedia',
      vi.fn(() => ({ matches: true })),
    )
    fetchEventList.mockReset()
    fetchPlaceList.mockReset()
    fetchEventList.mockResolvedValue({
      content: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
      hasNext: false,
    })
    fetchPlaceList.mockResolvedValue({
      content: [place],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
      hasNext: false,
    })
  })

  it('switches to Places and requests the Place list', async () => {
    const { wrapper, router } = await mountView()

    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Places')
      ?.trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Seongsu Onsil')
    expect(router.currentRoute.value.query.tab).toBe('places')
    expect(fetchPlaceList).toHaveBeenCalledWith(expect.objectContaining({ page: 0, size: 20 }))
  })

  it('restores the Places tab from the URL', async () => {
    const { wrapper } = await mountView('/explore?tab=places')

    expect(wrapper.get('[role="radio"][aria-checked="true"]').text()).toBe('Places')
  })

  it('restores the Events tab when navigating back after switching tabs', async () => {
    const { wrapper, router } = await mountView()

    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Places')
      ?.trigger('click')
    await flushPromises()
    router.back()
    await flushPromises()

    expect(router.currentRoute.value.query.tab).toBeUndefined()
    expect(wrapper.get('[role="radio"][aria-checked="true"]').text()).toBe('Events')
  })

  it('applies a translated Seoul region2 using the operational_v9 API value', async () => {
    const { wrapper, router } = await mountView()

    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Places')
      ?.trigger('click')
    await flushPromises()
    await wrapper
      .findAll('button')
      .find((button) => button.text().startsWith('Region'))
      ?.trigger('click')
    await wrapper
      .get('[role="dialog"]')
      .findAll('button')
      .find((button) => button.text().includes('Seongsu'))
      ?.trigger('click')
    await wrapper
      .get('[role="dialog"]')
      .findAll('button')
      .find((button) => button.text().includes('Apply'))
      ?.trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.query.placeRegion1).toBeUndefined()
    expect(router.currentRoute.value.query.placeRegion2).toEqual(['성수'])
    expect(fetchPlaceList).toHaveBeenLastCalledWith(
      expect.objectContaining({ region1: ['서울'], region2: ['성수'], page: 0 }),
    )
  })

  it('applies Place Other areas to the URL and API request', async () => {
    const { wrapper, router } = await mountView('/explore?tab=places')

    await wrapper
      .findAll('button')
      .find((button) => button.text().startsWith('Region'))
      ?.trigger('click')
    await wrapper
      .get('[role="dialog"]')
      .findAll('button')
      .find((button) => button.text() === 'Other areas')
      ?.trigger('click')
    await wrapper
      .get('[role="dialog"]')
      .findAll('button')
      .find((button) => button.text().includes('Apply'))
      ?.trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.query.placeRegion2Other).toBe('true')
    expect(fetchPlaceList).toHaveBeenLastCalledWith(
      expect.objectContaining({ region1: ['서울'], region2Other: true }),
    )
  })

  it('applies a Place option to the URL and API request', async () => {
    const { wrapper, router } = await mountView('/explore?tab=places')

    await wrapper
      .findAll('button')
      .find((button) => button.text().startsWith('Options'))
      ?.trigger('click')
    await wrapper
      .get('[role="dialog"]')
      .findAll('button')
      .find((button) => button.text() === 'Parking')
      ?.trigger('click')
    await wrapper
      .get('[role="dialog"]')
      .findAll('button')
      .find((button) => button.text().includes('Apply'))
      ?.trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.query.hasParking).toBe('true')
    expect(fetchPlaceList).toHaveBeenLastCalledWith(expect.objectContaining({ hasParking: true }))
  })

  it('ignores invalid Place kind values from the URL', async () => {
    const { wrapper } = await mountView('/explore?placeKinds=GARBAGE')

    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Places')
      ?.trigger('click')
    await flushPromises()

    expect(fetchPlaceList).toHaveBeenLastCalledWith(
      expect.not.objectContaining({ placeKinds: ['ETC'] }),
    )
  })

  it('rehydrates Place filters when the route query changes after mount', async () => {
    const { router } = await mountView('/explore?tab=places&placeRegion2=성수')

    await router.push('/explore?tab=places&placeRegion1=부산&placeRegion2=INVALID&hasParking=true')
    await flushPromises()

    expect(fetchPlaceList).toHaveBeenLastCalledWith(
      expect.objectContaining({
        region1: ['부산'],
        region2: ['INVALID'],
        hasParking: true,
        page: 0,
      }),
    )
    expect(router.currentRoute.value.query.placeRegion1).toBe('부산')
    expect(router.currentRoute.value.query.placeRegion2).toBe('INVALID')
  })

  it('preserves a non-Seoul Event region from a direct URL', async () => {
    const { router } = await mountView('/explore?region1=부산')

    expect(fetchEventList).toHaveBeenLastCalledWith(expect.objectContaining({ region1: ['부산'] }))
    expect(router.currentRoute.value.query.eventRegion1).toEqual(['부산'])
  })

  it('requests a selected Event page and scrolls to the top', async () => {
    fetchEventList.mockResolvedValue({
      content: [],
      page: 0,
      size: 20,
      totalElements: 120,
      totalPages: 6,
      hasNext: true,
    })
    const { wrapper, router } = await mountView()

    await wrapper.get('button[aria-label="Page 2"]').trigger('click')
    await flushPromises()

    expect(scrollToMock).toHaveBeenCalledWith({ top: 0, behavior: 'auto' })
    expect(fetchEventList).toHaveBeenLastCalledWith(expect.objectContaining({ page: 1, size: 20 }))
    expect(router.currentRoute.value.query.eventPage).toBe('1')
  })
})
