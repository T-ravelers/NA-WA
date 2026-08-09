import { VueQueryPlugin, QueryClient } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import { i18n } from '@/app/i18n'

const fetchEventList = vi.fn()
const fetchPlaceList = vi.fn()

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
    routes: [{ path: '/explore', name: 'explore', component: ExploreView }],
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

  it('applies a Place region filter and reflects it in the URL', async () => {
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
      .findAll('button')
      .find((button) => button.text().includes('Gyeonggi'))
      ?.trigger('click')
    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('Suwon'))
      ?.trigger('click')
    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('Apply'))
      ?.trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.query.placeRegion1).toEqual(['Gyeonggi'])
    expect(router.currentRoute.value.query.placeRegion2).toEqual(['Suwon'])
    expect(fetchPlaceList).toHaveBeenLastCalledWith(
      expect.objectContaining({ region1: ['Gyeonggi'], region2: ['Suwon'], page: 0 }),
    )
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
})
