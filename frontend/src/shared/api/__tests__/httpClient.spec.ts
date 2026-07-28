import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

describe('httpClient', () => {
  beforeEach(() => {
    vi.resetModules()
    vi.stubEnv('VITE_API_BASE_URL', 'https://api.example.test')
  })

  afterEach(() => {
    vi.unstubAllEnvs()
  })

  it('uses the shared API request defaults', async () => {
    const { httpClient } = await import('../httpClient')

    expect(httpClient.defaults.baseURL).toBe('https://api.example.test')
    expect(httpClient.defaults.timeout).toBe(10_000)
    expect(httpClient.defaults.withCredentials).toBe(true)
  })
})
