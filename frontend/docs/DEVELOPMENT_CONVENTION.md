# NA-WA Frontend 개발 컨벤션

Vue 3, TypeScript, Vite 기반 프론트엔드의 구조와 구현 규칙입니다. 현재 구현되지 않은
공통 기능은 이 문서의 경계를 먼저 따른 뒤 별도 Issue에서 추가합니다.

## 1. 기본 원칙

- TypeScript strict 설정과 ESLint 오류를 우회하지 않습니다.
- Vue 컴포넌트는 Composition API와 `<script setup lang="ts">`를 사용합니다.
- 사용자에게 보이는 문자열, API 계약, 상태의 소유자를 코드에서 명확히 구분합니다.
- 모바일 화면을 기본으로 만들고 넓은 화면을 점진적으로 확장합니다.
- 인증정보, 개인정보, 정산 데이터가 브라우저 저장소나 로그에 남지 않게 합니다.

## 2. 소스 구조와 의존 방향

```text
src/
├── app/          앱 진입, Router, Provider, 전역 스타일
├── features/     도메인 단위 기능
└── shared/       도메인 독립 공통 모듈
```

의존 방향은 `app → features → shared`입니다.

- `app`은 화면과 feature를 조합할 수 있습니다.
- `features`는 `shared`를 사용할 수 있지만 다른 feature 내부 구현에 직접 의존하지
  않습니다.
- `shared`는 `features`와 `app`을 import하지 않습니다.
- 두 feature가 함께 쓰더라도 도메인 의미가 있으면 섣불리 `shared`로 이동하지
  않습니다.

feature는 필요한 폴더만 생성합니다.

```text
features/journey/
├── api/          요청 함수와 DTO
├── components/   journey 전용 컴포넌트
├── composables/  journey 전용 조합 로직
├── model/        Query Key, 상태와 도메인 타입
└── schemas/      입력 검증 스키마
```

## 3. 파일과 이름

| 대상            | 규칙                    | 예시                  |
| --------------- | ----------------------- | --------------------- |
| Vue 컴포넌트    | PascalCase              | `JourneyCard.vue`     |
| composable      | `use` + PascalCase 의미 | `useJourneyList.ts`   |
| 일반 함수·변수  | camelCase               | `formatTravelDate`    |
| 타입·인터페이스 | PascalCase              | `JourneySummary`      |
| 상수            | UPPER_SNAKE_CASE        | `DEFAULT_PAGE_SIZE`   |
| 테스트          | 원본 이름 + `.spec`     | `JourneyCard.spec.ts` |

- 컴포넌트 이름은 두 단어 이상으로 역할이 드러나게 작성합니다.
- 의미가 불분명한 `utils.ts`, `common.ts`, `data.ts` 파일을 만들지 않습니다.
- 폴더 공개 API가 필요할 때만 `index.ts`를 사용하고, 무분별한 barrel export를 피합니다.
- `@` alias는 `frontend/src`를 가리킵니다.

## 4. Vue 컴포넌트

- props와 emits는 TypeScript로 선언합니다.
- template에서는 컴포넌트 이름을 PascalCase로 사용합니다.
- button에는 반드시 명시적인 `type`을 지정합니다.
- 파생 값은 `computed`, 부수효과는 `watch` 또는 `watchEffect`로 구분합니다.
- DOM 접근은 예외적으로 사용하고 템플릿 ref의 타입을 명시합니다.
- 화면 컴포넌트는 데이터 조합과 레이아웃에 집중하고, 재사용 로직은 feature
  composable로 분리합니다.

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

## 5. 상태 관리

상태의 실제 소유자에 따라 도구를 선택합니다.

| 상태                                 | 도구                          |
| ------------------------------------ | ----------------------------- |
| API 조회·캐시·mutation               | TanStack Vue Query            |
| 여러 화면이 공유하는 클라이언트 상태 | Pinia                         |
| 컴포넌트 또는 한 화면 내부 상태      | `ref`, `reactive`, `computed` |
| URL로 공유해야 하는 필터·선택        | Vue Router query/params       |

- Vue Query 데이터를 Pinia에 복사하지 않습니다.
- Pinia에 API loading/error 상태를 중복 저장하지 않습니다.
- 새 전역 store를 만들기 전에 URL이나 로컬 상태로 충분한지 확인합니다.
- 결제·정산 금액의 최종 계산을 JavaScript 부동소수점에 의존하지 않습니다. API 금액
  계약과 정밀 계산 도구가 정해질 때까지 금액 문자열을 임의로 `number`로 변환하지
  않습니다.

## 6. Query Key

Query Key는 feature의 `model`에서 factory로 관리합니다.

```ts
export const journeyKeys = {
  all: ['journeys'] as const,
  lists: () => [...journeyKeys.all, 'list'] as const,
  list: (filters: JourneyFilters) => [...journeyKeys.lists(), filters] as const,
  details: () => [...journeyKeys.all, 'detail'] as const,
  detail: (journeyId: number) => [...journeyKeys.details(), journeyId] as const,
}
```

