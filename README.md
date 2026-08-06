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

## 로컬 전체 스택 한 번에 실행하기

Docker와 Docker Compose만 있으면 IDE나 로컬 Node/Java 설치 없이 프론트엔드,
백엔드, MySQL, Redis를 한 번에 띄울 수 있습니다.

```shell
docker compose up
```

`docker-compose.yml`(운영 배포용)과 `docker-compose.override.yml`(로컬 개발
전용)이 자동으로 합쳐져 아래 서비스가 뜹니다.

| 서비스   | 접속 주소               |
| -------- | ------------------------ |
| frontend | http://localhost:5173    |
| backend  | http://localhost:8080    |
| mysql    | localhost:3306           |
| redis    | localhost:6379           |

- `docker-compose.override.yml`은 로컬 전용이며 EC2로는 배포되지 않습니다
  (`.github/workflows/deploy.yml`이 `docker-compose.yml`, `nginx/nginx.conf`,
  `deploy/deploy.sh`만 전송합니다). 운영에만 필요한 `nginx` 서비스는 로컬에서
  기본적으로 실행되지 않습니다.
- 프론트엔드는 `frontend/` 디렉터리를 컨테이너에 바인드 마운트하므로 소스를
  고치면 Vite가 자동으로 반영합니다.
- 백엔드는 Spring Legacy WAR라 소스를 바꾸면 다시 빌드해야 반영됩니다.

```shell
docker compose up -d --build backend
```

- 루트 `.env` 파일에 `MYSQL_ROOT_PASSWORD`, `MYSQL_DATABASE`, `MYSQL_USER`,
  `MYSQL_PASSWORD`, `DOCKERHUB_USERNAME`을 설정해야 합니다. `.env`는 Git에
  커밋하지 않습니다.
- 로컬 3306/8080 포트를 다른 프로세스(네이티브 MySQL, IDE에서 띄운 Tomcat
  등)가 이미 쓰고 있다면 포트 충돌이 발생하니 먼저 정리하세요.

컨테이너는 다음 명령으로 종료합니다.

```shell
docker compose down
```

## 프론트엔드 실행하기

### 실행 환경 준비

- Node.js `24.18.0`
- pnpm `11.17.0`

버전은 저장소 루트의 `.node-version`에 고정돼 있습니다. Node 버전 관리자로 fnm을
사용한다면 아래를 따르세요. 다른 관리자(nvm, Volta)를 쓰거나 Node를 직접 설치했다면
`24.18.0`이 잡히는지만 확인하면 됩니다.

#### macOS

```shell
brew install fnm
```

`~/.zshrc`(zsh) 또는 `~/.config/fish/config.fish`(fish)에 셸 훅을 추가하면 디렉터리를
옮길 때 `.node-version`이 자동으로 적용됩니다.

```shell
# zsh
eval "$(fnm env --use-on-cd --shell zsh)"

# fish
fnm env --use-on-cd --shell fish | source
```

#### Windows

PowerShell에서 설치합니다. winget 대신 scoop(`scoop install fnm`)이나
Chocolatey(`choco install fnm`)를 써도 됩니다.

```powershell
winget install Schniz.fnm
```

셸 훅은 PowerShell 프로필에 추가합니다. 프로필 파일이 없으면 먼저 만드세요.

```powershell
if (-not (Test-Path $PROFILE)) { New-Item -ItemType File -Path $PROFILE -Force }
notepad $PROFILE
```

열린 파일에 아래 한 줄을 넣고 저장한 뒤 PowerShell을 다시 엽니다.

```powershell
fnm env --use-on-cd --shell powershell | Out-String | Invoke-Expression
```

> 이 줄을 넣지 않으면 `fnm use`가 현재 세션에만 적용되고 새 터미널에서 풀립니다.
> Windows 환경 설정에서 가장 자주 막히는 지점입니다.

#### 공통

셸 훅을 설정한 뒤 프로젝트 버전을 설치하고 pnpm을 준비합니다. 저장소 루트에서
실행하세요.

```shell
fnm install
fnm use
corepack enable pnpm
node --version   # v24.18.0
pnpm --version   # 11.17.0
```

`fnm install`과 `fnm use`는 인자가 없으면 `.node-version`을 읽습니다.

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
