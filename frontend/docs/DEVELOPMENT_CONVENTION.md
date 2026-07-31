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

feature에는 필요한 폴더만 만드세요.

```text
features/journey/
├── api/          요청 함수와 DTO
├── components/   journey 전용 컴포넌트
├── composables/  journey 전용 조합 로직
├── model/        Query Key, 상태와 도메인 타입
└── schemas/      입력 검증 스키마
```

## 파일과 이름 정하기

| 대상              | 규칙                    | 예시                  |
| ----------------- | ----------------------- | --------------------- |
| Vue 컴포넌트      | PascalCase              | `JourneyCard.vue`     |
| composable        | `use` + PascalCase 의미 | `useJourneyList.ts`   |
| 일반 함수와 변수  | camelCase               | `formatTravelDate`    |
| 타입과 인터페이스 | PascalCase              | `JourneySummary`      |
| 상수              | UPPER_SNAKE_CASE        | `DEFAULT_PAGE_SIZE`   |
| 테스트            | 원본 이름 + `.spec`     | `JourneyCard.spec.ts` |

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

현재 공통 오류 정규화와 인증 인터셉터는 구현 전입니다. feature마다 임시 오류 형식을
추가하지 마세요.

Axios 인스턴스가 `withCredentials: true`를 사용하므로 서버 CORS는 실제 프론트엔드
Origin과 credentials를 함께 허용해야 합니다.

## Router 구성하기

- route는 `src/app/router`에서 정의합니다.
- 화면 route는 기본적으로 lazy import합니다.
- route name은 상수 또는 타입이 있는 공개 API로 관리합니다.
- 인증 route는 meta와 전역 guard를 사용하는 하나의 정책으로 처리합니다.
- 앱 셸을 구현할 때 404 route와 오류 복구 동선을 포함합니다.
- 새로고침하거나 공유해도 유지할 필터와 탭은 query param으로 표현합니다.

## 다국어 문구 작성하기

- 사용자에게 보이는 문자열은 Vue I18n message key로 관리합니다.
- 기본 locale과 fallback locale은 `ko`입니다.
- key에는 문장 자체가 아니라 의미와 화면 계층을 담습니다.

```text
journey.detail.joinButton
settlement.summary.totalAmount
common.error.retry
```

- 날짜, 숫자와 통화는 locale 기반 formatter를 사용합니다.
- 서버 오류 메시지를 그대로 노출하지 않습니다.
- 오류 코드에 대응하는 번역 문구를 사용합니다.

## 스타일과 접근성 확인하기

- Tailwind utility를 기본으로 사용합니다.
- 반복되는 의미 값은 디자인 토큰으로 승격합니다.
- 화면마다 임의 색상과 z-index를 추가하지 않습니다.
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

## 완료 조건 확인하기

- 정상, 빈 상태, 로딩, 오류와 권한 없음 상태를 처리했습니다.
- 모바일 화면, 키보드 접근과 다국어 문구를 확인했습니다.
- API, 환경 변수 또는 캐시 정책을 변경했다면 관련 문서를 수정했습니다.
- 관련 자동 테스트와 필요한 수동 확인을 통과했습니다.
- 디버그 로그, 임시 주석, 민감정보와 불필요한 빌드 산출물이 없습니다.
