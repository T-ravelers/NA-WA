# QR 결제 API 계약

QR 결제는 수취인이 QR을 생성하면 결제자가 스캔해 지갑 간 즉시 이체를 실행하는
기능이다. 모든 경로는 인증이 필요하며 공통 `ApiResponse` 형식을 사용한다.

## 유지 API

| API | 용도 |
| --- | --- |
| `POST /api/v1/wallet/qr/create` | 결제를 받을 QR을 생성한다. |
| `GET /api/v1/wallet/qr/active` | 아직 만료되지 않고 결제도 되지 않은 내 QR 목록을 조회한다. |
| `POST /api/v1/wallet/qr/resolve` | QR 토큰으로 결제 대상 정보를 조회한다. |
| `POST /api/v1/wallet/qr/payment/preview` | 실제 결제 전 예상 금액과 잔액을 미리 본다. |
| `POST /api/v1/wallet/qr/payment/execute` | 결제를 실행한다. `Idempotency-Key` 헤더가 필요하다. |
| `GET /api/v1/wallet/qr/payment/{transferId}` | 완료된 QR 결제 거래 상태를 조회한다. |

## 계정 유형 제약

결제자는 `TRAVELER` 계정만 가능하다. `MERCHANT` 계정은 QR 생성과 매출 조회만 할 수
있으므로 `resolve`·`payment/preview`·`payment/execute` 세 경로가 모두 `WALLET-030`으로
거절한다. 세 경로 다 직접 호출할 수 있으므로 한 곳만 막지 않는다. QR
생성(`POST /api/v1/wallet/qr/create`)은 두 계정 유형 모두 쓸 수 있다.

가맹점 매출은 별도 API가 아니라 기존 거래 내역 조회로 본다. 가맹점은 결제·충전·정산을
하지 않아 그 지갑 원장에는 QR 수입만 쌓인다.

```text
GET /api/v1/me/transactions?type=QR_PAYMENT&status=COMPLETED&from=2026-08-18&to=2026-08-18
```

계정 유형은 `GET /api/v1/members/me`의 `accountType`으로 확인하고, 가맹점 등록은
`POST /api/v1/members/me/merchant`로 한다.

## QR 생성

`POST /api/v1/wallet/qr/create`

```json
{
  "amount": 10000,
  "memo": "카페 결제"
}
```

- `amount`가 `null`이면 결제자가 금액을 직접 입력하는 QR이다. 값이 있으면 고정 금액
  QR이며, 결제 실행 시 결제자가 보낸 금액은 신뢰하지 않고 항상 QR 생성 시 저장된
  금액을 사용한다.
- `memo`는 최대 255자다.
- QR은 생성 후 **1분**이 지나면 만료된다(`QR_EXPIRATION_MINUTES`,
  `QrPaymentServiceImpl`). 만료 시각은 응답 `expiresAt`으로 내려간다.
- 성공 시 `201 Created`를 반환한다.

## 내 QR 목록 조회

`GET /api/v1/wallet/qr/active`

아직 만료되지 않고(`expires_at > now`) 결제도 되지 않은(`payment_status = 'ACTIVE'`)
내 QR만 반환한다. My QR 화면에서 사용한다.

## QR 조회 (resolve)

`POST /api/v1/wallet/qr/resolve`

```json
{ "qrToken": "..." }
```

QR의 존재, 만료, 완료 여부, 자기 자신 결제, 수취인 지갑 상태를 검증한 뒤 결제
대상 정보를 반환한다.

## 결제 미리보기

`POST /api/v1/wallet/qr/payment/preview`

```json
{
  "qrToken": "...",
  "amount": 10000,
  "spendingScope": "PERSONAL",
  "appointmentId": null
}
```

- `spendingScope`는 `PERSONAL`, `SHARED` 중 하나다.
- `PERSONAL`에는 `appointmentId`를 보낼 수 없고, `SHARED`에는 `appointmentId`가
  필수다.
- `SHARED`는 로그인 사용자가 해당 약속의 활성 멤버여야 하며, 그 약속에 여행이
  연결되어 있어야 한다.
- 잔액 부족은 예외가 아니라 응답의 `canPay: false`로 표현한다.

## 결제 실행

`POST /api/v1/wallet/qr/payment/execute`

`Idempotency-Key` 헤더(1~100자)가 필요하다. 요청 본문은 미리보기에 `spendingCategory`
하나를 더한 형태다.

```json
{
  "qrToken": "...",
  "amount": 10000,
  "spendingScope": "PERSONAL",
  "appointmentId": null,
  "spendingCategory": "FOOD"
}
```

- `spendingCategory`는 결제자가 고르는 소비 카테고리다. 값 집합과 화면별 쓰임은
  [소비 카테고리](./SPENDING_CATEGORY.md)가 정본이다.
- 값을 빼거나 `null`을 보내면 서버가 `OTHER`로 저장한다. 목록 밖의 값은 `WALLET-031`로
  거절한다.
- 미리보기에는 이 필드가 없다. 카테고리는 결제 금액과 잔액을 바꾸지 않는다.

같은 키로 같은 요청을 재시도하면 새 결제를 만들지 않고 기존 결제 결과를 그대로
반환한다. 같은 키로 다른 요청(다른 QR, 다른 금액, 다른 소비 범위, 다른 소비 카테고리)을
보내면 아래 표의 오류로 거절한다.

## 결제 상태 조회

`GET /api/v1/wallet/qr/payment/{transferId}`

QR 결제로 생성된 거래만 조회할 수 있다. 결제자 본인만 조회할 수 있으며, 수취인이나
제3자가 조회하면 거부된다.

## QR 오류 코드

| 오류 코드 | HTTP 상태 | 의미 |
| --- | ---: | --- |
| `WALLET-018` | 404 | QR 결제 정보를 찾을 수 없음 |
| `WALLET-019` | 410 | 만료된 QR 결제 |
| `WALLET-020` | 409 | 이미 결제가 완료된 QR |
| `WALLET-021` | 409 | 현재 사용할 수 없는 QR 결제 |
| `WALLET-022` | 400 | 본인에게 결제 시도 |
| `WALLET-023` | 409 | 수취인의 지갑을 현재 사용할 수 없음 |
| `WALLET-024` | 400 | 결제 금액 미입력(금액 입력형 QR) |
| `WALLET-025` | 400 | 개인 소비에 약속을 연결함 |
| `WALLET-026` | 404 | 약속 멤버십을 찾을 수 없음 |
| `WALLET-027` | 409 | 약속에 연결된 여행이 없음 |
| `WALLET-028` | 400 | 공동 소비에 약속 정보 누락 |
| `WALLET-029` | 409 | 결제 가능한 잔액 부족 |
| `WALLET-030` | 403 | 가맹점 계정은 결제할 수 없음 |
| `WALLET-031` | 400 | 지원하지 않는 소비 카테고리 |
