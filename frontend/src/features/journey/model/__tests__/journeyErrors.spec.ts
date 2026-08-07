import { describe, expect, it } from 'vitest'

import { NormalizedApiError } from '@/shared/api/apiError'

import { journeyErrorMessageKey } from '../journeyErrors'

describe('journeyErrorMessageKey', () => {
  it('returns a translated journey error key', () => {
    const error = new NormalizedApiError('JOURNEY-001', 404, 'missing journey')

    expect(journeyErrorMessageKey(error, (key) => key === 'journey.errorCode.JOURNEY-001')).toBe(
      'journey.errorCode.JOURNEY-001',
    )
  })

  it('falls back when the normalized error key has no translation', () => {
    const error = new NormalizedApiError('COMMON-001', 400, 'invalid input')

    expect(journeyErrorMessageKey(error, () => false)).toBe('error.unknown')
  })

  it('falls back for non-normalized failures', () => {
    expect(journeyErrorMessageKey(new Error('unexpected'), () => true)).toBe('error.unknown')
  })
})
