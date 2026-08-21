import type { Directive } from 'vue'

/**
 * `v-fit-text` — 한 줄 문구가 칸보다 길면 글자 크기를 줄여 한눈에 보이게 한다.
 *
 * 제목과 버튼 라벨은 로케일마다 길이가 다르다. 잘라서 `…`을 붙이면 무슨 화면인지, 무슨 동작인지
 * 읽을 수 없다. 하한(기본 50%)까지 줄여도 안 들어갈 때만 기존 `truncate`가 남는다.
 * 34px 화면 제목은 17px까지 내려간다 — `TRANSACTION DETAILS`가 390px에서 필요한 비율이 0.55다.
 *
 * 마운트·다시 그릴 때·칸 폭이 바뀔 때·폰트가 늦게 도착했을 때 글자 크기를 원래 값으로 되돌린 뒤
 * 다시 잰다. 원래 값은 클래스(토큰)가 정하므로 inline `font-size`만 비우면 된다.
 *
 * 한 줄 요소 전용이다. 여러 줄로 꺾는 제목(`break-words`)에는 쓰지 않는다.
 *
 * ```vue
 * <h1 v-fit-text class="truncate text-screen-title">{{ title }}</h1>
 * <span v-fit-text="0.75" class="truncate">{{ label }}</span>
 * ```
 */
const DEFAULT_MIN_RATIO = 0.5

/** 비율대로 줄여도 반올림 때문에 1px이 남는 일이 있어 몇 번 더 조인다. */
const EXTRA_PASSES = 3

interface FitState {
  minRatio: number
  lastWidth: number
  lastText: string
  observer: ResizeObserver | null
  fonts: FontFaceSet | null
  onFontsLoaded: () => void
}

const states = new WeakMap<HTMLElement, FitState>()

function resolveMinRatio(value: unknown): number {
  return typeof value === 'number' && value > 0 && value <= 1 ? value : DEFAULT_MIN_RATIO
}

/**
 * 글자가 실제로 차지하는 폭.
 *
 * `scrollWidth`는 정수로 반올림된다. 칸과 글자가 95px로 같아 보여도 소수점만큼 넘쳐 `…`이 붙는
 * 일이 실제로 있었다(이벤트 상세의 `Add to journey`). Range로 소수점까지 재고, 레이아웃이 없는
 * 환경(jsdom)에서는 0이 나오므로 `scrollWidth`와 큰 쪽을 쓴다.
 */
function contentWidth(el: HTMLElement): number {
  const range = document.createRange()

  range.selectNodeContents(el)

  const measured =
    typeof range.getBoundingClientRect === 'function' ? range.getBoundingClientRect().width : 0

  return Math.max(measured, el.scrollWidth)
}

/**
 * 글자 크기를 원래 값으로 되돌린 뒤, 넘치는 만큼 줄인다.
 *
 * 디렉티브 밖에서도 쓸 수 있게 순수 함수로 둔다. `minRatio`는 원래 크기 대비 하한(0–1)이다.
 */
export function fitText(el: HTMLElement, minRatio = DEFAULT_MIN_RATIO): void {
  el.style.fontSize = ''

  const base = Number.parseFloat(getComputedStyle(el).fontSize)
  const available = el.clientWidth
  const needed = contentWidth(el)

  if (!Number.isFinite(base) || base <= 0 || available <= 0 || needed <= available) {
    return
  }

  let ratio = available / needed

  for (let pass = 0; pass <= EXTRA_PASSES; pass += 1) {
    const applied = Math.max(minRatio, ratio)

    el.style.fontSize = `${(base * applied).toFixed(2)}px`

    if (applied === minRatio || contentWidth(el) <= el.clientWidth) {
      return
    }

    ratio *= 0.98
  }
}

function refit(el: HTMLElement): void {
  const state = states.get(el)

  if (state === undefined) {
    return
  }

  fitText(el, state.minRatio)
  state.lastWidth = el.clientWidth
  state.lastText = el.textContent ?? ''
}

function resolveFonts(): FontFaceSet | null {
  if (typeof document === 'undefined' || !('fonts' in document)) {
    return null
  }

  const fonts: unknown = document.fonts

  return typeof fonts === 'object' && fonts !== null && 'addEventListener' in fonts
    ? (fonts as FontFaceSet)
    : null
}

export const vFitText: Directive<HTMLElement, number | undefined> = {
  mounted(el, binding) {
    const state: FitState = {
      minRatio: resolveMinRatio(binding.value),
      lastWidth: -1,
      lastText: '',
      observer: null,
      fonts: resolveFonts(),
      onFontsLoaded: () => refit(el),
    }

    states.set(el, state)
    refit(el)

    if (typeof ResizeObserver !== 'undefined') {
      // 폭이 바뀔 때만 다시 잰다. 글자 크기를 바꾸면 높이가 따라 변하는데, 그때마다 재면 한 번 더 돈다.
      state.observer = new ResizeObserver(() => {
        if (el.clientWidth !== state.lastWidth) {
          refit(el)
        }
      })
      state.observer.observe(el)
    }

    // Display 폰트는 Adobe에서, CJK 슬라이스는 화면에 글자가 나온 뒤에 도착한다. 도착하면 폭이 바뀐다.
    state.fonts?.addEventListener('loadingdone', state.onFontsLoaded)
  },

  updated(el, binding) {
    const state = states.get(el)

    if (state === undefined) {
      return
    }

    state.minRatio = resolveMinRatio(binding.value)

    if (el.textContent !== state.lastText || el.clientWidth !== state.lastWidth) {
      refit(el)
    }
  },

  unmounted(el) {
    const state = states.get(el)

    if (state === undefined) {
      return
    }

    state.observer?.disconnect()
    state.fonts?.removeEventListener('loadingdone', state.onFontsLoaded)
    states.delete(el)
  },
}
