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
