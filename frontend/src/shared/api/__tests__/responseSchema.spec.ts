import { afterEach, describe, expect, it, vi } from 'vitest'
import { z } from 'zod'

import { NormalizedApiError } from '../apiError'
import { validateResponseData } from '../responseSchema'

describe('responseSchema', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('accepts a valid payload and does not transform the caller data', () => {
    const payload = { value: 'kept', extra: { nested: true } }
    const schema = z.object({ value: z.string() })
    const config = { url: '/api/v1/example', method: 'get', responseSchema: schema }

    expect(() => validateResponseData(config, 200, payload)).not.toThrow()
    expect(payload).toEqual({ value: 'kept', extra: { nested: true } })
  })

  it('throws UNKNOWN with status and emits only sanitized issue metadata', () => {
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => undefined)
    const schema = z.object({ value: z.string() })
    const config = { url: '/api/v1/example', method: 'get', responseSchema: schema }

    let thrown: unknown
    try {
      validateResponseData(config, 502, { value: 123, secret: 'do-not-log' })
    } catch (error) {
      thrown = error
    }

    expect(thrown).toBeInstanceOf(NormalizedApiError)
    expect(thrown).toMatchObject({
      code: 'UNKNOWN',
      status: 502,
      message: 'Internal response validation error',
      messageKey: 'error.unknown',
    })

    expect(consoleError).toHaveBeenCalledWith('API response validation failed', {
      url: '/api/v1/example',
      method: 'GET',
      status: 502,
      issues: [{ path: ['value'], code: 'invalid_type', expected: 'string' }],
    })
    expect(JSON.stringify(consoleError.mock.calls)).not.toContain('do-not-log')
  })

  it('keeps unconfigured calls as a no-op', () => {
    expect(() =>
      validateResponseData({ url: '/api/v1/example', method: 'get' }, 200, 123),
    ).not.toThrow()
  })
})
