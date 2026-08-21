import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it } from 'vitest'

import { useExploreReturnContextStore } from '../exploreReturnContext'

const STORAGE_KEY = 'nawa.explore.returnContext'

describe('useExploreReturnContextStore', () => {
  beforeEach(() => {
    sessionStorage.clear()
    setActivePinia(createPinia())
  })

  it('captures the journey and date a Discover entry came from', () => {
    const store = useExploreReturnContextStore()

    expect(store.capture({ journeyId: '7', startDate: '2026-09-03', endDate: '2026-09-03' })).toBe(
      true,
    )
    expect(store.journeyId).toBe(7)
    expect(store.visitDate).toBe('2026-09-03')
    expect(store.returnTo).toEqual({ name: 'journey-detail', params: { tripId: '7' } })
  })

  it('reads a repeated route query value as its first entry', () => {
    const store = useExploreReturnContextStore()

    expect(
      store.capture({ journeyId: ['7'], startDate: ['2026-09-03'], endDate: ['2026-09-03'] }),
    ).toBe(true)
    expect(store.journeyId).toBe(7)
  })

  it('keeps the existing context when an entry carries none', () => {
    const store = useExploreReturnContextStore()
    store.capture({ journeyId: '7', startDate: '2026-09-03', endDate: '2026-09-03' })

    expect(store.capture({ journeyId: undefined, startDate: undefined, endDate: undefined })).toBe(
      false,
    )
    expect(store.capture({ journeyId: '7', startDate: 'not-a-date', endDate: 'not-a-date' })).toBe(
      false,
    )
    expect(store.capture({ journeyId: '0', startDate: '2026-09-03', endDate: '2026-09-03' })).toBe(
      false,
    )
    expect(store.journeyId).toBe(7)
    expect(store.visitDate).toBe('2026-09-03')
  })

  it('ignores a date range that is not a single day', () => {
    const store = useExploreReturnContextStore()

    expect(store.capture({ journeyId: '7', startDate: '2026-09-03', endDate: '2026-09-30' })).toBe(
      false,
    )
    expect(store.capture({ journeyId: '7', startDate: '2026-09-03', endDate: undefined })).toBe(
      false,
    )
    expect(store.journeyId).toBeNull()
  })

  it('replaces the context when another journey enters Discover', () => {
    const store = useExploreReturnContextStore()
    store.capture({ journeyId: '7', startDate: '2026-09-03', endDate: '2026-09-03' })
    store.capture({ journeyId: '9', startDate: '2026-10-01', endDate: '2026-10-01' })

    expect(store.journeyId).toBe(9)
    expect(store.visitDate).toBe('2026-10-01')
    expect(store.returnTo).toEqual({ name: 'journey-detail', params: { tripId: '9' } })
  })

  it('drops the date and return location when a different journey is selected', () => {
    const store = useExploreReturnContextStore()
    store.capture({ journeyId: '7', startDate: '2026-09-03', endDate: '2026-09-03' })

    store.setJourneyId(7)
    expect(store.visitDate).toBe('2026-09-03')
    expect(store.returnTo).not.toBeNull()

    store.setJourneyId(9)
    expect(store.journeyId).toBe(9)
    expect(store.visitDate).toBeNull()
    expect(store.returnTo).toBeNull()
  })

  it('survives a reload through sessionStorage', () => {
    useExploreReturnContextStore().capture({
      journeyId: '7',
      startDate: '2026-09-03',
      endDate: '2026-09-03',
    })

    setActivePinia(createPinia())
    const restored = useExploreReturnContextStore()

    expect(restored.journeyId).toBe(7)
    expect(restored.visitDate).toBe('2026-09-03')
    expect(restored.returnTo).toEqual({ name: 'journey-detail', params: { tripId: '7' } })
  })

  it('ignores a stored value that is not a usable context', () => {
    sessionStorage.setItem(STORAGE_KEY, 'not json')
    expect(useExploreReturnContextStore().journeyId).toBeNull()

    setActivePinia(createPinia())
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify({ journeyId: -1, visitDate: 'nope' }))
    const store = useExploreReturnContextStore()

    expect(store.journeyId).toBeNull()
    expect(store.visitDate).toBeNull()
    expect(store.returnTo).toBeNull()
  })

  /*
   * Discover 상세에서 여정을 만들러 나갈 때 쓴다. `capture()`는 Journey에서 넘어온
   * 진입만 다뤄 복귀 위치가 늘 journey-detail이라 이 자리에 쓸 수 없었다.
   */
  it('돌아올 위치를 직접 심고 새로고침을 견딘다', () => {
    const store = useExploreReturnContextStore()
    store.captureReturnTo({ name: 'explore-event-detail', params: { eventId: '42' } })

    expect(store.returnTo).toEqual({
      name: 'explore-event-detail',
      params: { eventId: '42' },
    })

    setActivePinia(createPinia())
    expect(useExploreReturnContextStore().returnTo).toEqual({
      name: 'explore-event-detail',
      params: { eventId: '42' },
    })
  })

  it('돌아올 위치를 심어도 이미 고른 여정은 지우지 않는다', () => {
    const store = useExploreReturnContextStore()
    store.setJourneyId(7)
    store.captureReturnTo({ name: 'explore-place-detail', params: { placeId: '501' } })

    expect(store.journeyId).toBe(7)
  })

  it('쓸 수 없는 복귀 위치는 무시한다', () => {
    const store = useExploreReturnContextStore()
    store.captureReturnTo({ name: '', params: {} })
    store.captureReturnTo({ name: 'explore-event-detail', params: { eventId: 42 } })
    store.captureReturnTo('nope')

    expect(store.returnTo).toBeNull()
  })

  it('심은 복귀 위치도 한 번 쓰면 사라진다', () => {
    const store = useExploreReturnContextStore()
    store.captureReturnTo({ name: 'explore-event-detail', params: { eventId: '42' } })

    expect(store.consumeReturn()).toEqual({
      name: 'explore-event-detail',
      params: { eventId: '42' },
    })
    expect(store.consumeReturn()).toBeNull()
  })

  it('hands back the return location and drops the one-shot context', () => {
    const store = useExploreReturnContextStore()
    store.capture({ journeyId: '7', startDate: '2026-09-03', endDate: '2026-09-03' })

    expect(store.consumeReturn()).toEqual({ name: 'journey-detail', params: { tripId: '7' } })
    expect(store.visitDate).toBeNull()
    expect(store.returnTo).toBeNull()
    expect(store.journeyId).toBe(7)
  })

  it('has nothing left to consume the second time', () => {
    const store = useExploreReturnContextStore()
    store.capture({ journeyId: '7', startDate: '2026-09-03', endDate: '2026-09-03' })
    store.consumeReturn()

    expect(store.consumeReturn()).toBeNull()
  })

  it('does not bring the consumed date back after a reload', () => {
    useExploreReturnContextStore().capture({
      journeyId: '7',
      startDate: '2026-09-03',
      endDate: '2026-09-03',
    })
    useExploreReturnContextStore().consumeReturn()

    setActivePinia(createPinia())
    const restored = useExploreReturnContextStore()

    expect(restored.journeyId).toBe(7)
    expect(restored.visitDate).toBeNull()
    expect(restored.returnTo).toBeNull()
  })

  it('drops the one-shot context without handing back a destination', () => {
    const store = useExploreReturnContextStore()
    store.capture({ journeyId: '7', startDate: '2026-09-03', endDate: '2026-09-03' })

    store.discardReturn()

    expect(store.visitDate).toBeNull()
    expect(store.returnTo).toBeNull()
    expect(store.journeyId).toBe(7)
    expect(JSON.parse(sessionStorage.getItem(STORAGE_KEY) ?? 'null')).toEqual({
      journeyId: 7,
      visitDate: null,
      returnTo: null,
    })
  })

  it('clears the stored context', () => {
    const store = useExploreReturnContextStore()
    store.capture({ journeyId: '7', startDate: '2026-09-03', endDate: '2026-09-03' })
    store.clear()

    expect(store.journeyId).toBeNull()
    expect(sessionStorage.getItem(STORAGE_KEY)).toBeNull()
  })
})
