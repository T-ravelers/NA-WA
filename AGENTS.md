# AGENTS.md

이 저장소에서 코딩 에이전트가 먼저 읽는 파일입니다. **규칙의 본문은 여기에 두지 않고
기존 문서를 가리킵니다.** 같은 규칙이 두 곳에 있으면 반드시 어긋나기 때문입니다.

여기에 적는 것은 **실제로 어긴 적이 있는 규칙**과 **모르면 반드시 틀리는 전제**뿐입니다.

## 저장소 구조

| 경로         | 내용                                  | 전용 규칙                                |
| ------------ | ------------------------------------- | ---------------------------------------- |
| `frontend/`  | Vue 3 모바일 우선 PWA (pnpm workspace) | [frontend/AGENTS.md](./frontend/AGENTS.md) |
| `backend/`   | Spring MVC WAR                        | [backend/AGENTS.md](./backend/AGENTS.md) |
| `docs/`      | 공통 기술 문서                        | —                                        |
| `deploy/`    | EC2 배포 스크립트                     | —                                        |
| `nginx/`     | 백엔드 리버스 프록시 설정             | —                                        |
| `loadtest/`  | k6 부하 테스트 환경(시드·시나리오·관측 스택) | [loadtest/README.md](./loadtest/README.md) |

프론트엔드 명령은 **저장소 루트**에서, 백엔드 명령은 `backend/`에서 실행합니다.

## 고정된 것 — 임의로 올리지 않는다

Node `24.18.0` · pnpm `11.17.0` · Java `17`

버전은 `.node-version`과 `backend/build.gradle`에 고정돼 있습니다. 로컬에서 다른 버전이
잡히면 도구가 원인 불명으로 실패하므로, 코드를 의심하기 전에 버전부터 확인합니다.

`renovate.json`도 Node·pnpm 버전을 올리지 않게 막고 `@types/node`를 Node 24에 맞춰
`<25`로 묶습니다. Node major를 올릴 때는 이 상한도 함께 고칩니다. 고치지 않으면
`@types/node`가 옛 major에 조용히 멈춥니다.

## 작업 전에 반드시 확인할 것

- **`main`에 직접 push하지 않습니다.** 흐름은 `Issue → 작업 브랜치 → main 대상 PR → 리뷰
  → Squash and merge`입니다. 규칙은 [CONTRIBUTING.md](./CONTRIBUTING.md)가 정본입니다.
- 커밋 메시지에 `Co-Authored-By` 트레일러를 넣지 않습니다. 기존 이력에도 없습니다.
- 관련 없는 프론트엔드/백엔드 변경을 한 PR에 섞지 않습니다.
- **API·환경 변수·배포·공통 계약을 바꾸면 같은 PR에서 관련 문서와 이 파일도 고칩니다.**
- **운영 프론트엔드는 `https://na-wa.cloud`, 백엔드는 `https://api.na-wa.cloud`입니다.**
  TLS는 ALB의 ACM 인증서가 443에서 종료하고, nginx는 ALB가 넘기는 평문 80만 받습니다.
  인증서 발급·갱신은 ACM이 맡아 이 저장소에 certbot·443 설정이 없습니다. EC2는 private
  서브넷에 있고 보안 그룹이 ALB의 80만 허용하므로 nginx에 직접 닿는 경로가 없습니다.
  헬스 체크용 `/alb-health`는 항상 백엔드로 프록시하고, `X-Forwarded-Proto`는 아는 값
  (`http`, `https`)만 통과시켜 나머지는 실제 수신 프로토콜로 되돌립니다 — 그렇지 않으면
  클라이언트가 헤더 한 줄로 프로토콜을 위조할 수 있습니다. 이 구조에서 certbot이나
  443 `server` 블록을 되살리면 새 서버에서 인증서 파일이 없어 nginx가 시작하지 못합니다.
