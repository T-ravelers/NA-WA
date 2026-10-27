import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import type { PushEnvironment } from '../pushSubscription'

const savePushSubscription = vi.fn()
const deletePushSubscription = vi.fn()
const fetchPushPublicKey = vi.fn()

vi.mock('../../api/pushSubscriptionApi', () => ({
  savePushSubscription: (...args: unknown[]) => savePushSubscription(...args),
  deletePushSubscription: (...args: unknown[]) => deletePushSubscription(...args),
  fetchPushPublicKey: (...args: unknown[]) => fetchPushPublicKey(...args),
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
const NEW_ENDPOINT = 'https://fcm.googleapis.com/fcm/send/new'
// 서버의 현재 공개키다. base64url `-_8`은 바이트 [251, 255]다.
const SERVER_KEY = '-_8'

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

/** `usedKey`는 구독을 만들 때 쓴 공개키다. 기본값은 서버의 현재 키이고, null은 브라우저가 키를 알려 주지 않는 경우다. */
function fakeSubscription(options: { endpoint?: string; usedKey?: number[] | null } = {}) {
  const endpoint = options.endpoint ?? ENDPOINT
  const usedKey = options.usedKey === undefined ? [251, 255] : options.usedKey

  return {
    endpoint,
    options: { applicationServerKey: usedKey === null ? null : new Uint8Array(usedKey).buffer },
    toJSON: () => ({ endpoint, keys: { p256dh: 'P256DH', auth: 'AUTH' } }),
    unsubscribe: vi.fn().mockResolvedValue(true),
  }
}

/** 직접 끝낼 때까지 끝나지 않는 Promise와 그것을 끝내는 함수. */
function hold<T>(value: T) {
  let release: () => void = () => undefined
  const promise = new Promise<T>((resolve) => {
    release = () => resolve(value)
  })

  return { promise, release }
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
  const getRegistration = vi
    .fn()
    .mockResolvedValue(options.registered === false ? undefined : registration)
  const requestPermission = vi.fn(() => {
    calls.push('requestPermission')
    return Promise.resolve(options.requested ?? 'granted')
  })

  vi.stubGlobal('PushManager', class {})
  vi.stubGlobal('Notification', {
    permission: options.permission ?? 'default',
    requestPermission,
  })
  Object.defineProperty(navigator, 'serviceWorker', {
    configurable: true,
    value: {
      getRegistration,
      get ready() {
        calls.push('ready')
        return Promise.resolve(registration)
      },
    },
  })

  return { pushManager, calls, created, getRegistration, requestPermission }
}

beforeEach(() => {
  savePushSubscription.mockReset()
  savePushSubscription.mockResolvedValue(undefined)
  deletePushSubscription.mockReset()
  deletePushSubscription.mockResolvedValue(undefined)
  fetchPushPublicKey.mockReset()
  fetchPushPublicKey.mockResolvedValue(SERVER_KEY)
})

afterEach(() => {
  vi.unstubAllGlobals()
  Reflect.deleteProperty(navigator, 'serviceWorker')
  Reflect.deleteProperty(navigator, 'locks')
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

  /**
   * 구독은 만든 VAPID 공개키에 묶여 있다. 서버가 키를 바꾸면 예전 구독은 푸시 서비스가 거절하는데,
   * 브라우저는 키가 다른 구독이 남아 있으면 새 키로 구독하지도 못한다.
   */
  describe('when the device subscribed with an older server key', () => {
    it('replaces the stale subscription: server row, browser subscription, then a new one', async () => {
      const stale = fakeSubscription({ usedKey: [1, 2] })
      const created = fakeSubscription({ endpoint: NEW_ENDPOINT })
      const { pushManager } = installBrowser({ existing: stale, created })
      const order: string[] = []
      deletePushSubscription.mockImplementation(() => {
        order.push('delete-server-row')
        return Promise.resolve()
      })
      stale.unsubscribe.mockImplementation(() => {
        order.push('unsubscribe')
        return Promise.resolve(true)
      })
      pushManager.subscribe.mockImplementation(() => {
        order.push('subscribe')
        return Promise.resolve(created)
      })

      await expect(enablePush(SERVER_KEY, 'en')).resolves.toEqual({
        permission: 'granted',
        enabled: true,
      })

      expect(order).toEqual(['delete-server-row', 'unsubscribe', 'subscribe'])
      expect(deletePushSubscription).toHaveBeenCalledWith(ENDPOINT)
      expect(savePushSubscription).toHaveBeenCalledWith(
        expect.objectContaining({ endpoint: NEW_ENDPOINT }),
      )
    })

    /** 길이만 같고 내용이 다른 키도 다른 키다. */
    it('compares the key bytes, not just their length', async () => {
      const stale = fakeSubscription({ usedKey: [251, 254] })
      const { pushManager } = installBrowser({ existing: stale })

      await enablePush(SERVER_KEY, 'en')

      expect(stale.unsubscribe).toHaveBeenCalledOnce()
      expect(pushManager.subscribe).toHaveBeenCalledOnce()
    })

    it('still replaces it when the server row cannot be deleted', async () => {
      const stale = fakeSubscription({ usedKey: [1, 2] })
      const { pushManager } = installBrowser({ existing: stale })
      deletePushSubscription.mockRejectedValue(new Error('offline'))

      await expect(enablePush(SERVER_KEY, 'en')).resolves.toMatchObject({ enabled: true })

      expect(stale.unsubscribe).toHaveBeenCalledOnce()
      expect(pushManager.subscribe).toHaveBeenCalledOnce()
    })

    it('cancels the replacement too when the server rejects its registration', async () => {
      const stale = fakeSubscription({ usedKey: [1, 2] })
      const { created } = installBrowser({ existing: stale })
      savePushSubscription.mockRejectedValue(new Error('server down'))

      await expect(enablePush(SERVER_KEY, 'en')).rejects.toThrow('server down')

      expect(created.unsubscribe).toHaveBeenCalledOnce()
    })

    it('keeps a subscription whose key the browser does not report', async () => {
      const unknown = fakeSubscription({ usedKey: null })
      const { pushManager } = installBrowser({ existing: unknown })

      await enablePush(SERVER_KEY, 'en')

      expect(unknown.unsubscribe).not.toHaveBeenCalled()
      expect(pushManager.subscribe).not.toHaveBeenCalled()
      expect(savePushSubscription).toHaveBeenCalledOnce()
    })
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

  it('leaves a subscription made with the current server key alone', async () => {
    const existing = fakeSubscription()
    const { pushManager } = installBrowser({ permission: 'granted', existing })

    await syncPushSubscription('en')

    expect(deletePushSubscription).not.toHaveBeenCalled()
    expect(existing.unsubscribe).not.toHaveBeenCalled()
    expect(pushManager.subscribe).not.toHaveBeenCalled()
  })

  /** 키를 바꾼 서버에서 앱을 열면, 사용자가 아무것도 하지 않아도 새 키로 이어져야 한다. */
  describe('when the server key changed since the device subscribed', () => {
    it('subscribes again with the new key and registers that subscription', async () => {
      const stale = fakeSubscription({ usedKey: [1, 2] })
      const created = fakeSubscription({ endpoint: NEW_ENDPOINT })
      const { pushManager } = installBrowser({ permission: 'granted', existing: stale, created })

      await syncPushSubscription('ja')

      expect(deletePushSubscription).toHaveBeenCalledWith(ENDPOINT)
      expect(stale.unsubscribe).toHaveBeenCalledOnce()
      expect(Array.from(pushManager.subscribe.mock.calls[0]?.[0].applicationServerKey)).toEqual([
        251, 255,
      ])
      expect(savePushSubscription).toHaveBeenCalledExactlyOnceWith({
        endpoint: NEW_ENDPOINT,
        p256dh: 'P256DH',
        auth: 'AUTH',
        language: 'ja',
      })
    })

    it('cancels the new subscription when its registration fails', async () => {
      const { created } = installBrowser({
        permission: 'granted',
        existing: fakeSubscription({ usedKey: [1, 2] }),
      })
      savePushSubscription.mockRejectedValue(new Error('server down'))

      await expect(syncPushSubscription('en')).resolves.toBeUndefined()

      expect(created.unsubscribe).toHaveBeenCalledOnce()
    })

    /**
     * 사용자 동작을 요구하는 브라우저에서는 다시 구독이 막힐 수 있다. 쓸 수 없던 구독이라 잃는 것은
     * 없고, 구독이 없으니 카드가 "켜기"로 돌아가 사용자가 눌러 다시 켠다.
     */
    it('ends without a subscription, quietly, when subscribing again is refused', async () => {
      const { pushManager } = installBrowser({
        permission: 'granted',
        existing: fakeSubscription({ usedKey: [1, 2] }),
      })
      pushManager.subscribe.mockRejectedValue(new Error('NotAllowedError'))

      await expect(syncPushSubscription('en')).resolves.toBeUndefined()

      expect(savePushSubscription).not.toHaveBeenCalled()
    })

    /** 서버의 키를 모르면 예전 키인지도 알 수 없다. 멀쩡한 구독을 지우지 않고 이전처럼 등록만 한다. */
    it.each([
      ['the server has push turned off', () => fetchPushPublicKey.mockResolvedValue(null)],
      ['the key lookup fails', () => fetchPushPublicKey.mockRejectedValue(new Error('offline'))],
    ])('keeps the subscription when %s', async (_name, arrange) => {
      const existing = fakeSubscription({ usedKey: [1, 2] })
      const { pushManager } = installBrowser({ permission: 'granted', existing })
      arrange()

      await syncPushSubscription('en')

      expect(existing.unsubscribe).not.toHaveBeenCalled()
      expect(pushManager.subscribe).not.toHaveBeenCalled()
      expect(savePushSubscription).toHaveBeenCalledWith(
        expect.objectContaining({ endpoint: ENDPOINT }),
      )
    })
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

  /**
   * 이미 인증을 통과해 처리 중인 등록은 로그아웃으로 취소되지 않는다. 먼저 지우면 늦게 저장된
   * 등록이 삭제 뒤에 이전 계정의 구독을 되살린다.
   */
  describe('while a registration is still in flight', () => {
    let order: string[]
    let finishPut: () => void
    let failPut: () => void

    /** PUT은 직접 끝낼 때까지 서버에 닿지 않은 상태로 둔다. */
    function holdRegistration() {
      savePushSubscription.mockImplementation(
        () =>
          new Promise<void>((resolve, reject) => {
            finishPut = () => {
              order.push('put')
              resolve()
            }
            failPut = () => reject(new Error('503'))
          }),
      )
      deletePushSubscription.mockImplementation(() => {
        order.push('delete')
        return Promise.resolve()
      })
    }

    beforeEach(() => {
      order = []
    })

    /** 한 번 기다려 주어도 삭제가 먼저 나가지 않는지 본다. */
    const letPendingWorkRun = () => new Promise((resolve) => setTimeout(resolve, 0))

    it('deletes only after a language-change sync has been saved', async () => {
      installBrowser({ permission: 'granted', existing: fakeSubscription() })
      holdRegistration()

      const sync = syncPushSubscription('ja')
      await vi.waitFor(() => expect(savePushSubscription).toHaveBeenCalled())
      const removal = removePushSubscriptionFromServer()

      await letPendingWorkRun()
      expect(deletePushSubscription).not.toHaveBeenCalled()

      finishPut()
      await Promise.all([sync, removal])

      expect(order).toEqual(['put', 'delete'])
    })

    /** PUT을 시작하기 전, 구독을 읽는 중인 동기화도 같은 이유로 기다려야 한다. */
    it('also waits for a sync that has not reached the server yet', async () => {
      const existing = fakeSubscription()
      const { pushManager, getRegistration } = installBrowser({ permission: 'granted', existing })
      let readSubscription: (value: unknown) => void = () => undefined
      getRegistration.mockReturnValueOnce(new Promise((resolve) => (readSubscription = resolve)))
      holdRegistration()

      const sync = syncPushSubscription('ja')
      const removal = removePushSubscriptionFromServer()
      await letPendingWorkRun()
      expect(deletePushSubscription).not.toHaveBeenCalled()

      readSubscription({ pushManager })
      await vi.waitFor(() => expect(savePushSubscription).toHaveBeenCalled())
      finishPut()
      await Promise.all([sync, removal])

      expect(order).toEqual(['put', 'delete'])
    })

    it('deletes only after turning push on has been saved', async () => {
      installBrowser({ existing: fakeSubscription() })
      holdRegistration()

      const enabling = enablePush('-_8', 'en')
      await vi.waitFor(() => expect(savePushSubscription).toHaveBeenCalled())
      const removal = removePushSubscriptionFromServer()

      await letPendingWorkRun()
      expect(deletePushSubscription).not.toHaveBeenCalled()

      finishPut()
      await Promise.all([enabling, removal])

      expect(order).toEqual(['put', 'delete'])
    })

    it('still deletes when the registration it waited for failed', async () => {
      installBrowser({ permission: 'granted', existing: fakeSubscription() })
      holdRegistration()

      const sync = syncPushSubscription('ja')
      await vi.waitFor(() => expect(savePushSubscription).toHaveBeenCalled())
      const removal = removePushSubscriptionFromServer()

      failPut()
      await Promise.all([sync, removal])

      expect(order).toEqual(['delete'])
    })
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

  /**
   * 로그아웃은 등록이 끝나기를 3초만 기다리고 이 함수로 넘어온다. 기다림을 놓은 등록은 취소되지 않고
   * 계속 돌아서, 늦게 새 구독을 만들어 로그아웃한 계정으로 저장할 수 있다. 이 함수가 실행된 뒤에는
   * 그런 등록이 구독을 만들지 않아야 한다.
   */
  describe('for a registration that was still running when it ran', () => {
    it('lets a renewal that is waiting on the old server row stop before it subscribes', async () => {
      const stale = fakeSubscription({ usedKey: [1, 2] })
      const { pushManager } = installBrowser({ permission: 'granted', existing: stale })
      const deletion = hold(undefined)
      deletePushSubscription.mockReturnValue(deletion.promise)

      const sync = syncPushSubscription('ja')
      await vi.waitFor(() => expect(deletePushSubscription).toHaveBeenCalledWith(ENDPOINT))
      await dropBrowserPushSubscription()
      deletion.release()
      await sync

      expect(pushManager.subscribe).not.toHaveBeenCalled()
      expect(savePushSubscription).not.toHaveBeenCalled()
    })

    it('cancels the subscription a renewal finished creating afterwards, without saving it', async () => {
      const created = fakeSubscription({ endpoint: NEW_ENDPOINT })
      const { pushManager } = installBrowser({
        permission: 'granted',
        existing: fakeSubscription({ usedKey: [1, 2] }),
        created,
      })
      const subscribing = hold(created)
      pushManager.subscribe.mockReturnValue(subscribing.promise)

      const sync = syncPushSubscription('ja')
      await vi.waitFor(() => expect(pushManager.subscribe).toHaveBeenCalled())
      await dropBrowserPushSubscription()
      subscribing.release()
      await sync

      expect(created.unsubscribe).toHaveBeenCalledOnce()
      expect(savePushSubscription).not.toHaveBeenCalled()
    })

    it('does not subscribe for a permission answer that arrives afterwards', async () => {
      const { pushManager, requestPermission } = installBrowser({ existing: null })
      const answer = hold<NotificationPermission>('granted')
      requestPermission.mockReturnValueOnce(answer.promise)

      const enabling = enablePush('-_8', 'en')
      await dropBrowserPushSubscription()
      answer.release()

      await expect(enabling).resolves.toEqual({ permission: 'granted', enabled: false })
      expect(pushManager.subscribe).not.toHaveBeenCalled()
      expect(savePushSubscription).not.toHaveBeenCalled()
    })

    it('cancels the subscription that turning push on finished creating afterwards', async () => {
      const created = fakeSubscription({ endpoint: NEW_ENDPOINT })
      const { pushManager } = installBrowser({ existing: null, created })
      const subscribing = hold(created)
      pushManager.subscribe.mockReturnValue(subscribing.promise)

      const enabling = enablePush('-_8', 'en')
      await vi.waitFor(() => expect(pushManager.subscribe).toHaveBeenCalled())
      await dropBrowserPushSubscription()
      subscribing.release()

      await expect(enabling).resolves.toEqual({ permission: 'granted', enabled: false })
      expect(created.unsubscribe).toHaveBeenCalledOnce()
      expect(savePushSubscription).not.toHaveBeenCalled()
    })

    /** 로그아웃이 실패해 같은 페이지에 남을 수 있다. 그 뒤에 새로 시작하는 등록까지 막으면 안 된다. */
    it('does not stop a registration that starts afterwards', async () => {
      const created = fakeSubscription({ endpoint: NEW_ENDPOINT })
      installBrowser({
        permission: 'granted',
        existing: fakeSubscription({ usedKey: [1, 2] }),
        created,
      })

      await dropBrowserPushSubscription()
      await syncPushSubscription('en')

      expect(savePushSubscription).toHaveBeenCalledExactlyOnceWith(
        expect.objectContaining({ endpoint: NEW_ENDPOINT }),
      )
      expect(created.unsubscribe).not.toHaveBeenCalled()
    })
  })
})

/**
 * 갱신은 예전 서버 행의 삭제가 끝나야 새로 구독한다. 그 삭제가 느린 동안 사용자가 푸시를 끄면, 끄기가
 * 끝난 뒤에 갱신이 새 구독을 만들어 저장해서는 안 된다. 카드는 꺼짐인데 푸시가 계속 오게 된다.
 */
describe('disablePush and a registration that was still running', () => {
  it('lets a renewal that is waiting on the old server row stop before it subscribes', async () => {
    const { pushManager } = installBrowser({
      permission: 'granted',
      existing: fakeSubscription({ usedKey: [1, 2] }),
    })
    const deletion = hold(undefined)
    deletePushSubscription.mockReturnValueOnce(deletion.promise)

    const sync = syncPushSubscription('ja')
    await vi.waitFor(() => expect(deletePushSubscription).toHaveBeenCalledWith(ENDPOINT))
    await disablePush()
    deletion.release()
    await sync

    expect(pushManager.subscribe).not.toHaveBeenCalled()
    expect(savePushSubscription).not.toHaveBeenCalled()
  })

  it('cancels the subscription a renewal finished creating afterwards, without saving it', async () => {
    const created = fakeSubscription({ endpoint: NEW_ENDPOINT })
    const { pushManager } = installBrowser({
      permission: 'granted',
      existing: fakeSubscription({ usedKey: [1, 2] }),
      created,
    })
    const subscribing = hold(created)
    pushManager.subscribe.mockReturnValue(subscribing.promise)

    const sync = syncPushSubscription('ja')
    await vi.waitFor(() => expect(pushManager.subscribe).toHaveBeenCalled())
    await disablePush()
    subscribing.release()
    await sync

    expect(created.unsubscribe).toHaveBeenCalledOnce()
    expect(savePushSubscription).not.toHaveBeenCalled()
  })

  /** 탭마다 모듈 상태는 따로이니, 다른 탭에서 끈 것이 이 탭의 갱신까지 닿아야 한다. */
  it('also stops a renewal that is running in another tab', async () => {
    const { pushManager } = installBrowser({
      permission: 'granted',
      existing: fakeSubscription({ usedKey: [1, 2] }),
    })
    const deletion = hold(undefined)
    deletePushSubscription.mockReturnValueOnce(deletion.promise)
    const sync = syncPushSubscription('ja')
    await vi.waitFor(() => expect(deletePushSubscription).toHaveBeenCalledWith(ENDPOINT))

    vi.resetModules()
    const otherTab = await import('../pushSubscription')
    await otherTab.disablePush()
    deletion.release()
    await sync

    expect(pushManager.subscribe).not.toHaveBeenCalled()
    expect(savePushSubscription).not.toHaveBeenCalled()
  })

  /** 끄기가 끝난 뒤 사용자가 다시 켜는 것까지 막으면 안 된다. */
  it('does not stop turning push on afterwards', async () => {
    const created = fakeSubscription({ endpoint: NEW_ENDPOINT })
    installBrowser({ existing: null, created })

    await disablePush()
    const result = await enablePush(SERVER_KEY, 'en')

    expect(result).toEqual({ permission: 'granted', enabled: true })
    expect(savePushSubscription).toHaveBeenCalledExactlyOnceWith(
      expect.objectContaining({ endpoint: NEW_ENDPOINT }),
    )
  })
})

/** 갱신이 서버의 지금 키를 확인했다면 카드가 들고 있는 키도 그것이어야 한다. */
describe('syncPushSubscription and the server key it looked up', () => {
  it('hands the key to the caller', async () => {
    installBrowser({ permission: 'granted', existing: fakeSubscription() })
    const onPublicKey = vi.fn()

    await syncPushSubscription('en', onPublicKey)

    expect(onPublicKey).toHaveBeenCalledExactlyOnceWith(SERVER_KEY)
  })

  it('hands over the new key when it has to subscribe again too', async () => {
    installBrowser({
      permission: 'granted',
      existing: fakeSubscription({ usedKey: [1, 2] }),
      created: fakeSubscription({ endpoint: NEW_ENDPOINT }),
    })
    const onPublicKey = vi.fn()

    await syncPushSubscription('en', onPublicKey)

    expect(onPublicKey).toHaveBeenCalledExactlyOnceWith(SERVER_KEY)
  })

  it.each([
    ['the server has push turned off', () => fetchPushPublicKey.mockResolvedValue(null)],
    ['the key lookup fails', () => fetchPushPublicKey.mockRejectedValue(new Error('offline'))],
  ])('hands over nothing when %s', async (_name, arrange) => {
    installBrowser({ permission: 'granted', existing: fakeSubscription() })
    arrange()
    const onPublicKey = vi.fn()

    await syncPushSubscription('en', onPublicKey)

    expect(onPublicKey).not.toHaveBeenCalled()
  })
})

/**
 * 등록과 로그아웃의 삭제는 탭 사이에서도 순서를 지켜야 해서 Web Locks를 쓴다. 등록은 공유로 잡아 서로
 * 막지 않고, 삭제는 배타로 잡아 어느 탭이든 진행 중인 등록이 끝난 뒤에 실행한다.
 */
describe('registration locks', () => {
  /** 락을 잡을 때마다 `calls`에 'lock'을 남기고 곧바로 콜백을 실행하는 락 관리자. */
  function installLockSpy(calls: string[] = []) {
    const request = vi.fn(
      (_name: string, _options: { mode: string }, callback: () => Promise<unknown>) => {
        calls.push('lock')
        return callback()
      },
    )
    Object.defineProperty(navigator, 'locks', { configurable: true, value: { request } })

    return request
  }

  it('saves under a shared lock, so registrations do not hold each other up', async () => {
    installBrowser({ permission: 'granted', existing: fakeSubscription() })
    const request = installLockSpy()

    await syncPushSubscription('en')

    expect(request).toHaveBeenCalledExactlyOnceWith(
      expect.any(String),
      { mode: 'shared' },
      expect.any(Function),
    )
    expect(savePushSubscription).toHaveBeenCalledOnce()
  })

  /** 권한 요청은 클릭에서 곧바로 이어져야 해서, 락은 권한 응답을 받은 뒤에 잡는다. */
  it('turns push on under a shared lock taken after the permission prompt', async () => {
    const { calls } = installBrowser({ existing: fakeSubscription() })
    const request = installLockSpy(calls)

    await enablePush(SERVER_KEY, 'en')

    expect(request).toHaveBeenCalledExactlyOnceWith(
      expect.any(String),
      { mode: 'shared' },
      expect.any(Function),
    )
    expect(calls).toEqual(['requestPermission', 'ready', 'lock'])
  })

  it('deletes under an exclusive lock, on the same lock the registrations use', async () => {
    installBrowser({ permission: 'granted', existing: fakeSubscription() })
    const request = installLockSpy()

    await syncPushSubscription('en')
    await removePushSubscriptionFromServer()

    expect(request).toHaveBeenCalledTimes(2)
    expect(request.mock.calls[1]?.[1]).toEqual({ mode: 'exclusive' })
    expect(request.mock.calls[1]?.[0]).toBe(request.mock.calls[0]?.[0])
    expect(deletePushSubscription).toHaveBeenCalledExactlyOnceWith(ENDPOINT)
  })

  /** 락을 잡지 못하는 환경이어도 푸시는 락 없이 이전처럼 동작해야 한다. */
  it('still saves when the lock cannot be taken', async () => {
    installBrowser({ permission: 'granted', existing: fakeSubscription() })
    Object.defineProperty(navigator, 'locks', {
      configurable: true,
      value: {
        request: vi.fn().mockRejectedValue(new DOMException('opaque origin', 'SecurityError')),
      },
    })

    await syncPushSubscription('en')

    expect(savePushSubscription).toHaveBeenCalledOnce()
  })

  /** 등록 자체가 실패한 것을 락 실패로 오인해 한 번 더 실행하지 않는다. */
  it('reports a failed registration once, without running it again', async () => {
    installBrowser({ existing: fakeSubscription() })
    installLockSpy()
    savePushSubscription.mockRejectedValue(new Error('503'))

    await expect(enablePush(SERVER_KEY, 'en')).rejects.toThrow('503')

    expect(savePushSubscription).toHaveBeenCalledOnce()
  })

  /**
   * 락을 기다리는 동안에도 등록은 이미 시작된 것이다. 그 사이에 푸시를 껐다면, 락을 받고 나서
   * 시작하는 것처럼 취소를 놓쳐 구독을 새로 만들어서는 안 된다.
   */
  it('counts a cancellation that arrives while the registration waits for the lock', async () => {
    const { pushManager } = installBrowser({
      permission: 'granted',
      existing: fakeSubscription({ usedKey: [1, 2] }),
    })
    const granted = hold(undefined)
    Object.defineProperty(navigator, 'locks', {
      configurable: true,
      value: {
        request: vi.fn(
          async (_name: string, _options: unknown, callback: () => Promise<unknown>) => {
            await granted.promise
            return callback()
          },
        ),
      },
    })

    const sync = syncPushSubscription('ja')
    await disablePush()
    granted.release()
    await sync

    expect(pushManager.subscribe).not.toHaveBeenCalled()
    expect(savePushSubscription).not.toHaveBeenCalled()
  })
})
