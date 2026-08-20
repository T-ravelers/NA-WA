# NA-WA 기술 스택과 운영 경계

이 문서는 NA-WA에서 사용하는 기술과 각 기술이 소유하는 책임을 설명합니다. 새
기술을 도입하거나 데이터, 캐시, 배포의 책임을 바꿀 때 코드와 이 문서를 같은 PR에서
수정하세요.

표에서 `구현됨`은 현재 저장소에서 확인할 수 있는 동작을 뜻합니다. `운영 방향 확정`은
팀이 선택했지만 저장소 밖의 연결이나 운영 설정이 남아 있는 상태를 뜻합니다.

## Frontend

| 영역         | 기술                                  | 책임                                  |
| ------------ | ------------------------------------- | ------------------------------------- |
| Runtime      | Node.js `24.18.0`, pnpm `11.17.0`     | pnpm workspace와 프론트엔드 도구 실행 |
| UI           | Vue `3.5`, TypeScript `6`, Vite `8`   | 모바일 우선 SPA                       |
| Routing      | Vue Router `5`                        | 클라이언트 라우팅                     |
| Server state | TanStack Vue Query `5`                | API 조회, 캐시와 mutation 상태        |
| Client state | Pinia `4`                             | 여러 화면이 공유하는 클라이언트 상태  |
| HTTP         | Axios `1`                             | 공통 API 클라이언트                   |
| i18n         | Vue I18n `11`                         | 영어 기본 다국어 메시지               |
| Styling      | Tailwind CSS `4`                      | 디자인 토큰 기반 UI 스타일            |
| PWA          | vite-plugin-pwa, Workbox `generateSW` | 앱 셸과 정적 자원 사전 캐시           |
| Test         | Vitest, Vue Test Utils, Playwright    | 단위, 컴포넌트와 브라우저 테스트      |
| Quality      | ESLint, Prettier, Husky, lint-staged  | 정적 검사와 커밋 전 검사              |

지원 로케일은 `en`, `ja`, `zh-TW`, `vi`이며 기본과 폴백 모두 `en`입니다.
방한 외국인이 대상이라 한국어는 서비스 로케일이 아닙니다. 최종 기준은
`frontend/src/shared/i18n/locales.ts`입니다.

프론트엔드 의존 방향은 `app → features → shared`입니다.

| 계층       | 책임                                                   |
| ---------- | ------------------------------------------------------ |
| `app`      | Router, 전역 Provider, 전역 스타일과 앱 진입 구성      |
| `features` | 도메인별 API, 상태, 검증과 컴포넌트                    |
| `shared`   | 특정 도메인에 의존하지 않는 API 클라이언트와 공통 모듈 |

Vue Query가 서버 응답을 소유합니다. 서버 응답을 Pinia에 복제하지 마세요. 컴포넌트
내부에서 끝나는 상태는 `ref`와 `computed`로 관리하세요.

## Backend

| 영역        | 기술                                              | 책임                              |
| ----------- | ------------------------------------------------- | --------------------------------- |
| Runtime     | Java `17`, Tomcat `9`                             | WAR 실행                          |
| Framework   | Spring Framework/MVC `5.3`, Spring Security `5.8` | REST API와 인증·인가              |
| Persistence | MyBatis `3.5`, MySQL `8.4`                        | 서비스 데이터 영속화              |
| Cache       | Redis `7`                                         | TTL이 필요한 인증과 캐시 데이터   |
| Build       | Gradle Wrapper `9.6`, WAR plugin                  | 빌드와 테스트                     |
| API docs    | Springfox Swagger `2.9`                           | 개발 중 API 확인                  |
| Container   | Docker, Docker Compose                            | 백엔드, Nginx, MySQL과 Redis 실행 |

구현 규칙은 [백엔드 개발 컨벤션](../backend/docs/DEVELOPMENT_CONVENTION.md)을
따르세요. API 응답 계약은
[API 응답 및 오류 코드 컨벤션](../backend/docs/API_RESPONSE_CONVENTION.md)에서
확인할 수 있습니다.

## API와 인증 경계

- 프론트엔드는 `VITE_API_BASE_URL`에서 API 주소를 읽습니다.
- 공통 Axios 인스턴스는 `withCredentials: true`와 10초 timeout을 사용합니다.
- 인증 토큰을 `localStorage`와 `sessionStorage`에 저장하지 않습니다.
- 자격 증명을 포함한 CORS 요청에는 `*` Origin을 사용할 수 없습니다. 실제 Vercel
  Origin을 서버 allowlist에 등록하세요.
- 요청·응답 DTO와 오류 코드는 프론트엔드와 백엔드가 함께 검토하는 API 계약입니다.
- 백엔드는 공통 API 응답과 전역 예외 처리 구조를 구현했습니다.
- 프론트엔드는 공통 Axios 인스턴스의 인터셉터에서 CSRF 헤더 부착, 응답 봉투 해제,
  401 갱신 재시도와 오류 정규화를 처리합니다. feature에서 재시도 로직을 다시 만들지
  마세요. 백엔드의 refresh 재사용 감지에 걸립니다.

## PWA 캐시 경계

PWA는 필수 기능입니다. 개인정보와 정산 데이터의 안전을 위해 오프라인 동작 범위는
앱 셸과 정적 자원으로 제한합니다.

