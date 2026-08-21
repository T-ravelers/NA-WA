# NA-WA Frontend 개발 컨벤션

이 문서는 Vue 3, TypeScript와 Vite 기반 프론트엔드의 구조와 구현 규칙을 정의합니다.
새 기능을 만들 때 파일 위치, 상태 소유자, API 경계와 필요한 검증을 결정하는 기준으로
사용하세요.

아직 구현하지 않은 공통 기능은 이 문서의 경계를 먼저 따르고 별도 Issue에서
추가합니다.

## 기본 원칙

- TypeScript strict 설정과 ESLint 오류를 우회하지 않습니다.
- Vue 컴포넌트는 Composition API와 `<script setup lang="ts">`를 사용합니다.
- 사용자에게 보이는 문자열, API 계약과 상태 소유자를 코드에서 구분합니다.
- 모바일 화면부터 만들고 넓은 화면으로 점진적으로 확장합니다.
- 인증정보, 개인정보와 정산 데이터를 브라우저 저장소나 로그에 남기지 않습니다.

## 소스 위치 선택하기

```text
src/
├── app/          앱 진입, Router, Provider와 전역 스타일
├── features/     도메인 단위 기능
└── shared/       도메인 독립 공통 모듈
```

의존 방향은 `app → features → shared`입니다.

- `app`은 화면과 feature를 조합할 수 있습니다.
- `features`는 `shared`를 사용할 수 있습니다.
- 한 feature에서 다른 feature의 내부 구현을 직접 import하지 않습니다.
- `shared`는 `features`와 `app`을 import하지 않습니다.
- 여러 feature에서 사용하더라도 도메인 의미가 있다면 `shared`로 옮기지 않습니다.

ESLint는 alias와 상대경로를 모두 해석해 이 경계를 검사합니다. `import type`, re-export와
동적 `import()`도 같은 정책을 따릅니다. 같은 feature 내부의 상대경로 import는 허용하지만,
다른 feature의 구현이 필요하면 `app`에서 주입하거나 합의된 계약을 적절한 상위 계층에
둡니다. 경계를 피하려고 alias를 상대경로로 바꾸지 않습니다.

feature에는 필요한 폴더만 만드세요.

```text
features/journey/
├── api/          요청 함수와 DTO
├── components/   journey 전용 컴포넌트
├── composables/  journey 전용 조합 로직
├── i18n/         locale별 문구 (en.ts, ja.ts, …)
├── model/        Query Key, 상태와 도메인 타입
├── schemas/      입력 검증 스키마
├── views/        route가 가리키는 화면 컴포넌트
└── routes.ts     이 feature의 route 정의
```

### 도메인별 소유 폴더

여러 명이 동시에 작업하므로 담당 경계를 폴더로 나눕니다. 담당이 아닌 feature 폴더를
수정하지 않습니다.

| 폴더                   | 소유                            |
| ---------------------- | ------------------------------- |
| `features/auth/`       | 인증·온보딩 담당                |
| `features/explore/`    | 탐색 담당                       |
| `features/journey/`    | 여정 담당                       |
| `features/wallet/`     | 지갑 담당                       |
| `features/settlement/` | 정산 담당                       |
| `features/report/`     | 리포트 담당                     |
| `app/`, `shared/`      | 공통. 변경 전 합의가 필요합니다 |

`routes.ts`, `i18n/`, 디자인 토큰은 각각 자동 수집되거나 단일 파일로 고정되어 있어
feature를 추가할 때 공용 파일을 수정할 일이 없습니다. 공용 파일을 고쳐야 한다면 먼저
그 방법이 맞는지 확인하세요.

## 파일과 이름 정하기

| 대상              | 규칙                                                    | 예시                                     |
| ----------------- | ------------------------------------------------------- | ---------------------------------------- |
| Vue 컴포넌트      | PascalCase                                              | `JourneyCard.vue`                        |
| composable        | `use` + PascalCase 의미                                 | `useJourneyList.ts`                      |
| 일반 함수와 변수  | camelCase                                               | `formatTravelDate`                       |
| 타입과 인터페이스 | PascalCase                                              | `JourneySummary`                         |
| 상수              | UPPER_SNAKE_CASE                                        | `DEFAULT_PAGE_SIZE`                      |
| 테스트            | 원본 이름 + `.spec`                                     | `JourneyCard.spec.ts`                    |
| Pinia store       | 파일은 대상 이름, export는 `use` + PascalCase + `Store` | `savedEvents.ts`의 `useSavedEventsStore` |

