import { mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h } from 'vue'
import type { Router } from 'vue-router'

import { createEdgeSwipeTracker, useEdgeSwipeHistory } from './edgeSwipeHistory'

const WIDTH = 390

describe('createEdgeSwipeTracker', () => {
  it('ignores a swipe that starts away from the edges', () => {
    const tracker = createEdgeSwipeTracker()

    tracker.start(100, 300, WIDTH)
    tracker.move(300, 300)

    expect(tracker.end()).toBeNull()
  })

  it('goes back on a long rightward swipe from the left edge', () => {
    const tracker = createEdgeSwipeTracker()

    tracker.start(8, 300, WIDTH)
    tracker.move(120, 310)

    expect(tracker.end()).toBe('back')
  })

  it('goes forward on a long leftward swipe from the right edge', () => {
    const tracker = createEdgeSwipeTracker()

    tracker.start(WIDTH - 8, 300, WIDTH)
    tracker.move(WIDTH - 120, 300)

    expect(tracker.end()).toBe('forward')
  })

  it('ignores a short swipe', () => {
    const tracker = createEdgeSwipeTracker()

    tracker.start(8, 300, WIDTH)
    tracker.move(60, 300) // 52px < 72px

    expect(tracker.end()).toBeNull()
  })

  it('ignores a swipe that moves more vertically than horizontally', () => {
    const tracker = createEdgeSwipeTracker()

    tracker.start(8, 100, WIDTH)
    tracker.move(100, 250) // dx 92, dy 150 → 스크롤

    expect(tracker.end()).toBeNull()
  })

  it('ignores a swipe toward the same edge it started from', () => {
    const tracker = createEdgeSwipeTracker()

    tracker.start(WIDTH - 8, 300, WIDTH)
    tracker.move(WIDTH + 100, 300)

    expect(tracker.end()).toBeNull()
  })

  it('does nothing after the browser takes the gesture (cancel)', () => {
    const tracker = createEdgeSwipeTracker()

    tracker.start(8, 300, WIDTH)
    tracker.move(200, 300)
    tracker.cancel()

    expect(tracker.isTracking).toBe(false)
    expect(tracker.end()).toBeNull()
  })
})

/** jsdom에는 `Touch` 생성자가 없다. 평범한 Event에 `touches`만 얹어 보낸다. */
function touch(type: string, x: number, y: number): void {
  const event = new Event(type, { bubbles: true })

  Object.defineProperty(event, 'touches', {
    value: type === 'touchend' || type === 'touchcancel' ? [] : [{ clientX: x, clientY: y }],
  })
  window.dispatchEvent(event)
}

function swipe(fromX: number, toX: number, cancel = false): void {
  touch('touchstart', fromX, 300)
  touch('touchmove', toX, 300)
  touch(cancel ? 'touchcancel' : 'touchend', toX, 300)
}

describe('useEdgeSwipeHistory', () => {
  const back = vi.fn()
  const forward = vi.fn()
  const router = { back, forward } as unknown as Router
  let wrapper: ReturnType<typeof mount> | null = null

  beforeEach(() => {
    vi.clearAllMocks()
    Object.defineProperty(window, 'innerWidth', { configurable: true, value: WIDTH })
    window.history.replaceState(
      { back: '/journeys', current: '/wallet', forward: '/wallet/top-up' },
      '',
    )
    const Host = defineComponent({
      setup() {
        useEdgeSwipeHistory(router)

        return () => h('div')
      },
    })

    wrapper = mount(Host, { attachTo: document.body })
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = null
    document.body.innerHTML = ''
  })

  it('calls router.back for a left-edge swipe when there is somewhere to go back to', () => {
    swipe(8, 200)

    expect(back).toHaveBeenCalledTimes(1)
    expect(forward).not.toHaveBeenCalled()
  })

  it('calls router.forward for a right-edge swipe when there is a forward entry', () => {
    swipe(WIDTH - 8, WIDTH - 200)

    expect(forward).toHaveBeenCalledTimes(1)
    expect(back).not.toHaveBeenCalled()
  })

  it('stays put when history has no back entry', () => {
    window.history.replaceState({ back: null, current: '/', forward: null }, '')

    swipe(8, 200)

    expect(back).not.toHaveBeenCalled()
  })

  it('does not navigate when the browser cancelled the touch (native swipe took over)', () => {
    swipe(8, 200, true)

    expect(back).not.toHaveBeenCalled()
  })

  it('leaves an open sheet alone', () => {
    const sheet = document.createElement('div')

    sheet.setAttribute('aria-modal', 'true')
    document.body.appendChild(sheet)

    swipe(8, 200)

    expect(back).not.toHaveBeenCalled()
  })

  it('stops listening after unmount', () => {
    wrapper?.unmount()
    wrapper = null

    swipe(8, 200)

    expect(back).not.toHaveBeenCalled()
  })
})
