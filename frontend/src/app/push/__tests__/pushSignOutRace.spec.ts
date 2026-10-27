import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'

import { i18n } from '@/app/i18n'

/**
 * 라이프사이클·구독 모델·로그아웃 함수를 실제로 연결하고, 서버와 httpClient만 흉내 낸다.
 * 언어를 바꿔 시작한 구독 PUT이 끝나기 전에 로그아웃하는 순서를 다룬다.
 */

const ENDPOINT = 'https://fcm.googleapis.com/fcm/send/abc'

/** 서버에 저장된 이 기기의 구독. PUT이 넣고 DELETE가 지운다. */
const serverRows = new Set<string>()
const savePushSubscription = vi.fn()
const deletePushSubscription = vi.fn()
const post = vi.fn()

vi.mock('@/features/notification/api/pushSubscriptionApi', () => ({
  savePushSubscription: (...args: unknown[]) => savePushSubscription(...args),
  deletePushSubscription: (...args: unknown[]) => deletePushSubscription(...args),
}))

vi.mock('@/shared/api/httpClient', () => ({
  httpClient: { post: (...args: unknown[]) => post(...args) },
}))

const { registerPushSubscriptionLifecycle } = await import('../pushSubscriptionLifecycle')
const { requestSignOut } = await import('@/shared/api/sessionSignOut')

function createTestRouter(): Router {
  const page = { template: '<div />' }

  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/sign-in', name: 'sign-in', component: page },
      { path: '/wallet', name: 'wallet', component: page, meta: { requiresAuth: true } },
    ],
  })
}

/** 브라우저에 이 기기의 구독이 하나 있다. 로그아웃 뒤에도 남아 있는지 `unsubscribe`로 본다. */
function installBrowserWithSubscription() {
  const subscription = {
    endpoint: ENDPOINT,
    toJSON: () => ({ endpoint: ENDPOINT, keys: { p256dh: 'P256DH', auth: 'AUTH' } }),
    unsubscribe: vi.fn().mockResolvedValue(true),
  }

  vi.stubGlobal('PushManager', class {})
  vi.stubGlobal('Notification', { permission: 'granted' })
  Object.defineProperty(navigator, 'serviceWorker', {
    configurable: true,
    value: {
      getRegistration: () =>
        Promise.resolve({ pushManager: { getSubscription: () => Promise.resolve(subscription) } }),
    },
  })

  return subscription
}

/** 서버에 저장되는 PUT. 호출마다 직접 끝낼 때까지 서버에 닿지 않은 채로 둔다. */
function holdSaves() {
  const releases: Array<() => void> = []
  savePushSubscription.mockImplementation(
    (body: { endpoint: string }) =>
      new Promise<void>((resolve) => {
        releases.push(() => {
          serverRows.add(body.endpoint)
          resolve()
        })
      }),
  )

  return releases
}

/** 로그인 뒤 처음 보호 화면으로 들어가 그 동기화를 끝까지 마친다. */
async function signInAndFinishFirstSync(router: Router, releases: Array<() => void>) {
  await router.push('/wallet')
  await vi.waitFor(() => expect(releases).toHaveLength(1))
  releases[0]?.()
  await vi.waitFor(() => expect(serverRows.has(ENDPOINT)).toBe(true))
}

let stop: () => void = () => undefined

beforeEach(() => {
  serverRows.clear()
  savePushSubscription.mockReset()
  deletePushSubscription.mockReset()
  deletePushSubscription.mockImplementation((endpoint: string) => {
    serverRows.delete(endpoint)
    return Promise.resolve()
  })
  post.mockReset()
  post.mockResolvedValue(undefined)
  localStorage.clear()
  i18n.global.locale.value = 'en'
})

afterEach(() => {
  stop()
  vi.useRealTimers()
  vi.unstubAllGlobals()
  Reflect.deleteProperty(navigator, 'serviceWorker')
})

describe('signing out while a language-change sync is still being saved', () => {
  it('leaves no subscription on the server, because the delete waits for the save', async () => {
    const subscription = installBrowserWithSubscription()
    const releases = holdSaves()
    const router = createTestRouter()
    stop = registerPushSubscriptionLifecycle(router)

    // 로그인 뒤 처음 보호 화면의 동기화는 끝난다.
    await signInAndFinishFirstSync(router, releases)

    // 언어를 바꾼 직후, 그 PUT이 서버에 닿기 전에 로그아웃한다.
    i18n.global.locale.value = 'ja'
    await nextTick()
    await vi.waitFor(() => expect(savePushSubscription).toHaveBeenCalledTimes(2))
    const signOut = requestSignOut()

    await new Promise((resolve) => setTimeout(resolve, 0))
    expect(deletePushSubscription).not.toHaveBeenCalled()
    expect(post).not.toHaveBeenCalled()

    releases[1]?.()
    await signOut

    expect(serverRows.size).toBe(0)
    expect(post).toHaveBeenCalledWith('/api/v1/auth/logout')
    // 서버 삭제가 성공했으니 브라우저 구독은 그대로 둔다. 다음 로그인에서 권한을 다시 묻지 않는다.
    expect(subscription.unsubscribe).not.toHaveBeenCalled()
  })

  it('does not start another sync once the sign-out has begun', async () => {
    installBrowserWithSubscription()
    const releases = holdSaves()
    const router = createTestRouter()
    stop = registerPushSubscriptionLifecycle(router)

    await signInAndFinishFirstSync(router, releases)
    i18n.global.locale.value = 'ja'
    await nextTick()
    await vi.waitFor(() => expect(savePushSubscription).toHaveBeenCalledTimes(2))

    const signOut = requestSignOut()
    i18n.global.locale.value = 'vi'
    await nextTick()
    releases[1]?.()
    await signOut

    expect(savePushSubscription).toHaveBeenCalledTimes(2)
    expect(serverRows.size).toBe(0)
  })

  /**
   * 저장이 끝나지 않으면 삭제는 시간 안에 끝나지 못한다. 이때는 브라우저 구독을 대신 끊어 서버에
   * 늦게 저장되는 행이 있어도 이 기기로는 푸시가 도착하지 않게 한다.
   */
  it('cancels the browser subscription when the save does not finish in time', async () => {
    vi.useFakeTimers()
    const subscription = installBrowserWithSubscription()
    const releases = holdSaves()
    const router = createTestRouter()
    stop = registerPushSubscriptionLifecycle(router)

    await signInAndFinishFirstSync(router, releases)
    i18n.global.locale.value = 'ja'
    await nextTick()
    await vi.waitFor(() => expect(savePushSubscription).toHaveBeenCalledTimes(2))

    const signOut = requestSignOut()
    await vi.advanceTimersByTimeAsync(3_000)
    await signOut

    expect(subscription.unsubscribe).toHaveBeenCalledOnce()
    expect(post).toHaveBeenCalledWith('/api/v1/auth/logout')

    // 멈춰 있던 저장이 나중에 끝나도 뒤처리가 남지 않게 마무리한다.
    releases[1]?.()
    await vi.runAllTimersAsync()
  })
})