- 컴포넌트 이름에는 두 단어 이상을 사용해 역할을 드러냅니다.
- `utils.ts`, `common.ts`, `data.ts`처럼 책임을 알 수 없는 파일을 만들지 않습니다.
- 폴더 공개 API가 필요할 때만 `index.ts`를 사용합니다.
- 무분별한 barrel export를 만들지 않습니다.
- `@` alias는 `frontend/src`를 가리킵니다.

## Vue 컴포넌트 작성하기

- props와 emits를 TypeScript로 선언합니다.
- template에서 컴포넌트 이름을 PascalCase로 작성합니다.
- `button`에 명시적인 `type`을 지정합니다.
- 파생 값에는 `computed`를 사용합니다.
- 부수효과에는 `watch` 또는 `watchEffect`를 사용합니다.
- DOM에는 필요한 경우에만 접근하고 template ref의 타입을 선언합니다.
- 화면 컴포넌트는 데이터 조합과 레이아웃에 집중합니다.
- 재사용하는 도메인 로직은 feature composable로 분리합니다.

```vue
<script setup lang="ts">
interface Props {
  title: string
  selected?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  selected: false,
})

const emit = defineEmits<{
  select: [title: string]
}>()

const select = () => {
  emit('select', props.title)
}
</script>

<template>
  <button
    type="button"
    :aria-pressed="selected"
    @click="select"
  >
    {{ title }}
  </button>
</template>
```

## 상태 소유자 선택하기

| 상태                                       | 소유자                        |
| ------------------------------------------ | ----------------------------- |
| API 조회, 캐시와 mutation                  | TanStack Vue Query            |
| 여러 화면이 공유하는 클라이언트 상태       | Pinia                         |
| 컴포넌트 또는 한 화면 내부 상태            | `ref`, `reactive`, `computed` |
| 새로고침하거나 공유해도 유지할 필터와 선택 | Vue Router query/params       |

- Vue Query 데이터를 Pinia에 복사하지 않습니다.
- Pinia에 API loading과 error 상태를 중복 저장하지 않습니다.
- 새 전역 store를 만들기 전에 URL이나 로컬 상태로 충분한지 확인합니다.
- store는 그 상태를 쓰는 feature의 `model/`에 두고, id는 `<feature>-<대상>` 형식으로 붙입니다.
- 화면 사이를 건너는 복귀 맥락은 store가, 공유·북마크·뒤로가기가 되어야 하는 값은 URL이 가집니다.
- 결제와 정산 금액의 최종 계산을 JavaScript 부동소수점에 의존하지 않습니다.
- API 금액 계약과 정밀 계산 도구가 정해지기 전에는 금액 문자열을 임의로 `number`로
  변환하지 않습니다.

## Query Key 관리하기

feature의 `model`에서 Query Key factory를 관리합니다.

```ts
export const journeyKeys = {
  all: ['journeys'] as const,
  lists: () => [...journeyKeys.all, 'list'] as const,
  list: (filters: JourneyFilters) => [...journeyKeys.lists(), filters] as const,
  details: () => [...journeyKeys.all, 'detail'] as const,
  detail: (journeyId: number) => [...journeyKeys.details(), journeyId] as const,
}
```

- 컴포넌트마다 Query Key 배열을 직접 만들지 않습니다.
- 요청 결과를 바꾸는 모든 입력을 Key에 포함합니다.
- 직렬화할 수 있는 값만 사용합니다.
- mutation 성공 후 전체 캐시를 무조건 비우지 않습니다.
- 영향받는 Key만 갱신하거나 invalidate합니다.

## API와 DTO 관리하기

- 모든 요청은 `src/shared/api/httpClient.ts`의 Axios 인스턴스를 사용합니다.
- feature의 `api` 폴더가 요청 함수와 전송 DTO를 소유합니다.
- API DTO와 화면 표시 모델의 의미가 다르면 API 경계에서 변환합니다.
- 컴포넌트에서 Axios를 직접 import하거나 URL 문자열을 조립하지 않습니다.
- `VITE_API_BASE_URL`이 없을 때 운영 기본값으로 조용히 대체하지 않습니다.
- 인증 토큰을 `localStorage` 또는 `sessionStorage`에 저장하지 않습니다.
- 서버 오류는 화면에서 사용하기 전에 공통 오류 모델로 정규화합니다.

