import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import type { PushEnvironment } from '../pushSubscription'

const savePushSubscription = vi.fn()
const deletePushSubscription = vi.fn()

vi.mock('../../api/pushSubscriptionApi', () => ({
  savePushSubscription: (...args: unknown[]) => savePushSubscription(...args),
  deletePushSubscription: (...args: unknown[]) => deletePushSubscription(...args),
}))

const {
  detectPushSupport,
  disablePush,
  dropBrowserPushSubscription,
  enablePush,
  readPushSubscription,
  removePushSubscriptionFromServer,
  syncPushSubscription,
  toSubscribeBody,
  urlBase64ToUint8Array,
} = await import('../pushSubscription')

const ENDPOINT = 'https://fcm.googleapis.com/fcm/send/abc'

function environment(overrides: Partial<PushEnvironment> = {}): PushEnvironment {
  return {
    hasServiceWorker: true,
    hasPushManager: true,
    hasNotification: true,
    userAgent: 'Mozilla/5.0 (X11; Linux x86_64) Chrome/130',
    platform: 'Linux x86_64',
    maxTouchPoints: 0,
    standalone: false,
    ...overrides,
  }
}

function fakeSubscription() {
  return {
    endpoint: ENDPOINT,
    toJSON: () => ({ endpoint: ENDPOINT, keys: { p256dh: 'P256DH', auth: 'AUTH' } }),
    unsubscribe: vi.fn().mockResolvedValue(true),
  }
}

describe('detectPushSupport', () => {
  it('supports a browser with service workers, push and notifications', () => {
    expect(detectPushSupport(environment())).toBe('supported')
  })

  /** iOS 사파리 탭에는 PushManager가 없다. 홈 화면에 추가해야 열린다. */
  it('asks iPhone and iPad browser tabs to install the app first', () => {
    const iphoneTab = environment({
      hasPushManager: false,
      userAgent: 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) Safari/605.1',
    })
    // iPadOS 13+는 사용자 에이전트가 Mac으로 나온다.
    const ipadTab = environment({
      hasPushManager: false,
      userAgent: 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) Safari/605.1',
      platform: 'MacIntel',
      maxTouchPoints: 5,
    })

    expect(detectPushSupport(iphoneTab)).toBe('needs-install')
    expect(detectPushSupport(ipadTab)).toBe('needs-install')
  })

  it('supports an installed iOS web app that has push', () => {
    expect(
      detectPushSupport(
        environment({ userAgent: 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_4)', standalone: true }),
      ),
    ).toBe('supported')
  })

  /** 홈 화면에 추가했는데도 PushManager가 없으면 iOS 16.4 미만이라 설치로는 해결되지 않는다. */
  it('gives up on an installed iOS app that still has no push', () => {
    expect(
      detectPushSupport(
        environment({
          hasPushManager: false,
          userAgent: 'Mozilla/5.0 (iPhone; CPU iPhone OS 15_0)',
          standalone: true,
        }),
      ),
    ).toBe('unsupported')
  })

  it('does not support a desktop browser without push, and does not ask it to install', () => {
    expect(detectPushSupport(environment({ hasPushManager: false }))).toBe('unsupported')
    expect(detectPushSupport(environment({ hasServiceWorker: false }))).toBe('unsupported')
    expect(detectPushSupport(environment({ hasNotification: false }))).toBe('unsupported')
  })
})

describe('urlBase64ToUint8Array', () => {
  /** `-_8`은 표준 base64의 `+/8=`다. url 문자와 빠진 패딩을 모두 거친다. */
  it('decodes base64url without padding', () => {
    expect(Array.from(urlBase64ToUint8Array('-_8'))).toEqual([251, 255])
  })
})

