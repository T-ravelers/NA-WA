import { afterEach, describe, expect, it, vi } from 'vitest'

import { shareWithFallback } from './share'

function stubNavigator(share: unknown, clipboard: unknown): void {
  Object.defineProperty(navigator, 'share', { value: share, configurable: true })
  Object.defineProperty(navigator, 'clipboard', { value: clipboard, configurable: true })
}

describe('shareWithFallback', () => {
  afterEach(() => {
    Reflect.deleteProperty(navigator, 'share')
    Reflect.deleteProperty(navigator, 'clipboard')
  })

  it('returns shared after the native share sheet completes', async () => {
    const share = vi.fn().mockResolvedValue(undefined)
    const writeText = vi.fn()
    stubNavigator(share, { writeText })

    await expect(
      shareWithFallback({ title: 'Event', url: 'https://example.com/event' }, 'fallback link'),
    ).resolves.toBe('shared')
    expect(share).toHaveBeenCalledWith({ title: 'Event', url: 'https://example.com/event' })
    expect(writeText).not.toHaveBeenCalled()
  })

  it('falls back to the caller-provided text when native sharing is rejected', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined)
    stubNavigator(vi.fn().mockRejectedValue({ name: 'NotAllowedError' }), { writeText })

    await expect(
      shareWithFallback({ title: 'Private report', text: 'Report body' }, 'Report body'),
    ).resolves.toBe('copied')
    expect(writeText).toHaveBeenCalledWith('Report body')
  })

  it.each(['AbortError', 'InvalidStateError'])(
    'does not copy when native sharing ends with %s',
    async (name) => {
      const writeText = vi.fn()
      stubNavigator(vi.fn().mockRejectedValue({ name }), { writeText })

      await expect(shareWithFallback({ text: 'Report body' }, 'Report body')).resolves.toBe(
        'dismissed',
      )
      expect(writeText).not.toHaveBeenCalled()
    },
  )

  it('reports unavailable when neither capability exists', async () => {
    stubNavigator(undefined, undefined)

    await expect(shareWithFallback({ text: 'Report body' }, 'Report body')).resolves.toBe(
      'unavailable',
    )
  })

  it('reports a clipboard write failure', async () => {
    stubNavigator(undefined, { writeText: vi.fn().mockRejectedValue(new Error('denied')) })

    await expect(shareWithFallback({ text: 'Report body' }, 'Report body')).resolves.toBe('failed')
  })
})
