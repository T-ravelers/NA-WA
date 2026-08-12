import { beforeEach, describe, expect, it, vi } from 'vitest'

import {
  activateSignOutBarrier,
  clearSignOutBarrier,
  isSignOutBarrierActive,
  subscribeSignOutBarrier,
} from '../signOutBarrier'

describe('signOutBarrier', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('persists and clears the sign-out intent', () => {
    activateSignOutBarrier()

    expect(isSignOutBarrierActive()).toBe(true)
    expect(localStorage.getItem('nawa.auth.signOutBarrier')).toBe('active')

    clearSignOutBarrier()

    expect(isSignOutBarrierActive()).toBe(false)
  })

  it('notifies the current tab when the barrier changes', () => {
    const listener = vi.fn()
    const unsubscribe = subscribeSignOutBarrier(listener)

    activateSignOutBarrier()
    clearSignOutBarrier()

    expect(listener).toHaveBeenNthCalledWith(1, true)
    expect(listener).toHaveBeenNthCalledWith(2, false)

    unsubscribe()
  })

  it('reflects a barrier change from another tab', () => {
    const listener = vi.fn()
    const unsubscribe = subscribeSignOutBarrier(listener)

    localStorage.setItem('nawa.auth.signOutBarrier', 'active')
    window.dispatchEvent(
      new StorageEvent('storage', {
        key: 'nawa.auth.signOutBarrier',
        newValue: 'active',
        storageArea: localStorage,
      }),
    )

    expect(listener).toHaveBeenCalledWith(true)

    unsubscribe()
  })
})
