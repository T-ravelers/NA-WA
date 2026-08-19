# frontend/AGENTS.md

[루트 AGENTS.md](../AGENTS.md)를 먼저 읽으세요. 여기에는 **프론트엔드에서 실제로
어긴 적이 있는 규칙**만 적습니다. 전체 규칙은
[개발 컨벤션](./docs/DEVELOPMENT_CONVENTION.md)이 정본입니다.

## 계층 — `app → features → shared` 단방향

- feature가 다른 feature의 내부를 import하지 않습니다.
- `shared`는 router와 feature를 import하지 않습니다.
- 공유가 필요한 타입은 `shared`로 올리거나, 상위 화면이 이벤트로 넘깁니다.

> 실제로 어긴 적이 있습니다. `explore`가 `journey` 내부를 직접 import한 사례가
> 남아 있습니다(#112). `main`에 다른 전례는 없으므로 따라 하지 마세요.

## 공용 파일을 고치지 않고 확장한다

route는 `features/<domain>/routes.ts`, 문구는 `features/<domain>/i18n/<locale>.ts`가
`import.meta.glob`으로 **자동 수집**됩니다.

**화면이나 문구를 추가하는데 공용 파일을 수정해야 한다면 접근이 틀린 것입니다.**

## 상태 소유권

- 서버 응답은 TanStack Vue Query가 소유합니다. **Pinia에 복제하지 않습니다.**
- Query Key factory는 feature의 `model/`에 둡니다.
- 컴포넌트 안에서 끝나는 상태는 `ref`와 `computed`로 둡니다.

## 모든 요청은 `shared/api/httpClient.ts`로

공통 인터셉터가 이미 처리하는 것들입니다.

- `ApiResponse` 봉투 해제
- `NormalizedApiError`(`code` / `status` / `messageKey`) 정규화
- CSRF 헤더 부착
- 401을 받으면 갱신 1회 후 원 요청 재시도

**feature에서 재시도 로직을 다시 만들지 마세요.** 백엔드의 refresh 재사용 감지에
걸립니다. 오류 분기는 메시지가 아니라 `error.code`로 합니다.

성공 응답의 DTO 모양을 확인해야 하는 요청만 Axios config의 `responseSchema`에 자기
feature `api/` 폴더가 소유한 Zod 스키마를 지정합니다. shared는 스키마를 등록하거나
feature를 import하지 않으며, 스키마가 성공해도 Zod가 변환·제거한 값이 아니라 서버의
원본 `data`를 반환합니다. 설정하지 않은 기존 호출은 응답 검증 없이 이전과 동일하게
동작하고, 이 config는 401 refresh와 AUTH-005 CSRF 재시도에서 원 요청과 함께 유지됩니다.

검증 실패는 `UNKNOWN`/HTTP status의 `NormalizedApiError`로 정규화합니다. 로그에는 URL·
method·상태와 issue path/code/expected만 기록하며 response body, issue message/input,
인증·개인정보와 Axios error 전체를 기록하지 않습니다. feature API 테스트는
`responseSchema` 전달을 확인하고, 스키마 fixture 테스트로 실제 검증도 별도로 증명합니다.

정산 화면은 모든 환경에서 API 계약만 호출합니다. API 오류나 빈 응답을 예시 데이터로
대체하지 않습니다. 테스트와 화면 캡처는 production source의 데이터 분기를 만들지 말고
API mock 또는 Playwright route stub을 사용합니다.

## 문구 — 한국어는 서비스 locale이 아니다

지원 locale은 `en`, `ja`, `zh-TW`, `vi`이며 기본과 폴백 모두 `en`입니다.
방한 외국인이 대상이라 한국어 UI 문구를 만들지 않습니다. 최종 기준은
`src/shared/i18n/locales.ts`입니다.

오류 문구 key는 `<domain>.errorCode.<CODE>`이고, 없으면 `error.unknown`으로 폴백합니다.

> 코드 주석과 커밋 메시지는 한국어로 씁니다. 사용자에게 보이는 문구만 영어 원본입니다.

## 스타일 — 토큰만 쓴다

- 색·라운드·타이포는 `src/app/styles/tokens.css`에서 생성된 Tailwind 키만 씁니다.
- 컴포넌트에 HEX를 직접 쓰지 않습니다. `bg-[#aaa8a3]` 같은 arbitrary value도 같습니다.
- 디자인이 바뀌면 토큰 값만 교체하고 컴포넌트는 건드리지 않습니다.
- 화면을 만들기 전에 [shared/ui 안내](./src/shared/ui/README.md)를 먼저 봅니다.
  `AppCard`, `AppButton`, `StateLoading`, `StateError`, `StateEmpty`가 이미 있습니다.

> `main`에 HEX 직접 사용이 아직 남아 있습니다. **기존 위반을 근거로 따라 하지 마세요.**
> 정리는 별도 작업으로 진행합니다.

## 서버가 보내는 시각은 문자열이 아닐 수 있다

백엔드 DTO에 `@JsonFormat`이 없으면 `LocalDateTime`이 `[2026, 7, 25, 12, 0]` 형태의
숫자 배열로 옵니다. 파싱은 `parseServerDateTime`을 쓰고, 표시할 때는 타임존을
`Asia/Seoul`로 고정합니다.

> 두 번 겪었습니다(#108, #99). 예외도 로그도 없이 날짜만 조용히 사라지므로
> 발견이 늦습니다. 새 날짜 필드를 쓸 때는 백엔드 DTO에 `@JsonFormat`이 있는지
> 먼저 확인하세요. 프론트 타입 선언과 단위 테스트로는 잡히지 않습니다.

공용 파서는 `src/shared/lib/datetime.ts`의 `parseServerDateTime`만 사용합니다. 날짜 전용
`YYYY-MM-DD` 값은 같은 파일의 달력 전용 함수를 사용하고 서버 시각 파서에 넘기지 않습니다.

## PWA 캐시 경계

앱 셸과 정적 자원만 precache합니다. 인증·개인정보·정산·mutation 응답, 일반 API,
지도 타일은 runtime cache에 넣지 않습니다. 폰트도 precache에 넣지 않습니다
(`workbox.globPatterns`는 js/css/html만).

## 화면을 바꿨다면 스냅샷을 남긴다

```shell
pnpm --filter @na-wa/frontend screenshot
```

PR에 첨부합니다. 자동 검사가 없어 사람이 붙이지 않으면 그대로 넘어갑니다.

낱장은 `scripts/screenshot.mjs`의 `SCREENS`, 눌러서 넘어가는 과정은 같은 파일의 `FLOWS`에
적습니다. 흐름 도중에만 존재하는 화면(보내는 중, 결제 직후 상태)은 스텁이 응답을 잡아두거나
상태를 바꿔 줘야 찍힙니다. 잡아둘 때는 `createGate()`를 쓰고 그 화면을 찍은 **다음 단계
첫 줄**에서 `open()`으로 내보냅니다. 몇 초처럼 시간으로 늦추면 느린 실행에서는 이미 넘어간
화면이 찍히는데, 뒤 단계의 `waitFor`가 그냥 통과해 **오류 없이 잘못된 산출물이 나갑니다.**

## 범위 밖

제품 범위에 채팅·WebSocket/STOMP는 없습니다. `nginx/nginx.conf`에 남은 upgrade 설정은
기능 계약이 아닙니다.
