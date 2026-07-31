# NA-WA

NA-WA는 KB IT's Your Life 7기 T-ravelers 팀이 만드는 모바일 우선 여행 협업
서비스입니다. 이 문서에서 프로젝트 구조를 확인하고 프론트엔드 개발을 시작할 수
있습니다.

## 필요한 문서 찾기

| 하려는 일                          | 문서                                                                      |
| ---------------------------------- | ------------------------------------------------------------------------- |
| Issue, 브랜치, 커밋과 PR 규칙 확인 | [공통 협업 가이드](./CONTRIBUTING.md)                                     |
| 기술 선택과 운영 경계 확인         | [기술 스택과 운영 경계](./docs/TECH_STACK.md)                             |
| 프론트엔드 구조와 현재 상태 확인   | [프론트엔드 안내](./frontend/README.md)                                   |
| 프론트엔드 구현 규칙 확인          | [프론트엔드 개발 컨벤션](./frontend/docs/DEVELOPMENT_CONVENTION.md)       |
| 백엔드 구조와 검증 방법 확인       | [백엔드 안내](./backend/README.md)                                        |
| 백엔드 구현 규칙 확인              | [백엔드 개발 컨벤션](./backend/docs/DEVELOPMENT_CONVENTION.md)            |
| API 성공·실패 응답 계약 확인       | [API 응답 및 오류 코드 컨벤션](./backend/docs/API_RESPONSE_CONVENTION.md) |

## 저장소 구조

```text
NA-WA/
├── frontend/       Vue 3 모바일 우선 PWA
├── backend/        Spring MVC WAR 애플리케이션
├── deploy/         EC2 배포 스크립트
├── nginx/          백엔드 리버스 프록시 설정
└── docs/           공통 기술 문서
```

프론트엔드는 pnpm workspace로 관리합니다. 프론트엔드 명령은 저장소 루트에서
실행하세요.

## 프론트엔드 실행하기

### 실행 환경 준비

- Node.js `24.18.0`
- pnpm `11.17.0`

macOS에서 fnm을 사용한다면 프로젝트 버전을 활성화하세요.

```shell
fnm install 24.18.0
fnm use 24.18.0
corepack enable pnpm
pnpm --version
```

의존성을 설치하고 개발 서버를 실행하세요.

```shell
pnpm install
pnpm dev
```

개발 서버의 기본 주소는 `http://localhost:5173`입니다.

### API 주소 설정

`VITE_API_BASE_URL`에 프론트엔드가 요청할 API 주소를 설정하세요.

| 파일                              | 용도                      |
| --------------------------------- | ------------------------- |
| `frontend/.env.development`       | 팀이 공유하는 개발 기본값 |
| `frontend/.env.production`        | 운영 빌드 기본값          |
| `frontend/.env.development.local` | 개발자별 로컬 값          |

`VITE_*` 값은 클라이언트 번들에 포함됩니다. 토큰, 비밀번호와 API 비밀키를
저장하지 마세요.

### 변경 사항 검증

```shell
pnpm format:check
pnpm lint
pnpm type-check
pnpm --filter @na-wa/frontend test:unit --run
pnpm build
```

사용자 흐름을 변경했다면 Playwright 브라우저를 준비하고 E2E 테스트도 실행하세요.

```shell
pnpm --filter @na-wa/frontend exec playwright install
pnpm test:e2e
```

백엔드 실행과 검증 방법은 [백엔드 안내](./backend/README.md)를 참고하세요.

## CI/CD 범위

- 백엔드 CI는 `main` 대상 PR과 `main` push에서 Gradle 빌드와 테스트를 수행합니다.
- 백엔드 CD는 테스트 성공 후 Docker 이미지를 만들고 EC2의 Docker Compose 환경에
  배포합니다.
- 프론트엔드 CI는 잠금 파일 기반 설치, 포맷, 린트, 타입, 단위 테스트와 프로덕션
  빌드를 검증합니다.
- Vercel은 프론트엔드 Preview와 Production 배포를 담당합니다. GitHub Actions는
  프론트엔드 품질 검증만 담당합니다.

현재 제품 범위에는 채팅과 WebSocket/STOMP 기능이 없습니다. Nginx에 남아 있는
WebSocket upgrade 설정은 프론트엔드 기능 계약이 아닙니다.
