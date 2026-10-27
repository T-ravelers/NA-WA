import { beforeEach, describe, expect, it, vi } from 'vitest'

const get = vi.fn()
const put = vi.fn()
const del = vi.fn()

vi.mock('@/shared/api/httpClient', () => ({
  httpClient: {
    get: (...args: unknown[]) => get(...args),
    put: (...args: unknown[]) => put(...args),
    delete: (...args: unknown[]) => del(...args),
  },
}))

const { deletePushSubscription, fetchPushPublicKey, savePushSubscription } =
  await import('../pushSubscriptionApi')
const { pushPublicKeyResponseSchema } = await import('../notificationResponseSchemas')

beforeEach(() => {
  get.mockReset()
  put.mockReset()
  del.mockReset()
})

describe('fetchPushPublicKey', () => {
  it('asks for the key and checks the response shape', async () => {
    get.mockResolvedValue({ data: { publicKey: 'BKey' } })

    await expect(fetchPushPublicKey()).resolves.toBe('BKey')
    expect(get).toHaveBeenCalledWith('/api/v1/push-subscriptions/public-key', {
      responseSchema: pushPublicKeyResponseSchema,
    })
  })

  /** 서버의 푸시가 꺼져 있으면 키가 null이거나 아예 오지 않는다. 둘 다 "꺼짐"이다. */
  it.each([[{ publicKey: null }], [{}]])('reads %j as push being off', async (data) => {
    get.mockResolvedValue({ data })

    await expect(fetchPushPublicKey()).resolves.toBeNull()
  })
})

describe('pushPublicKeyResponseSchema', () => {
  it('accepts a key, an explicit null and a missing key', () => {
    expect(pushPublicKeyResponseSchema.safeParse({ publicKey: 'BKey' }).success).toBe(true)
    expect(pushPublicKeyResponseSchema.safeParse({ publicKey: null }).success).toBe(true)
    expect(pushPublicKeyResponseSchema.safeParse({}).success).toBe(true)
  })

  it('rejects a key that is not a string, which would break subscribing in the browser', () => {
    expect(pushPublicKeyResponseSchema.safeParse({ publicKey: 123 }).success).toBe(false)
  })

  it('keeps parsing when the server adds a field', () => {
    expect(pushPublicKeyResponseSchema.safeParse({ publicKey: 'BKey', extra: 1 }).success).toBe(
      true,
    )
  })
})

describe('savePushSubscription / deletePushSubscription', () => {
  it('registers the device with PUT so that repeating it is harmless', async () => {
    put.mockResolvedValue({})
    const body = {
      endpoint: 'https://fcm.googleapis.com/x',
      p256dh: 'P',
      auth: 'A',
      language: 'ja',
    }

    await savePushSubscription(body)

    expect(put).toHaveBeenCalledWith('/api/v1/push-subscriptions', body)
  })

  /** 주소가 URL이라 경로가 아니라 본문으로 보낸다. */
  it('cancels the device by sending its endpoint in the request body', async () => {
    del.mockResolvedValue({})

    await deletePushSubscription('https://fcm.googleapis.com/x')

    expect(del).toHaveBeenCalledWith('/api/v1/push-subscriptions', {
      data: { endpoint: 'https://fcm.googleapis.com/x' },
    })
  })
})
