/**
 * 화면 스냅샷.
 *
 * 실행 중인 개발 서버에 붙어 아래 `SCREENS`의 화면을 찍는다. PR에 붙일 이미지를 만드는
 * 용도이며 테스트가 아니다. 통과·실패를 판정하지 않는다.
 *
 * 코드와 단위 테스트로는 확인할 수 없는 것들이 있다. 폰트 폴백이 깨져 CJK가 두부(□□□)로
 * 나와도 DOM 단언은 통과하고, 여백과 대비는 클래스 이름을 봐서는 알 수 없다.
 *
 * 사용법:
 *   0) pnpm --filter @na-wa/frontend exec playwright install chromium  (최초 1회)
 *   1) pnpm dev
 *   2) pnpm --filter @na-wa/frontend screenshot
 *   3) 생긴 PNG를 PR 본문에 끌어다 놓는다
 *
 * 환경변수:
 *   SCREENSHOT_BASE  대상 주소 (기본 http://localhost:5173)
 *   SCREENSHOT_OUT   출력 경로 (기본 frontend/screenshots)
 *
 * 출력물은 저장소에 커밋하지 않는다. `.gitignore`에 들어 있다.
 */
import { mkdir } from 'node:fs/promises'
import { relative } from 'node:path'

import { chromium } from '@playwright/test'

/**
 * 시안 기준 폭이다. 리뷰어가 시안과 나란히 놓고 볼 수 있도록 고정한다.
 * 2배율로 찍어야 GitHub에서 축소돼도 글자가 뭉개지지 않는다.
 */
const VIEWPORT = { width: 390, height: 844 }
const SCALE = 2

/**
 * 끝 슬래시를 제거한 뒤 화면 경로와 결합한다.
 *
 * `http://localhost:5173/`처럼 주소가 슬래시로 끝나면 `//sign-in`이 만들어진다. 개발
 * 서버는 SPA fallback으로 어떤 경로에나 200을 돌려주므로 이동은 성공하고, 라우터만
 * 매칭에 실패해 모든 화면이 NotFound로 찍힌다. 실패가 성공처럼 보이는 것을 막는다.
 */
const BASE = (process.env.SCREENSHOT_BASE ?? 'http://localhost:5173').replace(/\/+$/, '')
const OUT = process.env.SCREENSHOT_OUT ?? 'screenshots'

/**
 * 찍을 화면.
 *
 * 작업 중인 화면을 여기에 추가한다. `prepare`는 진입한 뒤 실행되며, 바텀시트를 연 상태처럼
 * 조작이 필요한 화면을 찍을 때 쓴다.
 *
 * 머무르지 않고 스스로 빠져나가는 상태는 넣지 않는다. 예를 들어 `/auth/callback`의 대기
 * 화면은 세션 조회가 끝나는 즉시 `router.replace`로 넘어가므로, 목록에 넣으면 대기 화면이
 * 아니라 이동한 뒤의 화면이 조용히 찍힌다. 그런 상태를 찍으려면 응답을 붙잡아야 하는데
 * 그것은 리뷰용 이미지가 아니라 검증 도구의 일이다.
 *
 * @type {{ name: string, path: string, prepare?: (page: import('@playwright/test').Page) => Promise<void> }[]}
 */
const SCREENS = [
  { name: '01-sign-in', path: '/sign-in' },
  { name: '02-callback-failed', path: '/auth/callback?error=AUTH-014' },
  { name: '03-not-found', path: '/no-such-page' },

  // 조작이 필요한 상태는 이렇게 찍는다.
  //
  // {
  //   name: '04-sign-in-language',
  //   path: '/sign-in',
  //   prepare: async (page) => {
  //     await page.getByLabel('Change screen language').click()
  //     await page.waitForSelector('[role="dialog"]')
  //   },
  // },
]

async function assertServerIsUp() {
  try {
    await fetch(BASE, { signal: AbortSignal.timeout(3000) })
  } catch {
    console.error(
      `개발 서버에 붙지 못했다: ${BASE}\n` +
        '  먼저 `pnpm dev`로 서버를 띄운다.\n' +
        '  다른 주소라면 SCREENSHOT_BASE로 지정한다.',
    )
    process.exit(1)
  }
}

async function launchBrowser() {
  try {
    return await chromium.launch()
  } catch (error) {
    // Playwright가 안내하는 `npx playwright install`은 이 워크스페이스에서 맞지 않는다.
    console.error(
      `Chromium을 띄우지 못했다.\n` +
        '  브라우저가 없다면 최초 1회 설치한다.\n' +
        '  pnpm --filter @na-wa/frontend exec playwright install chromium\n' +
        `  원본 오류: ${error.message}`,
    )
    process.exit(1)
  }
}

await assertServerIsUp()
await mkdir(OUT, { recursive: true })

const browser = await launchBrowser()
const context = await browser.newContext({
  viewport: VIEWPORT,
  deviceScaleFactor: SCALE,
  locale: 'en-US',
})

let failed = 0

for (const screen of SCREENS) {
  const page = await context.newPage()

  try {
    // 화면 경로 앞의 슬래시도 함께 정규화해 중복 슬래시를 만들지 않는다.
    await page.goto(`${BASE}/${screen.path.replace(/^\/+/, '')}`, { waitUntil: 'networkidle' })
    await screen.prepare?.(page)

    // 웹폰트와 전환이 자리를 잡을 시간을 준다. 없으면 폴백 폰트가 찍히는 경우가 있다.
    await page.waitForTimeout(400)
    await page.screenshot({ path: `${OUT}/${screen.name}.png` })

    console.log(`  ✓ ${screen.name}.png  ← ${screen.path}`)
  } catch (error) {
    // 한 화면이 실패해도 나머지는 찍되, 실패했다는 사실은 종료 코드로 남긴다.
    failed += 1
    console.error(`  ✗ ${screen.name}  ← ${screen.path}\n    ${error.message}`)
  } finally {
    await page.close()
  }
}

await browser.close()

// 찍히지 않은 화면을 못 보고 넘어가지 않도록 실패를 종료 코드로 알린다. 이때는 산출물이
// 온전하지 않으므로 PR에 붙이라는 안내도 하지 않는다.
if (failed > 0) {
  console.error(`\n${failed}/${SCREENS.length}개 화면을 찍지 못했다. 위 오류를 확인한다.`)
  process.exit(1)
}

console.log(`\n${relative(process.cwd(), OUT) || OUT}/ 에 저장했다. PR 본문에 끌어다 놓는다.`)
