import { VueQueryPlugin, QueryClient } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import { i18n } from '@/app/i18n'
import { NormalizedApiError } from '@/shared/api/apiError'

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

const openMapAppUrl = vi.fn()

// 앱 스킴 진입은 현재 문서를 이동시킨다. jsdom의 window.location을 직접 건드리면
// navigation 경고가 다른 테스트로 새므로 이 함수만 부분 모킹한다.
vi.mock('@/shared/lib/mapLink', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/shared/lib/mapLink')>()),
  openMapAppUrl: (url: string | null) => openMapAppUrl(url),
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
  saved: false,
  hasParking: true,
  reservable: true,
  takeoutAvailable: true,
  hasRestroom: false,
}

async function mountView(path = '/explore/places/42') {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/explore',
        name: 'explore',
        component: { template: '<div>Explore</div>' },
      },
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
      {
        path: '/journeys/new',
        name: 'journey-create',
        component: { template: '<div>Journey create</div>' },
      },
    ],
  })
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })

  await router.push(path)
  await router.isReady()

  const wrapper = mount(PlaceDetailView, {
    global: {
      plugins: [i18n, router, createPinia(), [VueQueryPlugin, { queryClient }]],
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
    openMapAppUrl.mockReset()
    sessionStorage.clear()
  })

  it('renders Place details with enabled map buttons', async () => {
    const { wrapper } = await mountView()

    expect(wrapper.text()).toContain('Seongsu Onsil')
    expect(wrapper.text()).toContain('Seoul · Seongsu')
    expect(wrapper.text()).toContain('Sea urchin pasta')
    expect(wrapper.find('button').exists()).toBe(true)
    expect(
      wrapper
        .findAll('button')
        .find((button) => button.text() === 'Google Maps')
        ?.attributes('disabled'),
    ).toBeUndefined()
    expect(
      wrapper
        .findAll('button')
        .find((button) => button.text() === 'Google transit')
        ?.attributes('disabled'),
    ).toBeUndefined()
    expect(
      wrapper
        .findAll('button')
        .find((button) => button.text() === 'Find companions')
        ?.attributes('disabled'),
    ).toBeUndefined()
  })

  it('opens Google Maps for the Place coordinates', async () => {
    const openSpy = vi.spyOn(window, 'open').mockReturnValue(null)
    const { wrapper } = await mountView()

    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Google Maps')
      ?.trigger('click')
    expect(openSpy).toHaveBeenCalledWith(
      'https://www.google.com/maps/search/?api=1&query=37.54%2C127.05',
      '_blank',
      'noopener,noreferrer',
    )

    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Google transit')
      ?.trigger('click')
    expect(openSpy).toHaveBeenCalledWith(
      'https://www.google.com/maps/dir/?api=1&destination=37.54%2C127.05&travelmode=transit',
      '_blank',
      'noopener,noreferrer',
    )

    openSpy.mockRestore()
  })

  it('opens the Naver Map app scheme for the Place coordinates', async () => {
    const { wrapper } = await mountView()

    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Naver Map')
      ?.trigger('click')
    expect(openMapAppUrl).toHaveBeenCalledWith(
      'nmap://place?lat=37.54&lng=127.05&name=Seongsu%20Onsil&appname=NA-WA',
    )

    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Naver transit')
      ?.trigger('click')
    expect(openMapAppUrl).toHaveBeenCalledWith(
      'nmap://route/public?dlat=37.54&dlng=127.05&dname=Seongsu%20Onsil&appname=NA-WA',
    )
  })

  it('hides map buttons when the Place has no coordinates', async () => {
    fetchPlaceDetail.mockResolvedValue({ ...place, latitude: null, longitude: null })

    const { wrapper } = await mountView()

    const buttonLabels = wrapper.findAll('button').map((button) => button.text())
    expect(buttonLabels).not.toContain('Google Maps')
    expect(buttonLabels).not.toContain('Google transit')
    expect(buttonLabels).not.toContain('Naver Map')
    expect(buttonLabels).not.toContain('Naver transit')
  })

  it('translates operational region values on the detail screen', async () => {
    fetchPlaceDetail.mockResolvedValue({ ...place, region1: '서울', region2: '성수' })

    const { wrapper } = await mountView()

    expect(wrapper.text()).toContain('Seoul · Seongsu')
    expect(wrapper.text()).not.toContain('서울')
  })

  it('returns to the Place list from the detail screen', async () => {
    const { wrapper, router } = await mountView()

    await wrapper.get('button[aria-label="Back to places"]').trigger('click')
    await flushPromises()
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('explore')
    expect(router.currentRoute.value.query).toEqual({ tab: 'places' })
  })

  it('opens the Place appointment list with the Place filter', async () => {
    const { wrapper, router } = await mountView()

    const button = wrapper.findAll('button').find((button) => button.text() === 'Find companions')

    expect(button?.attributes('disabled')).toBeUndefined()
    await button?.trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('appointment-list')
    expect(router.currentRoute.value.query).toEqual({ itemId: '42', itemType: 'PLACE' })
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

  /*
   * Place는 운영 기간이 없다. 예전에는 그래서 날짜 시트에 isPermanent=true를 넘겨
   * **모든 날짜**를 열었고, 여정 기간 밖까지 열려 확정한 뒤에야 JOURNEY-007로 실패했다.
   */
  it('달력을 고른 여정의 기간으로 좁힌다', async () => {
    const { wrapper } = await mountView()

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

    const dayCell = (label: string) =>
      wrapper
        .get('[role="dialog"]')
        .findAll('button')
        .find((button) => button.text().trim() === label)

    expect(dayCell('11')?.attributes('disabled')).toBeUndefined()
    expect(dayCell('9')?.attributes('disabled')).toBeDefined()
    expect(dayCell('13')?.attributes('disabled')).toBeDefined()
  })

  it('담기 실패를 오류 코드별로 안내한다', async () => {
    addJourneyItem.mockRejectedValue(
      new NormalizedApiError('JOURNEY-004', 409, '이미 등록되어 있습니다.'),
    )
    const { wrapper } = await mountView()

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

    expect(wrapper.text()).toContain('This is already on that day of your journey.')
    expect(wrapper.text()).not.toContain('Please try again.')
  })

  it('담을 여정이 없으면 그 자리에서 여정 만들기로 나간다', async () => {
    fetchJourneys.mockResolvedValue([])
    const { wrapper, router } = await mountView()

    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Add to journey')
      ?.trigger('click')
    await flushPromises()

    await wrapper
      .get('[role="dialog"]')
      .findAll('button')
      .find((button) => button.text() === 'Create a journey')
      ?.trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('journey-create')
    expect(router.currentRoute.value.query).toEqual({ returnToExplore: '1' })
    expect(JSON.parse(sessionStorage.getItem('nawa.explore.returnContext') ?? '{}')).toMatchObject({
      returnTo: { name: 'explore-place-detail', params: { placeId: '42' } },
    })
  })

  it('여정을 만들고 돌아오면 담기 시트를 다시 연다', async () => {
    const { wrapper, router } = await mountView('/explore/places/42?openJourneySelect=1')

    expect(wrapper.get('[role="dialog"]').text()).toContain('Choose a journey')
    expect(router.currentRoute.value.query).toEqual({})
  })
})