공통 인터셉터가 다음을 처리하므로 feature에서 다시 구현하지 않습니다.

- `ApiResponse<T>` 봉투를 벗깁니다. 요청 함수는 `response.data`에서 바로 `data`를
  받습니다. `success` 여부를 직접 확인하지 않습니다.
- 모든 실패를 `NormalizedApiError`로 통일합니다. `code`, `status`, `messageKey`를
  가지며 화면은 `messageKey`로 문구를 만듭니다.
- 변경 요청에 CSRF 헤더를 붙입니다.
- 401을 받으면 갱신을 1회 시도하고 원 요청을 재시도합니다. 동시에 여러 요청이 401을
  받아도 갱신은 한 번만 실행합니다. feature에서 별도로 재시도 로직을 만들면 백엔드의
  refresh token 재사용 감지에 걸리므로 만들지 마세요.
- 이미지처럼 `responseType: 'blob'`으로 받는 요청은 실패하면 오류 본문까지 Blob으로
  옵니다. 인터셉터가 이를 글자로 풀어 되돌리므로 화면은 다른 요청과 똑같이 `error.code`로
  분기합니다. 본문이 JSON이 아니면 손대지 않고 `UNKNOWN`으로 정규화합니다. 이 해제는
  CSRF·401 판정보다 먼저 일어나야 바이너리 요청도 두 재시도에서 빠지지 않습니다.

### 요청별 응답 스키마 검증

백엔드 DTO와 화면 모델의 계약을 런타임에서도 확인해야 하는 요청은
`AxiosRequestConfig.responseSchema`에 feature `api/` 폴더가 소유한 Zod 스키마를
전달합니다. 이 검증은 공통 `ApiResponse` 봉투가 성공한 뒤 `data`에만 적용하며, Zod의
변환 결과가 아니라 서버가 보낸 원본 `data`를 반환합니다. feature 스키마는 알 수 없는
추가 객체 키를 허용하고, nullable 배열·날짜와 안전한 정규화/fallback이 있는 미래 enum
값의 처리는 기존 feature model이 담당합니다. 서버 allow-list가 화면 의미나 금액 방향을
결정하는 enum은 현재 계약 밖의 값을 검증 단계에서 거부할 수 있습니다.

`responseSchema`를 생략한 기존 요청은 검증 없이 봉투 해제, 401 refresh와 AUTH-005
CSRF 재시도를 그대로 유지합니다. 지정한 스키마는 두 재시도에서도 원 Axios config와
함께 전달됩니다. 검증 실패는 `UNKNOWN`과 실제 HTTP 상태를 가진
`NormalizedApiError`로 정규화하며, 로그에는 URL·method·상태와 issue의 path/code/
expected만 남깁니다. 응답 본문, issue message/input, 인증·개인정보와 전체 오류 객체는
로그에 남기지 않습니다. API mock 테스트는 config 전달을 확인하고, feature별 schema
fixture 테스트는 실제 parser 동작을 별도로 검증합니다.

세션이 완전히 끊겼을 때의 화면 이동은 `src/main.ts`가 `setSessionExpiredHandler`로
주입합니다. `shared`는 router와 feature를 import하지 않습니다.

로그아웃 요청 전에 `nawa.auth.signOutBarrier` 장벽을 세웁니다. 응답이 불확실하면
새로고침과 탭 이동 뒤에도 보호 경로와 401 refresh 복구를 차단하며, 서버 로그아웃 성공
응답 또는 오류 없는 새 로그인 callback에서만 해제합니다. 이 값은 인증정보가 아니라
사용자의 로그아웃 의도만 나타냅니다.

Axios 인스턴스가 `withCredentials: true`를 사용하므로 서버 CORS는 실제 프론트엔드
Origin과 credentials를 함께 허용해야 합니다.

## Router 구성하기

route는 feature마다 `features/<domain>/routes.ts`에서 정의하고
`RouteRecordRaw[]`를 default export합니다. `src/app/router/index.ts`가 이를 자동으로
수집하므로 **화면을 추가할 때 라우터 공용 파일을 수정하지 않습니다.**

