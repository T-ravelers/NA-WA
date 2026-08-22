import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it } from 'vitest'
import { createMemoryHistory, createRouter, type RouteRecordRaw } from 'vue-router'

import { useExploreFilterMemoryStore } from '../model/exploreFilterMemory'
import { useExploreReturnContextStore } from '../model/exploreReturnContext'
import routes from '../routes'

const Stub = { template: '<div />' }
const CONTEXT_ENTRY = '/explore?journeyId=7&startDate=2026-09-03&endDate=2026-09-03'
const CONTEXT_DATES = { startDate: '2026-09-03', endDate: '2026-09-03' }

/** 실제 route 정의를 그대로 쓰되 화면만 stub으로 바꾼다. beforeEnter는 진짜가 돈다. */
function createTestRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      ...routes.map((route) => ({ ...route, component: Stub }) as RouteRecordRaw),
      { path: '/journeys/:tripId', name: 'journey-detail', component: Stub },
      { path: '/', name: 'home', component: Stub },
    ],
  })
}

describe('explore routes', () => {
  beforeEach(() => {
    sessionStorage.clear()
    setActivePinia(createPinia())
  })

  it('registers authenticated list and detail screens', () => {
    expect(routes.map(({ path, name }) => ({ path, name }))).toEqual([
      { path: '/explore', name: 'explore' },
      { path: '/explore/events/:eventId', name: 'explore-event-detail' },
      { path: '/explore/places/:placeId', name: 'explore-place-detail' },
    ])
  })

  it('records the journey and date a Discover entry carries', async () => {
    const router = createTestRouter()

    await router.push(CONTEXT_ENTRY)

    const context = useExploreReturnContextStore()
    expect(context.journeyId).toBe(7)
    expect(context.visitDate).toBe('2026-09-03')
    expect(context.returnTo).toEqual({ name: 'journey-detail', params: { tripId: '7' } })
  })

  it('keeps the context while moving around inside Discover', async () => {
    const router = createTestRouter()
    await router.push(CONTEXT_ENTRY)

    await router.push('/explore/events/42')
    /* 필터를 한 번 바꾸면 URL에서 journeyId가 지워지므로, 뒤로 나올 때 맥락이 실려오지 않는다. */
    await router.push('/explore?eventKeyword=hongdae')

    const context = useExploreReturnContextStore()
    expect(context.visitDate).toBe('2026-09-03')
    expect(context.returnTo).not.toBeNull()
  })

  it('drops the one-shot context when Discover is entered from outside', async () => {
    const router = createTestRouter()
    await router.push(CONTEXT_ENTRY)

    await router.push('/journeys/7')
    await router.push('/explore')

    const context = useExploreReturnContextStore()
    expect(context.visitDate).toBeNull()
    expect(context.returnTo).toBeNull()
    expect(context.journeyId).toBe(7)
  })

  it('drops the one-shot context on a direct entry that carries none', async () => {
    sessionStorage.setItem(
      'nawa.explore.returnContext',
      JSON.stringify({
        journeyId: 7,
        visitDate: '2026-09-03',
        returnTo: { name: 'journey-detail', params: { tripId: '7' } },
      }),
    )
    const router = createTestRouter()

    await router.push('/explore')

    const context = useExploreReturnContextStore()
    expect(context.visitDate).toBeNull()
    expect(context.journeyId).toBe(7)
  })

  it('forgets the filters when Discover is entered from another screen', async () => {
    const router = createTestRouter()
    await router.push('/explore?eventKeyword=hongdae')

    await router.push('/')
    await router.push('/explore')

    /* 다른 화면을 보다 돌아온 사람은 목록을 처음부터 본다. */
    expect(router.currentRoute.value.query).toEqual({})
  })

  it('brings the filters back when returning from an item detail', async () => {
    const router = createTestRouter()
    await router.push('/explore?eventKeyword=hongdae')

    await router.push('/explore/events/42')
    /* 상세의 뒤로가기는 필터가 빠진 주소로 되돌아온다. */
    await router.push('/explore?tab=events')

    expect(router.currentRoute.value.query).toEqual({ eventKeyword: 'hongdae' })
  })

  it('brings back a filter URL that was never touched after a refresh', async () => {
    /* 새로고침하면 이 주소가 첫 진입이 된다. 화면의 필터 watcher는 아직 한 번도 돌지 않았다. */
    const router = createTestRouter()
    await router.push('/explore?eventKeyword=hongdae&freeOnly=true')

    await router.push('/explore/events/42')
    await router.push('/explore?tab=events')

    expect(router.currentRoute.value.query).toEqual({ eventKeyword: 'hongdae', freeOnly: 'true' })
  })

  it('brings back the Journey date after visiting an item detail', async () => {
    const router = createTestRouter()
    await router.push('/journeys/7')
    await router.push(CONTEXT_ENTRY)

    /* 필터를 만지지 않고 바로 상세로 들어갔다 나온다. */
    await router.push('/explore/events/42')
    await router.push('/explore?tab=events')

    expect(router.currentRoute.value.query).toEqual(CONTEXT_DATES)
    const context = useExploreReturnContextStore()
    expect(context.visitDate).toBe('2026-09-03')
    expect(context.returnTo).toEqual({ name: 'journey-detail', params: { tripId: '7' } })
  })

  it('restores the date filter without reviving the one-shot context', async () => {
    const router = createTestRouter()
    await router.push('/journeys/7')
    await router.push(CONTEXT_ENTRY)

    /* Discover 바깥으로 나갔다가 하단 탭으로 다시 들어온 진입이다. */
    await router.push('/')
    await router.push('/explore')

    /* 날짜는 필터라 기억과 함께 버려지고, journeyId는 맥락이라 애초에 되살아나지 않는다. */
    expect(router.currentRoute.value.query).toEqual({})
    const context = useExploreReturnContextStore()
    expect(context.visitDate).toBeNull()
    expect(context.returnTo).toBeNull()
  })

  it('goes back to the page the item detail was opened from', async () => {
    const router = createTestRouter()
    await router.push('/explore?tab=places&placeKeyword=cafe&placePage=4')

    await router.push('/explore/places/9')
    await router.push('/explore?tab=places')

    expect(router.currentRoute.value.query).toEqual({
      tab: 'places',
      placeKeyword: 'cafe',
      placePage: '4',
    })
  })

  it('starts the list over when Discover is entered from outside', async () => {
    const router = createTestRouter()
    await router.push('/explore?eventKeyword=hongdae&eventPage=3')

    await router.push('/')
    await router.push('/explore')

    /* 필터도 쪽 번호도 남기지 않는다. Discover를 새로 누른 사람은 목록을 처음부터 본다. */
    expect(router.currentRoute.value.query).toEqual({})
  })

  it('forgets both tabs at once when Discover is entered from outside', async () => {
    const router = createTestRouter()
    await router.push('/explore?tab=places&placeKinds=CAFE')

    await router.push('/')
    await router.push('/explore')

    /*
     * Events로 들어왔다고 Events 칸만 비우면 Places 칸이 살아남는다. 그러면 Places 탭을
     * 누르거나 장소 상세에서 뒤로 나오는 순간 화면에 없던 조건이 걸린다.
     */
    await router.push('/explore/places/9')
    await router.push('/explore?tab=places')

    expect(router.currentRoute.value.query).toEqual({ tab: 'places' })
  })

  it('lets an explicit shared URL win over the remembered filters', async () => {
    const router = createTestRouter()
    await router.push('/explore?eventKeyword=hongdae')

    await router.push('/')
    await router.push('/explore?eventKeyword=itaewon')

    expect(router.currentRoute.value.query).toEqual({ eventKeyword: 'itaewon' })
  })

  it('does not mix the Places filters into an Events entry', async () => {
    const router = createTestRouter()
    await router.push('/explore?tab=places&placeKinds=CAFE')
    await router.push('/')

    /* Event 상세에서 뒤로 나온 진입. Events는 아직 기억한 것이 없으므로 아무것도 안 붙는다. */
    await router.push('/explore?tab=events')
    expect(router.currentRoute.value.query).toEqual({ tab: 'events' })

    /* `/`를 거치면서 두 칸이 함께 비워졌으므로 Place 필터도 남아 있지 않다. */
    await router.push('/explore/places/9')
    await router.push('/explore?tab=places')
    expect(router.currentRoute.value.query).toEqual({ tab: 'places' })
  })

  it('keeps a cleared filter state cleared when coming back from an item detail', async () => {
    const router = createTestRouter()
    await router.push('/explore?eventKeyword=hongdae')
    /* 필터를 모두 지우는 것은 화면이 한다. 같은 라우트라 가드가 아니라 watcher가 기억한다. */
    useExploreFilterMemoryStore().remember({})

    await router.push('/explore/events/42')
    await router.push('/explore?tab=events')

    /* Events는 `tab`을 쓰지 않으므로 비운 상태로 정리된 주소가 된다. */
    expect(router.currentRoute.value.query).toEqual({})
  })

  it('replaces the context when another journey enters Discover', async () => {
    const router = createTestRouter()
    await router.push(CONTEXT_ENTRY)

    await router.push('/journeys/9')
    await router.push('/explore?journeyId=9&startDate=2026-10-01&endDate=2026-10-01')

    const context = useExploreReturnContextStore()
    expect(context.journeyId).toBe(9)
    expect(context.visitDate).toBe('2026-10-01')
  })
})