describe('toSubscribeBody', () => {
  it('keeps the endpoint and keys and adds the language', () => {
    expect(toSubscribeBody(fakeSubscription() as unknown as PushSubscription, 'ja')).toEqual({
      endpoint: ENDPOINT,
      p256dh: 'P256DH',
      auth: 'AUTH',
      language: 'ja',
    })
  })

  it('refuses a subscription whose keys are missing', () => {
    const incomplete = { toJSON: () => ({ endpoint: ENDPOINT }) } as unknown as PushSubscription

    expect(() => toSubscribeBody(incomplete, 'en')).toThrow()
  })
})

/**
 * 브라우저 API를 흉내 낸다. jsdom에는 서비스 워커·푸시·알림이 없다.
 * `calls`에 권한 요청과 `ready` 접근 순서를 남겨, 클릭에서 곧바로 권한을 묻는지 확인한다.
 */
function installBrowser(options: {
  permission?: NotificationPermission
  requested?: NotificationPermission
  existing?: ReturnType<typeof fakeSubscription> | null
  created?: ReturnType<typeof fakeSubscription>
  registered?: boolean
}) {
  const calls: string[] = []
  const created = options.created ?? fakeSubscription()
  const pushManager = {
    getSubscription: vi.fn().mockResolvedValue(options.existing ?? null),
    subscribe: vi.fn().mockResolvedValue(created),
  }
  const registration = { pushManager }

  vi.stubGlobal('PushManager', class {})
  vi.stubGlobal('Notification', {
    permission: options.permission ?? 'default',
    requestPermission: vi.fn(() => {
      calls.push('requestPermission')
      return Promise.resolve(options.requested ?? 'granted')
    }),
  })
  Object.defineProperty(navigator, 'serviceWorker', {
    configurable: true,
    value: {
      getRegistration: vi
        .fn()
        .mockResolvedValue(options.registered === false ? undefined : registration),
      get ready() {
        calls.push('ready')
        return Promise.resolve(registration)
      },
    },
  })

  return { pushManager, calls, created }
}

beforeEach(() => {
  savePushSubscription.mockReset()
  savePushSubscription.mockResolvedValue(undefined)
  deletePushSubscription.mockReset()
  deletePushSubscription.mockResolvedValue(undefined)
})

afterEach(() => {
  vi.unstubAllGlobals()
  Reflect.deleteProperty(navigator, 'serviceWorker')
})

describe('readPushSubscription', () => {
  it('returns the subscription this device already has', async () => {
    const existing = fakeSubscription()
    installBrowser({ existing })

    await expect(readPushSubscription()).resolves.toBe(existing)
  })

  it('returns null when there is no subscription or no registered service worker', async () => {
    installBrowser({ existing: null })
    await expect(readPushSubscription()).resolves.toBeNull()

    installBrowser({ registered: false })
    await expect(readPushSubscription()).resolves.toBeNull()
  })
})

