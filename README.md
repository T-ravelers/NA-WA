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
| 공용 UI 컴포넌트와 사용 규칙 확인  | [shared/ui 안내](./frontend/src/shared/ui/README.md)                      |
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

처음 실행할 때 루트 환경 변수 예시 파일을 복사합니다.

```shell
cp -n .env.example .env
```

`cp -n`은 이미 `.env`가 있을 때 기존 파일을 덮어쓰지 않습니다. 기존 `.env`가
있다면 `.env.example`에 새로 추가된 변수만 기존 파일에 직접 추가합니다. 이
예시 파일에는 Docker Compose가 참조하는 전체 환경 변수와 로컬 기본값이 정리되어
있습니다. MySQL 값은 로컬 컨테이너 전용 기본값이며, JWT·OAuth·Stripe·AWS 관련
실제 비밀값은 별도로 발급받아 `.env`에만 입력합니다. `.env`는 Git에 커밋하지
않습니다.

`AWS_*` 값은 정산 영수증 이미지를 저장하는 S3에 쓰입니다. 버킷은 백엔드가 도는
계정과 **다른 AWS 계정**이 소유하므로 EC2 인스턴스 역할이 아니라 버킷 소유 계정이
발급한 IAM 사용자 키로 접근합니다. `AWS_ACCESS_KEY_ID`와 `AWS_SECRET_ACCESS_KEY`는 항상 **한 쌍**입니다. 둘 다
비워두면 AWS SDK가 다른 경로에서 자격증명을 찾으므로, 영수증 기능을 쓰지 않는
로컬 개발에서는 값 없이 그대로 실행할 수 있습니다. 반대로 하나만 채우면 서버가
뜨지 않습니다. 이때 서버를 그냥 띄우면 SDK가 EC2에 붙은 권한을 대신 집어 드는데,
이 버킷은 다른 계정 소유라 그 권한으로는 열리지 않습니다. 그러면 서버는 정상으로
보이다가 영수증을 처음 올릴 때 실패하고, 원인이 "키 하나 누락"이라는 것을
찾기 어렵습니다.

`CLOVA_OCR_*` 값은 영수증 사진에서 품목 글자를 읽는 데 쓰입니다. 호출 주소와 Secret Key는
네이버 클라우드 콘솔에서 영수증(Receipt) 도메인을 만들면 그 도메인 전용으로 함께 나오므로
**항상 한 쌍**입니다. 하나만 채우면 서버가 시작할 때 멈춥니다. 안 막으면 서버는 정상으로
보이다가 사용자가 영수증을 찍는 순간에야 실패해서, 원인이 "값 하나 누락"이라는 것을 찾기
어렵습니다. 둘 다 비워두면 글자 인식 기능만 꺼지므로 이 기능을 쓰지 않는 로컬 개발에서는
값 없이 그대로 실행할 수 있습니다.

`RECEIPT_MAX_UPLOAD_BYTES`는 영수증 사진 한 장의 최대 크기(바이트)이며 비워두면 8MiB를
씁니다. 이 값을 올릴 때는 `nginx/nginx.conf`의 `client_max_body_size`도 함께 올립니다.
nginx가 더 작으면 요청이 백엔드에 닿기도 전에 잘려서, 서버가 "크기 초과"라고 알려줄
기회조차 없이 끊깁니다.

`DOCKERHUB_USERNAME=local`은 로컬에서 빌드하는 backend 이미지의 이름 공간입니다.
이미 빌드된 Docker Hub 이미지를 받으려면 해당 이미지의 실제 Docker Hub 이름으로
바꿉니다.

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
- `certbot` 서비스는 `profiles: ["ops"]`로 묶여 있어 평소 `docker compose up -d`에는
  포함되지 않습니다. TLS 인증서 최초 발급은 EC2에서
  `docker compose run --rm certbot certonly --webroot -w /var/www/certbot -d <도메인> ...`로
  1회 실행하고, 갱신과 nginx reload는 `.github/workflows/renew-cert.yml`이 매일
  자동으로 수행합니다.
- 운영 백엔드는 `https://api.clearpng.cloud`입니다. nginx가 443에서 TLS를 종료하고,
  80으로 온 요청은 `/.well-known/acme-challenge/`(인증서 갱신용)만 직접 응답한 뒤
  나머지는 `308`로 https에 넘깁니다. ALB 도입 준비로 `X-Forwarded-Proto`가 붙은
  요청과 헬스 체크용 `/alb-health`만 `308` 대신 백엔드로 프록시합니다.
  프론트엔드의 `VITE_API_BASE_URL`도 https
  주소여야 합니다 — 리다이렉트 응답에는 CORS 헤더가 없어 `http://`로 두면 브라우저가
  API 호출을 차단합니다.
