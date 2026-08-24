import { afterEach, describe, expect, it, vi } from 'vitest'

import { getAvatarInitial } from './avatarInitial'

describe('getAvatarInitial', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    vi.resetModules()
  })

  it.each([
    ['ada', 'A'],
    ['  mina', 'M'],
    ['', '?'],
    ['   ', '?'],
    ['🌙 Mina', '🌙'],
    ['🇰🇷 Mina', '🇰🇷'],
    ['👩‍💻 Mina', '👩‍💻'],
    ['ßeta', 'S'],
    ['ﬃona', 'F'],
  ])('returns one avatar grapheme for %s', (displayName, expected) => {
    expect(getAvatarInitial(displayName)).toBe(expected)
  })

  it('falls back to a complete code point when Intl.Segmenter is unavailable', async () => {
    vi.stubGlobal('Intl', { Segmenter: undefined })
    const { getAvatarInitial: getFallbackInitial } = await import('./avatarInitial')

    expect(getFallbackInitial('🌙 Mina')).toBe('🌙')
    expect(getFallbackInitial('ßeta')).toBe('S')
  })
})
