import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import type { Router } from 'vue-router'

import { i18n } from '@/app/i18n'

import {
  ENDPOINT,
  NEW_ENDPOINT,
  SERVER_KEY,
  createFakeLocks,
  createTestRouter,
  installBrowserWithSubscription,
} from './fakePushBrowser'

/**
 * 라이프사이클·구독 모델·로그아웃 함수를 실제로 연결하고, 서버와 httpClient만 흉내 낸다.
 * 구독 PUT이 끝나기 전에 로그아웃하는 순서를 같은 탭과 다른 탭에서 다룬다.
 */

/** 서버에 저장된 이 기기의 구독. PUT이 넣고 DELETE가 지운다. */
const serverRows = new Set<string>()
const savePushSubscription = vi.fn()
const deletePushSubscription = vi.fn()
const fetchPushPublicKey = vi.fn()
const post = vi.fn()

vi.mock('@/features/notification/api/pushSubscriptionApi', () => ({
  savePushSubscription: (...args: unknown[]) => savePushSubscription(...args),
  deletePushSubscription: (...args: unknown[]) => deletePushSubscription(...args),
  fetchPushPublicKey: (...args: unknown[]) => fetchPushPublicKey(...args),
}))

vi.mock('@/shared/api/httpClient', () => ({
  httpClient: { post: (...args: unknown[]) => post(...args) },
}))

const { registerPushSubscriptionLifecycle } = await import('../pushSubscriptionLifecycle')
const { requestSignOut } = await import('@/shared/api/sessionSignOut')

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
  fetchPushPublicKey.mockReset()
  fetchPushPublicKey.mockResolvedValue(SERVER_KEY)
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
  Reflect.deleteProperty(navigator, 'locks')
})

/** 같은 출처의 탭들이 락을 나눠 쓰는 브라우저. 한 번 설치하면 모든 탭이 쓴다. */
function installLocks(): void {
  Object.defineProperty(navigator, 'locks', { configurable: true, value: createFakeLocks() })
}

/**
 * 다른 탭을 연다. 탭마다 모듈 상태(진행 중인 등록 목록)는 따로이고, 브라우저의 구독·락·저장소와
 * 서버는 같은 것을 쓴다.
 */
async function openAnotherTab() {
  vi.resetModules()

  return import('@/features/notification/model/pushSubscription')
}