- 프론트엔드는 `frontend/` 디렉터리를 컨테이너에 바인드 마운트하므로 소스를
  고치면 Vite가 자동으로 반영합니다.
- 백엔드는 Spring Legacy WAR라 소스를 바꾸면 다시 빌드해야 반영됩니다.

```shell
docker compose up -d --build backend
```

- 로컬 3306/8080 포트를 다른 프로세스(네이티브 MySQL, IDE에서 띄운 Tomcat
  등)가 이미 쓰고 있다면 포트 충돌이 발생하니 먼저 정리하세요.

### 시연용 시드 — 리포트 비교

리포트 비교(`GET /api/v1/reports/{id}/comparison`)는 같은 약속 동료와 같은 국적 회원의
결제·리포트가 있어야 화면에 무언가 보입니다. 시연 데이터는 Flyway 마이그레이션이
아니라 SQL 파일 한 개로 넣습니다 — 스키마 버전을 올리지 않고, 두 번 돌려도 같은
결과가 됩니다(앞서 넣은 시드를 먼저 지웁니다).

1. `backend/docs/database/seed/report-demo.sql` 맨 위의 `SET @host := 1;`을 시연 계정의
   `member_id`로 바꿉니다.
2. 적용합니다.

```shell
docker compose exec -T mysql sh -c 'exec mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE"' < backend/docs/database/seed/report-demo.sql
```

세 변수는 `sh -c` 안에 두어 컨테이너에서 전개합니다. compose가 `.env`를 컨테이너에만
넣어 주므로 실행하는 사람의 셸에는 값이 없고, 이렇게 하면 비밀번호가 셸 히스토리에도
남지 않습니다.

3. 시연 계정으로 로그인해 여정 `Seed Report Journey`의 리포트를 UI에서 만듭니다. 호스트의
   결제는 일부러 리포트에 연결해 두지 않았습니다 — 연결돼 있으면 생성이 `REPORT-008`로
   막힙니다.

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

의존성을 설치하고 환경 변수 파일을 만든 뒤 개발 서버를 실행하세요.

```shell
pnpm install
cp frontend/.env.example frontend/.env.development
pnpm dev
```

개발 서버의 기본 주소는 `http://localhost:5173`입니다.

### 환경 변수 설정

프론트엔드가 요구하는 변수의 목록과 예시 값은 `frontend/.env.example`에
정리돼 있습니다. 이 파일을 복사해 개발용 파일을 만드세요.

```shell
cp frontend/.env.example frontend/.env.development
```

| 파일                              | Git 추적 | 용도                         |
| --------------------------------- | -------- | ---------------------------- |
| `frontend/.env.example`           | 추적     | 필요한 변수의 목록과 예시 값 |
| `frontend/.env.development`       | 미추적   | 개발 서버가 읽는 값          |
| `frontend/.env.production`        | 미추적   | 운영 빌드가 읽는 값          |
| `frontend/.env.development.local` | 미추적   | 개발자별 로컬 덮어쓰기       |

`.env.example`을 제외한 `.env*`는 `.gitignore`가 막습니다. **팀이 공유하는
기본값 파일은 없으므로 클론한 뒤 각자 만들어야 합니다.**

| 변수                          | 설명                                                   |
| ----------------------------- | ------------------------------------------------------ |
| `VITE_API_BASE_URL`           | 백엔드 API 주소. 비어 있으면 앱이 기동하지 않습니다    |
| `VITE_STRIPE_PUBLISHABLE_KEY` | Stripe 공개 키(`pk_`). 지갑 충전 화면에서만 사용합니다 |

`VITE_API_BASE_URL`을 설정하지 않으면 요청이 개발 서버 자신에게 가고 앱 셸
HTML이 `200 OK`로 돌아옵니다. 이 실패는 화면에 드러나지 않기 때문에, 앱이
기동 시점에 오류를 내고 멈춥니다.

`VITE_*` 값은 클라이언트 번들에 그대로 포함됩니다. 토큰, 비밀번호와 API
비밀키를 저장하지 마세요.

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
