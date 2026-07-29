# NA-WA Frontend

Vue 3와 TypeScript로 구성한 NA-WA의 모바일 우선 PWA입니다. 현재는 앱 진입점, 전역
Provider, API 클라이언트, PWA와 테스트 도구를 갖춘 기반 단계입니다.

## 관련 문서

- [저장소 실행·검증 안내](../README.md)
- [공통 협업 가이드](../CONTRIBUTING.md)
- [기술 스택과 운영 경계](../docs/TECH_STACK.md)
- [프론트엔드 개발 컨벤션](./docs/DEVELOPMENT_CONVENTION.md)

## 기술 구성

- Vue 3, Vite, TypeScript
- Vue Router, Pinia
- TanStack Vue Query
- Axios
- Vue I18n
- Tailwind CSS v4
- vite-plugin-pwa, Workbox
- Vitest, Vue Test Utils, Playwright
- ESLint, Prettier

## 소스 구조

```text
src/
├── app/          앱 진입 구성, Router, 전역 Provider, I18n, 전역 스타일
├── features/     도메인 단위 API, 상태, 검증, UI
└── shared/       공통 API 클라이언트와 도메인 독립 재사용 모듈
```

의존 방향은 `app → features → shared`를 따릅니다.

- 서버 상태: TanStack Vue Query
- 공유 클라이언트 상태: Pinia
- HTTP 통신: `src/shared/api/httpClient.ts`
- 사용자 노출 문구: Vue I18n

현재 Router의 실제 route, 인증 인터셉터, 공통 오류 모델과 도메인 feature는 구현 전입니다.

## PWA 정책

서비스 워커는 앱 셸과 정적 자원만 사전 캐시합니다. 인증·정산·개인정보 API 응답과 지도
타일에는 runtime cache를 사용하지 않습니다.

설치, 실행, 환경 변수와 검증 명령은 저장소 루트의
[README](../README.md)를 참고합니다.
