import { watch } from 'vue'
import type { Router } from 'vue-router'

import { i18n } from '@/app/i18n'
import {
  removePushSubscriptionFromServer,
  syncPushSubscription,
} from '@/features/notification/model/pushSubscription'
import { setBeforeSignOutHook } from '@/shared/api/sessionSignOut'

/**
 * 이 기기의 푸시 구독이 로그인한 계정·현재 언어와 어긋나지 않게 이어 둔다.
 *
 * - **로그인한 뒤 처음 보호 화면에 들어올 때** 한 번 서버에 다시 등록한다. 같은 기기에 다른
 *   계정이 로그인하면 서버가 구독의 소유자를 새 계정으로 옮긴다. 로그인은 OAuth 리다이렉트라
 *   페이지가 새로 뜨므로, 계정이 바뀌면 이 한 번이 반드시 다시 돈다.
 * - **화면 언어를 바꾸면** 다시 등록한다. 서버가 푸시 본문에 실어 보내는 언어가 구독에 저장돼
 *   있어서, 그대로 두면 화면은 일본어인데 알림은 예전 언어로 온다.
 * - **로그아웃하기 전에** 서버에서 지운다. 인증이 살아 있을 때만 가능하다. 실패해도 로그아웃은
 *   막지 않는다(`requestSignOut`가 보장한다).
 *
 * 브라우저 구독은 지우지 않는다. 같은 기기로 다시 로그인하면 권한을 다시 묻지 않고 새 계정으로
 * 이어진다. 구독이 없거나 푸시를 쓸 수 없는 환경에서는 모두 아무 일도 하지 않는다.
 *
 * @returns 이 연결을 모두 걷어내는 함수
 */
export function registerPushSubscriptionLifecycle(router: Router): () => void {
  let syncedAfterSignIn = false

  const isSignedIn = () => router.currentRoute.value.meta.requiresAuth === true
  const sync = () => {
    void syncPushSubscription(i18n.global.locale.value)
  }

  const removeAfterEach = router.afterEach((to) => {
    if (to.meta.requiresAuth !== true || syncedAfterSignIn) {
      return
    }

    syncedAfterSignIn = true
    sync()
  })

  const stopWatchingLocale = watch(i18n.global.locale, () => {
    // 로그인 화면에서 언어를 바꿀 때는 서버에 알릴 계정이 없다.
    if (isSignedIn()) {
      sync()
    }
  })

  setBeforeSignOutHook(removePushSubscriptionFromServer)

  return () => {
    removeAfterEach()
    stopWatchingLocale()
    setBeforeSignOutHook(undefined)
  }
}
