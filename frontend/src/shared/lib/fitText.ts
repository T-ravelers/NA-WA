import type { Directive } from 'vue'

/**
 * `v-fit-text` — 한 줄 문구가 칸보다 길면 글자 크기를 줄여 한눈에 보이게 한다.
 *
 * 제목과 버튼 라벨은 로케일마다 길이가 다르다. 잘라서 `…`을 붙이면 무슨 화면인지, 무슨 동작인지
 * 읽을 수 없다. 하한(기본 50%)까지 줄여도 안 들어갈 때만 기존 `truncate`가 남는다.
 *
 * **하한은 비율이라 절대 크기가 토큰을 따라 움직인다.** `--text-screen-title`은
 * `clamp(24px, 8.72vw, 34px)`이라 390에서는 34px → 17px이지만, 280에서는 기준이 이미 24.4px로
 * 내려가 있어 12.2px까지 내려간다. `TRANSACTION DETAILS`는 390에서 필요한 비율이 0.55라
 * 들어가고, 280에서는 하한에서도 3px가 모자라 `…`이 남는다. `--text-section-header`(22px
 * 고정)를 쓰는 헤더의 하한은 폭과 무관하게 11px이고, 이는 `--text-micro`와 같은 크기다.
 *
 * `.wrap` 수식어를 주면 하한에서도 안 들어갈 때 말줄임 대신 **줄을 꺾는다.** 버튼처럼 칸이 좁고
 * 높이에 여유가 있는 곳에 쓴다(가장 낮은 44px 버튼에도 12px 두 줄 31px이 들어간다).
 *
 * 마운트·다시 그릴 때·칸 폭이 바뀔 때·폰트가 늦게 도착했을 때 글자 크기를 원래 값으로 되돌린 뒤
 * 다시 잰다. 원래 값은 클래스(토큰)가 정하므로 inline `font-size`만 비우면 된다.
 *
 * 한 줄 요소 전용이다. 여러 줄로 꺾는 제목(`break-words`)에는 쓰지 않는다.
 *
 * ```vue
 * <h1 v-fit-text class="truncate text-screen-title">{{ title }}</h1>
 * <span v-fit-text.wrap="0.75" class="truncate">{{ label }}</span>
 * ```
 *
 * 나란히 놓인 버튼처럼 **크기가 같아야 하는 묶음**은 컨테이너에 `v-fit-text-group`을 건다.
 * 아래 `vFitTextGroup` 설명을 본다.
 */
const DEFAULT_MIN_RATIO = 0.5

/** 비율대로 줄여도 반올림 때문에 1px이 남는 일이 있어 몇 번 더 조인다. */
const EXTRA_PASSES = 3

/** 묶음 컨테이너 표식. 멤버가 `closest`로 자기 묶음을 찾는다. */
const GROUP_ATTR = 'data-fit-text-group'

interface FitState {
  minRatio: number
  wrap: boolean
  lastWidth: number
  lastText: string
  observer: ResizeObserver | null
  fonts: FontFaceSet | null
  onFontsLoaded: () => void
}

/** 한 번에 같은 비율로 맞출 대상 하나. */
interface Member {
  el: HTMLElement
  minRatio: number
  wrap: boolean
}

interface Box extends Member {
  base: number
  ratio: number
}

const states = new WeakMap<HTMLElement, FitState>()

function resolveMinRatio(value: unknown): number {
  return typeof value === 'number' && value > 0 && value <= 1 ? value : DEFAULT_MIN_RATIO
}

