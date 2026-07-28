# NA-WA

KB IT's Your Life 7기 최종 프로젝트입니다.

## 저장소 구조

- `backend`: Spring MVC 기반 백엔드 애플리케이션
- `frontend`: Vue 3 기반 모바일 우선 PWA

프론트엔드는 pnpm workspace로 관리하며, 저장소 루트에서 명령을 실행합니다.

## 프론트엔드 실행 환경

- Node.js `24.18.0`
- pnpm `11.17.0`

macOS의 fish와 fnm을 사용하는 경우 다음과 같이 프로젝트 버전을 활성화합니다.

```fish
fnm install 24.18.0
fnm use 24.18.0
corepack enable pnpm
pnpm --version
```

## 설치 및 실행

```fish
pnpm install
pnpm dev
```

개발 서버는 기본적으로 `http://localhost:5173`에서 실행됩니다.

## 환경변수

프론트엔드 API 주소는 `VITE_API_BASE_URL`로 설정합니다.

- 개발: `frontend/.env.development`
- 운영 플레이스홀더: `frontend/.env.production`
- 개인별 설정: `frontend/.env.development.local`

`VITE_*` 값은 클라이언트 번들에 포함되므로 토큰, 비밀번호, API 비밀키를 저장하지 않습니다.

## 검증 명령

```fish
pnpm format:check
pnpm lint
pnpm type-check
pnpm test:unit
pnpm build
```

Playwright 브라우저는 최초 한 번 설치합니다.

```fish
pnpm --filter @na-wa/frontend exec playwright install
pnpm test:e2e
```

특정 브라우저만 실행하려면 다음 명령을 사용합니다.

```fish
pnpm --filter @na-wa/frontend exec playwright test --project=chromium
```

## 커밋 전 검사

Husky의 pre-commit hook이 실행되며, lint-staged가 변경된 TypeScript·Vue 파일과 웹 설정·문서 파일에만 ESLint와 Prettier를 적용합니다. 백엔드 Java 파일은 대상이 아닙니다.

GitHub Actions, 실제 API 연동, 인증·토큰 처리 로직은 별도 이슈에서 구성합니다.
