import { mount } from '@vue/test-utils'
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick, ref, withDirectives } from 'vue'

import { fitText, vFitText } from './fitText'

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
