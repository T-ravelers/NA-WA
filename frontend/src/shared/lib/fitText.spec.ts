import { mount } from '@vue/test-utils'
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick, ref, withDirectives } from 'vue'

import { fitText, vFitText, vFitTextGroup } from './fitText'

/**
 * jsdom은 레이아웃을 계산하지 않는다. 칸 폭과 글자 폭을 흉내 낸다.
 *
 * - `clientWidth`: 칸 폭. 테스트가 `box.available`로 정한다.
 * - `scrollWidth`: 글자 폭. 글자 수 × 글자당 폭 × (현재 글자 크기 / 기본 34px).
 */
const BASE_FONT_SIZE = 34
const box = { available: 300, widthPerChar: 10 }

function currentFontSize(el: HTMLElement): number {
  const inline = Number.parseFloat(el.style.fontSize)

  return Number.isFinite(inline) ? inline : BASE_FONT_SIZE
}

beforeAll(() => {
  Object.defineProperty(HTMLElement.prototype, 'clientWidth', {
    configurable: true,
    get: () => box.available,
  })
  Object.defineProperty(HTMLElement.prototype, 'scrollWidth', {
    configurable: true,
    get(this: HTMLElement) {
      const chars = this.textContent?.length ?? 0

      return Math.round((chars * box.widthPerChar * currentFontSize(this)) / BASE_FONT_SIZE)
    },
  })
})

afterAll(() => {
  // jsdom 원래 getter(Element.prototype)로 돌아가게 덮어쓴 것을 지운다.
  Reflect.deleteProperty(HTMLElement.prototype, 'clientWidth')
  Reflect.deleteProperty(HTMLElement.prototype, 'scrollWidth')
})

beforeEach(() => {
  box.available = 300
  box.widthPerChar = 10
  vi.spyOn(window, 'getComputedStyle').mockReturnValue({
    fontSize: `${BASE_FONT_SIZE}px`,
  } as CSSStyleDeclaration)
})

afterEach(() => {
  vi.restoreAllMocks()
})

function mountTitle(text: string, minRatio?: number, modifiers: Record<string, boolean> = {}) {
  const title = ref(text)
  const Title = defineComponent({
    setup() {
      return () =>
        withDirectives(h('h1', { class: 'truncate' }, title.value), [
          [vFitText, minRatio, '', modifiers],
        ])
    },
  })
  const wrapper = mount(Title)

  return { wrapper, title, el: wrapper.element as HTMLElement }
}

