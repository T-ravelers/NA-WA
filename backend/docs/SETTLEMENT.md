# 정산 API 계약

정산은 원거래를 EQUAL 또는 ITEMIZED 방식으로 분담하는 기능이다. 참여자는 회원 ID가
아닌 약속 참가 행의 `appointment_member_id`로 식별한다.

## 유지 API

| API | 용도 |
| --- | --- |
| `GET /api/v1/settlements` | 현재 사용자가 생성했거나 지급할 정산 목록을 조회한다. |
| `GET /api/v1/settlements/candidates` | 생성 가능한 원거래와 약속 참가자 후보를 조회한다. |
| `POST /api/v1/appointments/{appointmentId}/settlements` | 원거래와 분담 규칙으로 정산을 생성한다. |
| `GET /api/v1/settlements/{settlementId}` | 참여한 정산의 상태, 개인 부담금과 ITEMIZED 품목 배분을 조회한다. 원결제자에게는 참여자별 납부 현황도 함께 내려준다. |
| `POST /api/v1/settlements/{settlementId}/members/me/pay` | 현재 사용자의 미지급 부담금을 지급한다. |
| `POST /api/v1/settlement-receipts` | 영수증 사진을 올리고 `receiptId`를 받는다. |
| `POST /api/v1/settlement-receipts/{receiptId}/ocr` | 올려 둔 사진에서 품목 초안을 읽는다. |
| `GET /api/v1/settlements/{settlementId}/receipt` | 정산에 붙은 영수증 사진을 조회한다. |

생성 요청과 지급 요청에는 각각 `Idempotency-Key` 헤더가 필요하며, 값은 1~100자다.

## 정산 목록 응답의 시각

`GET /api/v1/settlements`의 각 요약은 `createdAt`과 `completedAt`을 함께 반환한다. 형식은
`yyyy-MM-dd'T'HH:mm:ss`이고 기준 시간대는 서버와 같은 `Asia/Seoul`이다.

`completedAt`은 **애플리케이션이 넘긴 시각**을 기록한다. DB의 `CURRENT_TIMESTAMP`를 쓰지
않는다. 이 값이 기간 필터의 기준이 되므로, DB 시계에 기대면 시간대 설정이 어긋나는 순간
경계 근처의 정산이 통째로 다른 날짜로 묶인다.

**두 시각은 서로 다른 시계에서 나온다.** 아래 표가 정본이다.

| 필드 | 값을 적는 쪽 | 기준 시계 |
| --- | --- | --- |
| `createdAt` | `settlements.created_at`의 컬럼 기본값 `CURRENT_TIMESTAMP` | DB 세션 시간대 |
| `completedAt` | 애플리케이션이 넘긴 `LocalDateTime.now()` | JVM 시간대 |

