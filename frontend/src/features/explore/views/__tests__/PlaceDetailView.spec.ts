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

/**
 * 앱이 주입하는 `parseJourneyRouteQuery`와 같게 동작하는 스텁.
 *
 * journey feature를 직접 import할 수 없어(`architecture/no-cross-feature-imports`)
 * 규칙만 여기에 옮겨 둔다. 예전처럼 `() => null`로 막아 두면 **복귀 진입이 새 여정
 * id를 읽는 경로가 통째로 실행되지 않아**, 받는 쪽이 비어 있어도 테스트가 초록으로
 * 남는다. 실제 구현(`journey/model/journeyRouteQuery`)이 바뀌면 여기도 맞춰야 한다.
 */
function parseJourneyRouteQuery(value: unknown): number | null {
  const raw = Array.isArray(value) ? value[0] : value
  if (typeof raw !== 'string' && typeof raw !== 'number') return null

  const parsed = Number(raw)
  return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : null
}

vi.mock('../../model/journeyIntegration', async () => {
  const { useQuery } = await import('@tanstack/vue-query')
  const { computed, toValue } = await import('vue')
  return {
    useExploreJourneyIntegration: () => ({
      addJourneyItem: (journeyId: number, request: { itemId: number; visitDate: string }) =>
        addJourneyItem(journeyId, request),
      parseJourneyRouteQuery,
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

  // 수집한 영업시간은 대부분 { raw: '...' } 한 칸짜리 객체다. raw는 크롤러가 붙인
  // 키 이름이고 행에는 이미 Hours가 적혀 있어, 그대로 찍으면 'raw: 12:00 ~ 22:00'이
  // 된다. 값에 섞여 오는 <br>도 화면에서는 글자로 보인다.
  it('hides the raw hours key and turns <br> into a line break', async () => {
    fetchPlaceDetail.mockResolvedValue({
      ...place,
      openingHours: { raw: '- 12:00~22:00<br>- 준비시간 15:00~18:00' },
    })

    const { wrapper } = await mountView()

    expect(wrapper.text()).toContain('- 12:00~22:00')
    expect(wrapper.text()).toContain('- 준비시간 15:00~18:00')
    expect(wrapper.text()).not.toContain('raw:')
    expect(wrapper.text()).not.toContain('<br>')
  })

  // 수집한 대표 메뉴는 대부분 한 문자열에 '/'로 이어져 온다. 나누지 않으면
  // 'A / B / C'가 칩 하나에 통째로 들어간다.
  it('splits the signature menu into one chip per item', async () => {
    fetchPlaceDetail.mockResolvedValue({
      ...place,
      menuSummary: '떡볶이 / 순대 / 김밥',
    })

    const { wrapper } = await mountView()

    const chips = wrapper.findAll('span').map((chip) => chip.text())
    expect(chips).toContain('떡볶이')
    expect(chips).toContain('순대')
    expect(chips).toContain('김밥')
    expect(chips).not.toContain('떡볶이 / 순대 / 김밥')
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
    // 다른 화면에 들렀다 돌아오는 query 규약. 이 화면은 route param을 쓰므로
    // returnParams도 함께 싣는다. openJourneySelect는 그대로 돌아와 하던 일을 잇는다.
    expect(router.currentRoute.value.query).toEqual({
      returnRouteName: 'explore-place-detail',
      returnParams: 'placeId:42',
      openJourneySelect: '1',
    })
  })

  it('여정을 만들고 돌아오면 그 여정이 골라진 채 시트가 열리고 규약 key가 지워진다', async () => {
    const { wrapper, router } = await mountView('/explore/places/42?tripId=7&openJourneySelect=1')

    expect(wrapper.get('[role="dialog"]').text()).toContain('Choose a journey')

    const pressed = wrapper
      .get('[role="dialog"]')
      .findAll('button')
      .find((button) => button.attributes('aria-pressed') === 'true')
    expect(pressed?.text()).toContain('Seoul weekend')

    expect(router.currentRoute.value.query).toEqual({})
  })
})
