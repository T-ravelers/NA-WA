import { VueQueryPlugin, QueryClient } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { RouterView, createMemoryHistory, createRouter, type RouteRecordRaw } from 'vue-router'

import { i18n } from '@/app/i18n'

import { useExploreFilterMemoryStore } from '../../model/exploreFilterMemory'
import exploreRoutes from '../../routes'

const fetchEventList = vi.fn()
const fetchPlaceList = vi.fn()
const scrollToMock = vi.fn()

vi.mock('../../api/exploreApi', () => ({
  fetchEventList: (filters: unknown) => fetchEventList(filters),
  fetchPlaceList: (filters: unknown) => fetchPlaceList(filters),
}))

const ExploreView = (await import('../ExploreView.vue')).default
const { presetDateRange } = await import('../../model/datePresets')

const Stub = { template: '<div />' }

/** afterEach에서 정리하려고 마지막으로 띄운 화면을 들고 있는다. */
let routedWrapper: ReturnType<typeof mount> | null = null

const EMPTY_PAGE = {
  content: [],
  page: 0,
  size: 20,
  totalElements: 0,
  totalPages: 0,
  hasNext: false,
}

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
  saved: false,
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
      plugins: [i18n, router, createPinia(), [VueQueryPlugin, { queryClient }]],
    },
  })

  await flushPromises()
  return { wrapper, router }
}

