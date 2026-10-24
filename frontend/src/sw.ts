import { clientsClaim } from 'workbox-core'
import {
  cleanupOutdatedCaches,
  createHandlerBoundToURL,
  precacheAndRoute,
  type PrecacheEntry,
} from 'workbox-precaching'
import { NavigationRoute, registerRoute } from 'workbox-routing'

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
