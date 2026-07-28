# NA-WA Frontend

Vue 3와 TypeScript로 구성한 NA-WA의 모바일 우선 PWA입니다.

## 기술 구성

- Vue 3, Vite, TypeScript
- Vue Router, Pinia
- TanStack Vue Query
- Axios
- Vue I18n
- Tailwind CSS v4
- vite-plugin-pwa
- Vitest, Playwright
- ESLint, Prettier

## 소스 구조

- `src/app`: 앱 진입 구성, Router, 전역 Provider, I18n, 전역 스타일
- `src/features`: 도메인 단위 기능
- `src/shared`: 공통 API 클라이언트와 재사용 모듈

의존 방향은 `app → features → shared`를 따릅니다.

- 서버 상태: TanStack Vue Query
- 클라이언트 상태: Pinia
- HTTP 통신: `src/shared/api/httpClient.ts`

현재 Axios 인스턴스에는 `baseURL`, timeout, `withCredentials`만 구성되어 있습니다. 인증 인터셉터와 실제 API 연동은 포함하지 않습니다.

## PWA 정책

서비스 워커는 앱 셸과 정적 자원만 사전 캐시합니다. API 응답에 대한 런타임 캐시는 사용하지 않습니다.

설치, 실행, 환경변수와 검증 명령은 저장소 루트의 [README](../README.md)를 참고합니다.