`created_at`을 DB가 적는 것은 이 저장소의 모든 테이블이 그렇게 하기 때문이고, 정산만
바꾸면 오히려 규칙이 갈린다. 대신 **두 값이 같은 기준으로 읽히려면 DB와 애플리케이션의
시간대가 맞아 있어야 한다.** 운영은 맞춰 두었다 — `docker-compose.yml`의 `mysql`이
`--default-time-zone=+09:00`이고 백엔드 컨테이너가 `TZ=Asia/Seoul`이다(#294). CI는 이
맞춤을 **일부러 깨서**(MySQL만 UTC) DB 시계에 기대는 코드가 드러나게 한다. 세 곳의 설정은
[AGENTS.md](../../AGENTS.md)와 [docs/TECH_STACK.md](../../docs/TECH_STACK.md)가 정본이다.

`completedAt`은 settlement가 `COMPLETED`로 전이한 시각이며, `REQUESTED`인 동안에는 `null`이다.
이 필드를 남기기 시작한 시점보다 먼저 완료된 정산도 `null`이다. `updated_at`이 갱신되지 않는
스키마라 되살릴 근거 값이 없어 데이터를 보정하지 않았다. 완료 시각으로 목록을 거르거나
표시하는 클라이언트는 `completedAt`이 없으면 `createdAt`으로 대신한다.

목록 정렬은 지금도 `created_at` 내림차순이며 이 응답 필드가 정렬을 바꾸지 않는다.

## 정산 후보 생성 문맥

`GET /api/v1/settlements/candidates`의 각 후보는 `transferId`, `appointmentId`,
`payerAppointmentMemberId`, `participants`를 함께 반환한다. `appointmentId`는 정산 생성
URL의 경로 변수이고, `transferId`는 생성 요청의 `sourceTransferId`다.

`payerAppointmentMemberId`는 원결제자의 `appointment_member_id`이며 반드시 같은 후보의
`participants[].id` 중 하나다. 클라이언트는 회원 ID를 약속 참가 ID로 바꾸거나 원결제자의
참가 ID를 추론하지 않고, 이 값을 `participantAppointmentMemberIds`에 포함해 생성 요청을
구성한다. `participants[].id`도 모두 `appointment_member_id`다.

## 생성·금액·상태

생성 요청에는 `sourceTransferId`, `type`,
`participantAppointmentMemberIds`를 포함한다. `type`은 `EQUAL` 또는 `ITEMIZED`만
허용한다. 생성이 성공하면 HTTP 201을 반환하고 settlement 상태는 즉시 `REQUESTED`다.

- `EQUAL`은 원거래 금액을 대상 참가자에게 균등 배분한다. 통화의 최소 단위로 나눌 때
  남는 금액은 `appointment_member_id` 오름차순으로 하나씩 배분한다. 예를 들어 KRW
  100원을 3명에게 나누면 34원, 33원, 33원이다.
- `ITEMIZED`는 사용자가 정산 품목과 수량을 직접 입력하고, 각 품목의 수량을 대상
  참가자에게 배분한다. 품목 금액과 참가자별 배분 금액의 합계는 원거래 금액과 일치해야
  한다.

`ITEMIZED` 요청에는 `items`를 추가한다. 각 품목은 `name`, `unitPrice`, `quantity`와
`allocations`를 가지며, 각 allocation은 `appointmentMemberId`, `quantity`를 가진다.
클라이언트는 품목별 또는 참여자별 금액, OCR 결과, 영수증 분석 ID를 보내지 않는다. 서버가
`unitPrice × quantity`로 품목·배분 금액을 계산하고 `settlement_items`,
`settlement_item_shares`에 스냅샷으로 저장한다. 영수증 사진은 요청 본문에 담지 않고,
미리 올려 받은 `receiptId`만 보낸다. 자세한 내용은 [영수증과 multipart](#영수증과-multipart)에 있다.

품목명은 최대 200자다. `unitPrice`, 품목 금액과 배분 금액은 `DECIMAL(19,4)`, 품목 및
배분 수량은 `DECIMAL(12,3)` 범위를 벗어나면 서버가 `SETTLEMENT-005`(400)으로 거절한다.

`settlements.settlement_status`는 `REQUESTED`, `COMPLETED`만 사용한다.
`settlement_members.request_status`는 생성자의 `NOT_REQUESTED`와 지급 대상자의
`PENDING`, `PAID`만 사용한다. `REQUESTED` 정산에서 `PENDING`인 모든 구성원이
`PAID`가 되면 settlement는 `COMPLETED`로 전이한다.

원결제자는 반드시 `participantAppointmentMemberIds`에 포함해야 하며, 원결제자 외에도
양수 부담금(`shareAmount > 0`)을 가진 지급 대상자가 최소 한 명 있어야 한다. 원결제자만
선택한 요청은 `SETTLEMENT-005`(400)으로 거절한다. 원결제자 외 선택한 모든 참여자는
양수 부담금을 가져야 한다.

## 정산 상세의 납부 현황

`GET /api/v1/settlements/{settlementId}`는 `collection`을 함께 반환한다. 원결제자에게만
채워 주고 그 밖의 참여자에게는 `null`이다. 빈 배열이 아니라 `null`인 것은 "볼 수 없다"와
"청구한 상대가 없다"를 구분하기 위해서다. 참여자가 다른 참여자의 납부 여부를 보는 경로는
아직 없다.

| 필드 | 내용 |
| --- | --- |
| `collection.totalCount` | 청구한 상대 수. **원결제자 본인은 세지 않는다.** |
| `collection.paidCount` | 그중 지급을 마친 수 |
| `collection.participants[].id` | 회원 ID가 아닌 `appointment_member_id` |
| `collection.participants[].name` · `initials` | 표시용 이름과 이름 첫 글자 |
| `collection.participants[].shareAmount` | 그 사람의 부담금 |
| `collection.participants[].requestStatus` | `PENDING` 또는 `PAID` |

`participants`에는 `request_status`가 `NOT_REQUESTED`가 아닌 구성원만 담는다. 이 상태는
생성자에게만 붙고 생성자가 곧 원결제자이므로(`chk_settlements_creator_is_payer`), 결과적으로
원결제자 본인 행이 빠진다. 본인을 세면 자기 자신에게 보낼 돈이 없어 전원이 지급해도
`paidCount`가 `totalCount`에 닿지 못한다.

**고르는 기준은 `COMPLETED` 전이 조건과 글자 그대로 같다.** 둘 다 정산 구성원 행만 보고
약속 참가 쪽 상태(`membership_status`·`deleted_at`)는 보지 않는다. 약속에서 나가든 참가
기록이 지워지든 이미 진 빚은 그대로라 정산은 그 사람이 낼 때까지 끝나지 않기 때문이다.
목록만 한 사람이라도 더 걸러내면 화면은 다 냈다고 말하는데 정산은 `REQUESTED`에 멈춰 있고
원결제자가 그 이유를 볼 방법이 없어진다. 약속 참가 행을 잇는 것은 이름을 얻기 위해서다.

정렬은 **아직 내지 않은 사람이 먼저**이고, 같은 상태 안에서는 `appointment_member_id`
오름차순이다(EQUAL 나머지 배분 순서와 같다). 이 목록을 여는 이유가 "누가 아직 안 냈나"라서,
낸 사람 사이에 섞으면 원결제자가 배지를 하나씩 훑어야 한다. 사람이 지급할 때마다 그 행이
아래로 내려가므로 목록의 위쪽은 항상 남은 사람이다.

## 멱등성

생성 멱등성은 `(created_by_member_id, idempotency_key)`와 요청 지문으로 보장한다.
같은 키와 같은 요청을 재시도하면 기존 생성 결과를 반환한다. 같은 키로 다른 요청을
보내면 `SETTLEMENT-009`(409), 이미 다른 정산에 사용한 원거래를 사용하면
`SETTLEMENT-010`(409)이다.

지급은 구성원별 `payment_idempotency_key`로 멱등 처리한다. 같은 키 재시도는 확정된
지급 결과를 반환하며, 이미 지급된 건에 다른 키를 쓰면 `SETTLEMENT-014`(409)이다.
비어 있거나 100자를 초과한 멱등성 키는 `SETTLEMENT-015`(400)이다.

## 정산 오류 코드

| 오류 코드 | HTTP 상태 | 의미 |
| --- | ---: | --- |
| `SETTLEMENT-001` | 404 | 정산 정보를 찾을 수 없음 |
| `SETTLEMENT-002` | 409 | 현재 상태에서 정산 지급을 진행할 수 없음 |
| `SETTLEMENT-003` | 403 | 현재 사용자의 정산 부담금을 찾을 수 없음 |
| `SETTLEMENT-004` | 404 | 정산 가능한 원거래를 찾을 수 없음 |
| `SETTLEMENT-005` | 400 | 정산 생성 정보가 올바르지 않음 |
| `SETTLEMENT-009` | 409 | 같은 생성 멱등성 키의 요청 지문이 다름 |
| `SETTLEMENT-010` | 409 | 원거래가 이미 다른 정산에 사용됨 |
| `SETTLEMENT-014` | 409 | 정산 지급이 이미 다른 멱등성 키로 처리됨 |
| `SETTLEMENT-015` | 400 | 멱등성 키가 비었거나 길이 제한을 초과함 |
| `SETTLEMENT-016` | 400 | 영수증 이미지 형식이 허용 목록에 없거나 내용과 다름 |
| `SETTLEMENT-017` | 409 | 남이 올렸거나 이미 사용된 영수증을 연결하려 함 |
| `SETTLEMENT-018` | 404 | 정산에 연결된 영수증이 없거나 조회 권한이 없음 |
| `SETTLEMENT-019` | 503 | 영수증 저장소를 사용할 수 없음 |
| `SETTLEMENT-020` | 410 | 영수증 보관 기한이 지나 저장소에서 사라짐 |
| `SETTLEMENT-021` | 500 | 올라온 영수증 파일을 서버가 읽지 못함 |
| `SETTLEMENT-022` | 400 | 글자 인식이 다루지 못하는 이미지 형식(webp) |
| `SETTLEMENT-023` | 422 | 사진에서 품목을 하나도 읽지 못함 |
| `SETTLEMENT-024` | 504 | 글자 인식이 정해진 시간 안에 끝나지 않음 |
| `SETTLEMENT-025` | 503 | 글자 인식 서비스에 닿지 못했거나, 설정되지 않았거나, 알 수 없는 응답을 보냄 |

업로드 크기 초과는 정산 코드가 아니라 공통 코드 `COMMON-004`(413)로 응답한다. multipart
해석 단계에서 실패해 정산 컨트롤러에 닿지 못하기 때문이다.

### 지급은 지갑 오류도 그대로 내려준다

`POST /api/v1/settlements/{settlementId}/members/me/pay`는 지갑 이체를 타므로 **위 표에 없는
`WALLET-*` 코드로도 실패한다.** 정산 서비스가 이 예외를 감싸지 않고 그대로 올려보낸다.

| 오류 코드 | HTTP 상태 | 의미 |
| --- | ---: | --- |
| `WALLET-001` | 404 | 지갑이 없음 |
| `WALLET-014` | 400 | 이체 금액이나 대상이 올바르지 않음(본인에게 보내는 경우 포함) |
| `WALLET-015` | 409 | 지갑 잔액이 부족함 |
| `WALLET-016` | 403 | 지갑을 이 이체에 쓸 수 없는 상태 |

`WALLET-001`과 `WALLET-016`은 **원결제자 쪽 지갑 때문일 수도 있다.** 이체가 양쪽 지갑을
모두 확인하기 때문이다(`WalletTransferService.transfer`). 클라이언트 문구가 "당신의 지갑"이라고
단정하면 자기 지갑이 멀쩡한 사용자가 원인을 찾지 못한다.

**이 표가 `SETTLEMENT-*`만 담고 있던 것이 실제로 버그를 냈다.** 프론트엔드가 "정산 API의
오류는 `SETTLEMENT-*`뿐"으로 읽고 지갑 코드를 매핑에서 빠뜨려, 잔액이 모자란 사용자에게
충전으로 가는 길 대신 같은 요청을 다시 보내는 재시도 버튼만 보여 줬다(#452).

## 영수증과 multipart

영수증 사진은 정산보다 **먼저** 올린다. 정산 품목이 영수증에서 나온 값이라, 품목을 먼저
확정하고 사진을 나중에 붙이면 그 사진이 그 품목의 근거라는 보장이 사라지기 때문이다.

```
POST /api/v1/settlement-receipts             → { "receiptId": 12 }
POST /api/v1/settlement-receipts/12/ocr      → 품목 초안 (저장 안 함)
POST /api/v1/appointments/{id}/settlements   (본문에 receiptId: 12)
GET  /api/v1/settlements/{id}/receipt        → 이미지 바이트
```

`receiptId`는 정산 생성 요청의 선택 필드다. 넣지 않으면 사진 없는 정산이 만들어진다.
`receiptId`는 생성 요청 지문에도 들어가므로, 같은 멱등성 키로 영수증만 바꿔 다시 보내면
`SETTLEMENT-009`로 거절한다.

**보관 기한 안에서는 연결된 영수증을 교체하거나 삭제할 수 없다.** 품목이 그 사진에서 뽑은
스냅샷이라 사진만 바꾸면 대응이 깨진다. 잘못 올렸다면 정산을 만들기 전에 다시 올린다.

### 보관 기한

사진의 보관 기한은 **업로드 후 365일**이다. 기한은 버킷의 수명주기 규칙이 정하며, 규칙은
`receipts/` 아래 전부에 똑같이 적용된다. **정산에 연결됐는지 여부를 구분하지 않는다.**
따라서 1년이 지나면 연결된 영수증도 저장소에서 사라지고, 정산 기록만 남는다.

저장소는 사진을 지웠다고 알려주지 않는다. 그래서 애플리케이션은 만료 시점을 미리 계산하지
않고, **조회하다 "그런 파일 없다"는 응답을 받은 그 순간을 삭제 신호로 삼아**
`settlement_receipts.deleted_at`에 시각을 남긴다. 기한 숫자를 코드가 따로 들고 있으면
수명주기 규칙만 바뀌었을 때 양쪽이 조용히 어긋나기 때문이다.

이 기록 덕분에 "영수증을 처음부터 안 붙였다"(`SETTLEMENT-018`)와 "붙였지만 기한이 지나
사라졌다"(`SETTLEMENT-020`)를 구분할 수 있다. 사진이 사라졌다는 사실과 그것을 알아챈
시각이 남는 것이 이 컬럼의 목적이다.

**만료된 행은 조회에서 걸러내지 않는다.** `findBySettlementIdForViewer`는 `deleted_at`이
채워진 행도 그대로 돌려주고, 만료인지 아닌지는 서비스가 판단한다. 쿼리에서 걸러 버리면
만료 후 가장 먼저 조회한 참여자 한 명만 `SETTLEMENT-020`을 받고 나머지 참여자는 "처음부터
없었다"와 같은 `SETTLEMENT-018`을 받게 되어, 구분해 알려주려고 남긴 기록이 정작 쓰이지
못한다. 두 번째 조회부터는 `deleted_at`을 보고 **저장소를 부르지 않고 바로**
`SETTLEMENT-020`으로 답한다.

`linkToSettlement`의 `deleted_at IS NULL` 조건은 성격이 다르다. 그쪽은 초안을 보호하는
용도이므로 그대로 둔다.

아무도 조회하지 않은 행과 **정산에 연결되지 않은 초안 행은 표시되지 않고 그대로 남는다.**
초안은 `settlement_id`가 NULL이라 어떤 조회 경로로도 닿지 않으므로, 지워진 사진을 가리키는
행이 남아도 사용자에게 드러나지 않는다. 지금 규모에서는 무해하다고 보고 별도의 정리
작업을 두지 않는다.

### 업로드 규칙

| 항목 | 값 |
| --- | --- |
| 요청 형식 | `multipart/form-data`, 파트 이름 `file` |
| 허용 형식 | `image/jpeg`, `image/png`, `image/webp` |
| 크기 상한 | `RECEIPT_MAX_UPLOAD_BYTES` (기본 8MiB) |
| 정산당 장수 | 한 장 |

브라우저가 알려준 형식과 파일 내용에서 읽어낸 실제 형식이 모두 허용 목록에 있고 서로 같아야
통과한다. 확장자만 이미지로 바꾼 파일을 거르기 위해서다.

크기 상한을 올릴 때는 `nginx/nginx.conf`의 `client_max_body_size`도 함께 올린다. nginx가 더
작으면 요청이 백엔드에 닿기도 전에 잘려서 애플리케이션이 오류 코드를 돌려줄 기회조차 없다.

### 조회

`GET /api/v1/settlements/{settlementId}/receipt`는 이미지 바이트를 그대로 돌려주며 공통 응답
봉투를 쓰지 않는다. 정산 참여자와 생성자만 볼 수 있고, 그 밖의 사용자에게는
`SETTLEMENT-018`(404)로 응답해 정산의 존재 여부까지 감춘다.

보관 기한이 지나 사진이 사라졌다면 `SETTLEMENT-020`(410)으로 응답한다. **참여자 전원이
같은 답을 받는다** — 처음 알아챈 사람이든 그 뒤에 조회한 사람이든 마찬가지다. "원래
없었다"와 "기한이 지나 사라졌다"를 구분해 알려주는 편이 낫고, 로그에서도 상태 코드만으로
갈린다.

응답에는 `X-Content-Type-Options: nosniff`와 `Cache-Control: private, no-store`를 함께
내린다. 사용자가 올린 파일이라 브라우저가 형식을 임의로 재해석하지 못하게 막고, 다른
참여자에게 보이면 안 되는 사진이라 중간 캐시에 남기지 않기 위해서다.

인증은 쿠키 기반이므로 프론트엔드와 API의 오리진이 다르면 `<img src>`로는 쿠키가 실리지
않는다. 이 경우 클라이언트가 인증을 실어 직접 받아 표시해야 한다.

### 글자 인식

`POST /api/v1/settlement-receipts/{receiptId}/ocr`는 올려 둔 사진을 네이버 CLOVA OCR에
보내 ITEMIZED 품목의 **초안**을 돌려준다.

```json
{
  "items": [{ "name": "아메리카노", "unitPrice": 4500, "quantity": 2 }],
  "recognizedTotal": 9000
}
```

**결과는 저장하지 않는다.** DB에 남는 것은 사용자가 확인·수정해 정산 생성 요청으로 다시
올린 값뿐이다. 인식 결과를 저장해 두면 사용자가 고친 값과 원래 읽은 값 중 어느 쪽이 그
정산의 근거인지 알 수 없게 된다. 그래서 인식 결과용 테이블도 두지 않는다.

**아직 정산에 붙지 않은 자기 초안만 인식할 수 있다.** 남의 사진이거나 이미 정산에 붙은
사진이면 `SETTLEMENT-018`(404)로 답해 존재 여부까지 감춘다. 이미 붙은 사진을 다시 읽어 봐야
품목은 확정된 뒤라 쓸 데가 없고, 인식은 부를 때마다 요금이 나간다.

읽기만 하는데 `POST`인 이유도 요금이다. 브라우저나 중간 서버가 임의로 다시 부르면 안 되고,
사진 크기에 따라 수 초씩 걸리는 응답이 캐시에 남아서도 안 된다.

#### 읽은 값을 다듬는 규칙

| 상황 | 결과 |
| --- | --- |
| 줄 합계와 수량이 나누어떨어짐 | `unitPrice = 합계 ÷ 수량`, 수량 그대로 |
| 나누어떨어지지 않음 | `unitPrice = 줄 합계`, **수량 1** |
| 수량을 못 읽음 | 수량 1 |
| 이름과 금액을 모두 못 읽음 | 그 줄을 버림 |
| 쓸 만한 줄이 하나도 없음 | `SETTLEMENT-023`(422) |

수량보다 줄 합계를 먼저 믿는 이유는, ITEMIZED 정산이 **품목 합계와 원결제 금액이 정확히
같을 때만** 만들어지기 때문이다. 낱개 값 쪽을 믿으면 나누어떨어지지 않는 영수증에서 몇
원씩 어긋나 정산 생성이 통째로 거절된다. 수량은 사용자가 다시 넣을 수 있지만 금액은 그렇지
않다.

`recognizedTotal`은 영수증에 찍힌 합계다. 할인이나 봉사료가 품목 줄 밖에 붙기 때문에 품목을
다 더한 값과 다를 수 있어서, 계산해 채우지 않고 읽은 그대로 둔다.

**응답에는 남아 있지만 화면은 이 값을 쓰지 않는다.** 사진을 반듯하게 찍지 않으면 합계부터
틀리게 읽히는데, 그 값으로 결제 금액과 견주어 "다릅니다"라고 알리면 사용자가 손댈 수도 없는
숫자를 근거로 겁을 주게 된다. 인식이 하는 일은 품목 카드를 대신 채워 주는 것 하나다.
품목 합계가 원결제 금액과 같아야 한다는 규칙은 사용자가 카드에 확정한 값으로 판단하므로
이것과 별개다.

#### webp는 인식하지 못한다

업로드가 받아주는 세 형식 중 **webp만 CLOVA가 다루지 못한다**. 서버에서 png로 바꿔 보내면
사용자가 확인한 사진과 인식에 쓰인 사진이 달라져 "사진 한 장이 품목의 근거"라는 전제가
깨지므로, 바꾸지 않고 `SETTLEMENT-022`(400)로 거절한다. 사용자는 다시 찍거나 직접 입력한다.

#### 설정

| 환경 변수 | 뜻 |
| --- | --- |
| `CLOVA_OCR_INVOKE_URL` | 콘솔에서 영수증 도메인을 만들면 나오는 호출 주소 |
| `CLOVA_OCR_SECRET_KEY` | 같은 도메인의 Secret Key. 응답과 로그에 남기지 않는다 |
| `CLOVA_OCR_CONNECT_TIMEOUT_MILLIS` | 접속 대기(기본 3000) |
| `CLOVA_OCR_READ_TIMEOUT_MILLIS` | 응답 대기(기본 10000) |

주소와 비밀키는 도메인 하나에서 함께 나오므로 **항상 한 쌍**이다. 하나만 채우면 서버가
시작할 때 멈춘다. 사용자가 영수증을 찍는 순간에야 실패하면 원인을 찾기 어렵기 때문이다.
둘 다 비우면 글자 인식만 꺼지고 나머지 기능은 그대로 뜬다. 이 상태에서 인식을 부르면
`SETTLEMENT-025`(503)로 답한다.

읽기 대기가 OAuth(5초)보다 긴 것은 사진을 실제로 분석하는 시간이 들어가기 때문이다. 짧게
잡으면 정상 요청도 `SETTLEMENT-024`(504)로 끊긴다.

호출 주소가 영수증 도메인이 아니면 인식 자체는 성공했다는 응답이 오지만 영수증 결과가 없다.
이때도 `SETTLEMENT-025`(503)로 답한다. 사진 문제(`SETTLEMENT-023`)로 안내하면 사용자는
멀쩡한 영수증을 계속 다시 찍게 되고, 주소가 잘못됐다는 사실은 끝내 드러나지 않는다.
