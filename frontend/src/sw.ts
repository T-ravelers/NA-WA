import { clientsClaim } from 'workbox-core'
import {
  cleanupOutdatedCaches,
  createHandlerBoundToURL,
  precacheAndRoute,
  type PrecacheEntry,
} from 'workbox-precaching'
import { NavigationRoute, registerRoute } from 'workbox-routing'

import {
  parsePushPayload,
  PUSH_RECEIVED_MESSAGE_TYPE,
  pushNotificationContent,
} from '@/features/notification/model/pushNotification'

// 빌드가 `self.__WB_MANIFEST` 자리에 precache 목록을 채워 넣는다.
declare const self: ServiceWorkerGlobalScope & {
  __WB_MANIFEST: Array<PrecacheEntry | string>
}

/**
 * 앱 서비스 워커.
 *
 * 예전에 `generateSW`가 만들던 것과 같은 동작을 직접 적은 것이다 — 새 버전이 설치되면 기다리지
 * 않고 바로 이어받고(`autoUpdate`), 낡은 precache는 치우고, 주소로 직접 들어오거나 새로고침한
 * 화면 요청은 앱 셸(`index.html`)로 돌려 오프라인에서도 라우팅이 동작하게 한다.
 *
 * 캐시 경계는 `frontend/AGENTS.md`의 「PWA 캐시 경계」가 정본이다. 앱 셸과 정적 자원만
 * precache하고(`vite.config.ts`의 `injectManifest.globPatterns`), runtime cache는 두지 않는다.
 * 그래서 이 파일에는 API 요청을 가로채는 `registerRoute`가 없다. 인증·정산 응답을 캐시에 넣는
 * 코드를 여기에 추가하지 마세요.
 */
void self.skipWaiting()
clientsClaim()

precacheAndRoute(self.__WB_MANIFEST)
cleanupOutdatedCaches()

registerRoute(new NavigationRoute(createHandlerBoundToURL('index.html')))

/**
 * 웹 푸시.
 *
 * 앱이 닫혀 있든 열려 있든 푸시가 오면 기기 알림을 띄운다. 눈에 보이는 알림 없이 푸시를
 * 넘기면 iOS는 구독을 취소한다. 열린 창에는 따로 알려서 알림·정산 쿼리를 바로 다시 받게 한다.
 * 알림을 눌러도 읽음 처리는 하지 않는다 — 화면에 들어온 것만으로는 읽음이 아니다.
 */
self.addEventListener('push', (event) => {
  event.waitUntil(showPush(event.data?.text() ?? null))
})

async function showPush(raw: string | null): Promise<void> {
  const content = pushNotificationContent(parsePushPayload(raw))

  await self.registration.showNotification(content.title, {
    body: content.body,
    lang: content.lang,
    icon: '/pwa-192x192.png',
    data: { url: content.url },
  })

  const windows = await self.clients.matchAll({ type: 'window', includeUncontrolled: true })
  for (const client of windows) {
    client.postMessage({ type: PUSH_RECEIVED_MESSAGE_TYPE })
  }
}

self.addEventListener('notificationclick', (event) => {
  event.notification.close()

  const data: unknown = event.notification.data
  const path = typeof data === 'object' && data !== null && 'url' in data ? data.url : undefined
  event.waitUntil(openTarget(typeof path === 'string' ? path : '/'))
})

/** 열린 창이 있으면 그 창을 이동시켜 앞으로 가져오고, 없으면 새 창을 연다. */
async function openTarget(path: string): Promise<void> {
  const target = new URL(path, self.location.origin)
  // 알림 데이터는 우리가 만든 것이지만 같은 출처가 아닌 주소는 열지 않는다.
  const href = target.origin === self.location.origin ? target.href : self.location.origin

  const windows = await self.clients.matchAll({ type: 'window', includeUncontrolled: true })
  const open = windows.find((client) => new URL(client.url).origin === self.location.origin)
  if (open) {
    try {
      await open.navigate(href)
    } catch {
      // 이동하지 못하는 창(서비스 워커의 제어 밖 등)이면 새 창으로 연다.
      await self.clients.openWindow(href)
      return
    }
    // 앞으로 가져오지 못해도(플랫폼 제한) 이동은 끝났으니 실패로 치지 않는다.
    await open.focus().catch(() => undefined)
    return
  }
  await self.clients.openWindow(href)
}
