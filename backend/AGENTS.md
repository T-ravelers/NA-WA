# backend/AGENTS.md

[루트 AGENTS.md](../AGENTS.md)를 먼저 읽으세요. 여기에는 **모르면 반드시 틀리는 전제**와
**실제로 CI를 깨뜨린 적이 있는 함정**만 적습니다. 전체 규칙은
[개발 컨벤션](./docs/DEVELOPMENT_CONVENTION.md)이 정본입니다.

## Spring Boot가 아닙니다

`javax.servlet` + Tomcat 9 기반 **WAR**이며, Java Config(`me/nawa/config/`)로
부트스트랩합니다. `RootConfig`, `ServletConfig`, `SecurityConfig`,
`SecurityWebApplicationInitializer`, `RedisConfig`, `SwaggerConfig`가 그 구성입니다.

- **Spring 6 / Tomcat 10(`jakarta`)으로 임의 전환하지 않습니다.**
- Springfox Swagger 2.9를 유지합니다. 팀이 확정한 결정입니다.
- Boot용 어노테이션과 자동 설정이 여기서는 동작하지 않습니다. 예제를 그대로 옮기지
  마세요.

## 단위 테스트만으로는 안전하지 않습니다

Root context와 Servlet context의 bean 구성이 다릅니다. **전체 테스트와 WAR 빌드가
통과했는데도 런타임 500이 난 적이 있습니다** — `RootConfig`의 component scan 누락
때문에 `/auth/me`가 죽었습니다.

- `RootConfigComponentScanTest`를 유지합니다.
- **새 controller를 추가한 뒤에는 focused test가 아니라 backend 전체 build를 돌립니다.**

```shell
cd backend && ./gradlew build --no-daemon
```

### Springfox 제한 context가 전체 CI를 깨뜨립니다

Swagger 테스트 context가 새 controller를 scan하면서 그 controller가 의존하는 service
bean을 찾지 못해 **관계없는 전체 CI가 실패**할 수 있습니다. 새 controller를 추가했다면
`SwaggerConfigTest`에 최소 fake bean이 필요한지 함께 확인하세요.

## 스키마는 Flyway forward-only

- 마이그레이션은 `src/main/resources/db/migration`에 있습니다.
- **이미 적용된 마이그레이션을 수정하지 않습니다.** 다음 V 번호를 추가합니다.
- 도메인별 ERD 설명은 [docs/database/](./docs/database/README.md)에 있습니다.

## 응답 계약

모든 JSON 응답은 `common/response/ApiResponse` 봉투(`success` + `data` | `error`)를
씁니다. 실패는 `BusinessException` → `GlobalExceptionHandler`로 변환합니다.

오류 코드는 `<DOMAIN>-NNN` 형식(`COMMON-001`, `AUTH-003`, `WALLET-001`)이며,
**프론트엔드는 메시지가 아니라 `error.code`로 분기합니다.** 코드를 바꾸면 화면이
깨지므로 같은 PR에서 프론트와 문서를 함께 고칩니다. 정본은
[API 응답 및 오류 코드 컨벤션](./docs/API_RESPONSE_CONVENTION.md)입니다.

### `LocalDateTime` 필드에는 `@JsonFormat`을 붙입니다

없으면 Jackson이 `[2026, 7, 25, 12, 0]` 형태의 숫자 배열로 직렬화합니다. 프론트는
ISO 문자열을 기대하므로 **날짜만 조용히 사라집니다.** 예외도 로그도 없어 발견이
늦습니다. journey·explore DTO는 이미 붙어 있습니다.

응답 형식을 고정하는 테스트를 함께 두세요. `MockMvcBuilders.standaloneSetup`으로
충분합니다.

## 데이터 소유권

- **MySQL이 영구 원본**입니다. 회원, 소셜 계정과 서비스 데이터.
- **Redis는 TTL이 필요한 것만** 담습니다. OAuth state와 refresh 세션 해시.
- refresh token 원문은 저장하지 않습니다.
- 같은 이메일이라는 이유로 Google·LINE 계정을 병합하지 않습니다. 소셜 식별자는
  `(provider, provider_user_id)`입니다.

## 인증은 확정 계약입니다 — 바꾸기 전에 확인하세요

access cookie `Path=/`, refresh cookie `Path=/api/v1/auth`, 둘 다 HttpOnly.
운영 기본값은 host-only, `Secure=true`, `SameSite=Lax`입니다.

로그인 시작이 발급하는 `oauth_state` 쿠키(`Path=/api/v1/auth`, HttpOnly)는
**`auth.cookie.same-site` 설정과 무관하게 항상 `SameSite=Lax`입니다.** 공급자
콜백이 외부 사이트에서 오는 top-level 이동이라 Strict면 쿠키가 실려 오지 않아
모든 로그인이 실패합니다. 이 쿠키를 다른 두 개와 같은 설정으로 묶지 마세요.

- `/api/**` CORS는 명시적 allowlist + `credentials=true`입니다. wildcard Origin 금지.
- **CORS 책임은 Spring Security 한 곳에 둡니다.** Nginx에서 중복 헤더를 넣지 않습니다.
- refresh 실패 `AUTH-001`, 재사용 감지 `AUTH-002`, 인증 필요 `AUTH-003`.
  로그아웃은 멱등적입니다.

운영 세부는 [소셜 로그인 운영](./docs/AUTHENTICATION.md)에 있습니다.

## 검증 — 자동 테스트 외에 증거를 남깁니다

통합 테스트는 기본 build에서 건너뜁니다. 필요한 플래그를 켜서 따로 돌립니다.

```shell
RUN_REDIS_INTEGRATION_TESTS=true ./gradlew test --no-daemon
RUN_MYSQL_INTEGRATION_TESTS=true ./gradlew test --no-daemon
```

API·인증·DB를 바꿨다면 아래 중 해당하는 것을 확인하고 결과를 PR에 적습니다.

- Swagger JSON / UI가 뜨는지
- 응답 본문과 오류 코드
- MySQL 데이터와 Flyway 적용 결과
- Redis TTL·회전·재사용 감지
- **브라우저 쿠키와 CORS는 API origin이 아니라 실제 `http://localhost:5173`에서
  `credentials: include`로 확인합니다.** `Origin: null`로 확인하지 마세요.
- 실제 WAR/Tomcat 기동과 component scan
