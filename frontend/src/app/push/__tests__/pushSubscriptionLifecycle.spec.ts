import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'

import { i18n } from '@/app/i18n'

const syncPushSubscription = vi.fn()
const removePushSubscriptionFromServer = vi.fn()
const dropBrowserPushSubscription = vi.fn()
const finishSignOutCleanup = vi.fn()
const setBeforeSignOutHook = vi.fn()

vi.mock('@/features/notification/model/pushSubscription', () => ({
  syncPushSubscription: (language: string) => syncPushSubscription(language),
  removePushSubscriptionFromServer: () => removePushSubscriptionFromServer(),
  dropBrowserPushSubscription: () => dropBrowserPushSubscription(),
  finishSignOutCleanup: () => finishSignOutCleanup(),
}))

vi.mock('@/shared/api/sessionSignOut', () => ({
  setBeforeSignOutHook: (hook: unknown, fallback?: unknown, after?: unknown) =>
    setBeforeSignOutHook(hook, fallback, after),
}))

const { registerPushSubscriptionLifecycle } = await import('../pushSubscriptionLifecycle')

function createTestRouter(): Router {
  const page = { template: '<div />' }

  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/sign-in', name: 'sign-in', component: page },
      { path: '/wallet', name: 'wallet', component: page, meta: { requiresAuth: true } },
      {
        path: '/notifications',
        name: 'notifications',
        component: page,
        meta: { requiresAuth: true },
      },
    ],
  })
}

let stop: () => void = () => undefined

beforeEach(() => {
  syncPushSubscription.mockReset()
  removePushSubscriptionFromServer.mockReset()
  dropBrowserPushSubscription.mockReset()
  finishSignOutCleanup.mockReset()
  setBeforeSignOutHook.mockReset()
  i18n.global.locale.value = 'en'
})

afterEach(() => {
  stop()
})

describe('registerPushSubscriptionLifecycle', () => {
  it('re-registers the device once, with the screen language, on the first protected screen', async () => {
    i18n.global.locale.value = 'vi'
    const router = createTestRouter()
    stop = registerPushSubscriptionLifecycle(router)

    await router.push('/sign-in')
    expect(syncPushSubscription).not.toHaveBeenCalled()

    await router.push('/wallet')
    expect(syncPushSubscription).toHaveBeenCalledExactlyOnceWith('vi')

    // 보호 화면을 오가도 로그인 뒤 한 번이면 충분하다.
    await router.push('/notifications')
    await router.push('/wallet')
    expect(syncPushSubscription).toHaveBeenCalledOnce()
  })

  it('re-registers when the screen language changes while signed in', async () => {
    const router = createTestRouter()
    stop = registerPushSubscriptionLifecycle(router)
    await router.push('/wallet')
    syncPushSubscription.mockClear()

    i18n.global.locale.value = 'ja'
    await nextTick()

    expect(syncPushSubscription).toHaveBeenCalledExactlyOnceWith('ja')
  })

  /** 로그인 화면에서 언어를 고르는 사용자는 아직 서버에 알릴 계정이 없다. */
  it('ignores a language change on a screen that is not signed in', async () => {
    const router = createTestRouter()
    stop = registerPushSubscriptionLifecycle(router)
    await router.push('/sign-in')

    i18n.global.locale.value = 'ja'
    await nextTick()

    expect(syncPushSubscription).not.toHaveBeenCalled()
  })

  it('removes this device from the server before sign-out', () => {
    stop = registerPushSubscriptionLifecycle(createTestRouter())

    const hook = setBeforeSignOutHook.mock.calls[0]?.[0] as () => void
    hook()

    expect(removePushSubscriptionFromServer).toHaveBeenCalledOnce()
  })

  /** 서버 삭제가 끝나지 못해도 로그아웃한 계정의 알림이 이 기기에 남지 않게 한다. */
  it('cuts only the browser subscription when the server removal cannot finish', () => {
    stop = registerPushSubscriptionLifecycle(createTestRouter())

    const fallback = setBeforeSignOutHook.mock.calls[0]?.[1] as () => void
    fallback()

    expect(dropBrowserPushSubscription).toHaveBeenCalledOnce()
    expect(removePushSubscriptionFromServer).not.toHaveBeenCalled()
  })

  /** 삭제 뒤에도 다른 탭의 등록을 막아 두던 것은 로그아웃 요청이 끝난 뒤에야 놓는다. */
  it('finishes the cleanup once the logout request is over', () => {
    stop = registerPushSubscriptionLifecycle(createTestRouter())

    const after = setBeforeSignOutHook.mock.calls[0]?.[2] as () => void
    after()

    expect(finishSignOutCleanup).toHaveBeenCalledOnce()
    expect(removePushSubscriptionFromServer).not.toHaveBeenCalled()
  })

  /**
   * 정리가 진행 중인 등록이 끝나기를 기다리는 동안 새 PUT이 끼어들면 기다림이 끝나지 않고, 삭제 뒤에
   * 저장되면 구독이 되살아난다. 로그아웃 정리가 시작되면 새 동기화를 시작하지 않는다.
   */
  it('stops starting new syncs once the sign-out cleanup has begun', async () => {
    const router = createTestRouter()
    stop = registerPushSubscriptionLifecycle(router)
    await router.push('/wallet')
    expect(syncPushSubscription).toHaveBeenCalledOnce()

    const hook = setBeforeSignOutHook.mock.calls[0]?.[0] as () => void
    hook()
    i18n.global.locale.value = 'ja'
    await nextTick()
    await router.push('/notifications')

    expect(syncPushSubscription).toHaveBeenCalledOnce()
  })

  it('does not sync on the first protected screen when the cleanup began before it', async () => {
    const router = createTestRouter()
    stop = registerPushSubscriptionLifecycle(router)

    const hook = setBeforeSignOutHook.mock.calls[0]?.[0] as () => void
    hook()
    await router.push('/wallet')

    expect(syncPushSubscription).not.toHaveBeenCalled()
  })

  it('takes everything back when stopped', async () => {
    const router = createTestRouter()
    stop = registerPushSubscriptionLifecycle(router)
    stop()

    await router.push('/wallet')
    i18n.global.locale.value = 'ja'
    await nextTick()

    expect(syncPushSubscription).not.toHaveBeenCalled()
    expect(setBeforeSignOutHook).toHaveBeenLastCalledWith(undefined, undefined, undefined)
  })
})
