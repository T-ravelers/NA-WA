import { onBeforeUnmount, onMounted } from 'vue'
import type { Router } from 'vue-router'

/**
 * 화면 가장자리 스와이프로 뒤로·앞으로 가기.
 *
 * 사파리 탭에서도, 홈 화면에 설치한 PWA(`display: standalone`)에서도 같은 제스처를 준다(#381).
 * 브라우저가 네이티브 스와이프로 가져가면 페이지에는 `touchcancel`이 오므로 우리는 취소한다 —
 * 네이티브가 있는 환경에서 두 번 이동하지 않는 이유가 이것이다. `touchend`까지 우리에게 온
 * 경우에만 이동한다.
 *
 * 시각 피드백은 없다. 네이티브가 있는 환경에서는 네이티브가, 없는 환경에서는 즉시 전환이 기대치다.
 */
export interface EdgeSwipeOptions {
  /** 가장자리 인식 폭(px). 이 안에서 시작한 터치만 추적한다. */
  edge?: number
  /** 확정에 필요한 가로 이동(px). */
  threshold?: number
}

export type EdgeSwipeDirection = 'back' | 'forward'

const DEFAULT_EDGE = 24
const DEFAULT_THRESHOLD = 72

interface Tracking {
  side: 'left' | 'right'
  startX: number
  startY: number
  lastX: number
  lastY: number
}

/**
 * 제스처 판정만 떼어낸 순수 추적기. DOM·라우터를 모르므로 그대로 테스트한다.
 *
 * `start`는 가장자리에서 시작했을 때만 추적을 연다. `end`는 확정된 방향을 돌려주고, 아니면 `null`.
 * `cancel`은 브라우저가 제스처를 가져갔을 때(`touchcancel`) 부른다.
 */
export function createEdgeSwipeTracker(options: EdgeSwipeOptions = {}) {
  const edge = options.edge ?? DEFAULT_EDGE
  const threshold = options.threshold ?? DEFAULT_THRESHOLD
  let tracking: Tracking | null = null

  return {
    start(x: number, y: number, viewportWidth: number): void {
      tracking = null

      if (x <= edge) {
        tracking = { side: 'left', startX: x, startY: y, lastX: x, lastY: y }
      } else if (x >= viewportWidth - edge) {
        tracking = { side: 'right', startX: x, startY: y, lastX: x, lastY: y }
      }
    },

    move(x: number, y: number): void {
      if (tracking !== null) {
        tracking.lastX = x
        tracking.lastY = y
      }
    },

    end(): EdgeSwipeDirection | null {
      if (tracking === null) {
        return null
      }

      const dx = tracking.lastX - tracking.startX
      const dy = Math.abs(tracking.lastY - tracking.startY)
      const side = tracking.side

      tracking = null

      // 세로로 더 많이 움직였으면 스크롤이다.
      if (Math.abs(dx) < threshold || dy >= Math.abs(dx)) {
        return null
      }

      if (side === 'left' && dx > 0) {
        return 'back'
      }

      if (side === 'right' && dx < 0) {
        return 'forward'
      }

      return null
    },

    cancel(): void {
      tracking = null
    },

    get isTracking(): boolean {
      return tracking !== null
    },
  }
}

/** vue-router가 `history.state`에 남기는 이웃 항목. 없으면 갈 곳이 없다. */
function historyHas(direction: EdgeSwipeDirection): boolean {
  const state: unknown = window.history.state

  if (typeof state !== 'object' || state === null) {
    return false
  }

  const value = (state as Record<string, unknown>)[direction]

  return typeof value === 'string' && value !== ''
}

/** 시트·모달이 열려 있으면 제스처를 끈다. 먼저 닫히는 것이 맞다. */
function isModalOpen(): boolean {
  return document.querySelector('[aria-modal="true"]') !== null
}

/**
 * `AppShell`에서 한 번 켠다. `window`에 터치 리스너를 `passive`로 걸어 스크롤 성능을 건드리지 않는다.
 *
 * 마우스·트랙패드는 대상이 아니다 — 가장자리 드래그를 뒤로가기로 오인하지 않게 터치 이벤트만 듣는다.
 */
export function useEdgeSwipeHistory(router: Router, options: EdgeSwipeOptions = {}): void {
  const tracker = createEdgeSwipeTracker(options)

  const onTouchStart = (event: TouchEvent): void => {
    if (event.touches.length !== 1 || isModalOpen()) {
      tracker.cancel()
      return
    }

    const touch = event.touches[0]

    if (touch !== undefined) {
      tracker.start(touch.clientX, touch.clientY, window.innerWidth)
    }
  }

  const onTouchMove = (event: TouchEvent): void => {
    const touch = event.touches[0]

    if (touch !== undefined && tracker.isTracking) {
      tracker.move(touch.clientX, touch.clientY)
    }
  }

  const onTouchEnd = (): void => {
    const direction = tracker.end()

    if (direction === null || !historyHas(direction)) {
      return
    }

    if (direction === 'back') {
      router.back()
    } else {
      router.forward()
    }
  }

  const onTouchCancel = (): void => {
    tracker.cancel()
  }

  onMounted(() => {
    window.addEventListener('touchstart', onTouchStart, { passive: true })
    window.addEventListener('touchmove', onTouchMove, { passive: true })
    window.addEventListener('touchend', onTouchEnd)
    window.addEventListener('touchcancel', onTouchCancel)
  })

  onBeforeUnmount(() => {
    window.removeEventListener('touchstart', onTouchStart)
    window.removeEventListener('touchmove', onTouchMove)
    window.removeEventListener('touchend', onTouchEnd)
    window.removeEventListener('touchcancel', onTouchCancel)
  })
}
