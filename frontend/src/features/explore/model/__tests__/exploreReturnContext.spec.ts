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

  it('clears the stored context', () => {
    const store = useExploreReturnContextStore()
    store.capture({ journeyId: '7', startDate: '2026-09-03', endDate: '2026-09-03' })
    store.clear()

    expect(store.journeyId).toBeNull()
    expect(sessionStorage.getItem(STORAGE_KEY)).toBeNull()
  })
})
