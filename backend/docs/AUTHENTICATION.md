# 소셜 로그인 운영 가이드

NA-WA 인증은 Google·LINE OpenID Connect 로그인과 HttpOnly 쿠키 기반 자체 세션을
사용합니다. 프런트엔드는 공급자 토큰을 받거나 저장하지 않으며, 로그인 완료 후
`GET /api/v1/members/me`로 로그인 상태와 온보딩 필요 여부를 확인합니다.

## API 흐름

| 단계        | 요청                                                            | 결과                                           |
| ----------- | --------------------------------------------------------------- | ---------------------------------------------- |
| CSRF 준비   | `GET /api/v1/auth/csrf`                                         | CSRF 쿠키와 요청 헤더 이름 반환                |
| 로그인 시작 | `GET /api/v1/auth/oauth2/authorization/{provider}?returnPath=/` | 상태 쿠키 발급 후 Google 또는 LINE으로 `302` 이동 |
| 공급자 콜백 | `GET /api/v1/auth/oauth2/callback/{provider}`                   | 자체 토큰 쿠키 발급 후 프런트엔드로 `302` 이동 |
| 로그인 확인 | `GET /api/v1/members/me`                                        | 현재 회원 정보와 `onboardingRequired` 반환     |
| 토큰 갱신   | `POST /api/v1/auth/refresh`                                     | access·refresh 쿠키 교체                       |
| 로그아웃    | `POST /api/v1/auth/logout`                                      | 인증 쿠키 삭제와 Redis 세션 폐기 시도          |

`provider`는 `google` 또는 `line`만 허용합니다. 성공·실패 리다이렉트 URL에는
토큰이나 개인정보를 넣지 않습니다. 실패 시에는 프런트엔드가 처리할 안정적인
`error` 코드만 전달합니다.

### 로그인을 시작한 브라우저에서만 콜백을 완료할 수 있습니다

`GET /api/v1/auth/oauth2/authorization/{provider}`는 공급자로 보내기 전에 난수를
하나 만들어 `oauth_state` 쿠키로 브라우저에 심고, 그 SHA-256 해시를 Redis의 state
세션에 함께 저장합니다. 쿠키는 HttpOnly이며 `Path=/api/v1/auth`, `Max-Age`는 state
TTL(기본 600초)과 같습니다.

**이 쿠키만 `SameSite=Lax`로 고정합니다.** 공급자 콜백은 외부 사이트에서 오는
top-level 이동이라 `AUTH_COOKIE_SAME_SITE=Strict`로 운영하면 쿠키가 실려 오지 않고
모든 로그인이 실패합니다. access·refresh 쿠키는 설정값을 그대로 씁니다.

`GET /api/v1/auth/oauth2/callback/{provider}`는 이 쿠키 값을 다시 해시해 저장된
해시와 상수 시간으로 비교합니다. 쿠키가 없거나 값이 다르면 공급자와 통신하기 전에
`AUTH-014`로 거부합니다. state는 조회와 동시에 Redis에서 삭제되므로 검증에 실패해도
같은 state를 다시 쓸 수 없습니다. 성공·실패·만료 세 경로 모두 응답에서 `oauth_state`
쿠키를 삭제합니다.

쿠키 이름은 `AUTH_OAUTH_STATE_COOKIE_NAME`으로 바꿀 수 있고 기본값은 `oauth_state`
입니다. 브라우저마다 쿠키가 하나이므로 **탭 두 개에서 동시에 로그인을 시작하면
나중에 시작한 쪽만 완료됩니다.**

### 토큰 갱신은 현재 회원 상태를 다시 확인합니다

`POST /api/v1/auth/refresh`는 MySQL에서 현재 회원 상태와 삭제 여부를 확인한 뒤
token을 회전합니다. `ACTIVE` 회원만 기존 Redis 원자적 회전을 거쳐 새
access·refresh 쿠키를 받습니다.

정지 회원은 `403 AUTH-016`, 탈퇴·삭제되었거나 존재하지 않는 회원은
`403 AUTH-017`을 반환합니다. 이때 해당 Redis refresh 세션을 폐기하고 응답에서
access·refresh 쿠키를 삭제합니다. MySQL 오류처럼 현재 상태를 확인할 수 없으면 새
token을 발급하지 않지만, 확인되지 않은 Redis 세션까지 폐기하지는 않습니다.

이미 발급된 access token은 설정된 TTL까지 유효할 수 있습니다. 회원별 전체 세션
인덱스와 access token 즉시 폐기는 별도 운영 정책이 필요한 후속 범위입니다.

### 로그인 상태 확인은 `members/me`가 겸합니다