- **최상위 `/internal/`은 외부에 열지 않습니다.** nginx가 80에서 `404`로
  막습니다. 지금은 Micrometer 지표(`/internal/metrics`)가 여기에 있는데, JVM 힙·
  스레드 상태가 인증 없이 공개되면 내부 구조가 그대로 드러납니다. Spring Security
  쪽은 `permitAll`이라 **이 한 줄만 보고 "열려 있다"고 판단하면 안 됩니다** — 수집기가
  nginx를 거치지 않고 컨테이너 포트로 직접 읽어야 해서 그렇고, 실제 차단은 nginx와
  "운영 compose가 backend 포트를 공개하지 않는 것" 두 겹이 맡습니다.
  **`/api/v1/internal/...`은 이것과 별개입니다** — 적재 파이프라인처럼 인증을 거쳐
  쓰는 API 경로이고, 위 nginx 규칙은 최상위 `/internal/`만 매칭하므로 그쪽에는 닿지
  않습니다. 둘을 같은 규칙으로 읽지 마세요. 수집기처럼 **외부에 아예 노출하지 않을**
  경로를 새로 만들 때만 최상위 `/internal/`을 쓰면 차단 규칙을 늘리지 않아도 됩니다.
- **부하 테스트 전용 코드는 `backend/src/loadtest/java`에 둡니다.** `build.gradle`이
  `-Ploadtest`를 준 빌드에서만 컴파일 대상에 넣고, 배포 워크플로는 그 플래그를 넘기지
  않습니다. 지금 여기 있는 로그인 경로(`/internal/loadtest/login`)는 공유 비밀만 맞으면
  **임의 회원으로 로그인시켜 줍니다** — 운영에 들어가면 인증이 없는 것과 같습니다.
  같은 소스셋의 Stripe 클라이언트는 부하 테스트가 외부 결제망을 호출하지 않도록
  로컬 성공 응답으로 바꿉니다. 운영에서 이 스텁을 쓰면 결제 검증이 사라집니다.
  환경변수 게이트(`LOADTEST_LOGIN_SECRET`)는 두 번째 방어선일 뿐이니 둘 다 유지하세요.
  이 격리는 조용히 깨집니다(클래스를 `src/main/java`로 옮겨도 빌드가 성공합니다).
  `LoadTestSourceIsolationTest`가 플래그 상태와 클래스패스를 대조해 막습니다.
- **시간대는 세 곳에서 따로 정해집니다.** 백엔드 컨테이너의 `TZ=Asia/Seoul`,
  `docker-compose.yml` `mysql`의 `--default-time-zone=+09:00`, `DATABASE_URL`의
  `serverTimezone`입니다. 마지막 것은 **드라이버 설정일 뿐이라 DB가 평가하는
  `CURRENT_TIMESTAMP`를 바꾸지 못합니다** — "URL에 Asia/Seoul이 있으니 맞춰져 있다"는
  판단이 실제로 약속 전환을 9시간 밀리게 했습니다. 그리고 셋을 맞추더라도 `WHERE`·
  `ORDER BY`에서 `NOW()`로 분기하지 말고 앱이 넘긴 시각을 쓰세요. CI는 MySQL을 일부러
  UTC로 둬서 이 의존을 잡습니다. 자세한 것은 [docs/TECH_STACK.md](./docs/TECH_STACK.md).
- 리다이렉트 응답에는 CORS 헤더가 없습니다. 프론트의 `VITE_API_BASE_URL`이 `http://`로
  남아 있으면 브라우저가 preflight 단계에서 차단해 **모든 API 호출이 실패합니다.**
  백엔드 도메인·프로토콜을 바꿀 때는 Vercel 환경 변수와 EC2 `.env`의
  `AUTH_ALLOWED_ORIGINS`·OAuth redirect URI를 같은 시점에 맞춥니다.
- 성공 `ApiResponse.data`의 런타임 검증은 요청별 `AxiosRequestConfig.responseSchema`로
  선택합니다. 스키마는 해당 feature의 `api/` 폴더가 소유하고, 공용 계층은 feature를
  import하지 않습니다. 설정하지 않은 요청은 기존 봉투 해제·인증/CSRF 재시도 동작을
  그대로 유지합니다.
- 바이너리(`responseType: 'blob'`)로 받는 요청은 실패하면 오류 본문도 Blob으로 옵니다.
  공용 인터셉터가 이를 CSRF·401 판정보다 **먼저** 글자로 풀어 오류 코드를 살립니다. 순서를
  뒤로 미루면 바이너리 요청만 재시도에서 조용히 빠집니다.
- 응답 검증 실패도 `NormalizedApiError`의 `UNKNOWN`으로 정규화합니다. 진단 로그에는
  URL·method·HTTP 상태와 스키마 issue의 path/code/expected만 남기며 응답 본문·인증정보·
  개인정보·원본 오류 객체를 출력하지 않습니다.
