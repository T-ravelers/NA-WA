import { onBeforeUnmount, onMounted } from 'vue'
import type { Router } from 'vue-router'

/**
 * 설치형 PWA에서 왼쪽 가장자리 스와이프로 뒤로 가기(#381).
 *
 * **홈 화면에 설치해 `standalone`으로 뜬 앱에서만 켠다.** 브라우저 탭에는 네이티브 가장자리
 * 제스처가 이미 있어 우리 제스처와 겹치고, 겹쳤을 때 두 번 이동하지 않는다는 보장은
 * `touchcancel`이 온다는 가정뿐이다. 그 가정은 실기기에서 확인되지 않았다(#383 리뷰).
 *
 * **앞으로 가기는 넣지 않는다.** Android 제스처 내비게이션은 좌우 양쪽 가장자리가 모두 시스템
 * Back이라, 오른쪽 가장자리를 `forward`로 쓰면 같은 제스처의 결과가 기기·설정에 따라 반대로
 * 갈린다(#383 리뷰).
 *
 * 시각 피드백은 없다 — 즉시 전환이 기대치다.
 */
export interface EdgeSwipeOptions {
  /** 가장자리 인식 폭(px). 이 안에서 시작한 터치만 추적한다. */
  edge?: number
  /** 확정에 필요한 가로 이동(px). */
  threshold?: number
}

const DEFAULT_EDGE = 24
const DEFAULT_THRESHOLD = 72

interface Tracking {
  startX: number
  startY: number
  lastX: number
  lastY: number
}

/**
 * 제스처 판정만 떼어낸 순수 추적기. DOM·라우터를 모르므로 그대로 테스트한다.
 *
 * `start`는 왼쪽 가장자리에서 시작했을 때만 추적을 연다. `end`는 뒤로 갈 제스처였는지 돌려준다.
 * `cancel`은 브라우저가 제스처를 가져갔을 때(`touchcancel`) 부른다.
 */
export function createEdgeSwipeTracker(options: EdgeSwipeOptions = {}) {
  const edge = options.edge ?? DEFAULT_EDGE
  const threshold = options.threshold ?? DEFAULT_THRESHOLD
  let tracking: Tracking | null = null

  return {
    start(x: number, y: number): void {
      tracking = x <= edge ? { startX: x, startY: y, lastX: x, lastY: y } : null
    },

    move(x: number, y: number): void {
      if (tracking !== null) {
        tracking.lastX = x
        tracking.lastY = y
      }
    },

    /** 뒤로 갈 제스처였으면 `true`. 어느 쪽이든 추적은 닫힌다. */
    end(): boolean {
      if (tracking === null) {
        return false
      }

      const dx = tracking.lastX - tracking.startX
      const dy = Math.abs(tracking.lastY - tracking.startY)

      tracking = null

      // 세로로 더 많이 움직였으면 스크롤이다.
      return dx >= threshold && dy < dx
    },

    cancel(): void {
      tracking = null
    },

    get isTracking(): boolean {
      return tracking !== null
    },
  }
}

/** vue-router가 `history.state`에 남기는 이전 항목. 없으면 갈 곳이 없다. */
function hasBackEntry(): boolean {
  const state: unknown = window.history.state

  if (typeof state !== 'object' || state === null) {
    return false
  }

  const back = (state as Record<string, unknown>).back

  return typeof back === 'string' && back !== ''
}

/** 시트·모달이 열려 있으면 제스처를 끈다. 먼저 닫히는 것이 맞다. */
function isModalOpen(): boolean {
  return document.querySelector('[aria-modal="true"]') !== null
}

/**
 * 홈 화면에 설치해 `standalone`으로 뜬 앱인지.
 *
 * iOS 사파리는 오래 `navigator.standalone`만 지원했고 `display-mode` 미디어 쿼리는 뒤늦게
 * 붙었다. 판정이 어긋나면 제스처가 통째로 죽으므로 둘 다 본다.
 */
function isInstalledApp(): boolean {
  const legacy = (window.navigator as Navigator & { standalone?: boolean }).standalone === true

  return legacy || window.matchMedia('(display-mode: standalone)').matches
}

/**
 * `AppShell`에서 한 번 켠다. `window`에 터치 리스너를 `passive`로 걸어 스크롤 성능을 건드리지 않는다.
 *
 * 마우스·트랙패드는 대상이 아니다 — 가장자리 드래그를 뒤로가기로 오인하지 않게 터치 이벤트만 듣는다.
 */
export function useEdgeSwipeHistory(router: Router, options: EdgeSwipeOptions = {}): void {
  if (!isInstalledApp()) {
    return
  }

  const tracker = createEdgeSwipeTracker(options)

  const onTouchStart = (event: TouchEvent): void => {
    if (event.touches.length !== 1 || isModalOpen()) {
      tracker.cancel()
      return
    }

    const touch = event.touches[0]

    if (touch !== undefined) {
      tracker.start(touch.clientX, touch.clientY)
    }
  }

  const onTouchMove = (event: TouchEvent): void => {
    const touch = event.touches[0]

    if (touch !== undefined && tracker.isTracking) {
      tracker.move(touch.clientX, touch.clientY)
    }
  }

  /**
   * 손을 뗀 좌표까지 넣고 판정한다. 브라우저는 `touchmove`를 프레임 단위로 묶어 보내므로,
   * 빠르게 튕긴 제스처는 마지막 move가 임계값에 못 미쳐도 뗀 위치는 넘는다(#383 리뷰).
   */
  const onTouchEnd = (event: TouchEvent): void => {
    const touch = event.changedTouches[0]

    if (touch !== undefined && tracker.isTracking) {
      tracker.move(touch.clientX, touch.clientY)
    }

    if (tracker.end() && hasBackEntry()) {
      router.back()
    }
  }

  const onTouchCancel = (): void => {
    tracker.cancel()
  }

  onMounted(() => {
    window.addEventListener('touchstart', onTouchStart, { passive: true })
    window.addEventListener('touchmove', onTouchMove, { passive: true })
    window.addEventListener('touchend', onTouchEnd, { passive: true })
    window.addEventListener('touchcancel', onTouchCancel)
  })

  onBeforeUnmount(() => {
    window.removeEventListener('touchstart', onTouchStart)
    window.removeEventListener('touchmove', onTouchMove)
    window.removeEventListener('touchend', onTouchEnd)
    window.removeEventListener('touchcancel', onTouchCancel)
  })
}
