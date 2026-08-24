import { readFileSync } from 'node:fs'
import { join } from 'node:path'

import { describe, expect, it } from 'vitest'

// vitest의 root는 프론트엔드 패키지다. CSS는 번들러를 태우지 않고 파일 그대로 읽는다.
const CSS = readFileSync(join(process.cwd(), 'src/app/styles/tokens.css'), 'utf8')

/**
 * 하단 탭이 실제로 쓰는 유리 면을 컴포넌트에서 읽는다.
 *
 * 면 색과 알파를 이 파일에 복제하면 `BottomNav.vue`만 `bg-paper/90`이나 `bg-canvas/20`으로
 * 되돌려도 테스트가 계속 초록이다. 값이 아니라 **실제 클래스**를 근거로 계산한다.
 */
const BOTTOM_NAV = readFileSync(join(process.cwd(), 'src/shared/ui/BottomNav.vue'), 'utf8')

function bottomNavGlass(): { surface: string; alpha: number } {
  // `reduce-transparency:bg-canvas`처럼 variant가 붙거나 알파가 없는 것은 걸리지 않는다.
  const matches = [...BOTTOM_NAV.matchAll(/(?<![\w:-])bg-([a-z0-9-]+)\/(\d{1,3})(?![\w-])/g)]

  if (matches.length !== 1) {
    throw new Error(`BottomNav.vue의 반투명 배경 클래스는 하나여야 하는데 ${matches.length}개다`)
  }

  const [, surface, alpha] = matches[0] as unknown as [string, string, string]

  return { surface, alpha: Number(alpha) / 100 }
}

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

  /*
   * 계열색을 **글자로** 쓰는 자리(#476). 리포트 레이더 축 라벨과 인사이트 문장의 카테고리
   * 단어가 여기 해당한다. 방향이 위 블록과 반대다 — 저쪽은 계열색이 면이고 잉크가 글자다.
   *
   * 기준 면은 canvas다. `surface-1`(#262626) 위에서는 shopping·show가 4.21로 미달이라
   * 두 자리를 카드 밖으로 옮겼다. 카드 위로 되돌리면 여기서는 잡히지 않으므로,
   * `ReportDetailView.spec`이 두 블록이 AppCard 밖에 있는지를 함께 지킨다.
   */
  it.each(['food', 'shopping', 'show', 'beauty', 'settlement', 'status-ongoing', 'ink-3'])(
    'the %s series colour clears AA as text on the canvas',
    (series) => {
      expect(contrast(token('canvas'), token(series))).toBeGreaterThanOrEqual(AA_TEXT)
    },
  )
})

/**
 * 하단 탭 유리 면 위 잉크(#496).
 *
 * 탭은 화면 위에 떠 있어 **뒤로 무엇이든 지나간다** — 어두운 캔버스도, 밝은 종이 카드도,
 * 코어색 티켓도. 옛 구현은 `rgb(217 217 217 / 0.2)`라 밝은 면 위에서 대비가 1.15:1까지
 * 무너졌다. 어두운 canvas를 90%로 깔아 그 의존을 끊었고, 여기서 값으로 고정한다.
 */
describe('bottom nav glass contrast', () => {
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
    const { surface, alpha } = bottomNavGlass()
    const glass = composite(token(surface), token(behind), alpha)

    expect(contrast(glass, token('ink'))).toBeGreaterThanOrEqual(AA_TEXT)
    expect(contrast(glass, token('ink-2'))).toBeGreaterThanOrEqual(AA_TEXT)
  })
})

/**
 * 진행·예정 배지 면 위 글자(#402).
 *
 * ongoing은 이미지 위에 놓이는 반투명 면이라 사진의 양 극단(검정·흰색)을 함께 잰다.
 * scheduled는 불투명 상태색 면이라 뒤 배경과 무관하다. AppBadge.spec이 여기서 계산하는
 * 토큰과 컴포넌트의 실제 클래스를 연결한다.
 */
describe('AppBadge status surface contrast', () => {
  it.each([
    ['canvas', token('canvas')],
    ['surface-1', token('surface-1')],
    ['black image', '#000000'],
    ['white image', '#ffffff'],
  ])('the ongoing label clears AA over %s', (_background, behind) => {
    const surface = composite(token('canvas'), behind, 0.7)

    expect(contrast(surface, token('ink'))).toBeGreaterThanOrEqual(AA_TEXT)
  })

  it('the scheduled label clears AA on its opaque status surface', () => {
    expect(contrast(token('status-scheduled'), token('on-paper'))).toBeGreaterThanOrEqual(AA_TEXT)
  })
})