```ts
// features/journey/routes.ts
import type { RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/journeys',
    name: 'journey-list',
    component: () => import('./views/JourneyListView.vue'),
    meta: { requiresAuth: true },
  },
]

export default routes
```

- 화면 route는 기본적으로 lazy import합니다.
- route name은 `<domain>-<screen>` 형식으로 feature 사이에 겹치지 않게 짓습니다.
- 인증은 `meta.requiresAuth`와 `meta.guestOnly`를 읽는 전역 guard 하나로 처리합니다.
  화면 컴포넌트에서 개별적으로 인증을 확인하지 않습니다.
- 도메인에 속하지 않는 route와 404는 `app/router/appRoutes.ts`에 둡니다.
- 여러 feature가 함께 참조하는 경로는 `shared/config/routePaths.ts`에 상수로 둡니다.
  `/auth/callback`은 백엔드 `AUTH_FRONTEND_SUCCESS_URL`과 일치해야 하므로 백엔드 설정을
  함께 바꾸지 않고 변경하지 않습니다.
- 새로고침하거나 공유해도 유지할 필터와 탭은 query param으로 표현합니다.

## 다국어 문구 작성하기

NA-WA는 방한 외국인을 대상으로 하므로 **한국어는 서비스 locale이 아닙니다.**
지원 locale은 `en`, `ja`, `zh-TW`, `vi`이고 기본 locale과 fallback locale은
모두 `en`입니다. 문구는 `en`을 원본으로 작성하고, 번역되지 않은 key는 `en`으로
폴백하므로 화면에 raw key가 노출되지 않습니다.

문구 파일은 `shared/i18n/<locale>.ts`와 `features/<domain>/i18n/<locale>.ts`에 두면
자동으로 수집됩니다. **문구를 추가할 때 공용 파일을 수정하지 않습니다.**

```ts
// features/journey/i18n/en.ts
export default {
  journey: {
    detail: { joinButton: 'Join this journey' },
  },
}
```

- 파일 하나는 자기 feature 이름을 최상위 네임스페이스로 갖습니다. 다른 feature와
  겹치지 않게 합니다.
- key에는 문장 자체가 아니라 의미와 화면 계층을 담습니다.
- 날짜, 숫자와 통화는 locale 기반 formatter를 사용합니다.
- 서버 오류 메시지를 그대로 노출하지 않습니다. `NormalizedApiError.messageKey`가
  가리키는 번역 문구를 사용합니다.
- 오류 코드 문구는 `<domain>.errorCode.<CODE>`에 둡니다. `AUTH-001`은
  `auth.errorCode.AUTH-001`로 해석됩니다. 대응 문구가 없으면 `error.unknown`으로
  폴백합니다.

### 문장 규칙

문구를 새로 쓰거나 고칠 때 아래를 지킵니다. 각 규칙은 `en` 문구에서 실제로 흔들린
지점에 대응합니다.

- **미국식 철자를 씁니다.** `unrecognized`, `canceled`처럼 씁니다. 영국식을 섞지
  않습니다.
- **완전한 문장에만 마침표를 찍습니다.** 라벨과 상태 표시에는 찍지 않습니다.
  `'No activity yet'`은 상태라서 찍지 않고, `'Enter a trip name.'`은 문장이라서
  찍습니다.
- **말줄임표를 쓰지 않습니다.** `'Loading transactions...'`가 아니라
  `'Loading transactions'`로 씁니다. `...`와 `…`가 섞이면 사람도 기계도 찾아내기
  어렵습니다.
- **로딩·재시도·빈 상태·일반 오류는 `shared/i18n`의 문구를 씁니다.** feature
  네임스페이스에 같은 문구를 다시 만들지 않습니다. 도메인 고유의 안내가 필요할 때만
  새로 만듭니다.
- **실패를 알리는 오류 문구의 주어는 서비스입니다.**
  `'We could not verify your account. Please try again.'` 형태로 씁니다. 다만
  `'Your session has expired.'`처럼 사용자의 상태를 서술하는 문장은 그대로 둡니다.
- **오류 문구에 다음 행동을 넣습니다.** 원인만 적고 끝내지 않습니다.
  `'Your session has expired. Please sign in again.'`이 기준입니다. 앱에 없는 행동은
  안내하지 않습니다.
