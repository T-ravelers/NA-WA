# NA-WA 기술 스택과 운영 경계

이 문서는 저장소에서 확인되는 현재 구성과 팀이 확정한 운영 방향을 구분해 기록합니다.
새 기술을 도입하거나 책임 경계를 바꿀 때 코드와 이 문서를 같은 PR에서 수정합니다.

## 1. Frontend

| 영역         | 기술                                  | 책임                                  |
| ------------ | ------------------------------------- | ------------------------------------- |
| Runtime      | Node.js `24.18.0`, pnpm `11.17.0`     | pnpm workspace와 프론트엔드 도구 실행 |
| UI           | Vue `3.5`, TypeScript `6`, Vite `8`   | 모바일 우선 SPA                       |
| Routing      | Vue Router `5`                        | 클라이언트 라우팅                     |
| Server state | TanStack Vue Query `5`                | API 조회, 캐시, mutation 상태         |
| Client state | Pinia `4`                             | 여러 화면이 공유하는 클라이언트 상태  |
| HTTP         | Axios `1`                             | 공통 API 클라이언트                   |
| i18n         | Vue I18n `11`                         | 한국어 기본 다국어 메시지             |
| Styling      | Tailwind CSS `4`                      | 디자인 토큰 기반 UI 스타일            |
| PWA          | vite-plugin-pwa, Workbox `generateSW` | 앱 셸과 정적 자원 사전 캐시           |
| Test         | Vitest, Vue Test Utils, Playwright    | 단위·컴포넌트·브라우저 테스트         |
| Quality      | ESLint, Prettier, Husky, lint-staged  | 정적 검사와 커밋 전 검사              |

프론트엔드 의존 방향은 `app → features → shared`입니다.

- `app`: Router, 전역 Provider, 전역 스타일과 앱 진입 구성
- `features`: 도메인별 API, 상태, 검증, 컴포넌트
- `shared`: 특정 도메인에 의존하지 않는 API 클라이언트와 공통 모듈

서버 응답은 Vue Query가 소유하고, Pinia에 복제하지 않습니다. 컴포넌트 내부에서 끝나는
상태는 `ref`와 `computed`를 사용합니다.

## 2. Backend

| 영역        | 기술                                              | 책임                          |
| ----------- | ------------------------------------------------- | ----------------------------- |
| Runtime     | Java `17`, Tomcat `9`                             | WAR 실행                      |
| Framework   | Spring Framework/MVC `5.3`, Spring Security `5.8` | REST API와 인증·인가          |
| Persistence | MyBatis `3.5`, MySQL `8.4`                        | 서비스 데이터 영속화          |
| Cache       | Redis `7`                                         | TTL이 필요한 인증·캐시 데이터 |
| Build       | Gradle Wrapper `9.6`, WAR plugin                  | 빌드와 테스트                 |
| API docs    | Springfox Swagger `2.9`                           | 개발 중 API 확인              |
| Container   | Docker, Docker Compose                            | 백엔드·Nginx·MySQL·Redis 실행 |

백엔드의 상세 규칙은
[백엔드 개발 컨벤션](../backend/docs/DEVELOPMENT_CONVENTION.md)을 따릅니다.

## 3. API와 인증 경계

- 프론트엔드는 `VITE_API_BASE_URL`을 통해 API 주소를 주입합니다.
- 공통 Axios 인스턴스는 `withCredentials: true`와 10초 timeout을 사용합니다.
- 인증 토큰은 `localStorage`와 `sessionStorage`에 저장하지 않습니다.
- 자격 증명 요청의 CORS는 `*`를 사용할 수 없습니다. 실제 Vercel Origin을 서버
  allowlist에 정확히 등록합니다.
- 요청·응답 DTO와 오류 코드는 프론트엔드·백엔드가 함께 검토하는 API 계약입니다.
- 현재 프론트엔드에는 인증 인터셉터와 공통 API 오류 정규화가 구현되어 있지 않습니다.

## 4. PWA 캐시 경계

PWA는 필수 기능이지만 오프라인 동작 범위는 의도적으로 제한합니다.

### 캐시함

- 앱 셸
- 빌드된 JavaScript와 CSS
- manifest, 아이콘 등 정적 자원
- 이후 별도로 승인한 읽기 전용 스냅샷

### 캐시하지 않음

- 로그인·인증 응답
- 비용·정산 등 mutation 응답
- 개인정보 또는 민감 API 응답
- 지도 타일
- 일반 API runtime cache

서비스 워커는 `generateSW`를 사용하며, 캐시 범위를 넓힐 때는 데이터 민감도와 만료
정책을 먼저 문서화합니다.

## 5. CI/CD와 배포

| 대상                | 현재 상태      | 책임                                                 |
| ------------------- | -------------- | ---------------------------------------------------- |
| Backend CI          | 구현됨         | `main` PR/push에서 Gradle 빌드·테스트                |
| Backend CD          | 구현됨         | CI 성공 후 Docker Hub push와 EC2 Docker Compose 배포 |
| Frontend CI         | 후속 작업      | format, lint, type-check, unit test, build 검증      |
| Frontend Preview    | 운영 방향 확정 | Vercel PR Preview                                    |
| Frontend Production | 운영 방향 확정 | Vercel `main` Production                             |

Vercel은 프론트엔드 배포를 담당하고 GitHub Actions는 품질 검증을 담당합니다. 같은
프론트엔드 배포를 두 시스템에서 중복 실행하지 않습니다.

## 6. 현재 범위와 알려진 정리 항목

- 제품 범위에는 채팅과 WebSocket/STOMP 기능이 없습니다.
- 현재 Nginx에는 WebSocket upgrade 설정이 남아 있으나 프론트엔드 기능 계약이
  아닙니다. 인프라 후속 작업에서 제거 여부를 결정합니다.
- `@tabler/icons-vue`는 프론트엔드 사용 의존성이므로 루트가 아닌
  `frontend/package.json`에서 관리하도록 후속 정리합니다.
- Router의 실제 route, 공통 API 오류 모델, Query Key factory, 공통 UI와 앱 셸은 아직
  구현 전입니다.
