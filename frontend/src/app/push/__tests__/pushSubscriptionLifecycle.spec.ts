import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'

import { i18n } from '@/app/i18n'

const syncPushSubscription = vi.fn()
const removePushSubscriptionFromServer = vi.fn()
const setBeforeSignOutHook = vi.fn()

vi.mock('@/features/notification/model/pushSubscription', () => ({
  syncPushSubscription: (language: string) => syncPushSubscription(language),
  removePushSubscriptionFromServer: () => removePushSubscriptionFromServer(),
}))

vi.mock('@/shared/api/sessionSignOut', () => ({
  setBeforeSignOutHook: (hook: unknown) => setBeforeSignOutHook(hook),
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

  it('takes everything back when stopped', async () => {
    const router = createTestRouter()
    stop = registerPushSubscriptionLifecycle(router)
    stop()

    await router.push('/wallet')
    i18n.global.locale.value = 'ja'
    await nextTick()

    expect(syncPushSubscription).not.toHaveBeenCalled()
    expect(setBeforeSignOutHook).toHaveBeenLastCalledWith(undefined)
  })
})