describe('signing out while a language-change sync is still being saved', () => {
  it('leaves no subscription on the server, because the delete waits for the save', async () => {
    const { subscription } = installBrowserWithSubscription()
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
    const { subscription } = installBrowserWithSubscription()
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

describe('signing out while a subscription renewed for a changed server key is being saved', () => {
  /**
   * 키가 바뀐 서버에서 앱을 열면 예전 구독의 서버 행을 지우고 새로 구독해 등록한다. 그 등록이 끝나기
   * 전에 로그아웃해도, 지워지는 것은 예전 주소가 아니라 새로 등록되는 주소여야 한다.
   */
  it('deletes the renewed subscription once it has been saved', async () => {
    const { renewed } = installBrowserWithSubscription({ staleKey: true })
    const releases = holdSaves()
    const router = createTestRouter()
    stop = registerPushSubscriptionLifecycle(router)

    await router.push('/wallet')
    await vi.waitFor(() => expect(releases).toHaveLength(1))
    const signOut = requestSignOut()

    await new Promise((resolve) => setTimeout(resolve, 0))
    expect(deletePushSubscription.mock.calls).toEqual([[ENDPOINT]])

    releases[0]?.()
    await signOut

    expect(deletePushSubscription.mock.calls).toEqual([[ENDPOINT], [NEW_ENDPOINT]])
    expect(serverRows.size).toBe(0)
    expect(renewed.unsubscribe).not.toHaveBeenCalled()
  })

  /**
   * 예전 행의 삭제가 3초 안에 끝나지 않으면 로그아웃은 기다림을 놓고 브라우저 구독을 끊는다. 놓인
   * 갱신은 취소되지 않으므로, 삭제가 나중에 끝났을 때 새 구독을 만들어 저장하면 로그아웃한 계정의 알림이
   * 이 기기로 다시 온다.
   */
  it('does not create or save a new subscription once the sign-out gave up waiting', async () => {
    vi.useFakeTimers()
    const { subscription, renewed, subscribe } = installBrowserWithSubscription({ staleKey: true })
    let finishStaleDelete: () => void = () => undefined
    deletePushSubscription.mockImplementation(
      () =>
        new Promise<void>((resolve) => {
          finishStaleDelete = resolve
        }),
    )
    const router = createTestRouter()
    stop = registerPushSubscriptionLifecycle(router)

    await router.push('/wallet')
    await vi.waitFor(() => expect(deletePushSubscription).toHaveBeenCalledWith(ENDPOINT))
    const signOut = requestSignOut()
    await vi.advanceTimersByTimeAsync(3_000)
    await signOut

    expect(post).toHaveBeenCalledWith('/api/v1/auth/logout')
    expect(subscription.unsubscribe).toHaveBeenCalledOnce()

    // 멈춰 있던 삭제가 로그아웃이 끝난 뒤에 응답한다.
    finishStaleDelete()
    await vi.runAllTimersAsync()

    expect(subscribe).not.toHaveBeenCalled()
    expect(savePushSubscription).not.toHaveBeenCalled()
    expect(serverRows.size).toBe(0)
    expect(renewed.unsubscribe).not.toHaveBeenCalled()
  })
})

describe('signing out while another tab is still working on a registration', () => {
  /**
   * 진행 중인 등록의 목록은 탭마다 따로라서, 다른 탭에서 이미 시작한 PUT은 이 탭의 로그아웃이 볼 수
   * 없다. 그대로 지우면 그 PUT이 삭제 뒤에 저장되어 로그아웃한 계정의 구독이 되살아난다.
   */
  it('deletes only after the other tab has finished saving', async () => {
    installLocks()
    const { subscription } = installBrowserWithSubscription()
    const releases = holdSaves()
    const router = createTestRouter()
    stop = registerPushSubscriptionLifecycle(router)
    await signInAndFinishFirstSync(router, releases)

    const otherTab = await openAnotherTab()
    void otherTab.syncPushSubscription('ja')
    await vi.waitFor(() => expect(savePushSubscription).toHaveBeenCalledTimes(2))
    const signOut = requestSignOut()

    await new Promise((resolve) => setTimeout(resolve, 0))
    expect(deletePushSubscription).not.toHaveBeenCalled()
    expect(post).not.toHaveBeenCalled()

    releases[1]?.()
    await signOut

    expect(serverRows.size).toBe(0)
    expect(post).toHaveBeenCalledWith('/api/v1/auth/logout')
    // 서버 삭제가 성공했으니 브라우저 구독은 그대로 둔다.
    expect(subscription.unsubscribe).not.toHaveBeenCalled()
  })

  /**
   * 이 탭의 로그아웃이 기다림을 놓고 브라우저 구독을 끊었다면, 다른 탭에서 멈춰 있던 갱신도 그 뒤에는
   * 구독을 새로 만들지 못해야 한다. 취소 신호도 탭 사이에서 공유한다.
   */
  it('keeps the other tab from creating a subscription after this tab gave up waiting', async () => {
    installLocks()
    const { subscription, subscribe } = installBrowserWithSubscription({ staleKey: true })
    const otherTab = await openAnotherTab()
    vi.useFakeTimers()
    let finishStaleDelete: () => void = () => undefined
    deletePushSubscription.mockImplementationOnce(
      () =>
        new Promise<void>((resolve) => {
          finishStaleDelete = resolve
        }),
    )
    stop = registerPushSubscriptionLifecycle(createTestRouter())

    void otherTab.syncPushSubscription('ja')
    await vi.waitFor(() => expect(deletePushSubscription).toHaveBeenCalledWith(ENDPOINT))
    const signOut = requestSignOut()
    await vi.advanceTimersByTimeAsync(3_000)
    await signOut

    expect(post).toHaveBeenCalledWith('/api/v1/auth/logout')
    expect(subscription.unsubscribe).toHaveBeenCalledOnce()

    // 다른 탭에서 멈춰 있던 삭제가 로그아웃이 끝난 뒤에 응답한다.
    finishStaleDelete()
    await vi.runAllTimersAsync()

    expect(subscribe).not.toHaveBeenCalled()
    expect(savePushSubscription).not.toHaveBeenCalled()
  })
})
