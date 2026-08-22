import { describe, expect, it } from 'vitest'

import { buildJourneyInviteCode, buildJourneyInviteUrl } from '../inviteCode'

describe('buildJourneyInviteCode', () => {
  it('formats the code as a readable boarding-pass stub', () => {
    expect(buildJourneyInviteCode(42)).toMatch(/^TR-[2-9A-HJ-NP-Z]{4}$/)
  })

  /* 코드를 불러 주는 자리가 있어서, 같은 여정은 언제 열어도 같은 코드여야 한다. */
  it('gives the same journey the same code every time', () => {
    expect(buildJourneyInviteCode(7)).toBe(buildJourneyInviteCode(7))
  })

  it('gives different journeys different codes', () => {
    const codes = new Set([1, 2, 3, 42, 100, 9_999].map(buildJourneyInviteCode))

    expect(codes.size).toBe(6)
  })

  /*
   * 헷갈리는 글자를 빼는 것이 이 표기의 목적이다. 사람이 소리 내어 읽고 받아 적는다.
   */
  it('never uses characters that are read wrong out loud', () => {
    const body = Array.from({ length: 200 }, (_, index) => buildJourneyInviteCode(index + 1))
      .map((code) => code.slice(3))
      .join('')

    expect(body).not.toMatch(/[IO01]/)
  })

  it('falls back visibly for an unusable journey id', () => {
    expect(buildJourneyInviteCode(0)).toBe('TR-????')
    expect(buildJourneyInviteCode(-1)).toBe('TR-????')
    expect(buildJourneyInviteCode(Number.NaN)).toBe('TR-????')
  })
})

describe('buildJourneyInviteUrl', () => {
  it('points at the journey detail route', () => {
    expect(buildJourneyInviteUrl(42, 'https://nawa.example')).toBe(
      'https://nawa.example/journeys/42',
    )
  })

  /* origin이 슬래시로 끝나면 `//journeys`가 만들어져 라우터가 다른 곳으로 간다. */
  it('does not double the slash when the origin ends with one', () => {
    expect(buildJourneyInviteUrl(42, 'https://nawa.example/')).toBe(
      'https://nawa.example/journeys/42',
    )
  })
})