- **서로 다른 오류 코드에 같은 문구를 쓰지 않습니다.** 코드가 나뉘어 있으면 사용자가
  둘을 구분할 수 있어야 합니다. 구분할 필요가 없으면 코드를 합칩니다.

### 용어집

| UI 문구                                | 쓰지 않는 표현        | 비고                                                                                                                                                                                                                       |
| -------------------------------------- | --------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `NA-WA`                                | `NAWA`, `Na-wa`       | `app.name`이 정본입니다                                                                                                                                                                                                    |
| `journey`                              | `trip`                | 코드와 DB는 `trip`을 씁니다. **UI 문구만 `journey`입니다**                                                                                                                                                                 |
| `top up`(동사) / `top-up`(명사)        | `charge`, `recharge`  |                                                                                                                                                                                                                            |
| `split`                                | `settle up`, `divide` | 정산 도메인의 UI 문구입니다. 코드·API·오류 코드는 `settlement`을 그대로 씁니다. 화면 이름은 복수형 `Splits`, 새로 만드는 동작은 단수형 `Start Split`입니다. 브랜드 태그라인(`app.tagline`)의 `settle up`만 예외로 남습니다 |
| `To Pay` / `To Collect`                | `Received`, `Sent`    | 정산 목록 토글입니다. 낼 돈인지 받을 돈인지를 말이 직접 드러내야 합니다. 코드 식별자는 `received`·`sent`를 유지하고, 어느 쪽을 보고 있는지는 `query.side`로 화면 사이에 나릅니다                                           |
| `sign in`(동사) / `sign-in`(명사·수식) | `log in`, `login`     | 현재 용법이 맞습니다                                                                                                                                                                                                       |

기존 문구 중 이 규칙을 어긴 것이 아직 남아 있습니다. **위반을 근거로 따라 쓰지
마세요.** 일괄 수정은 별도 작업으로 진행합니다.

## 스타일과 접근성 확인하기

- Tailwind utility를 기본으로 사용합니다.
- 색, 라운드, 간격, 그림자, 타이포 스케일은 `src/app/styles/tokens.css`에 정의된
  토큰만 사용합니다. **컴포넌트에 HEX 색상을 직접 쓰지 않습니다.**
- 디자인 시안이 바뀌면 `tokens.css`의 값만 교체하고 컴포넌트는 수정하지 않습니다.
  이 구조를 유지하려면 토큰을 우회하는 임의 색상이 없어야 합니다.
- `src/app/styles/index.css`는 Tailwind import, `@font-face`, 최소한의 base 레이어만
  담습니다. 화면별 스타일을 여기에 추가하지 않습니다.
- 화면마다 임의 색상과 z-index를 추가하지 않습니다.
- 폰트는 `public/fonts`의 woff2를 `@font-face`로 등록합니다. CJK 폰트는 원본이 9~12MB라
  unicode-range 슬라이스로 나눠 등록하고, 브라우저가 화면에 실제로 나온 글자가 속한
  슬라이스만 내려받습니다. 로케일이 아니라 글자 기준입니다. 자세한 내용은
  `public/fonts/README.md`를 보세요.
- 모바일 viewport에서는 `dvh`와 safe-area를 고려합니다.
- 터치 대상을 충분한 크기로 만들고 hover에만 의존하지 않습니다.
- icon-only button에 접근 가능한 이름을 제공합니다.
- form control과 label, 오류 문구, focus 상태를 연결합니다.
- Modal과 BottomSheet의 focus 이동, Escape 또는 뒤로가기, 배경 스크롤 잠금은 공통
  컴포넌트에서 처리합니다.

## PWA 캐시 변경하기

- 서비스 워커는 앱 셸과 정적 자원만 사전 캐시합니다.
- 인증, 개인정보, 비용·정산, mutation 응답과 지도 타일을 runtime cache에 넣지
  않습니다.
- **폰트를 precache에 넣지 않습니다.** `vite.config.ts`의 `workbox.globPatterns`가
  `js`, `css`, `html`만 포함하므로 woff2는 사전 캐시되지 않습니다. CJK 폰트까지
  포함하면 사전 캐시가 수십 MB로 커지므로 이 패턴을 넓히지 않습니다.
