# 부하 테스트 실행

설계와 근거는 Notion [부하 테스트 환경 구성](https://app.notion.com/p/3c35ae76088781a0a5a4d25999144134)과
[부하 테스트 시나리오](https://app.notion.com/p/3c05ae760887812cb6fbdcac028e2b5c)에 있습니다.
여기에는 **실행 절차만** 둡니다.

## 구조

SUT와 Docker 내부를 읽어야 하는 수집기만 Docker에 넣고, 부하 발생기와 저장·시각화
도구는 macOS 네이티브로 돌립니다. Docker Desktop의 리소스 설정은 다이얼이 하나뿐이라
안에 k6까지 넣으면 측정 대상과 같은 예산을 놓고 싸우게 되기 때문입니다.

```
Docker VM 전체 (2 CPU · 2.25GiB)
  nginx · backend · mysql · redis   ← 개별 제한 없이 남은 자원 공유
  cAdvisor · node-exporter          ← 각각 최대 128MiB
  Docker VM 자체 오버헤드           ← 같은 예산에서 사용

macOS 네이티브 (제한 없음)      k6 · InfluxDB · Prometheus · Grafana
```

## 최초 1회

```shell
brew install k6 prometheus grafana influxdb@1

cp .env.loadtest.example .env.loadtest

# 비어 있는 비밀값 두 개를 채웁니다.
export NAWA_LOADTEST_JWT_SECRET="$(openssl rand -base64 32)"
export NAWA_LOADTEST_LOGIN_SECRET="$(openssl rand -hex 16)"
perl -pi -e 's/^JWT_SECRET=.*/JWT_SECRET=$ENV{NAWA_LOADTEST_JWT_SECRET}/' .env.loadtest
perl -pi -e 's/^LOADTEST_LOGIN_SECRET=.*/LOADTEST_LOGIN_SECRET=$ENV{NAWA_LOADTEST_LOGIN_SECRET}/' .env.loadtest
unset NAWA_LOADTEST_JWT_SECRET NAWA_LOADTEST_LOGIN_SECRET
```

> `JWT_SECRET`은 **Base64로 인코딩된** 32바이트 이상이어야 합니다. 평문을 넣으면
> `Illegal base64 character`로 스프링 컨텍스트가 죽는데, **Tomcat은 정상적으로 떠서
> 모든 요청이 404가 됩니다.** 원인을 찾기 어려운 실패라 위 명령을 그대로 쓰세요.

> `influxdb`가 아니라 **`influxdb@1`**입니다. 기본 포뮬러는 3.x인데 k6 내장 output은
> v1 line protocol만 지원합니다.

> **`-p nawa-loadtest`를 반드시 붙입니다.** 빼면 프로젝트 이름이 디렉터리 이름(`na-wa`)이
> 되어 **평소 개발용으로 띄워 둔 컨테이너를 그대로 갈아치웁니다.** 볼륨도 공유해서 부하
> 테스트 시드 데이터가 개발 DB에 섞입니다. 포트는 개발 스택(8080)과 겹치지 않게
> 9080번대로 뒀습니다.

**Docker Desktop → Settings → Resources**에서 CPU `2`, Memory `2.25GiB`로 낮춥니다.
슬라이더가 0.25GiB 단위를 지원하지 않으면 2.5GiB로 두되 관측 도구의 128MiB 상한은
그대로 유지하고, 실행 결과에 메모리 상한 차이를 기록합니다.

> 이 설정은 SUT에 정확히 2GiB를 예약하지 않습니다. 2.25GiB는 Docker VM 전체 상한이고,
> 관측 컨테이너의 128MiB는 최대치일 뿐입니다. SUT 네 컨테이너는 관측 도구가 쓰지 않은
> 메모리와 Docker VM 오버헤드를 포함한 나머지를 공유합니다. 병목 위치를 찾기 위한
> 근사 환경이며 t3.small의 절대 처리량을 재현하는 환경이 아닙니다.

Grafana 데이터소스를 자동 등록하려면 심볼릭 링크를 겁니다.

```shell
brew services stop grafana
ln -s "$PWD/loadtest/grafana/provisioning/datasources/loadtest.yml" \
      "$(brew --prefix)/etc/grafana/provisioning/datasources/loadtest.yml"
brew services start grafana
```

## 테스트할 때마다

```shell
# 0. 평소 개발 스택이 떠 있다면 내린다. 같은 호스트 자원을 나눠 쓰면 측정값이 흐려진다.
docker compose down

# 1. SUT 기동 — pull 이 아니라 build 다
docker compose -p nawa-loadtest \
  -f docker-compose.yml -f docker-compose.ec2-clone.yml \
  --env-file .env.loadtest up -d --build

# 2. 헬스 체크
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:9080/api/v1/members/me   # 401 이면 정상
curl -s http://127.0.0.1:9081/internal/metrics | head -3                           # 지표가 나와야 한다
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:9080/internal/metrics    # 404 여야 한다

# 3. 테스트 데이터 시딩 (Issue #396 범위 밖, 별도 이슈)

# 4. 관측 스택 기동
brew services start influxdb@1
prometheus --config.file=loadtest/prometheus.yml &
brew services start grafana

# 5. 부하 발생
set -a && source .env.loadtest && set +a
k6 run --out influxdb=http://127.0.0.1:8086/k6 \
  -e LOADTEST_LOGIN_SECRET="$LOADTEST_LOGIN_SECRET" \
  -e RUN_ID="$(date +%s)" \
  loadtest/k6/scenario1-login-join.js
```

### 환경변수는 `-e`로 명시합니다

로컬 셸 환경에 기대지 않고 실행 명령만으로 입력을 재현할 수 있도록 중요한 값은
`-e`로 명시합니다. `--include-system-env-vars`를 쓸 수도 있지만, CI나 다른 팀원 셸에서
어떤 값이 들어갔는지 확인하기 어려워 기본 절차로 사용하지 않습니다.

`RUN_ID`는 멱등성 키에 섞입니다. 안 넘기면 실행 시각으로 자동 생성되지만, 여러 대에서
나눠 돌릴 때는 명시해서 겹치지 않게 하세요. **키가 겹치면 서버가 실제 작업 대신
멱등 응답을 돌려주므로 결제 성능을 재지 못합니다.**

### 스크립트가 쓰는 환경변수

| 변수 | 기본값 | 뜻 |
| --- | --- | --- |
| `BASE_URL` | `http://127.0.0.1:9080` | nginx 주소 |
| `ORIGIN` | `http://127.0.0.1:5173` | `AUTH_ALLOWED_ORIGINS`와 같아야 함. 안 맞으면 전부 `AUTH-006`(403) |
| `LOADTEST_LOGIN_SECRET` | (필수) | 백엔드 `loadtest.login-secret`과 같아야 함 |
| `VUS` | `8920` | 목표 VU |
| `START_WINDOW_SECONDS` | `300` | 각 VU의 1회 실행 시작을 분산하는 시간 |
| `MAX_DURATION` | `10m` | 마지막 VU까지 완료되기를 기다리는 최대 시간 |
| `RUN_ID` | 현재 시각 | 멱등성 키 구분자 |
| `MEMBER_ID_BASE` | `900000` | VU가 쓸 회원 번호 시작값 |
| `PAYEE_ID_BASE` / `PAYEE_POOL_SIZE` | `950000` / `100` | QR 수취인 계정 풀 |
| `HOSTED_APPOINTMENT_BASE` | `2000000` | VU가 방장인 약속 번호 시작값 |
| `RECRUITING_APPOINTMENT_BASE` / `VU_SLOTS_PER_APPOINTMENT` | `1000000` / `5` | 시나리오 1에서 참여할 약속. **시드의 `@vu_slots_per_appointment`와 같아야 한다** |
| `RUN_INDEX` / `RUN_STRIDE` | `1` / `10000` | 몇 번째 실행인가. 시드의 `RUNS` 이하여야 한다 |
| `SETTLEMENT_PARTICIPANT_MEMBER_ID_BASE` | `970000` | 생성자가 아닌 정산 납부 회원 번호 시작값 |

시드 스크립트가 만드는 번호 범위와 **반드시 맞춰야 합니다.** 어긋나면 로그인 다음부터
전부 404가 나는데, 부하는 정상으로 걸려서 눈치채기 어렵습니다.

### 다시 돌릴 때는 `RUN_INDEX`를 올린다

한 번 실행하면 두 가지가 소진됩니다.

- 참여한 약속은 같은 VU의 재참여를 막습니다 (`ALREADY_JOINED`)
- 출석을 확정한 약속은 `COMPLETED`가 되어 두 번째 확정을 받지 않습니다

그래서 시드가 **회차분을 미리 깔아 둡니다.** 볼륨을 초기화하지 않고 `RUNS`번까지 그대로
다시 돌릴 수 있습니다.

```shell
RUNS=5 VUS=8920 ./loadtest/seed.sh          # 5회차분을 미리 깐다

k6 run ... -e RUN_INDEX=1 ...               # 1회차
k6 run ... -e RUN_INDEX=2 ...               # 2회차 — 초기화 불필요
```

`RUN_INDEX`가 시드의 `RUNS`보다 크면 없는 약속을 불러 **404**가 납니다. 회차를 다 쓰면
그때 볼륨을 초기화하고 다시 시드합니다.

> 잔액은 회차 제약이 아닙니다. 실측해 보니 주 사용자는 충전(+50,000)이 소모(보증금
> 5,000 + QR 9,000)보다 커서 **회차마다 잔액이 오히려 늡니다.** 정산 참여자만 회차당
> 약 4,500을 내므로 시드가 `RUNS`에 맞춰 여유를 둡니다.

### 참여할 약속은 VU마다 다르다

시나리오 1은 목록의 첫 약속에 참여하지 **않습니다.** `joinAppointment`가 약속 행을
`FOR UPDATE`로 잠그기 때문에, 모든 VU가 한 약속에 몰리면 백엔드 처리량이 아니라
**그 행의 락 대기를 측정하게 됩니다.** 운영에서는 일어나지 않는 병목입니다.

그래서 시드가 `CEIL(VUS / 5)`개의 약속을 만들고 VU를 순서대로 나눠 담습니다.
슬롯이 정원(6)이 아니라 5인 것은 `current_member_count`가 **방장까지 세기** 때문입니다.

```
RUN_INDEX=1:  VU 1~5 → 1000001   VU 6~10 → 1000002   VU 11~15 → 1000003 ...
RUN_INDEX=2:  VU 1~5 → 1010001   VU 6~10 → 1010002   ...
```

`VU_SLOTS_PER_APPOINTMENT`를 시드와 다르게 주면 뒤쪽 VU가 정원 초과(`JOIN_NOT_AVAILABLE`)로
실패하거나 빈 약속이 남습니다. 목록 조회 자체는 실제 사용자 흐름이라 그대로 둡니다.

특히 시나리오 2의 `SETTLEMENT_PARTICIPANT_MEMBER_ID_BASE + VU` 회원은 해당 VU가 만든
공동지출 정산에 선택되는 약속 멤버여야 하고, 원결제자와 다른 회원이어야 합니다. 각 VU가
조회할 리포트도 미리 존재해야 상세 조회 2건이 모두 실행됩니다. 이 관계를 만드는 SQL은
Issue #396의 제외 범위인 시드 이슈에서 구현합니다.

## 시드 전에는 연결 진단만 합니다

시드 없이 전체 시나리오를 스모크 테스트할 수 없습니다. 대신 아래 스크립트는
test-login, access cookie, 보호 API 도달, CSRF 발급까지만 확인합니다.

```shell
k6 run -e LOADTEST_LOGIN_SECRET="$LOADTEST_LOGIN_SECRET" loadtest/k6/smoke-auth.js
```

`members/me`가 200이거나 `MEMBER-001`까지 도달하면 인증 필터를 통과한 것입니다.
`AUTH-003`(401)이나 `AUTH-006`(403)이면 데이터가 아니라 로그인 비밀 또는 Origin
설정 문제입니다.

## 시드 후 전체 시나리오 스모크 테스트

시드가 준비된 뒤에만 전체 흐름을 1 VU로 실행합니다.

```shell
k6 run -e LOADTEST_LOGIN_SECRET="$LOADTEST_LOGIN_SECRET" \
  -e VUS=1 -e START_WINDOW_SECONDS=1 loadtest/k6/scenario1-login-join.js

k6 run -e LOADTEST_LOGIN_SECRET="$LOADTEST_LOGIN_SECRET" \
  -e VUS=1 -e START_WINDOW_SECONDS=1 loadtest/k6/scenario2-qr-report.js
```

두 실행 모두 `checks` 성공률이 100%여야 합니다. 그다음 `100 → 1,000 → 8,920`으로
단계를 올립니다. 각 VU는 한 번만 실행되고 시작 시각만 5분 창에 분산됩니다.

### 시나리오 2의 보조 세션 트래픽

Notion의 20건은 QR 결제부터 리포트까지의 업무 API만 센 값입니다. 독립된 k6 파일은
시나리오 1의 브라우저 세션을 이어받을 수 없고, QR 수취인과 실제 정산 납부자도 별도
세션이어야 합니다. 그래서 결제자·수취인·정산 참여자 각각에 test-login과 CSRF 발급이
필요해 **VU당 6건이 추가**됩니다. 서버가 실제로 받는 요청은 VU당 26건이고, 8,920명을
5분에 실행하면 전체 관측 TPS 상한은 약 **773 TPS**입니다. Grafana에서 업무 API 태그와
`test-login`·`csrf` 태그를 분리해 595 TPS 목표와 보조 트래픽을 함께 해석합니다.

## 외부 서비스와 공개 예시값

`-Ploadtest` 빌드는 Stripe 클라이언트를 로컬 스텁으로 교체합니다. PaymentIntent 생성은
로컬 대기 응답, 상태 조회는 로컬 성공 응답을 반환하므로 Stripe 네트워크·rate limit·키에
측정값이 흔들리지 않고 실제 결제도 시도하지 않습니다. 이 클래스도 테스트 로그인과 같이
`backend/src/loadtest/java`에 있어 운영 WAR에는 포함되지 않습니다.

`.env.loadtest.example`에는 실제 비밀값을 넣지 않습니다. `JWT_SECRET`,
`LOADTEST_LOGIN_SECRET`, AWS 키, OCR 키, 파이프라인 비밀은 빈 값이어야 합니다. 실제 값을
넣는 `.env.loadtest`는 `.env.*` 규칙으로 Git에서 무시되며 강제로 add하지 않습니다.

## 결과 보기 — 세 축을 함께

하나만 보면 오독합니다.

| 축 | 출처 | 보는 것 |
| --- | --- | --- |
| k6 결과 | InfluxDB | 달성 TPS · 응답시간 분포 · 에러율 |
| 컨테이너 자원 | cAdvisor | 넷 중 누가 먼저 한계에 닿는가 |
| JVM 내부 | backend `/internal/metrics` | 힙 · GC 정지시간 · Tomcat 스레드풀 · **HikariCP 대기** |

여기에 **node-exporter로 Docker VM 전체**를 함께 봅니다. VM이 포화되면 개별 컨테이너
수치를 해석하기 전에 전체가 한계라는 뜻입니다.

`hikaricp_connections_pending`이 쌓이면 커넥션 풀이, `tomcat_threads_busy / config_max`가
1에 가까우면 스레드풀이, GC 정지시간이 길면 힙이 병목입니다. 셋은 대응이 전혀 다릅니다.

### Grafana 대시보드

데이터소스 프로비저닝(`loadtest/grafana/provisioning/datasources/loadtest.yml`)만으로는
패널이 없습니다. Grafana HTTP API로 4개를 등록합니다 — 데이터소스처럼 파일 프로비저닝을
해도 되지만, 커뮤니티 대시보드 3개는 버전이 자주 바뀌어 최신본을 매번 받는 쪽이
안전해서 수동/스크립트 임포트로 남겨 둡니다.

| 대시보드 | 출처 | 데이터소스 |
| --- | --- | --- |
| k6 Load Testing Results | [grafana.com #2587](https://grafana.com/grafana/dashboards/2587/) | `k6` (InfluxDB) |
| Node Exporter Full | [grafana.com #1860](https://grafana.com/grafana/dashboards/1860/) | `loadtest-prometheus` (템플릿 변수로 자체 선택) |
| Cadvisor exporter | [grafana.com #14282](https://grafana.com/grafana/dashboards/14282/) | `loadtest-prometheus` |
| NA-WA Backend (JVM/Tomcat/HikariCP) | 저장소 자체 제작, JSON 없음(API로만 생성) | `loadtest-prometheus` |

임포트 예시(Grafana가 `127.0.0.1:3000`에서, admin/admin 기본 계정으로 떠 있다고 가정):

```shell
curl -s "https://grafana.com/api/dashboards/2587/revisions/latest/download" \
  | python3 -c "
import json, sys
d = json.load(sys.stdin)
payload = {
    'dashboard': d, 'overwrite': True,
    'inputs': [{'name': 'DS_K6', 'type': 'datasource', 'pluginId': 'influxdb', 'value': 'k6'}],
}
print(json.dumps(payload))
" | curl -s -u admin:admin -H "Content-Type: application/json" \
  -X POST http://127.0.0.1:3000/api/dashboards/import -d @-
```

`Cadvisor exporter`(#14282)도 같은 패턴이되 입력 이름이 `DS_PROMETHEUS`, `pluginId`가
`prometheus`, `value`가 `loadtest-prometheus`입니다. `Node Exporter Full`(#1860)은
`__inputs`가 비어 있고 대시보드 안의 `ds_prometheus` 템플릿 변수가 Prometheus 타입
데이터소스를 자동으로 고르므로 `inputs: []`로 그대로 임포트합니다.

네 번째(`NA-WA Backend`)는 이 저장소가 노출하는 커스텀 메트릭 전용이라 커뮤니티에
없습니다 — `POST /api/dashboards/db`로 직접 만듭니다. 패널 6개: JVM 힙 사용/최대,
GC 정지시간, Tomcat 스레드(`busy`/`current`/`config_max`), HikariCP 커넥션
(`active`/`idle`/`pending`/`max`), 프로세스·시스템 CPU, HikariCP 커넥션 획득 p95.
JSON은 커밋해 두지 않았습니다 — Grafana 인스턴스마다 새로 만드는 로컬 산출물이라
소스에 넣을 이유가 없습니다(데이터소스 프로비저닝과 다른 점입니다: 그건 Grafana가
어디를 봐야 하는지에 대한 저장소의 결정이고, 대시보드 패널 배치는 보는 사람의 취향).

## 이 환경이 재현하지 못하는 것

절대 TPS 수치를 그대로 믿으면 안 됩니다. 넷 다 결과를 **실제보다 좋게** 만듭니다.

- **CPU 아키텍처** — 맥북은 arm64, t3.small은 x86_64
- **코어 속도** — `cpus` 제한은 CPU 시간의 *양*만 줄이고 코어 *속도*는 못 낮춥니다.
  Apple Silicon 성능 코어가 t3.small의 버스터블 vCPU보다 몇 배 빠릅니다
- **네트워크** — loopback이라 지연도 NIC 병목도 없습니다
- **TLS** — 운영은 nginx가 443에서 종료하지만 여기서는 평문입니다. 동시 연결이 수천
  개면 핸드셰이크가 CPU를 꽤 씁니다

그래서 이 환경의 용도는 **병목의 위치를 찾는 것**입니다. "654 TPS를 버티는가"라는
판정은 실제 t3.small EC2에서 따로 확인합니다.

## k6가 실제 사용자와 다른 점

실제 사용자는 `authorization` → 공급자 동의 → `callback` → `members/me`를 거칩니다.
k6는 외부 동의 화면을 통과하지 않고 `authorization`의 302까지만 확인한 뒤
`test-login`으로 callback의 토큰 발급을 대신합니다. 백엔드 요청 수는 유지하지만 OAuth
공급자 통신과 callback의 프로필 교환 비용은 재현하지 않습니다.

## 정리

```shell
docker compose -p nawa-loadtest \
  -f docker-compose.yml -f docker-compose.ec2-clone.yml down -v
brew services stop grafana influxdb@1
# prometheus 는 포그라운드로 띄웠다면 Ctrl+C
```

`-v`를 붙이면 MySQL 볼륨까지 지웁니다. 시드를 다시 넣을 거라면 붙이고, 재사용할
거라면 빼세요.