- 로그아웃 응답이 불확실할 때는 브라우저 장벽이 보호 경로와 refresh를 함께 차단합니다.
  장벽은 서버 로그아웃 성공 또는 새 로그인 callback 성공에서만 해제합니다.
- 영수증 이미지 S3 버킷은 **백엔드가 도는 AWS 계정이 아닌 다른 계정**이 소유합니다.
  그래서 EC2 인스턴스 역할로 권한을 얹지 못하고, 버킷 소유 계정이 발급한 IAM 사용자 키를
  `AWS_ACCESS_KEY_ID`·`AWS_SECRET_ACCESS_KEY`로 받아 씁니다. 이 정적 자격증명을 인스턴스
  역할로 "정리"하면 운영에서 접근이 끊깁니다. IAM 정책이 `receipts/` 접두사로 좁혀져 있어
  객체 키가 이 접두사를 벗어나면 런타임에 `AccessDenied`가 납니다.
- 영수증 사진은 **정산보다 먼저** 올립니다. 정산 품목이 영수증에서 나온 값이라, 품목을
  먼저 확정하고 사진을 나중에 붙이면 그 사진이 품목의 근거라는 보장이 사라집니다.
  `POST /api/v1/settlement-receipts`로 받은 `receiptId`를 정산 생성 요청에 실어 연결하고,
  **연결된 뒤에는 교체하지 않습니다.** 이 순서를 뒤집으면 영수증 OCR을 얹을 자리가 없어집니다.
- 영수증 글자 인식(CLOVA OCR)은 **정산에 붙지 않은 자기 초안 사진**에만 씁니다. 결과는
  저장하지 않고 사용자가 확인·수정한 값만 정산 생성 요청으로 저장됩니다. 인식 결과를
  저장하면 사용자가 고친 값과 원래 읽은 값 중 무엇이 그 정산의 근거인지 알 수 없게 됩니다.
  업로드가 받아주는 형식 중 **webp만 CLOVA가 읽지 못합니다** — 서버에서 형식을 바꿔 보내면
  사용자가 확인한 사진과 인식에 쓰인 사진이 달라지므로 바꾸지 않고 거절합니다.
  `CLOVA_OCR_INVOKE_URL`과 `CLOVA_OCR_SECRET_KEY`는 도메인 하나에서 함께 나오는 한 쌍이라
  하나만 채우면 서버가 시작할 때 멈추고, 둘 다 비우면 인식 기능만 꺼집니다.
- 이 백엔드는 Spring Boot가 아니라서 `spring.servlet.multipart.*`가 동작하지 않습니다.
  업로드 크기는 `WebConfig`의 `MultipartConfigElement`가 `RECEIPT_MAX_UPLOAD_BYTES`
  환경변수로 정하고, `ServletConfig`의 `multipartResolver` 빈이 요청을 해석합니다.
  `nginx/nginx.conf`의 `client_max_body_size`가 이 값보다 작으면 요청이 백엔드에 닿기도
  전에 잘려서 애플리케이션이 오류 코드를 돌려줄 기회조차 없습니다. 둘을 함께 조정합니다.
- 웹 푸시는 **알림 목록을 대신하지 않습니다.** 푸시는 최선 노력(재시도 없음, 대기열이 차면
  버림)이고 놓친 알림은 15초 폴링이 메웁니다. 푸시가 실패해도 정산·알림 적재는 영향받지 않아야
  해서 `PushSender`는 예외를 밖으로 던지지 않습니다. 로그에는 구독 번호와 상태 코드만 남깁니다 —
  endpoint 경로는 그 기기로 보낼 권한을 가리키는 값이라 남기지 않습니다.
- 푸시 구독의 `endpoint`는 사용자가 적어 보낸 값인데 **서버가 직접 POST하는 주소**입니다. 그래서
  `https`이면서 알려진 푸시 서비스 도메인일 때만 받습니다(`PushSubscriptionServiceImpl`의
  `PUSH_SERVICE_DOMAINS`). 새 브라우저의 구독이 400으로 막히면 그 도메인을 목록에 더하되, 사설
  주소로 풀릴 수 있는 호스트는 더하지 않습니다. 이 검사를 풀면 서버가 임의의 주소로 요청을
  보내게 됩니다(SSRF).