- 오프라인 읽기 기능을 추가하기 전에 데이터 종류, 만료 시간과 로그아웃 시 제거
  방법을 정합니다.
- 서비스 워커를 변경하면 새 버전 업데이트와 기존 캐시 제거 동작을 확인합니다.

## 테스트 작성하기

- 순수 변환과 검증 로직은 Vitest 단위 테스트로 검증합니다.
- 컴포넌트는 사용자 입력과 렌더링 결과를 Vue Test Utils로 검증합니다.
- 로그인, 여행 참여, 일정 변경과 정산 등 중요한 사용자 흐름은 Playwright로
  검증합니다.
- 구현 세부사항보다 사용자가 관찰하는 결과를 assertion합니다.
- 버그를 수정할 때는 가능하면 실패를 재현하는 회귀 테스트를 먼저 추가합니다.

PR을 열기 전에 다음 명령을 실행하세요.

```shell
pnpm format:check
pnpm lint
pnpm type-check
pnpm --filter @na-wa/frontend test:unit --run
pnpm build
```

사용자 흐름을 변경했다면 E2E 테스트도 실행하세요.

```shell
pnpm test:e2e
```

## 화면 스냅샷 남기기

화면을 추가하거나 크게 바꿨다면 스냅샷을 찍어 PR에 첨부하세요. 코드와 단위 테스트로는
확인할 수 없는 것이 있습니다. 폰트 폴백이 깨져 CJK가 두부(□□□)로 나와도 DOM assertion은
통과하고, 여백과 대비는 클래스 이름만 봐서는 알 수 없습니다.

스냅샷은 Chromium으로 찍으므로 브라우저를 최초 1회 내려받아야 합니다. E2E 테스트를 이미
실행해 봤다면 준비돼 있습니다.

```shell
pnpm --filter @na-wa/frontend exec playwright install chromium
```

개발 서버를 띄운 뒤 실행합니다.

```shell
pnpm dev
pnpm --filter @na-wa/frontend screenshot
```

찍을 화면은 `frontend/scripts/screenshot.mjs`의 `SCREENS` 배열에 추가합니다. 바텀시트를
연 상태처럼 조작이 필요한 화면은 `prepare`에 동작을 적습니다.

낱장이 아니라 **화면을 눌러 넘어가는 과정**은 같은 파일의 `FLOWS` 배열에 적습니다. 한
항목이 페이지를 한 번만 열고 단계마다 한 장씩 찍으므로, 화면 모양뿐 아니라 버튼이 실제로
다음 화면에 이어져 있는지도 함께 드러납니다. 각 단계는 도착 화면을 직접 기다리게 적습니다.
러너는 `networkidle`을 걸지 않는데, "보내는 중" 같은 화면은 응답을 기다리는 사이에만
존재하기 때문입니다.

기본 실행은 시안 기준인 390×844에 2배율로 찍습니다. 리뷰어가 시안과 나란히 놓고 볼 수
있게 하려는 것이므로 PR에 첨부하는 기본 스냅샷의 크기는 바꾸지 마세요. 반응형 검증이
필요할 때만 `SCREENSHOT_WIDTH`로 다른 폭(폴더블 커버 최소폭 280 등)을 지정합니다.
다국어 화면은 `SCREENSHOT_LOCALE`(ja·zh-TW·vi)로 찍습니다. 두 옵션의 산출물은
`screenshots-<width>/`·`screenshots-<locale>/`로 분리되어 기본 산출물과 섞이지 않습니다.

출력물은 `frontend/screenshots/`(옵션 실행은 위의 분리 폴더)에 생기며 **저장소에
커밋하지 않습니다.** GitHub은 이미지 업로드를 웹 UI에서만 지원하므로, 생성된 PNG를
PR 본문에 끌어다 놓으세요.

## 완료 조건 확인하기

- 정상, 빈 상태, 로딩, 오류와 권한 없음 상태를 처리했습니다.
- 모바일 화면, 키보드 접근과 다국어 문구를 확인했습니다.
- API, 환경 변수 또는 캐시 정책을 변경했다면 관련 문서를 수정했습니다.
- 관련 자동 테스트와 필요한 수동 확인을 통과했습니다.
- 디버그 로그, 임시 주석, 민감정보와 불필요한 빌드 산출물이 없습니다.
