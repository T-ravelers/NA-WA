# NA-WA

KB IT's Your Life 7기 T-ravelers 팀의 모바일 우선 여행 협업 서비스입니다.

## 문서

- [공통 협업 가이드](./CONTRIBUTING.md)
- [기술 스택과 운영 경계](./docs/TECH_STACK.md)
- [프론트엔드 안내](./frontend/README.md)
- [프론트엔드 개발 컨벤션](./frontend/docs/DEVELOPMENT_CONVENTION.md)
- [백엔드 안내](./backend/README.md)
- [백엔드 개발 컨벤션](./backend/docs/DEVELOPMENT_CONVENTION.md)

## 저장소 구조

```text
NA-WA/
├── frontend/       Vue 3 모바일 우선 PWA
├── backend/        Spring MVC WAR 애플리케이션
├── deploy/         EC2 배포 스크립트
├── nginx/          백엔드 리버스 프록시 설정
└── docs/           공통 기술 문서
```

프론트엔드는 pnpm workspace로 관리하며 관련 명령은 저장소 루트에서 실행합니다.

## 프론트엔드 빠른 시작

### 실행 환경

- Node.js `24.18.0`
- pnpm `11.17.0`

macOS에서 fnm을 사용하는 경우 프로젝트 버전을 활성화합니다.

```shell
fnm install 24.18.0
fnm use 24.18.0
corepack enable pnpm
pnpm --version
```

의존성을 설치하고 개발 서버를 실행합니다.

```shell
pnpm install
pnpm dev
```

개발 서버의 기본 주소는 `http://localhost:5173`입니다.

### 환경 변수

프론트엔드 API 주소는 `VITE_API_BASE_URL`로 설정합니다.

- 개발 공통값: `frontend/.env.development`
- 운영 기본값: `frontend/.env.production`
- 개인별 개발값: `frontend/.env.development.local`

`VITE_*` 값은 클라이언트 번들에 포함됩니다. 토큰, 비밀번호, API 비밀키를 저장하지
않습니다.

### 검증 명령

```shell
pnpm format:check
pnpm lint
pnpm type-check
pnpm --filter @na-wa/frontend test:unit --run
pnpm build
```

Playwright 브라우저는 최초 한 번 설치합니다.

```shell
pnpm --filter @na-wa/frontend exec playwright install
pnpm test:e2e
```

## CI/CD 현황

- 백엔드: `main` 대상 PR과 `main` push에서 Gradle 빌드·테스트를 수행합니다.
- 백엔드 배포: `main` 테스트 성공 후 Docker 이미지를 빌드하고 EC2의 Docker Compose
  환경에 배포합니다.
- 프론트엔드: Vercel이 Preview·Production 배포를 담당한다는 운영 방침을 사용합니다.
  Vercel 연결과 프론트엔드 GitHub Actions 검증은 후속 작업입니다.

현재 제품 범위에는 채팅과 WebSocket/STOMP 기능이 없습니다. 병합된 Nginx 설정의
WebSocket upgrade 처리는 프론트엔드 기능 계약으로 간주하지 않습니다.
