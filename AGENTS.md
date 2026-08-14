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

프론트엔드 명령은 **저장소 루트**에서, 백엔드 명령은 `backend/`에서 실행합니다.

## 고정된 것 — 임의로 올리지 않는다

Node `24.18.0` · pnpm `11.17.0` · Java `17`

버전은 `.node-version`과 `backend/build.gradle`에 고정돼 있습니다. 로컬에서 다른 버전이
잡히면 도구가 원인 불명으로 실패하므로, 코드를 의심하기 전에 버전부터 확인합니다.

## 작업 전에 반드시 확인할 것

- **`main`에 직접 push하지 않습니다.** 흐름은 `Issue → 작업 브랜치 → main 대상 PR → 리뷰
  → Squash and merge`입니다. 규칙은 [CONTRIBUTING.md](./CONTRIBUTING.md)가 정본입니다.
- 커밋 메시지에 `Co-Authored-By` 트레일러를 넣지 않습니다. 기존 이력에도 없습니다.
- 관련 없는 프론트엔드/백엔드 변경을 한 PR에 섞지 않습니다.
- **API·환경 변수·배포·공통 계약을 바꾸면 같은 PR에서 관련 문서와 이 파일도 고칩니다.**
- 성공 `ApiResponse.data`의 런타임 검증은 요청별 `AxiosRequestConfig.responseSchema`로
  선택합니다. 스키마는 해당 feature의 `api/` 폴더가 소유하고, 공용 계층은 feature를
  import하지 않습니다. 설정하지 않은 요청은 기존 봉투 해제·인증/CSRF 재시도 동작을
  그대로 유지합니다.
- 응답 검증 실패도 `NormalizedApiError`의 `UNKNOWN`으로 정규화합니다. 진단 로그에는
  URL·method·HTTP 상태와 스키마 issue의 path/code/expected만 남기며 응답 본문·인증정보·
  개인정보·원본 오류 객체를 출력하지 않습니다.
- 로그아웃 응답이 불확실할 때는 브라우저 장벽이 보호 경로와 refresh를 함께 차단합니다.
  장벽은 서버 로그아웃 성공 또는 새 로그인 callback 성공에서만 해제합니다.

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
| 정산 API·상태·멱등성 | [backend/docs/SETTLEMENT.md](./backend/docs/SETTLEMENT.md)                  |
| QR 결제 API·상태·멱등성 | [backend/docs/QR_PAYMENT_API.md](./backend/docs/QR_PAYMENT_API.md)      |
| 소셜 로그인 운영     | [backend/docs/AUTHENTICATION.md](./backend/docs/AUTHENTICATION.md)          |
| DB 지도·도메인 ERD   | [backend/docs/database/README.md](./backend/docs/database/README.md)         |

문서와 코드가 어긋나면 **조용히 한쪽을 고르지 말고 보고합니다.** 우선순위는
현재 `main`의 런타임 동작 → 적용된 Flyway DDL → 병합된 문서 순입니다.