describe('enablePush', () => {
  it('asks permission, subscribes with the server key and registers the device', async () => {
    const { pushManager, created } = installBrowser({})

    await expect(enablePush('-_8', 'ja')).resolves.toEqual({ permission: 'granted', enabled: true })

    expect(pushManager.subscribe).toHaveBeenCalledWith({
      userVisibleOnly: true,
      applicationServerKey: expect.any(Uint8Array),
    })
    expect(Array.from(pushManager.subscribe.mock.calls[0]?.[0].applicationServerKey)).toEqual([
      251, 255,
    ])
    expect(savePushSubscription).toHaveBeenCalledWith({
      endpoint: created.endpoint,
      p256dh: 'P256DH',
      auth: 'AUTH',
      language: 'ja',
    })
  })

  /** Safari는 사용자 동작에서 이어지지 않은 권한 요청을 거절한다. */
  it('asks permission before it touches anything asynchronous', async () => {
    const { calls } = installBrowser({})

    await enablePush('-_8', 'en')

    expect(calls).toEqual(['requestPermission', 'ready'])
  })

  it('reuses the subscription the device already has instead of creating another', async () => {
    const existing = fakeSubscription()
    const { pushManager } = installBrowser({ existing })

    await enablePush('-_8', 'en')

    expect(pushManager.subscribe).not.toHaveBeenCalled()
    expect(savePushSubscription).toHaveBeenCalledOnce()
  })

  it.each(['denied', 'default'] as const)(
    'does nothing more when permission is %s',
    async (requested) => {
      const { pushManager, calls } = installBrowser({ requested })

      await expect(enablePush('-_8', 'en')).resolves.toEqual({
        permission: requested,
        enabled: false,
      })

      expect(pushManager.subscribe).not.toHaveBeenCalled()
      expect(savePushSubscription).not.toHaveBeenCalled()
      expect(calls).not.toContain('ready')
    },
  )

  it('fails loudly when the server rejects the registration, so the screen can say so', async () => {
    installBrowser({})
    savePushSubscription.mockRejectedValue(new Error('server down'))

    await expect(enablePush('-_8', 'en')).rejects.toThrow('server down')
  })

  /**
   * 서버에 등록하지 못한 새 구독이 브라우저에 남으면, 화면을 다시 열 때 구독이 있다는 이유로
   * "켜짐"으로 보인다. 서버에는 보낼 주소가 없어 알림이 오지 않는데도 켜기 버튼이 사라진다.
   */
  it('cancels the subscription it just created when the server rejects the registration', async () => {
    const { pushManager, created } = installBrowser({})
    let current: ReturnType<typeof fakeSubscription> | null = null
    pushManager.subscribe.mockImplementation(() => {
      current = created
      return Promise.resolve(created)
    })
    pushManager.getSubscription.mockImplementation(() => Promise.resolve(current))
    created.unsubscribe.mockImplementation(() => {
      current = null
      return Promise.resolve(true)
    })
    savePushSubscription.mockRejectedValue(new Error('server down'))

    await expect(enablePush('-_8', 'en')).rejects.toThrow('server down')

    expect(created.unsubscribe).toHaveBeenCalledOnce()
    // 카드를 다시 열었을 때 읽는 값이다. 구독이 없어야 "켜기"가 다시 보인다.
    await expect(readPushSubscription()).resolves.toBeNull()
  })

  it('reports the registration failure even when cancelling the new subscription fails too', async () => {
    const { created } = installBrowser({})
    created.unsubscribe.mockRejectedValue(new Error('push service unreachable'))
    savePushSubscription.mockRejectedValue(new Error('server down'))

    await expect(enablePush('-_8', 'en')).rejects.toThrow('server down')
  })

  /** 이미 있던 구독은 이번 시도가 만든 것이 아니다. 등록이 실패해도 건드리지 않는다. */
  it('keeps a subscription the device already had when the registration fails', async () => {
    const existing = fakeSubscription()
    installBrowser({ existing })
    savePushSubscription.mockRejectedValue(new Error('server down'))

    await expect(enablePush('-_8', 'en')).rejects.toThrow('server down')

    expect(existing.unsubscribe).not.toHaveBeenCalled()
  })

  it('cancels the new subscription when the browser gave back no keys', async () => {
    const { created } = installBrowser({
      created: Object.assign(fakeSubscription(), { toJSON: () => ({ endpoint: ENDPOINT }) }),
    })

    await expect(enablePush('-_8', 'en')).rejects.toThrow('missing its endpoint or keys')

    expect(created.unsubscribe).toHaveBeenCalledOnce()
    expect(savePushSubscription).not.toHaveBeenCalled()
  })
})

describe('disablePush', () => {
  it('removes the device from the server and from the browser', async () => {
    const existing = fakeSubscription()
    installBrowser({ existing })

    await disablePush()

    expect(deletePushSubscription).toHaveBeenCalledWith(ENDPOINT)
    expect(existing.unsubscribe).toHaveBeenCalledOnce()
  })

  /** 서버 행이 남아도 푸시 서비스가 410으로 끝난 구독이라고 알려 주면 서버가 스스로 치운다. */
  it('still cancels the browser subscription when the server cannot be reached', async () => {
    const existing = fakeSubscription()
    installBrowser({ existing })
    deletePushSubscription.mockRejectedValue(new Error('offline'))

    await disablePush()

    expect(existing.unsubscribe).toHaveBeenCalledOnce()
  })

  it('does nothing when the device has no subscription', async () => {
    installBrowser({ existing: null })

    await disablePush()

    expect(deletePushSubscription).not.toHaveBeenCalled()
  })
})