- `VAPID_PUBLIC_KEY`와 `VAPID_PRIVATE_KEY`는 한 쌍이라 하나만 채우면 서버가 시작할 때 멈추고,
  둘 다 비우면 푸시만 꺼집니다. **키를 바꾸면 이미 구독한 기기는 새 키로 다시 구독하기 전까지
  푸시를 받지 못합니다.** 앱이 그 기기에서 열릴 때 구독을 만든 키와 지금 공개키를 비교해 자동으로
  다시 구독하므로, 앱을 열지 않은 기기는 열 때까지 받지 못합니다. 공개키는 프론트 환경 변수가 아니라
  `GET /api/v1/push-subscriptions/public-key`로 내려줍니다. 서비스 워커는 `generateSW`가
  아니라 `frontend/src/sw.ts`에 직접 적습니다.

## 검증

변경한 영역의 검증을 모두 실행하고, **CI 성공과 로컬 성공을 구분해서** 보고합니다.
실제로 확인하지 않은 원격·런타임 결과를 성공했다고 말하지 않습니다.

```shell
# 프론트엔드 (저장소 루트에서)
pnpm format:check && pnpm lint && pnpm type-check
pnpm --filter @na-wa/frontend test:unit --run
pnpm build

# 백엔드
cd backend && ./gradlew build --no-daemon
```

## 비밀값

비밀값, 토큰, OAuth secret, 실제 DB 접속정보를 출력하거나 커밋하지 않습니다.
**이 저장소는 공개입니다.** 보안 취약점의 위치와 성격은 Issue 본문이나 커밋 메시지에도
적지 않습니다.

환경 변수는 `.env.example`을 복사해 씁니다. 값을 코드의 기본값으로 대체하지 않습니다.

## 문서 지도

| 알고 싶은 것         | 문서                                                                        |
| -------------------- | --------------------------------------------------------------------------- |
| 협업·PR 규칙         | [CONTRIBUTING.md](./CONTRIBUTING.md)                                        |
| 설치·실행·환경 변수  | [README.md](./README.md)                                                    |
| 기술 선택과 운영 경계 | [docs/TECH_STACK.md](./docs/TECH_STACK.md)                                   |
| 프론트엔드 구현 규칙 | [frontend/docs/DEVELOPMENT_CONVENTION.md](./frontend/docs/DEVELOPMENT_CONVENTION.md) |
| 백엔드 구현 규칙     | [backend/docs/DEVELOPMENT_CONVENTION.md](./backend/docs/DEVELOPMENT_CONVENTION.md) |
| API 응답·오류 코드   | [backend/docs/API_RESPONSE_CONVENTION.md](./backend/docs/API_RESPONSE_CONVENTION.md) |
| Journey 설정 수정 API | [backend/docs/JOURNEY_API.md](./backend/docs/JOURNEY_API.md)                  |
| 약속·보증금·후기 ENUM·상태 전이 | [backend/docs/APPOINTMENT_DEPOSIT_STATE_MACHINE.md](./backend/docs/APPOINTMENT_DEPOSIT_STATE_MACHINE.md) |
| 정산 API·상태·멱등성 | [backend/docs/SETTLEMENT.md](./backend/docs/SETTLEMENT.md)                  |
| 알림 API·폴링 주기   | [backend/docs/NOTIFICATION_API.md](./backend/docs/NOTIFICATION_API.md)      |
| QR 결제 API·상태·멱등성 | [backend/docs/QR_PAYMENT_API.md](./backend/docs/QR_PAYMENT_API.md)      |
| 소비 카테고리 값 집합·칭호 | [backend/docs/SPENDING_CATEGORY.md](./backend/docs/SPENDING_CATEGORY.md) |
| 리포트 스냅샷·비교 API | [backend/docs/REPORT_API.md](./backend/docs/REPORT_API.md)                  |
| 소셜 로그인 운영     | [backend/docs/AUTHENTICATION.md](./backend/docs/AUTHENTICATION.md)          |
| 아바타 S3 저장 계약  | [backend/docs/AVATAR_STORAGE.md](./backend/docs/AVATAR_STORAGE.md)          |
| DB 지도·도메인 ERD   | [backend/docs/database/README.md](./backend/docs/database/README.md)         |

문서와 코드가 어긋나면 **조용히 한쪽을 고르지 말고 보고합니다.** 우선순위는
현재 `main`의 런타임 동작 → 적용된 Flyway DDL → 병합된 문서 순입니다.
