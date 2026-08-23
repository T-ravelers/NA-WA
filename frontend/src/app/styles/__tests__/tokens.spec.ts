import { readFileSync } from 'node:fs'
import { join } from 'node:path'

import { describe, expect, it } from 'vitest'

// vitest의 root는 프론트엔드 패키지다. CSS는 번들러를 태우지 않고 파일 그대로 읽는다.
const CSS = readFileSync(join(process.cwd(), 'src/app/styles/tokens.css'), 'utf8')

/**
 * 토큰 쌍의 명암비를 값으로 고정한다(#443).
 *
 * 잉크 토큰은 전부 검정에 가까워 **눈으로는 미달을 잡을 수 없다.** `tokens.css`의 주석이
 * 「몇 대 몇으로 보정했다」고 적어 두지만 주석은 코드와 함께 늙는다. 여기서 실제 값을 읽어
 * WCAG 2.x 상대 휘도로 다시 계산한다 — 색을 되돌리면 여기서 빨개진다.
 */
function token(name: string): string {
  const match = new RegExp(`--color-${name}:\\s*(#[0-9a-fA-F]{6})`).exec(CSS)

  if (match?.[1] === undefined) {
    throw new Error(`--color-${name}를 tokens.css에서 찾지 못했다`)
  }

  return match[1]
}

function channel(value: number): number {
  const c = value / 255

  return c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4
}

function luminance(hex: string): number {
  const [r, g, b] = [1, 3, 5].map((index) =>
    channel(Number.parseInt(hex.slice(index, index + 2), 16)),
  )

  return 0.2126 * (r ?? 0) + 0.7152 * (g ?? 0) + 0.0722 * (b ?? 0)
}

function contrast(a: string, b: string): number {
  const [high, low] = [luminance(a), luminance(b)].sort((x, y) => y - x)

  return ((high ?? 0) + 0.05) / ((low ?? 0) + 0.05)
}

const AA_TEXT = 4.5

/**
 * 반투명 면을 뒤 색 위에 합성한다.
 *
 * `bg-canvas/90` 같은 유리 면은 뒤 색이 비쳐 실제 면 색이 화면마다 다르다. 뒤에 무엇이
 * 오든 잉크가 AA를 넘는지 보려면 합성한 색으로 계산해야 한다. 흐림(`backdrop-blur`)은
 * 뒤 색을 평균낼 뿐 극단값을 없애지 못하므로 계산에 넣지 않는다 — 흐림 없이 통과하면
 * 흐림이 있을 때도 통과한다.
 */
function composite(over: string, under: string, alpha: number): string {
  const mixed = [1, 3, 5].map((index) => {
    const o = Number.parseInt(over.slice(index, index + 2), 16)
    const u = Number.parseInt(under.slice(index, index + 2), 16)

    return Math.round(alpha * o + (1 - alpha) * u)
  })

  return `#${mixed.map((value) => value.toString(16).padStart(2, '0')).join('')}`
}

describe('tokens.css contrast', () => {
  // 코어색 면 위 텍스트. `show`가 4.37:1이라 잉크를 순검정으로 옮겼다(#443).
  it.each(['beauty', 'shopping', 'show', 'food'])(
    'ink on %s surface clears AA for body text',
    (surface) => {
      expect(contrast(token(surface), token('on-category'))).toBeGreaterThanOrEqual(AA_TEXT)
    },
  )

  // 파괴적 버튼(`bg-danger text-on-category`)도 같은 쌍이다.
  it('ink on the danger surface clears AA for body text', () => {
    expect(contrast(token('danger'), token('on-category'))).toBeGreaterThanOrEqual(AA_TEXT)
  })

  // 밝은 면 위 보조 글자. V2 원값 #737373이 미달이라 이미 보정해 둔 자리다.
  it.each(['paper', 'paper-fill'])('the secondary ink clears AA on %s', (surface) => {
    expect(contrast(token(surface), token('on-paper-2'))).toBeGreaterThanOrEqual(AA_TEXT)
  })

  it('the primary inks clear AA on their own surfaces', () => {
    expect(contrast(token('canvas'), token('ink'))).toBeGreaterThanOrEqual(AA_TEXT)
    expect(contrast(token('paper'), token('on-paper'))).toBeGreaterThanOrEqual(AA_TEXT)
  })
})

/**
 * 하단 탭 유리 면 위 잉크(#496).
 *
 * 탭은 화면 위에 떠 있어 **뒤로 무엇이든 지나간다** — 어두운 캔버스도, 밝은 종이 카드도,
 * 코어색 티켓도. 옛 구현은 `rgb(217 217 217 / 0.2)`라 밝은 면 위에서 대비가 1.15:1까지
 * 무너졌다. 어두운 canvas를 90%로 깔아 그 의존을 끊었고, 여기서 값으로 고정한다.
 */
describe('bottom nav glass contrast', () => {
  /** `BottomNav.vue`의 `bg-canvas/90`. 저 값을 바꾸면 여기도 바꾼다. */
  const GLASS_ALPHA = 0.9

  /** 탭 뒤로 지나갈 수 있는 면. 밝을수록 유리가 밝아져 잉크 대비가 낮아진다. */
  const BEHIND = [
    'canvas',
    'surface-1',
    'paper',
    'paper-fill',
    'food',
    'show',
    'shopping',
    'beauty',
  ]

  it.each(BEHIND)('both tab inks clear AA on the glass over %s', (behind) => {
    const glass = composite(token('canvas'), token(behind), GLASS_ALPHA)

    expect(contrast(glass, token('ink'))).toBeGreaterThanOrEqual(AA_TEXT)
    expect(contrast(glass, token('ink-2'))).toBeGreaterThanOrEqual(AA_TEXT)
  })
})
