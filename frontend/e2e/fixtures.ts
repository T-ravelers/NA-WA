import { test as base } from '@playwright/test'

/**
 * 모든 스펙이 같은 전제 위에서 돌도록 브라우저 컨텍스트를 손본다.
 *
 * 두 가지를 한다.
 *
 * **서비스 워커를 등록하지 않는다.** 프로덕션 빌드에는 PWA 서비스 워커가 들어간다.
 * 서비스 워커가 요청을 가로채면 `page.route` 목킹이 WebKit에서 적용되지 않아, 같은
 * 스펙이 dev 서버에서는 통과하고 preview 빌드에서는 실패한다. 서비스 워커 자체의
 * 동작(오프라인 캐시, 갱신 알림)은 이 스위트의 검증 대상이 아니다.
 *
 * **목킹하지 않은 API 요청을 끊는다.** API 주소가 테스트 서버 자신이라
 * (`playwright.config.ts` 참고) 가로채지 않은 요청은 preview 서버의 SPA fallback에
 * 걸려 앱 셸 HTML이 `200 OK`로 돌아온다. 그러면 봉투를 벗기는 단계에서야 깨져
 * 원인을 찾기 어렵다 — `shared/config/apiBaseUrl.ts`가 경고하는 바로 그 실패다.
 * 아예 끊어 두면 스펙이 무엇을 목킹해야 하는지 즉시 드러난다.
 *
 * 이 catch-all은 컨텍스트 수준이고 스펙보다 먼저 등록된다. Playwright는 나중에
 * 등록된 라우트를 먼저 맞추고 page 수준을 context 수준보다 앞세우므로, 스펙이 건
 * 목킹이 항상 이긴다.
 */
export const test = base.extend({
  context: async ({ context }, use) => {
    await context.addInitScript(() => {
      if ('serviceWorker' in navigator) {
        navigator.serviceWorker.register = () =>
          new Promise<ServiceWorkerRegistration>(() => undefined)
      }
    })

    await context.route('**/api/**', async (route) => {
      await route.abort()
    })

    await use(context)
  },
})

export { expect } from '@playwright/test'
