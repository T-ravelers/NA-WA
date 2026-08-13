# 정산 API 계약

정산은 원거래를 EQUAL 또는 ITEMIZED 방식으로 분담하는 기능이다. 참여자는 회원 ID가
아닌 약속 참가 행의 `appointment_member_id`로 식별한다.

## 유지 API

| API | 용도 |
| --- | --- |
| `GET /api/v1/settlements` | 현재 사용자가 생성했거나 지급할 정산 목록을 조회한다. |
| `GET /api/v1/settlements/candidates` | 생성 가능한 원거래와 약속 참가자 후보를 조회한다. |
| `POST /api/v1/appointments/{appointmentId}/settlements` | 원거래와 분담 규칙으로 정산을 생성한다. |
| `GET /api/v1/settlements/{settlementId}` | 참여한 정산의 상태, 개인 부담금과 ITEMIZED 품목 배분을 조회한다. |
| `POST /api/v1/settlements/{settlementId}/members/me/pay` | 현재 사용자의 미지급 부담금을 지급한다. |

생성 요청과 지급 요청에는 각각 `Idempotency-Key` 헤더가 필요하며, 값은 1~100자다.

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
클라이언트는 품목별 또는 참여자별 금액, 영수증 파일, OCR 결과, 영수증 분석 ID를 보내지
않는다. 서버가 `unitPrice × quantity`로 품목·배분 금액을 계산하고
`settlement_items`, `settlement_item_shares`에 스냅샷으로 저장한다.

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
