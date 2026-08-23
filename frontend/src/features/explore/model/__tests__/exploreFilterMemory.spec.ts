import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it } from 'vitest'

import { useExploreFilterMemoryStore } from '../exploreFilterMemory'

describe('useExploreFilterMemoryStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('leaves an entry that already carries filters alone', () => {
    const store = useExploreFilterMemoryStore()
    store.remember({ eventKeyword: 'hongdae' })

    expect(store.resolveEntry({ eventKeyword: 'itaewon' })).toBeNull()
  })

  it('restores the remembered filters for an entry that carries none', () => {
    const store = useExploreFilterMemoryStore()
    store.remember({ eventKeyword: 'hongdae', freeOnly: 'true' })

    expect(store.resolveEntry({})).toEqual({ eventKeyword: 'hongdae', freeOnly: 'true' })
  })

  it('restores nothing before any filter has been seen', () => {
    const store = useExploreFilterMemoryStore()

    expect(store.resolveEntry({})).toBeNull()
    expect(store.resolveEntry({ tab: 'places' })).toBeNull()
  })

  it('keeps a cleared filter state cleared', () => {
    const store = useExploreFilterMemoryStore()
    store.remember({ eventKeyword: 'hongdae' })
    store.remember({})

    expect(store.resolveEntry({})).toBeNull()
  })

  it('keeps the Events and Places filters apart', () => {
    const store = useExploreFilterMemoryStore()
    store.remember({ tab: 'places', placeKinds: 'CAFE' })

    /* Place 전용 키가 Events 진입에 섞이면 안 된다. Events는 아직 기억한 것이 없다. */
    expect(store.resolveEntry({ tab: 'events' })).toBeNull()
    expect(store.resolveEntry({ tab: 'places' })).toEqual({ tab: 'places', placeKinds: 'CAFE' })
  })

  it('follows the tab that was last seen when the entry names none', () => {
    const store = useExploreFilterMemoryStore()
    store.remember({ tab: 'places', placeKinds: 'CAFE' })

    /*
     * 하단 탭이 보내는 `/explore`에는 `tab`이 없다. 그것을 Events로 읽으면 Places를 보던
     * 사람만 탭과 필터를 함께 잃는다.
     */
    expect(store.resolveEntry({})).toEqual({ tab: 'places', placeKinds: 'CAFE' })
  })

  it('stays on the last seen tab even with nothing to restore', () => {
    const store = useExploreFilterMemoryStore()
    store.remember({ tab: 'places' })

    /* 되돌릴 필터가 없다는 이유로 탭까지 바뀌면 안 된다. */
    expect(store.resolveEntry({})).toEqual({ tab: 'places' })
  })

  it('forgets both tabs and the last seen tab at once', () => {
    const store = useExploreFilterMemoryStore()
    store.remember({ eventKeyword: 'hongdae' })
    store.remember({ tab: 'places', placeKinds: 'CAFE' })

    store.clear()

    expect(store.resolveEntry({})).toBeNull()
    expect(store.resolveEntry({ tab: 'events' })).toBeNull()
    expect(store.resolveEntry({ tab: 'places' })).toBeNull()
  })

  it('remembers each tab on its own', () => {
    const store = useExploreFilterMemoryStore()
    store.remember({ eventKeyword: 'hongdae' })
    store.remember({ tab: 'places', placeKinds: 'CAFE' })

    expect(store.resolveEntry({ tab: 'events' })).toEqual({ eventKeyword: 'hongdae' })
    expect(store.resolveEntry({ tab: 'places' })).toEqual({ tab: 'places', placeKinds: 'CAFE' })
  })

  it('does not keep the entry context among the filters', () => {
    const store = useExploreFilterMemoryStore()
    store.remember({ journeyId: '7', startDate: '2026-09-03', endDate: '2026-09-03' })

    /* 날짜는 필터라 되돌리지만 journeyId는 일회성 맥락이라 되살리지 않는다. */
    expect(store.resolveEntry({})).toEqual({ startDate: '2026-09-03', endDate: '2026-09-03' })
  })

  it('drops the page number unless the entry came back from an item detail', () => {
    const store = useExploreFilterMemoryStore()
    store.remember({ eventKeyword: 'hongdae', eventPage: '3' })

    expect(store.resolveEntry({})).toEqual({ eventKeyword: 'hongdae' })
    expect(store.resolveEntry({}, { keepPage: true })).toEqual({
      eventKeyword: 'hongdae',
      eventPage: '3',
    })
  })

  it('does not ask for a navigation that changes nothing', () => {
    const store = useExploreFilterMemoryStore()
    store.remember({ tab: 'places' })

    expect(store.resolveEntry({ tab: 'places' })).toBeNull()
  })
})