`GET /api/v1/auth/me`는 더 이상 존재하지 않습니다. auth 도메인은 로그인
시작·콜백, `/csrf`, `/refresh`, `/logout`만 소유하고, 로그인 회원 정보 조회는
`GET /api/v1/members/me`(member 도메인)로 옮겼습니다. `/api/**`는
`SecurityConfig`에서 `authenticated()`이므로 이 엔드포인트는 인증되지 않은
요청에 자동으로 `401`을 반환합니다. 즉 별도의 "세션 확인" 요청 없이
`GET /api/v1/members/me` 호출 하나로 로그인 여부 판정과 프로필 조회를 동시에
끝냅니다. `auth/me`를 그대로 남겨 두면 화면 진입마다 두 번 요청하거나 죽은
엔드포인트가 남는 결과가 되므로, 유지하는 대신 제거했습니다.

### 로그아웃은 브라우저 인증 쿠키를 항상 삭제합니다

`POST /api/v1/auth/logout`은 refresh 세션이 없거나 Redis 세션 폐기에 실패해도
멱등적인 성공 응답과 access·refresh 삭제 쿠키를 반환합니다. 내부 세션 폐기 실패는
서버 오류 로그로 기록합니다. 컨트롤러는 실패 유형과 예외 스택을 남기되 refresh token이나
세션 식별자를 별도 로그 필드로 직접 추가하지 않습니다. 따라서 이 로그의 목적은 개별 고아
키를 찾아 수동 삭제하는 것이 아니라 Redis 이상 상태를 감지하는 것입니다. 폐기하지 못한
키는 설정된 refresh TTL까지 남을 수 있으며 durable 재처리는 현재 범위에 포함하지 않습니다.

이 계약은 서버가 HTTP 응답을 반환한 경우에 적용됩니다. 네트워크 단절로 브라우저가
응답을 받지 못하면 쿠키 삭제를 확인할 수 없으므로 프런트엔드는 성공으로 처리하지
않습니다. 또한 로그아웃 컨트롤러보다 앞선 필터·인터셉터에서 응답 생성이 실패하면 삭제
쿠키를 보장할 수 없습니다. 이러한 불확실한 실패에 대한 클라이언트 장벽은 별도 범위입니다.

### 회원 프로필 응답

```json
{
  "success": true,
  "data": {
    "memberId": 1,
    "displayName": "여행자",
    "profileImageUrl": null,
    "nationalityCode": null,
    "preferredLanguage": "en",
    "preferredCurrencyCode": null,
    "onboardingRequired": true
  }
}
```

`PATCH /api/v1/members/me`는 같은 형태의 응답을 반환하며 `displayName`·
`profileImageUrl`·`nationalityCode`·`preferredLanguage`·`preferredCurrencyCode`를
부분 수정합니다. 필드를 아예 보내지 않는 것과 값에 `null`을 보내는 것은 모두
"변경하지 않음"으로 취급하며, 모든 필드가 없으면 `MEMBER-004`를 반환합니다.
지원 언어는 `en`, `ja`, `zh-TW`, `vi`이며 이 백엔드 allow-list가 정본입니다
(한국어는 서비스 locale이 아닙니다). 목록에 없는 언어는 `MEMBER-002`, 활성 통화
코드가 아니면 `MEMBER-003`, ISO 3166-1 alpha-2가 아닌 국적은 `MEMBER-005`를
반환합니다. 국적은 대소문자를 가리지 않고 받아 대문자로 저장합니다. 표시 이름
길이는 code point 기준 50자입니다. 프로필 이미지는 `http`·`https` URL만 받습니다 —
이 값은 다른 회원 화면에 이미지로 렌더되므로 그 밖의 스킴은 `MEMBER-007`로
거부합니다.

`PATCH /api/v1/members/me/onboarding`은 온보딩 프로필을 저장하고 완료를
기록합니다. `displayName`·`nationalityCode`·`preferredLanguage`·
`preferredCurrencyCode` 네 필드가 모두 필수이며 하나라도 없으면 `MEMBER-008`을
반환합니다. 성공 시 `onboarding_completed_at`이 기록되어 이후 응답의
`onboardingRequired`가 `false`가 됩니다. 재호출은 값만 갱신하고 완료 시각은
최초 값을 유지합니다(멱등). 검증 규칙과 오류 코드는 `PATCH /me`와 같습니다.
서버는 온보딩 미완료 회원의 다른 업무 API를 차단하지 않습니다 — 미완료 회원의
화면 진입 통제는 프런트엔드 라우터 guard가 담당합니다.

access token이 없거나 유효하지 않으면 `AUTH-003`, 정지 회원은 `AUTH-016`,
탈퇴 또는 삭제 회원은 `AUTH-017`을 반환합니다. 회원을 찾을 수 없으면
`MEMBER-001`을 반환합니다. 일반 JSON 오류 형식은
[API 응답 및 오류 코드 컨벤션](API_RESPONSE_CONVENTION.md)을 따릅니다.