- 배열 리터럴을 컴포넌트마다 직접 만들지 않습니다.
- Key에는 요청 결과를 바꾸는 모든 입력을 포함합니다.
- 직렬화 가능한 값만 사용합니다.
- mutation 성공 후 무조건 전체 캐시를 비우지 않고 영향받는 Key만 갱신하거나
  invalidate합니다.

## 7. API와 DTO

- 모든 요청은 `src/shared/api/httpClient.ts`의 공통 Axios 인스턴스를 사용합니다.
- feature의 `api` 폴더는 요청 함수와 전송 DTO를 소유합니다.
- API DTO와 화면 표시 모델의 의미가 다르면 경계에서 변환합니다.
- 컴포넌트에서 Axios를 직접 import하거나 URL 문자열을 조립하지 않습니다.
- `VITE_API_BASE_URL`이 없는 상태를 조용히 운영 기본값으로 대체하지 않습니다.
- 인증 토큰을 `localStorage` 또는 `sessionStorage`에 저장하지 않습니다.
- 오류를 화면에서 사용하기 전에 공통 오류 모델로 정규화합니다. 현재 오류 정규화와
  인증 인터셉터는 구현 전이므로 feature마다 임시 형식을 확산하지 않습니다.

`withCredentials: true`를 사용하므로 서버 CORS는 실제 프론트엔드 Origin과 credentials를
함께 허용해야 합니다.

## 8. Router

- route 정의는 `src/app/router`에서 관리합니다.
- 화면 route는 기본적으로 lazy import합니다.
- route name은 문자열 하드코딩을 줄일 수 있도록 상수 또는 타입이 있는 공개 API로
  관리합니다.
- 인증이 필요한 route는 meta와 전역 guard의 한 가지 정책으로 처리합니다.
- 404 route와 오류 복구 동선을 앱 셸 작업에 포함합니다.
- filter나 탭처럼 새로고침·공유 시 유지되어야 하는 상태는 query param으로 표현합니다.

## 9. 다국어

- 사용자에게 보이는 문자열은 Vue I18n message key로 관리합니다.
- 기본 locale과 fallback locale은 `ko`입니다.
- key는 문장 자체가 아니라 의미와 화면 계층을 나타냅니다.

```text
journey.detail.joinButton
settlement.summary.totalAmount
common.error.retry
```

- 날짜, 숫자, 통화는 locale 기반 formatter를 사용합니다.
- 서버 오류 메시지를 그대로 노출하지 않고 오류 코드에 대응하는 번역 문구를 사용합니다.

## 10. 스타일과 접근성

- Tailwind utility를 기본으로 사용하고 반복되는 의미 값은 디자인 토큰으로 승격합니다.
- 임의 색상과 z-index를 화면마다 늘리지 않습니다.
- 모바일 viewport는 `dvh`와 safe-area를 고려합니다.
- 터치 대상은 충분한 크기를 확보하고 hover에만 의존하지 않습니다.
- icon-only button에는 접근 가능한 이름을 제공합니다.
- form control은 label, 오류 문구, focus 상태를 연결합니다.
- Modal과 BottomSheet는 focus 이동, Escape/뒤로가기, 배경 스크롤 잠금을 공통
  컴포넌트에서 처리합니다.

## 11. PWA

- 서비스 워커는 앱 셸과 정적 자원만 사전 캐시합니다.
- 인증, 개인정보, 비용·정산, mutation 응답과 지도 타일을 runtime cache에 넣지
  않습니다.
- 오프라인 읽기 기능은 데이터 종류, 만료, 로그아웃 시 제거 정책을 정한 뒤 추가합니다.
- 서비스 워커 변경은 새 버전 업데이트와 기존 캐시 정리 동작까지 확인합니다.

## 12. 테스트

- 순수 변환·검증 로직은 Vitest 단위 테스트를 작성합니다.
- 컴포넌트는 사용자 입력과 렌더링 결과를 중심으로 Vue Test Utils로 검증합니다.
- 로그인, 여행 참여, 일정 변경, 정산처럼 중요한 사용자 흐름은 Playwright로
  검증합니다.
- 구현 세부사항보다 사용자가 관찰하는 결과를 assertion합니다.
- 버그 수정에는 가능하면 실패를 재현하는 회귀 테스트를 먼저 추가합니다.

PR 전 기본 검증입니다.

```shell
pnpm format:check
pnpm lint
pnpm type-check
pnpm --filter @na-wa/frontend test:unit --run
pnpm build
```

사용자 흐름이 바뀌면 다음 검증도 수행합니다.

```shell
pnpm test:e2e
```

## 13. 완료 기준

- 요구사항의 정상·빈 상태·로딩·오류·권한 없음 상태를 처리했습니다.
- 모바일 화면, 키보드 접근, 다국어 문구를 확인했습니다.
- API·환경 변수·캐시 정책이 바뀌었다면 문서를 갱신했습니다.
- 관련 자동 테스트와 필요한 수동 확인을 통과했습니다.
- 디버그 로그, 임시 주석, 민감정보와 불필요한 빌드 산출물이 없습니다.