describe('syncPushSubscription', () => {
  it('registers the existing subscription again with the current language', async () => {
    installBrowser({ permission: 'granted', existing: fakeSubscription() })

    await syncPushSubscription('vi')

    expect(savePushSubscription).toHaveBeenCalledWith({
      endpoint: ENDPOINT,
      p256dh: 'P256DH',
      auth: 'AUTH',
      language: 'vi',
    })
  })

  /** 권한을 아직 안 준 사용자에게 로그인만 했다고 권한을 묻거나 구독을 만들지는 않는다. */
  it.each(['default', 'denied'] as const)(
    'does nothing while permission is %s',
    async (permission) => {
      const { pushManager } = installBrowser({ permission, existing: fakeSubscription() })

      await syncPushSubscription('en')

      expect(savePushSubscription).not.toHaveBeenCalled()
      expect(pushManager.subscribe).not.toHaveBeenCalled()
    },
  )

  it('does nothing when there is no subscription to carry over', async () => {
    installBrowser({ permission: 'granted', existing: null })

    await syncPushSubscription('en')

    expect(savePushSubscription).not.toHaveBeenCalled()
  })

  it('does nothing where push is not available', async () => {
    // 이 환경에는 PushManager가 없다.
    await expect(syncPushSubscription('en')).resolves.toBeUndefined()

    expect(savePushSubscription).not.toHaveBeenCalled()
  })

  it('never throws, so a failure cannot disturb the screen it ran from', async () => {
    installBrowser({ permission: 'granted', existing: fakeSubscription() })
    savePushSubscription.mockRejectedValue(new Error('server down'))

    await expect(syncPushSubscription('en')).resolves.toBeUndefined()
  })
})

describe('removePushSubscriptionFromServer', () => {
  it('deletes this device from the server but keeps the browser subscription', async () => {
    const existing = fakeSubscription()
    installBrowser({ existing })

    await removePushSubscriptionFromServer()

    expect(deletePushSubscription).toHaveBeenCalledWith(ENDPOINT)
    expect(existing.unsubscribe).not.toHaveBeenCalled()
  })

  it('does nothing when there is nothing to delete or push is not available', async () => {
    await removePushSubscriptionFromServer()
    installBrowser({ existing: null })
    await removePushSubscriptionFromServer()

    expect(deletePushSubscription).not.toHaveBeenCalled()
  })

  /** 실패를 삼키는 것은 로그아웃을 부르는 쪽의 몫이다. 여기서는 그대로 알린다. */
  it('reports a failed deletion to the caller', async () => {
    installBrowser({ existing: fakeSubscription() })
    deletePushSubscription.mockRejectedValue(new Error('401'))

    await expect(removePushSubscriptionFromServer()).rejects.toThrow('401')
  })
})

describe('dropBrowserPushSubscription', () => {
  /** 서버 삭제가 실패한 로그아웃에서 알림이 계속 뜨지 않도록, 서버 호출 없이 브라우저 구독을 끊는다. */
  it('cancels the browser subscription without calling the server', async () => {
    const existing = fakeSubscription()
    installBrowser({ existing })

    await dropBrowserPushSubscription()

    expect(existing.unsubscribe).toHaveBeenCalledOnce()
    expect(deletePushSubscription).not.toHaveBeenCalled()
  })

  it('does nothing when there is no subscription or push is not available', async () => {
    await dropBrowserPushSubscription()
    installBrowser({ existing: null })
    await dropBrowserPushSubscription()

    expect(deletePushSubscription).not.toHaveBeenCalled()
  })
})