describe('fitText', () => {
  it('leaves the font size alone when the text fits', () => {
    const { el } = mountTitle('Wallet') // 6자 × 10px = 60px < 300px

    expect(el.style.fontSize).toBe('')
  })

  it('shrinks the font size by the overflow ratio', () => {
    const { el } = mountTitle('TRANSACTION DETAILS') // 19자 × 10px = 190px

    box.available = 152 // 190 → 152 = 80%
    fitText(el)

    expect(Number.parseFloat(el.style.fontSize)).toBeCloseTo(27.2, 2)
  })

  it('never shrinks below the minimum ratio', () => {
    box.available = 100

    const { el } = mountTitle('A title that is much longer than the column') // 43자 = 430px

    expect(Number.parseFloat(el.style.fontSize)).toBeCloseTo(BASE_FONT_SIZE * 0.5, 2)
  })

  it('accepts a custom minimum ratio through the directive value', () => {
    box.available = 100

    const { el } = mountTitle('A title that is much longer than the column', 0.8)

    expect(Number.parseFloat(el.style.fontSize)).toBeCloseTo(BASE_FONT_SIZE * 0.8, 2)
  })

  it('refits when the text changes after mount', async () => {
    const { el, title } = mountTitle('Short')

    expect(el.style.fontSize).toBe('')

    title.value = 'A much longer title for this screen' // 35자 = 350px > 300px
    await nextTick()

    expect(el.style.fontSize).not.toBe('')
    expect(Number.parseFloat(el.style.fontSize)).toBeLessThan(BASE_FONT_SIZE)

    title.value = 'Short'
    await nextTick()

    expect(el.style.fontSize).toBe('')
  })

  it('resets to the token size before measuring again', () => {
    const { el } = mountTitle('TRANSACTION DETAILS')

    box.available = 152 // 190 → 152 = 80%
    fitText(el)
    expect(Number.parseFloat(el.style.fontSize)).toBeCloseTo(27.2, 2)

    box.available = 300
    fitText(el)
    expect(el.style.fontSize).toBe('')
  })

  it('keeps truncating at the floor without the wrap modifier', () => {
    box.available = 100

    const { el } = mountTitle('A title that is much longer than the column')

    expect(el.style.whiteSpace).toBe('')
    expect(el.style.textAlign).toBe('')
  })

  it('wraps instead of truncating when the floor is not enough and .wrap is set', () => {
    box.available = 100

    const { el } = mountTitle('A title that is much longer than the column', 0.75, { wrap: true })

    expect(Number.parseFloat(el.style.fontSize)).toBeCloseTo(BASE_FONT_SIZE * 0.75, 2)
    expect(el.style.whiteSpace).toBe('normal')
    expect(el.style.textAlign).toBe('center')
  })

  it('does not wrap when shrinking was enough', () => {
    const { el } = mountTitle('TRANSACTION DETAILS', 0.5, { wrap: true }) // 190px

    box.available = 152 // 80%면 들어간다
    fitText(el, 0.5, true)

    expect(el.style.whiteSpace).toBe('')
  })

  it('does nothing when the element has no width yet', () => {
    box.available = 0

    const { el } = mountTitle('TRANSACTION DETAILS')

    expect(el.style.fontSize).toBe('')
  })
})

/**
 * `contentWidth`의 Range 측정 경로.
 *
 * jsdom에는 `Range.prototype.getBoundingClientRect`가 없어 위 케이스는 전부 정수 `scrollWidth`
 * 폴백만 탄다. 칸과 글자가 95px로 같아 보여도 소수점만큼 넘쳐 `…`이 붙던 회귀
 * (이벤트 상세의 `Add to journey`)는 그 경로로는 잡히지 않으므로, 여기서만 직접 심는다.
 */
describe('fitText — 소수점 폭 측정', () => {
  const LABEL = 'Add to journey'
  /** 반올림되지 않은 실제 글자 폭. 기본 글자 크기 기준이고, 줄인 만큼 같이 줄어든다. */
  const exact = { width: 0 }

  beforeEach(() => {
    Object.defineProperty(Range.prototype, 'getBoundingClientRect', {
      configurable: true,
      value(this: Range) {
        const el = this.commonAncestorContainer as HTMLElement

        return { width: (exact.width * currentFontSize(el)) / BASE_FONT_SIZE } as unknown as DOMRect
      },
    })
  })

  afterEach(() => {
    Reflect.deleteProperty(Range.prototype, 'getBoundingClientRect')
  })

  it('shrinks when the fraction overflows a column that scrollWidth says fits', () => {
    box.available = 95
    box.widthPerChar = 95 / LABEL.length
    exact.width = 95.4

    // 정수 폭으로는 95 = 95라 "들어간다"고 나온다. 이 경로만 있으면 아무 일도 일어나지 않는다.
    const probe = document.createElement('span')

    probe.textContent = LABEL
    expect(probe.scrollWidth).toBe(box.available)

    const { el } = mountTitle(LABEL, 0.75)

    expect(Number.parseFloat(el.style.fontSize)).toBeLessThan(BASE_FONT_SIZE)
  })
})

/**
 * 칸 폭도 소수점으로 재는 경로.
 *
 * 글자 쪽만 소수점으로 재면 반쪽이다. 지갑의 `Top up`은 칸이 48.656px인데 `clientWidth`가 49로
 * 올라가, 글자 48.984px를 「들어간다」로 판정하고 멈춰 `…`이 그대로 남았다.
 */
