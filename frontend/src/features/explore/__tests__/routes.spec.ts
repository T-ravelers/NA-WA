import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it } from 'vitest'
import { createMemoryHistory, createRouter, type RouteRecordRaw } from 'vue-router'

import { useExploreReturnContextStore } from '../model/exploreReturnContext'
import routes from '../routes'

const Stub = { template: '<div />' }
const CONTEXT_ENTRY = '/explore?journeyId=7&startDate=2026-09-03&endDate=2026-09-03'

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
    await router.push('/explore?keyword=hongdae')

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