## 저장소 책임

- MySQL의 `members`, `social_accounts`가 회원과 소셜 계정 연결의 영구 원본입니다.
- 같은 이메일이라는 이유만으로 서로 다른 소셜 계정을 자동 병합하지 않습니다.
- Redis에는 `state`와 refresh token 세션만 TTL과 함께 저장합니다. Redis 데이터는
  재생성 가능한 인증 상태이며 회원 원본으로 사용하지 않습니다.
- `state` 세션에는 브라우저 상태 쿠키의 원본이 아니라 SHA-256 해시만 넣습니다.
- access token은 짧게 유지하고, refresh token은 회전시킵니다. 재사용이 감지되면
  해당 세션을 폐기합니다.

기본 TTL은 access token 15분, refresh token 14일, OAuth state 10분입니다.

## 로컬 설정

1. `backend/.env.example`을 복사해 `backend/.env.local`을 만들고 실제 값을
   입력합니다. 이 파일은 애플리케이션이 자동으로 읽지 않으므로 IntelliJ의 Tomcat
   실행 구성에 환경 변수 파일로 연결합니다.
2. `JWT_SECRET`에는 32바이트 이상의 난수를 Base64로 인코딩한 값을 사용합니다.
3. Google Console과 LINE Developers Console에 아래 로컬 콜백을 정확히 등록합니다.

```text
http://localhost:8080/api/v1/auth/oauth2/callback/google
http://localhost:8080/api/v1/auth/oauth2/callback/line
```

LINE은 NA-WA 서비스용 Provider 하나 아래에 LINE Login Channel 하나를 두는 것을
기본으로 합니다. 국가별 약관·운영 주체·회원 데이터 분리가 실제로 필요할 때만
Provider를 분리합니다. 현재 웹 로그인에는 LIFF 앱이 필요하지 않습니다.

비밀값은 Git에 커밋하지 않습니다. 팀에는 변수 이름과 비밀이 아닌 기본값만
`.env.example`로 공유하고, 실제 비밀값은 팀이 합의한 비밀 저장소나 접근 제한된
채널로 전달합니다.

## 배포 설정

EC2의 `~/nawa/.env`에 `docker-compose.yml`이 참조하는 인증 환경 변수를
등록합니다. 특히 다음 값은 운영 배포 전에 반드시 실제 운영 주소와 비밀값으로
설정해야 합니다.

```text
JWT_SECRET
AUTH_FRONTEND_SUCCESS_URL
AUTH_FRONTEND_FAILURE_URL
AUTH_ALLOWED_ORIGINS
GOOGLE_OAUTH_CLIENT_ID
GOOGLE_OAUTH_CLIENT_SECRET
GOOGLE_OAUTH_REDIRECT_URI
LINE_OAUTH_CLIENT_ID
LINE_OAUTH_CLIENT_SECRET
LINE_OAUTH_REDIRECT_URI
```

운영 콜백 URL은 각각 Google Console과 LINE Developers Console에도 동일하게
등록합니다. 운영에서는 HTTPS를 사용하고 `AUTH_COOKIE_SECURE=true`를 유지합니다.
CORS는 Spring Security가 단일 책임을 가지며 Nginx에서 별도 CORS 헤더를 추가하지
않습니다.

## 수동 스모크 테스트

Tomcat을 재시작하고 브라우저의 네트워크 탭을 연 뒤 Google과 LINE을 각각
확인합니다.

1. `/api/v1/auth/oauth2/authorization/google?returnPath=/` 또는 `line` 경로를 엽니다.
2. 공급자 동의 후 프런트엔드 콜백으로 돌아오며 URL에 토큰·이메일이 없는지 봅니다.
3. `access_token`과 `refresh_token`이 HttpOnly 쿠키로 저장되고 `oauth_state` 쿠키가
   삭제됐는지 확인합니다.
4. `GET /api/v1/members/me`가 `200`과 회원 정보를 반환하는지 확인합니다.
5. `POST /api/v1/auth/refresh` 후 두 쿠키가 교체되는지 확인합니다.
6. `POST /api/v1/auth/logout` 후 두 쿠키가 삭제되고, 다시
   `GET /api/v1/members/me`를 호출하면 `401 AUTH-003`인지 확인합니다.
7. MySQL에서 동일 `(provider, provider_user_id)`로 회원과 소셜 계정이 중복 생성되지
   않았는지 확인합니다.
8. 로그인을 시작한 뒤 공급자 화면에 머문 상태로 `oauth_state` 쿠키를 지우고 동의를
   마칩니다. 프런트엔드 실패 URL로 `error=AUTH-014`가 오는지 확인합니다.

