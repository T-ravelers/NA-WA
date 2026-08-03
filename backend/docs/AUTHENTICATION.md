# 소셜 로그인 운영 가이드

NA-WA 인증은 Google·LINE OpenID Connect 로그인과 HttpOnly 쿠키 기반 자체 세션을
사용합니다. 프런트엔드는 공급자 토큰을 받거나 저장하지 않으며, 로그인 완료 후
`GET /api/v1/auth/me`로 로그인 상태와 온보딩 필요 여부를 확인합니다.

## API 흐름

| 단계        | 요청                                                            | 결과                                           |
| ----------- | --------------------------------------------------------------- | ---------------------------------------------- |
| CSRF 준비   | `GET /api/v1/auth/csrf`                                         | CSRF 쿠키와 요청 헤더 이름 반환                |
| 로그인 시작 | `GET /api/v1/auth/oauth2/authorization/{provider}?returnPath=/` | Google 또는 LINE으로 `302` 이동                |
| 공급자 콜백 | `GET /api/v1/auth/oauth2/callback/{provider}`                   | 자체 토큰 쿠키 발급 후 프런트엔드로 `302` 이동 |
| 로그인 확인 | `GET /api/v1/auth/me`                                           | 현재 회원 정보와 `onboardingRequired` 반환     |
| 토큰 갱신   | `POST /api/v1/auth/refresh`                                     | access·refresh 쿠키 교체                       |
| 로그아웃    | `POST /api/v1/auth/logout`                                      | Redis 세션 폐기와 인증 쿠키 삭제               |

`provider`는 `google` 또는 `line`만 허용합니다. 성공·실패 리다이렉트 URL에는
토큰이나 개인정보를 넣지 않습니다. 실패 시에는 프런트엔드가 처리할 안정적인
`error` 코드만 전달합니다.

### 로그인 회원 응답

```json
{
  "success": true,
  "data": {
    "memberId": 1,
    "displayName": "여행자",
    "profileImageUrl": null,
    "preferredLanguage": "en",
    "preferredCurrencyCode": null,
    "onboardingRequired": true
  }
}
```

access token이 없거나 유효하지 않으면 `AUTH-003`, 정지 회원은 `AUTH-016`,
탈퇴 또는 삭제 회원은 `AUTH-017`을 반환합니다. 일반 JSON 오류 형식은
[API 응답 및 오류 코드 컨벤션](API_RESPONSE_CONVENTION.md)을 따릅니다.

## 저장소 책임

- MySQL의 `members`, `social_accounts`가 회원과 소셜 계정 연결의 영구 원본입니다.
- 같은 이메일이라는 이유만으로 서로 다른 소셜 계정을 자동 병합하지 않습니다.
- Redis에는 `state`와 refresh token 세션만 TTL과 함께 저장합니다. Redis 데이터는
  재생성 가능한 인증 상태이며 회원 원본으로 사용하지 않습니다.
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
3. `access_token`과 `refresh_token`이 HttpOnly 쿠키로 저장됐는지 확인합니다.
4. `GET /api/v1/auth/me`가 `200`과 회원 정보를 반환하는지 확인합니다.
5. `POST /api/v1/auth/refresh` 후 두 쿠키가 교체되는지 확인합니다.
6. `POST /api/v1/auth/logout` 후 두 쿠키가 삭제되고, 다시 `/me`를 호출하면
   `401 AUTH-003`인지 확인합니다.
7. MySQL에서 동일 `(provider, provider_user_id)`로 회원과 소셜 계정이 중복 생성되지
   않았는지 확인합니다.

Swagger UI는 `http://localhost:8080/swagger-ui.html`에서 확인할 수 있습니다.
OAuth 콜백은 브라우저 리다이렉트 API이므로 Swagger에서 공급자 로그인을 끝까지
진행하는 대신 위 브라우저 스모크 테스트를 사용합니다.
