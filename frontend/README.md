# NA-WA Frontend

NA-WA 프론트엔드는 Vue 3와 TypeScript로 구성한 모바일 우선 PWA입니다. 현재 앱
진입점, 전역 Provider, API 클라이언트, PWA와 테스트 도구를 제공하며 도메인 기능은
구현 전입니다.

## 필요한 문서 찾기

| 하려는 일                              | 문서                                                       |
| -------------------------------------- | ---------------------------------------------------------- |
| 설치, 실행, 환경 변수와 검증 명령 확인 | [프로젝트 안내](../README.md)                              |
| Issue, 브랜치, 커밋과 PR 규칙 확인     | [공통 협업 가이드](../CONTRIBUTING.md)                     |
| 기술 선택과 운영 경계 확인             | [기술 스택과 운영 경계](../docs/TECH_STACK.md)             |
| 프론트엔드 구현 규칙 확인              | [프론트엔드 개발 컨벤션](./docs/DEVELOPMENT_CONVENTION.md) |
| 공용 UI 컴포넌트와 사용 규칙 확인      | [shared/ui 안내](./src/shared/ui/README.md)                |

## 소스 구조

```text
src/
├── app/          앱 진입 구성, Router, 전역 Provider, i18n과 전역 스타일
├── features/     도메인 단위 API, 상태, 검증과 UI
└── shared/       공통 API 클라이언트와 도메인 독립 재사용 모듈
```

의존 방향은 `app → features → shared`입니다.

| 상태 또는 책임                       | 소유자                         |
| ------------------------------------ | ------------------------------ |
| API 조회, 캐시와 mutation            | TanStack Vue Query             |
| 여러 화면이 공유하는 클라이언트 상태 | Pinia                          |
| HTTP 통신                            | `src/shared/api/httpClient.ts` |
| 사용자에게 보이는 문구               | Vue I18n                       |

현재 Router에는 실제 route가 없습니다. 인증 인터셉터, 공통 오류 모델과 도메인
feature도 구현 전입니다.

## PWA 캐시 경계

서비스 워커는 앱 셸과 정적 자원만 사전 캐시합니다. 인증, 정산, 개인정보 API
응답과 지도 타일에는 runtime cache를 사용하지 않습니다.

자세한 캐시 범위와 변경 조건은
[기술 스택과 운영 경계](../docs/TECH_STACK.md#pwa-캐시-경계)에서 확인하세요.
