import { vi } from 'vitest'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'

/**
 * 푸시 구독을 라이프사이클·모델·카드와 실제로 연결해 보는 테스트가 함께 쓰는 브라우저 흉내.
 * 서버와 httpClient를 흉내 내는 쪽은 각 테스트가 가진다.
 */

export const ENDPOINT = 'https://fcm.googleapis.com/fcm/send/abc'
export const NEW_ENDPOINT = 'https://fcm.googleapis.com/fcm/send/new'
// 서버의 현재 공개키(base64url `-_8`, 바이트 [251, 255])다.
export const SERVER_KEY = '-_8'
// 서버가 키를 바꾸기 전의 공개키(base64url `AQI`, 바이트 [1, 2])다.
export const OLD_SERVER_KEY = 'AQI'

export function createTestRouter(): Router {
  const page = { template: '<div />' }

  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/sign-in', name: 'sign-in', component: page },
      { path: '/wallet', name: 'wallet', component: page, meta: { requiresAuth: true } },
    ],
  })
}

export function fakeSubscription(endpoint: string, usedKey: number[]) {
  return {
    endpoint,
    options: { applicationServerKey: new Uint8Array(usedKey).buffer },
    toJSON: () => ({ endpoint, keys: { p256dh: 'P256DH', auth: 'AUTH' } }),
    unsubscribe: vi.fn().mockResolvedValue(true),
  }
}

/**
 * 브라우저에 이 기기의 구독이 하나 있다. 로그아웃 뒤에도 남아 있는지 `unsubscribe`로 본다.
 * `staleKey`이면 서버가 키를 바꾸기 전에 만든 구독이라, 해지하면 새 키로 만든 `renewed`가 대신한다.
 * 해지는 그 구독이 지금 구독일 때만 비운다(이미 해지한 구독을 다시 해지해도 새 구독은 그대로다).
 */
export function installBrowserWithSubscription(options: { staleKey?: boolean } = {}) {
  const subscription = fakeSubscription(ENDPOINT, options.staleKey === true ? [1, 2] : [251, 255])
  const renewed = fakeSubscription(NEW_ENDPOINT, [251, 255])
  let current: typeof subscription | null = subscription
  for (const each of [subscription, renewed]) {
    each.unsubscribe.mockImplementation(() => {
      if (current === each) {
        current = null
      }

      return Promise.resolve(true)
    })
  }
  // 어떤 키로 구독했는지 `mock.calls`로 볼 수 있게 인자의 모양을 적어 둔다.
  const subscribe = vi.fn<(options: { applicationServerKey: Uint8Array }) => Promise<unknown>>(
    () => {
      current = renewed
      return Promise.resolve(renewed)
    },
  )
  const registration = {
    pushManager: { getSubscription: () => Promise.resolve(current), subscribe },
  }

  vi.stubGlobal('PushManager', class {})
  vi.stubGlobal('Notification', {
    permission: 'granted',
    requestPermission: vi.fn().mockResolvedValue('granted'),
  })
  Object.defineProperty(navigator, 'serviceWorker', {
    configurable: true,
    value: {
      getRegistration: () => Promise.resolve(registration),
      ready: Promise.resolve(registration),
    },
  })

  return { subscription, renewed, subscribe }
}

type LockMode = 'shared' | 'exclusive'

/**
 * Web Locks(`navigator.locks`)의 승인 규칙만 따라 하는 흉내.
 * 배타 락은 같은 이름의 락이 하나도 없을 때, 공유 락은 배타 락이 없을 때 승인되고, 먼저 줄을 선
 * 요청이 있으면 뒤 요청은 순서를 기다린다. 락은 콜백이 끝나면 풀린다.
 * 같은 `navigator`를 쓰는 탭들이 하나를 나눠 쓰는 것처럼 한 번 만들어 모든 탭이 쓴다.
 */
export function createFakeLocks() {
  const held = new Map<string, LockMode[]>()
  const queues = new Map<string, Array<{ mode: LockMode; start: () => void }>>()

  function grantable(name: string, mode: LockMode): boolean {
    const holders = held.get(name) ?? []

    return mode === 'exclusive' ? holders.length === 0 : !holders.includes('exclusive')
  }

  function drain(name: string): void {
    const queue = queues.get(name) ?? []
    while (queue[0] !== undefined && grantable(name, queue[0].mode)) {
      queue.shift()?.start()
    }
  }

  return {
    request<T>(name: string, options: { mode: LockMode }, callback: () => Promise<T>): Promise<T> {
      return new Promise<T>((resolve, reject) => {
        const queue = queues.get(name) ?? []
        queues.set(name, queue)
        queue.push({
          mode: options.mode,
          start: () => {
            const holders = held.get(name) ?? []
            held.set(name, holders)
            holders.push(options.mode)
            void Promise.resolve()
              .then(callback)
              .then(resolve, reject)
              .finally(() => {
                holders.splice(holders.indexOf(options.mode), 1)
                drain(name)
              })
          },
        })
        drain(name)
      })
    },
  }
}
