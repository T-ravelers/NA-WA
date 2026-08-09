import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it } from 'vitest'

import { useSavedEventsStore } from '../savedEvents'

describe('useSavedEventsStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('shares a toggled Event state by id', () => {
    const store = useSavedEventsStore()

    expect(store.isSaved(42)).toBe(false)
    expect(store.toggle(42)).toBe(true)
    expect(store.isSaved(42)).toBe(true)
    expect(store.toggle(42)).toBe(false)
    expect(store.isSaved(42)).toBe(false)
  })
})
