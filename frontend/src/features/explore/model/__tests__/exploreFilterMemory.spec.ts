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

  it('lets the incoming tab win over the remembered one', () => {
    const store = useExploreFilterMemoryStore()
    store.remember({ tab: 'places', keyword: 'hongdae' })

    expect(store.resolveEntry({ tab: 'events' })).toEqual({ tab: 'events', keyword: 'hongdae' })
    expect(store.resolveEntry({})).toEqual({ tab: 'places', keyword: 'hongdae' })
  })

  it('does not ask for a navigation that changes nothing', () => {
    const store = useExploreFilterMemoryStore()
    store.remember({ tab: 'places' })

    expect(store.resolveEntry({ tab: 'places' })).toBeNull()
  })
})