Swagger UI는 `http://localhost:8080/swagger-ui.html`에서 확인할 수 있습니다.
OAuth 콜백은 브라우저 리다이렉트 API이므로 Swagger에서 공급자 로그인을 끝까지
진행하는 대신 위 브라우저 스모크 테스트를 사용합니다.

## 부하 테스트 로그인 경로

소셜 로그인은 브라우저 동의 화면을 사람이 거쳐야 완료됩니다. k6 같은 부하 도구는
그 화면을 통과할 수 없어 시나리오 첫 단계에서 막힙니다. 그 구간만 건너뛰는 경로를
따로 둡니다.

```text
POST /internal/loadtest/login
{ "secret": "<LOADTEST_LOGIN_SECRET>", "memberId": 1234 }
```

성공하면 정상 로그인과 **같은** `access_token`·`refresh_token` 쿠키를 발급합니다.
토큰 발급과 검증은 기존 `AuthTokenService`·`AuthCookieManager`를 그대로 쓰므로 인증
체계가 갈라지지 않습니다.

`ServiceTokenController`와 달리 **대상 회원을 요청이 고릅니다.** 부하 테스트는 수천
명이 서로 다른 계정으로 동시에 접속하는 상황을 만들어야 하기 때문입니다. 대신 그
설계는 이 경로가 운영에 존재하지 않는다는 전제 위에서만 성립합니다.

### 필터 두 개를 함께 면제해야 합니다

이 경로는 POST라 컨트롤러에 닿기 전에 두 검사를 지납니다. **`permitAll`과는 별개로
적용되므로** 하나라도 빠지면 403으로 끊깁니다.

| 검사 | 빠졌을 때 | 등록할 곳 |
| --- | --- | --- |
| Origin | `AUTH-006` | `OriginValidationFilter.ORIGINLESS_PATHS` |
| CSRF | `AUTH-005` | `SecurityConfig`의 `csrf.ignoringRequestMatchers` |

`/api/v1/auth/service-token`이 같은 이유로 두 곳 모두에 등록돼 있습니다. `/internal/metrics`는
**GET**이라 두 검사 어디에도 걸리지 않으므로 이 경로의 선례가 되지 못합니다.

`SecurityConfigTest.loadTestLogin_passesOriginAndCsrfFilters`가 둘 다 고정합니다.

### 실행 환경

이 경로는 **`-Ploadtest`로 빌드한 산출물에만** 있고, nginx가 최상위 `/internal/`을 404로
막으며 운영 compose는 backend 포트를 공개하지 않습니다. 즉 컨테이너 포트에 직접 붙는
별도 환경에서만 부를 수 있습니다.

```shell
# 이미지를 부하 테스트용으로 만든다
docker build --build-arg GRADLE_ARGS=-Ploadtest -t nawa-backend:loadtest ./backend

# 컨테이너에 시크릿을 넣고 포트를 127.0.0.1 에만 연다
LOADTEST_LOGIN_SECRET=$(openssl rand -hex 16)
```

> **이미지 태그를 운영과 나눠 쓰세요.** 배포는 `nawa-backend:latest` 하나를 봅니다.
> `-Ploadtest`로 만든 이미지를 그 태그로 push하면 **다음 배포에 그대로 들어갑니다.**
> 위처럼 `:loadtest` 같은 별도 태그를 쓰고, 절대 `latest`로 push하지 않습니다.

부하 테스트 환경 전체 구성은 별도 이슈에서 다룹니다.

### 운영에 들어가지 않게 하는 두 겹

| 겹 | 내용 |
| --- | --- |
| 빌드 | 클래스가 `backend/src/loadtest/java`에 있고, `build.gradle`이 `-Ploadtest`를 준 빌드에서만 컴파일합니다. 배포 워크플로는 이 플래그를 넘기지 않으므로 **운영 이미지에는 클래스 자체가 없습니다** |
| 런타임 | `LOADTEST_LOGIN_SECRET`이 비어 있으면 요청을 거부합니다 (`AUTH-003`) |

여기에 nginx가 `/internal/` 접두사를 `404`로 막는 것까지 더해집니다.

확인 방법입니다.

```shell
# 운영과 같은 빌드 — 결과가 없어야 한다
./gradlew war --no-daemon && unzip -l build/libs/*.war | grep -i loadtest

# 부하 테스트용 빌드 — 클래스가 나와야 한다
./gradlew war --no-daemon -Ploadtest && unzip -l build/libs/*.war | grep -i loadtest
```

이 격리는 **조용히 깨집니다.** 클래스를 `src/main/java`로 옮기거나 `build.gradle`의
조건을 지워도 빌드는 성공하고 다른 테스트도 다 통과합니다.
`LoadTestSourceIsolationTest`가 플래그 상태와 클래스패스 실제 상태를 대조해 막습니다.