describe('fitText — 칸 폭도 소수점으로 잰다', () => {
  const geom = { box: 0, text: 0 }

  beforeEach(() => {
    Object.defineProperty(Element.prototype, 'getBoundingClientRect', {
      configurable: true,
      value: () => ({ width: geom.box }) as unknown as DOMRect,
    })
    Object.defineProperty(Range.prototype, 'getBoundingClientRect', {
      configurable: true,
      value(this: Range) {
        const el = this.commonAncestorContainer as HTMLElement

        return { width: (geom.text * currentFontSize(el)) / BASE_FONT_SIZE } as unknown as DOMRect
      },
    })
  })

  afterEach(() => {
    Reflect.deleteProperty(Element.prototype, 'getBoundingClientRect')
    Reflect.deleteProperty(Range.prototype, 'getBoundingClientRect')
  })

  it('shrinks when the column is narrower than clientWidth rounds it up to', () => {
    box.available = 49 // 정수로는 49 = 49라 "들어간다"고 나온다
    box.widthPerChar = 0 // scrollWidth는 재우지 않고 Range만 쓴다
    geom.box = 48.656
    geom.text = 48.984

    const { el } = mountTitle('Top up', 0.75)

    expect(Number.parseFloat(el.style.fontSize)).toBeLessThan(BASE_FONT_SIZE)
  })
})

/**
 * `v-fit-text-group` — 나란한 요소를 한 비율로 맞춘다.
 */
describe('vFitTextGroup', () => {
  /** 묶음 안 n번째 라벨. 없으면 테스트가 조용히 통과하지 않도록 던진다. */
  function member(members: HTMLElement[], index: number): HTMLElement {
    const el = members[index]

    if (el === undefined) {
      throw new Error(`묶음에 ${index}번 멤버가 없다`)
    }

    return el
  }

  // `defineComponent`를 쓰지 않는다 — 한 파일에 컴포넌트가 둘이면 lint가 막는다.
  function mountGroup(labels: readonly (readonly [string, number])[]): HTMLElement[] {
    const wrapper = mount({
      setup() {
        return () =>
          withDirectives(
            h(
              'div',
              {},
              labels.map(([text, minRatio]) =>
                withDirectives(h('span', { class: 'truncate' }, text), [[vFitText, minRatio]]),
              ),
            ),
            [[vFitTextGroup]],
          )
      },
    })

    return wrapper.findAll('span').map((found) => found.element as HTMLElement)
  }

  it('shrinks every member by the ratio the longest one needs', () => {
    box.available = 150

    // 'Short' 5자 = 50px는 그대로 들어가지만, 17자 = 170px에 맞춰 함께 줄어든다.
    const members = mountGroup([
      ['Short', 0.5],
      ['Much longer label', 0.5],
    ])
    const short = member(members, 0)
    const long = member(members, 1)

    expect(short.style.fontSize).toBe(long.style.fontSize)
    expect(Number.parseFloat(long.style.fontSize)).toBeCloseTo(BASE_FONT_SIZE * (150 / 170), 1)
  })

  it('leaves every member alone when they all fit', () => {
    box.available = 300

    const members = mountGroup([
      ['Short', 0.5],
      ['Also fits', 0.5],
    ])

    expect(member(members, 0).style.fontSize).toBe('')
    expect(member(members, 1).style.fontSize).toBe('')
  })

  it('uses the largest floor among the members', () => {
    box.available = 100

    // 200px가 필요해 비율은 0.5지만, 멤버 하나가 0.75를 요구하므로 0.75에서 멈춘다.
    const members = mountGroup([
      ['Short', 0.5],
      ['Twenty characters!!!', 0.75],
    ])

    expect(Number.parseFloat(member(members, 0).style.fontSize)).toBeCloseTo(
      BASE_FONT_SIZE * 0.75,
      2,
    )
    expect(Number.parseFloat(member(members, 1).style.fontSize)).toBeCloseTo(
      BASE_FONT_SIZE * 0.75,
      2,
    )
  })
})