function toPx(value: string | undefined): number {
  return Number.parseFloat(value ?? '') || 0
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
 * 글자가 들어갈 칸의 폭.
 *
 * **`clientWidth`도 정수로 반올림된다.** 글자 쪽만 소수점으로 재면 반쪽이다 — 지갑의 `Top up`은
 * 칸이 48.656px인데 `clientWidth`가 49로 올라가, 글자 48.984px를 「들어간다」로 판정하고 멈춰
 * `…`이 그대로 남았다. 칸도 소수점으로 재고, 레이아웃이 없는 환경에서는 `clientWidth`로 돌아간다.
 */
function availableWidth(el: HTMLElement, style: CSSStyleDeclaration): number {
  const rect = typeof el.getBoundingClientRect === 'function' ? el.getBoundingClientRect().width : 0

  if (rect <= 0) {
    return el.clientWidth
  }

  return (
    rect -
    toPx(style.paddingLeft) -
    toPx(style.paddingRight) -
    toPx(style.borderLeftWidth) -
    toPx(style.borderRightWidth)
  )
}

/** 글자 크기와 줄바꿈을 원래 값으로 되돌린다. 원래 값은 클래스(토큰)가 정한다. */
function restore(el: HTMLElement): void {
  el.style.fontSize = ''
  el.style.whiteSpace = ''
  el.style.textAlign = ''
  el.style.overflowWrap = ''
  el.style.hyphens = ''
}

/**
 * 하한에서도 안 들어가는 라벨의 줄을 꺾는다.
 *
 * 단어 하나가 칸보다 길면(`companions`) 줄을 꺾어도 잘리므로, 하이픈이 되면 하이픈으로,
 * 아니면 단어 안에서 끊는다.
 *
 * **줄이 버튼 높이를 넘으면 잘리는 것이 아니라 버튼 밖으로 나간다.** span은 `truncate`의
 * `overflow: hidden`을 갖지만 높이를 따로 받지 않아 내용만큼 늘어나고, 감싸는 `button`은
 * `overflow: visible`이다. 12px 세 줄 46.8px까지는 48px 버튼에 들어가고 네 줄(62.4px)부터
 * 넘쳐, 위아래로 삐져나와 옆 내용과 겹친다.
 */
function wrapLines(el: HTMLElement): void {
  el.style.whiteSpace = 'normal'
  el.style.textAlign = 'center'
  el.style.overflowWrap = 'anywhere'
  el.style.hyphens = 'auto'
}

function fits(el: HTMLElement): boolean {
  return contentWidth(el) <= availableWidth(el, getComputedStyle(el))
}

function measure(member: Member): Box | null {
  const style = getComputedStyle(member.el)
  const base = Number.parseFloat(style.fontSize)
  const available = availableWidth(member.el, style)
  const needed = contentWidth(member.el)

  // 한 글자도 못 담는 칸은 배치된 것으로 보지 않는다. 로딩 중 `AppButton` 라벨은 `sr-only`라
  // 폭이 1px인데, 이것을 멤버로 세면 그 비율(≈0.01)이 `Math.min`을 타고 묶음 전체를 하한까지
  // 끌어내린다. 형제가 로딩을 도는 내내 옆 버튼 라벨이 작아졌다 돌아온다.
  if (!Number.isFinite(base) || base <= 0 || available < base) {
    return null
  }

  return { ...member, base, ratio: needed <= available ? 1 : available / needed }
}

/**
 * 여러 요소를 **한 비율로** 맞춘다.
 *
 * 묶음 안에서 가장 많이 줄여야 하는 요소의 비율을 전부가 함께 쓴다. 그래야 나란한 버튼의 라벨이
 * 제각각인 크기로 보이지 않는다. 대신 짧은 라벨도 긴 라벨을 따라 작아진다.
 *
 * 하한은 멤버 중 **가장 큰 하한**을 쓴다. 하나라도 읽히지 않을 만큼 작아지면 안 되기 때문이다.
 */
function fitTogether(members: Member[]): void {
  // 형제의 크기가 서로의 측정에 섞이지 않도록 전부 되돌린 뒤에 잰다.
  for (const member of members) {
    restore(member.el)
  }

  const boxes: Box[] = []

  for (const member of members) {
    const box = measure(member)

    if (box !== null) {
      boxes.push(box)
    }
  }

  if (boxes.length === 0) {
    return
  }

  const floor = Math.max(...boxes.map((box) => box.minRatio))
  let ratio = Math.min(...boxes.map((box) => box.ratio))

  if (ratio >= 1) {
    return
  }

  for (let pass = 0; pass <= EXTRA_PASSES; pass += 1) {
    const applied = Math.max(floor, ratio)

    for (const box of boxes) {
      box.el.style.fontSize = `${(box.base * applied).toFixed(2)}px`
    }

    const overflowing = boxes.filter((box) => !fits(box.el))

    if (overflowing.length === 0) {
      return
    }

    if (applied === floor) {
      // 하한까지 줄여도 안 들어간다. 말줄임이 기본이고, `.wrap`이면 줄을 꺾는다.
      for (const box of overflowing) {
        if (box.wrap) {
          wrapLines(box.el)
        }
      }

      return
    }

    ratio *= 0.98
  }
}

/**
 * 글자 크기를 원래 값으로 되돌린 뒤, 넘치는 만큼 줄인다.
 *
 * 디렉티브 밖에서도 쓸 수 있게 순수 함수로 둔다. `minRatio`는 원래 크기 대비 하한(0–1)이고,
 * `wrap`이 참이면 하한에서도 안 들어갈 때 줄을 꺾는다.
 */
export function fitText(el: HTMLElement, minRatio = DEFAULT_MIN_RATIO, wrap = false): void {
  fitTogether([{ el, minRatio, wrap }])
}

function toMember(el: HTMLElement): Member | null {
  const state = states.get(el)

  return state === undefined ? null : { el, minRatio: state.minRatio, wrap: state.wrap }
}

/** 컨테이너 안에서 `v-fit-text`가 걸린 요소를 모은다. */
function collectMembers(root: HTMLElement): Member[] {
  const members: Member[] = []
  const self = toMember(root)

  if (self !== null) {
    members.push(self)
  }

  for (const node of root.querySelectorAll<HTMLElement>('*')) {
    const member = toMember(node)

    if (member !== null) {
      members.push(member)
    }
  }

  return members
}

function remember(members: Member[]): void {
  for (const member of members) {
    const state = states.get(member.el)

    if (state !== undefined) {
      state.lastWidth = member.el.clientWidth
      state.lastText = member.el.textContent ?? ''
    }
  }
}

function groupOf(el: HTMLElement): HTMLElement | null {
  return typeof el.closest === 'function' ? el.closest<HTMLElement>(`[${GROUP_ATTR}]`) : null
}

function apply(members: Member[]): void {
  if (members.length === 0) {
    return
  }

  fitTogether(members)
  remember(members)
}

/** 묶음에 속해 있으면 묶음 전체를, 아니면 자기만 다시 맞춘다. */
function refit(el: HTMLElement): void {
  const group = groupOf(el)

  if (group !== null) {
    apply(collectMembers(group))

    return
  }

  const member = toMember(el)

  apply(member === null ? [] : [member])
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
      wrap: binding.modifiers.wrap === true,
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
    state.wrap = binding.modifiers.wrap === true

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

/**
 * `v-fit-text-group` — 안에 있는 `v-fit-text` 요소를 **같은 크기로** 맞춘다.
 *
 * `v-fit-text`는 요소마다 따로 잰다. 나란히 놓인 버튼에 그대로 걸면 문구가 짧은 쪽은 크게 남고
 * 긴 쪽만 줄어들어, 한 줄에 크기가 제각각인 라벨이 놓인다. 묶음으로 두면 가장 많이 줄여야 하는
 * 쪽의 비율을 전부가 함께 쓴다.
 *
 * 표식을 `created`에서 붙인다. 자식이 마운트될 때 이미 자기 묶음을 찾을 수 있어야 한다.
 *
 * ```vue
 * <div v-fit-text-group class="grid grid-cols-2 gap-2">
 *   <AppButton>Google Maps</AppButton>
 *   <AppButton>Google transit</AppButton>
 * </div>
 * ```
 */
export const vFitTextGroup: Directive<HTMLElement> = {
  created(el) {
    el.setAttribute(GROUP_ATTR, '')
  },

  mounted(el) {
    apply(collectMembers(el))
  },

  unmounted(el) {
    el.removeAttribute(GROUP_ATTR)
  },
}