### 캐시하는 데이터

- 앱 셸
- 빌드된 JavaScript와 CSS
- manifest와 아이콘 등 정적 자원
- 데이터 종류와 만료 정책을 별도로 승인한 읽기 전용 스냅샷

### 캐시하지 않는 데이터

- 로그인과 인증 응답
- 비용·정산 등 mutation 응답
- 개인정보 또는 민감 API 응답
- 지도 타일
- 일반 API runtime cache

서비스 워커는 `generateSW`를 사용합니다. 캐시 범위를 넓히기 전에 데이터 민감도,
만료 시간과 로그아웃 시 제거 방법을 문서화하세요.

## CI/CD와 배포

| 대상                | 상태           | 책임                                                 |
| ------------------- | -------------- | ---------------------------------------------------- |
| Backend CI          | 구현됨         | `main` PR과 push에서 Gradle 빌드·테스트              |
| Backend CD          | 구현됨         | CI 성공 후 Docker Hub push와 EC2 Docker Compose 배포 |
| TLS 인증서 갱신     | 구현됨         | 매일 스케줄로 Let's Encrypt 갱신 후 nginx reload (`.github/workflows/renew-cert.yml`) |
| Frontend CI         | 구현됨         | `main` PR과 push에서 설치, 품질 검사와 빌드          |
| Frontend Preview    | 운영 방향 확정 | Vercel PR Preview                                    |
| Frontend Production | 운영 방향 확정 | Vercel `main` Production                             |

Frontend CI는 고정된 잠금 파일로 의존성을 설치한 뒤 format, lint, type-check, unit
test와 build를 실행합니다. Vercel은 프론트엔드 배포를 담당하고 GitHub Actions는
품질 검증을 담당합니다. 같은 프론트엔드 배포를 두 시스템에서 중복 실행하지
마세요.

운영 백엔드는 `https://api.clearpng.cloud`이고 TLS는 nginx가 443에서 종료합니다.
인증서는 Let's Encrypt에서 발급받아 `certbot_conf` 볼륨으로 nginx와 공유하며, 80은
`/.well-known/acme-challenge/`(갱신 검증 경로)만 직접 응답하고 나머지는 `308`로
https에 넘깁니다. ALB 도입 준비로 `X-Forwarded-Proto`가 붙은 요청과 헬스 체크용
`/alb-health`만 `308` 대신 백엔드로 프록시합니다. 리다이렉트 응답에는 CORS 헤더가
없으므로 프론트엔드의
`VITE_API_BASE_URL`은 반드시 https 주소여야 합니다.

시간대는 **서로 다른 일을 하는 세 곳**에서 정해집니다. 하나로 뭉뚱그리면 반드시
틀리므로 구분해서 봅니다.

| 설정 | 위치 | 정하는 것 |
| --- | --- | --- |
| `TZ=Asia/Seoul` | `backend/Dockerfile` | JVM의 `LocalDateTime.now()`가 찍는 시각 |
| `--default-time-zone=+09:00` | `docker-compose.yml`의 `mysql` | **DB 서버가 평가하는 `NOW()`·`CURRENT_TIMESTAMP`** |
| `serverTimezone=Asia/Seoul` | `DATABASE_URL` | JDBC 드라이버가 값을 주고받을 때의 해석 |

`serverTimezone`은 **드라이버 설정일 뿐이라 DB 서버가 평가하는 `CURRENT_TIMESTAMP`를
바꾸지 못합니다.** 이 점을 놓치면 "URL에 이미 Asia/Seoul이 있으니 맞춰져 있다"고
오해하기 쉽습니다. 실제로 `mysql` 서비스에 시간대 설정이 없던 동안 DB는 이미지
기본값인 UTC로 돌았고, 약속 마감·시작 전환 스케줄러가 DB의 `CURRENT_TIMESTAMP`에
기대고 있어 전환이 9시간씩 밀렸습니다.

세 곳을 맞춰도 **시각을 근거로 판단하는 주체는 애플리케이션이어야 합니다.** DB 시계에
기대는 쿼리는 컨테이너 설정 하나로 다시 어긋날 수 있으므로, `WHERE`·`ORDER BY`에서
`NOW()`·`CURRENT_TIMESTAMP`로 분기하지 말고 앱이 넘긴 시각을 씁니다. `updated_at`
같은 값에 대입하는 용도는 그대로 두어도 됩니다.

CI는 이 정렬을 **일부러 깨서** 돌립니다. MySQL 서비스를 UTC로 두고 JVM만
`TZ=Asia/Seoul`로 맞춰 운영과 같은 시차를 재현하므로, DB 시계에 의존하는 코드가
`.github/workflows/test.yml`의 통합 테스트에서 걸립니다. 양쪽을 맞추면 이 유형의
버그를 CI가 잡지 못합니다.

## 아직 구현하지 않았거나 정리할 항목

- 제품 범위에는 채팅과 WebSocket/STOMP 기능이 없습니다.
- Nginx에는 WebSocket upgrade 설정이 남아 있습니다. 이 설정은 프론트엔드 기능
  계약이 아니며 인프라 후속 작업에서 제거 여부를 결정합니다.
