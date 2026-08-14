import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it } from 'vitest'

import { useExploreFilterMemoryStore } from '../exploreFilterMemory'

describe('useExploreFilterMemoryStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('leaves an entry that already carries filters alone', () => {
    const store = useExploreFilterMemoryStore()
    store.remember({ keyword: 'hongdae' })

    expect(store.resolveEntry({ keyword: 'itaewon' })).toBeNull()
  })

  it('restores the remembered filters for an entry that carries none', () => {
    const store = useExploreFilterMemoryStore()
    store.remember({ keyword: 'hongdae', freeOnly: 'true' })

    expect(store.resolveEntry({})).toEqual({ keyword: 'hongdae', freeOnly: 'true' })
  })

  it('restores nothing before any filter has been seen', () => {
    const store = useExploreFilterMemoryStore()

    expect(store.resolveEntry({})).toBeNull()
    expect(store.resolveEntry({ tab: 'places' })).toBeNull()
  })

  it('keeps a cleared filter state cleared', () => {
    const store = useExploreFilterMemoryStore()
    store.remember({ keyword: 'hongdae' })
    store.remember({})

    expect(store.resolveEntry({})).toBeNull()
  })

  it('keeps the Events and Places filters apart', () => {
    const store = useExploreFilterMemoryStore()
    store.remember({ tab: 'places', placeKinds: 'CAFE' })

    /* Place 전용 키가 Events 진입에 섞이면 안 된다. Events는 아직 기억한 것이 없다. */
    expect(store.resolveEntry({ tab: 'events' })).toBeNull()
    expect(store.resolveEntry({})).toBeNull()
    expect(store.resolveEntry({ tab: 'places' })).toEqual({ tab: 'places', placeKinds: 'CAFE' })
  })

  it('remembers each tab on its own', () => {
    const store = useExploreFilterMemoryStore()
    store.remember({ keyword: 'hongdae' })
    store.remember({ tab: 'places', placeKinds: 'CAFE' })

    expect(store.resolveEntry({})).toEqual({ keyword: 'hongdae' })
    expect(store.resolveEntry({ tab: 'places' })).toEqual({ tab: 'places', placeKinds: 'CAFE' })
  })

  it('does not ask for a navigation that changes nothing', () => {
    const store = useExploreFilterMemoryStore()
    store.remember({ tab: 'places' })

    expect(store.resolveEntry({ tab: 'places' })).toBeNull()
  })
})
