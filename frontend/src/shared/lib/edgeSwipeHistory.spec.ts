import { mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h } from 'vue'
import type { Router } from 'vue-router'

import { createEdgeSwipeTracker, useEdgeSwipeHistory } from './edgeSwipeHistory'

describe('createEdgeSwipeTracker', () => {
  it('ignores a swipe that starts away from the edge', () => {
    const tracker = createEdgeSwipeTracker()

    tracker.start(100, 300)
    tracker.move(300, 300)

    expect(tracker.end()).toBe(false)
  })

  it('goes back on a long rightward swipe from the left edge', () => {
    const tracker = createEdgeSwipeTracker()

    tracker.start(8, 300)
    tracker.move(120, 310)

    expect(tracker.end()).toBe(true)
  })

  it('ignores a short swipe', () => {
    const tracker = createEdgeSwipeTracker()

    tracker.start(8, 300)
    tracker.move(60, 300) // 52px < 72px

    expect(tracker.end()).toBe(false)
  })

  it('ignores a swipe that moves more vertically than horizontally', () => {
    const tracker = createEdgeSwipeTracker()

    tracker.start(8, 100)
    tracker.move(100, 250) // dx 92, dy 150 → 스크롤

    expect(tracker.end()).toBe(false)
  })

  it('ignores a leftward swipe that starts on the left edge', () => {
    const tracker = createEdgeSwipeTracker()

    tracker.start(20, 300)
    tracker.move(-100, 300)

    expect(tracker.end()).toBe(false)
  })

  it('does nothing after the browser takes the gesture (cancel)', () => {
    const tracker = createEdgeSwipeTracker()

    tracker.start(8, 300)
    tracker.move(200, 300)
    tracker.cancel()

    expect(tracker.isTracking).toBe(false)
    expect(tracker.end()).toBe(false)
  })
})

/**
 * jsdom에는 `Touch` 생성자가 없다. 평범한 Event에 좌표 목록만 얹어 보낸다.
 *
 * 끝나는 이벤트에서 좌표는 `touches`가 아니라 `changedTouches`에 있다 — 실제 브라우저와 같다.
 */
function touch(type: string, x: number, y = 300): void {
  const event = new Event(type, { bubbles: true })
  const points = [{ clientX: x, clientY: y }]
  const ended = type === 'touchend' || type === 'touchcancel'

  Object.defineProperty(event, 'touches', { value: ended ? [] : points })
  Object.defineProperty(event, 'changedTouches', { value: ended ? points : [] })
  window.dispatchEvent(event)
}

/** 시작 → 중간 `touchmove` → 손 뗌. 실기기에서 보통 오는 모양이다. */
function swipe(fromX: number, toX: number): void {
  touch('touchstart', fromX)
  touch('touchmove', toX)
  touch('touchend', toX)
}

const back = vi.fn()
const router = { back } as unknown as Router
let wrapper: ReturnType<typeof mount> | null = null

function mountHost(): void {
  const Host = defineComponent({
    setup() {
      useEdgeSwipeHistory(router)

      return () => h('div')
    },
  })

  wrapper = mount(Host, { attachTo: document.body })
}

/** 홈 화면에 설치해 `standalone`으로 떴는지 정한다. jsdom에는 `matchMedia`가 없다. */
function stubInstalledApp(installed: boolean): void {
  vi.stubGlobal(
    'matchMedia',
    vi.fn(() => ({ matches: installed })),
  )
}

beforeEach(() => {
  vi.clearAllMocks()
  window.history.replaceState({ back: '/journeys', current: '/wallet' }, '')
})

afterEach(() => {
  wrapper?.unmount()
  wrapper = null
  document.body.innerHTML = ''
  vi.unstubAllGlobals()
})

describe('useEdgeSwipeHistory in an installed app', () => {
  beforeEach(() => {
    stubInstalledApp(true)
    mountHost()
  })

  it('calls router.back for a left-edge swipe when there is somewhere to go back to', () => {
    swipe(8, 200)

    expect(back).toHaveBeenCalledTimes(1)
  })

  it('decides on the release point when the browser sent no touchmove', () => {
    touch('touchstart', 8)
    touch('touchend', 200)

    expect(back).toHaveBeenCalledTimes(1)
  })

  it('decides on the release point when the last touchmove was short of the threshold', () => {
    touch('touchstart', 8)
    touch('touchmove', 60) // 52px — 아직 임계값 아래
    touch('touchend', 200) // 192px — 뗀 위치는 넘는다

    expect(back).toHaveBeenCalledTimes(1)
  })

  it('stays put when history has no back entry', () => {
    window.history.replaceState({ back: null, current: '/' }, '')

    swipe(8, 200)

    expect(back).not.toHaveBeenCalled()
  })

  it('does not navigate when the browser cancelled the touch (native swipe took over)', () => {
    touch('touchstart', 8)
    touch('touchmove', 200)
    touch('touchcancel', 200)

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

describe('useEdgeSwipeHistory in a browser tab', () => {
  it('does not listen at all — the browser has its own edge gesture', () => {
    stubInstalledApp(false)
    mountHost()

    swipe(8, 200)

    expect(back).not.toHaveBeenCalled()
  })
})