describe('ExploreView Place branch', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
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

  it('requests the Event list with the NEWEST sort by default', async () => {
    await mountView()

    expect(fetchEventList).toHaveBeenCalledWith(expect.objectContaining({ sort: 'NEWEST' }))
  })

  it('sends a single selected date as a one-day range', async () => {
    await mountView('/explore?startDate=2026-08-21')

    expect(fetchEventList).toHaveBeenCalledWith(
      expect.objectContaining({ startDate: '2026-08-21', endDate: '2026-08-21' }),
    )
  })

  it('requests only saved events when the URL asks for them', async () => {
    await mountView('/explore?eventSavedOnly=true')

    expect(fetchEventList).toHaveBeenCalledWith(expect.objectContaining({ savedOnly: true }))
  })

  /*
   * Saved는 Options 시트가 아니라 정렬 시트에 있다. Options 버튼이 켜지면 눌러 봐도
   * 아무것도 체크돼 있지 않고 그 시트의 초기화로도 지워지지 않는다.
   */
  it('does not light up the Options button for the Saved sort', async () => {
    const { wrapper } = await mountView('/explore?eventSavedOnly=true')

    const optionsButton = wrapper
      .findAll('button')
      .find((button) => button.text().startsWith('Options'))

    expect(optionsButton?.text()).toBe('Options')
  })

  it('keeps the UI-only datePreset out of list query filters', async () => {
    await mountView('/explore?datePreset=THIS_WEEKEND')

    expect(fetchEventList.mock.calls[fetchEventList.mock.calls.length - 1]?.[0]).not.toHaveProperty(
      'datePreset',
    )
  })

  it('derives dates from a legacy preset-only URL', async () => {
    const range = presetDateRange('THIS_WEEKEND')

    await mountView('/explore?datePreset=THIS_WEEKEND')

    expect(fetchEventList).toHaveBeenCalledWith(
      expect.objectContaining({ startDate: range?.min, endDate: range?.max }),
    )
  })

  it('drops an unknown preset from an old URL', async () => {
    await mountView('/explore?datePreset=GARBAGE')

    const lastFilters = fetchEventList.mock.calls[fetchEventList.mock.calls.length - 1]?.[0]
    expect(lastFilters).not.toHaveProperty('datePreset')
    expect(lastFilters).toMatchObject({ startDate: undefined })
  })

  it('keeps an Opening soon filter open ended and off the API params', async () => {
    await mountView('/explore?datePreset=OPENING_SOON&startDate=2026-08-21')

    expect(fetchEventList).toHaveBeenCalledWith(
      expect.objectContaining({ startDate: '2026-08-21', endDate: undefined }),
    )
  })

  it('sends a completed range with its own end date', async () => {
    await mountView('/explore?startDate=2026-08-21&endDate=2026-08-23')

    expect(fetchEventList).toHaveBeenCalledWith(
      expect.objectContaining({ startDate: '2026-08-21', endDate: '2026-08-23' }),
    )
  })

  it('reads a legacy LATEST event sort from an old URL as NEWEST', async () => {
    await mountView('/explore?sort=LATEST')

    expect(fetchEventList).toHaveBeenCalledWith(expect.objectContaining({ sort: 'NEWEST' }))
  })

  it('requests the Place list with the POPULAR sort by default', async () => {
    await mountView('/explore?tab=places')
    await flushPromises()

    expect(fetchPlaceList).toHaveBeenCalledWith(expect.objectContaining({ sort: 'POPULAR' }))
  })

  it('reads a legacy LATEST place sort from an old URL as NEWEST', async () => {
    await mountView('/explore?tab=places&placeSort=LATEST')
    await flushPromises()

    expect(fetchPlaceList).toHaveBeenCalledWith(expect.objectContaining({ sort: 'NEWEST' }))
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

  it('remembers the Place filters it writes to the URL', async () => {
    const { wrapper } = await mountView('/explore?tab=places')

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

    expect(useExploreFilterMemoryStore().lastQuery.places).toEqual(
      expect.objectContaining({ tab: 'places', hasParking: 'true' }),
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

  it('keeps Event filters after a direct-URL entry and a tab round trip', async () => {
    fetchEventList.mockResolvedValue({
      content: [],
      page: 2,
      size: 20,
      totalElements: 60,
      totalPages: 3,
      hasNext: false,
    })
    const { wrapper, router } = await mountView('/explore?eventKeyword=festival&eventPage=2')
    await flushPromises()

    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Places')
      ?.trigger('click')
    await flushPromises()
    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Events')
      ?.trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.query.eventKeyword).toBe('festival')
    expect(router.currentRoute.value.query.eventPage).toBe('2')
    expect(fetchEventList).toHaveBeenLastCalledWith(
      expect.objectContaining({ keyword: 'festival', page: 2 }),
    )
  })

  it('keeps Place filters after a direct-URL entry and a tab round trip', async () => {
    fetchPlaceList.mockResolvedValue({
      content: [place],
      page: 1,
      size: 20,
      totalElements: 30,
      totalPages: 2,
      hasNext: false,
    })
    const { wrapper, router } = await mountView(
      '/explore?tab=places&placeKeyword=onsil&placePage=1',
    )
    await flushPromises()

    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Events')
      ?.trigger('click')
    await flushPromises()
    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Places')
      ?.trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.query.placeKeyword).toBe('onsil')
    expect(router.currentRoute.value.query.placePage).toBe('1')
    expect(fetchPlaceList).toHaveBeenLastCalledWith(
      expect.objectContaining({ keyword: 'onsil', page: 1 }),
    )
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

/**
 * 실제 route 정의를 통해 화면을 띄운다.
 *
 * `onBeforeRouteUpdate`는 route record에 매칭된 컴포넌트에만 등록된다. 위의 `mountView`처럼
 * 컴포넌트를 직접 mount하면 가드가 아예 달리지 않아 이 동작을 볼 수 없다.
 *
 * pinia는 하나만 쓴다. guard는 컴포넌트 밖에서 돌아 `setActivePinia`가 가리키는 것을 잡으므로,
 * 화면에 다른 인스턴스를 넘기면 기억을 쓰는 쪽과 읽는 쪽이 갈라진다.
 */
async function mountRoutedView(path = '/explore') {
  const pinia = createPinia()
  setActivePinia(pinia)

  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      /* 목록만 진짜로 띄운다. 상세는 이 스펙의 관심사가 아니고 API도 mock되어 있지 않다. */
      ...exploreRoutes.map((route) =>
        route.name === 'explore' ? route : ({ ...route, component: Stub } as RouteRecordRaw),
      ),
      { path: '/appointments', name: 'appointments', component: Stub },
    ],
  })
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })

  await router.push(path)
  await router.isReady()

  const wrapper = mount(RouterView, {
    global: { plugins: [i18n, router, pinia, [VueQueryPlugin, { queryClient }]] },
  })
  routedWrapper = wrapper

  await flushPromises()
  return { wrapper, router }
}

/**
 * 다음 테스트로 넘어가기 전에 반드시 unmount한다.
 *
 * `onBeforeRouteUpdate`로 등록한 가드는 unmount될 때 풀린다. 남겨두면 앞 테스트의 화면이
 * 끝나지 않은 이동을 계속 들고 있다가, 다음 테스트의 pinia가 활성인 시점에 가드가 돌아
 * 남의 기억을 덮어쓴다. 단독으로는 통과하고 같이 돌리면 깨지는 형태로 나타난다.
 */
afterEach(() => {
  routedWrapper?.unmount()
  routedWrapper = null
})

describe('ExploreView filter memory across entries', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    scrollToMock.mockReset()
    vi.stubGlobal('scrollTo', scrollToMock)
    vi.stubGlobal(
      'matchMedia',
      vi.fn(() => ({ matches: true })),
    )
    fetchEventList.mockReset()
    fetchPlaceList.mockReset()
    fetchEventList.mockResolvedValue(EMPTY_PAGE)
    fetchPlaceList.mockResolvedValue(EMPTY_PAGE)
  })

  it('keeps the filters when the bottom tab opens Discover again', async () => {
    const { router } = await mountRoutedView('/explore?eventKeyword=hongdae&eventPage=3')

    /* 하단 탭은 query 없는 `/explore`로 보낸다. route record가 같아 beforeEnter는 안 돈다. */
    await router.push('/explore')
    await flushPromises()

    /* 필터는 남고 쪽 번호만 버린다. 새로 누른 사람은 목록을 처음부터 본다. */
    expect(router.currentRoute.value.query).toEqual({ eventKeyword: 'hongdae' })
  })

  it('keeps the Places tab and its filters when the bottom tab opens Discover again', async () => {
    const { router } = await mountRoutedView('/explore?tab=places&placeKeyword=cafe&placePage=3')

    await router.push('/explore')
    await flushPromises()

    /*
     * 하단 탭이 보내는 주소에는 `tab`이 없어서 그냥 두면 Places를 보던 사람만 Events로
     * 떨어진다. Events와 Places가 같은 규칙으로 남아야 한다.
     */
    expect(router.currentRoute.value.query).toEqual({ tab: 'places', placeKeyword: 'cafe' })
  })

  it('goes back to the top when the bottom tab opens Discover again', async () => {
    const { router } = await mountRoutedView('/explore?eventKeyword=hongdae&eventPage=3')
    scrollToMock.mockClear()

    await router.push('/explore')
    await flushPromises()

    /* 쪽 번호만 1쪽으로 떨어지고 스크롤이 그대로면 목록이 안 바뀐 것처럼 보인다. */
    expect(scrollToMock).toHaveBeenCalledWith({ top: 0, behavior: 'auto' })
  })

  it('goes back to the top when Discover is entered from another screen', async () => {
    const { router } = await mountRoutedView('/explore?eventKeyword=hongdae')

    await router.push('/appointments')
    scrollToMock.mockClear()
    await router.push('/explore')
    await flushPromises()

    expect(scrollToMock).toHaveBeenCalledWith({ top: 0, behavior: 'auto' })
  })

  it('leaves the scroll alone when coming back from an item detail', async () => {
    const { router } = await mountRoutedView('/explore?tab=places&placeKeyword=cafe')

    await router.push('/explore/places/42')
    scrollToMock.mockClear()
    await router.push('/explore?tab=places')
    await flushPromises()

    /* 쪽 번호를 되돌려 놓고 스크롤만 위로 올리면 보던 자리와 어긋난다. */
    expect(scrollToMock).not.toHaveBeenCalled()
  })

  it('leaves the scroll alone while the screen writes its own filters', async () => {
    const { wrapper } = await mountRoutedView('/explore')
    scrollToMock.mockClear()

    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Places')
      ?.trigger('click')
    await flushPromises()

    expect(scrollToMock).not.toHaveBeenCalled()
  })

  it('stays on the Places tab even when it has no filters to restore', async () => {
    const { router } = await mountRoutedView('/explore?tab=places')

    await router.push('/explore')
    await flushPromises()

    /* 되돌릴 필터가 없다는 이유로 탭까지 바뀌면 필터를 건 사람과 안 건 사람이 달라진다. */
    expect(router.currentRoute.value.query).toEqual({ tab: 'places' })
  })

  /*
   * 탭 전환이 히스토리를 늘리면 뒤로 가기가 Discover 안에 갇힌다. 되돌아간 자리는 필터가
   * 빠진 `/explore`라서 필터를 되살리는 guard가 하단 탭이 보낸 진입과 구별하지 못하고,
   * 방금 떠나온 탭으로 다시 보낸다 — 화면은 아무 반응도 없는 것처럼 보인다.
   */
  it('does not trap the back button inside Discover after a tab switch', async () => {
    /* 다른 화면에서 Discover로 들어온다 — 뒤로 가기가 돌아갈 자리가 있어야 한다. */
    const { wrapper, router } = await mountRoutedView('/appointments')
    await router.push('/explore')
    await flushPromises()

    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Places')
      ?.trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.query).toEqual({ tab: 'places' })

    router.back()
    await flushPromises()
    await flushPromises()

    /*
     * 뒤로 가기 한 번으로 Discover를 벗어난다. 탭 전환이 히스토리를 남기면 여기서
     * 필터가 빠진 `/explore`에 닿는데, 필터를 되살리는 guard가 그것을 하단 탭이 보낸
     * 진입과 구별하지 못하고 방금 떠나온 탭으로 다시 보낸다.
     */
    expect(router.currentRoute.value.path).toBe('/appointments')
  })

  it('does not revive a filter value the screen dropped from the URL', async () => {
    /* 없는 분류 ID는 화면이 걸러내고 주소에서 지운다. 기억이 그것을 되돌리면 리다이렉트가 돈다. */
    const { router } = await mountRoutedView('/explore?eventSectorIds=99999')
    await flushPromises()

    expect(router.currentRoute.value.query).toEqual({})
  })

  it("does not bring another tab's remembered filters back on a tab switch", async () => {
    const { wrapper, router } = await mountRoutedView('/explore?tab=places&placeKinds=CAFE')

    await router.push('/appointments')
    await router.push('/explore')
    await flushPromises()

    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Places')
      ?.trigger('click')
    await flushPromises()

    /* 화면에 없던 조건이 탭을 누르는 순간 걸리면 안 된다. */
    expect(router.currentRoute.value.query).toEqual({ tab: 'places' })
  })
})
