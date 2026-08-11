# 정산 API 계약

이 문서는 정산 생성, 요청, 결제와 영수증 분석의 백엔드 계약을 정의합니다. 참여자
식별자는 회원 ID가 아니라 약속 참가 행의 `appointment_member_id`를 사용합니다.

## 생성과 상태 전이

- `POST /api/v1/appointments/{appointmentId}/settlements`
  - 필수 헤더: `Idempotency-Key`(1~100자)
  - 본문: `sourceTransferId`, `type`, `participantAppointmentMemberIds`
  - ITEMIZED는 `receiptAnalysisId`, GAME은 `game.type`과 `game.liableCount`를 추가합니다.
  - 성공하면 `201 Created`와 생성된 ID를 반환하고 상태는 `DRAFT`입니다.
- `POST /api/v1/settlements/{settlementId}/request`
  - 생성자만 DRAFT를 REQUESTED로 바꿀 수 있습니다.
  - 생성자를 제외한 정산 구성원은 `PENDING`으로 전이합니다.
- `POST /api/v1/settlements/{settlementId}/cancel`
  - 생성자는 DRAFT 또는 결제가 시작되지 않은 REQUESTED를 취소할 수 있습니다.
  - GAME 정산은 연결된 게임도 같은 트랜잭션에서 CANCELLED로 바뀝니다.

같은 생성자와 같은 멱등성 키로 동일한 요청을 재시도하면 기존 ID를 반환합니다. 같은
키의 요청 지문이 다르면 `SETTLEMENT-009`, 다른 키로 이미 사용한 원거래를 다시 사용하면
`SETTLEMENT-010`과 HTTP 409를 반환합니다. 요청 지문에는 appointment, source, type,
정렬한 참가 행 ID, ITEMIZED 분석 ID와 GAME 설정이 포함됩니다.

## 금액 무결성

EQUAL은 원거래 통화의 `currencies.decimal_places`를 사용합니다. 나눗셈 후 남은 최소
통화 단위는 `appointment_member_id` 오름차순으로 배정합니다. 예를 들어 KRW 100원을
3명이 나누면 34원, 33원, 33원입니다.

ITEMIZED 생성은 다음 금액이 모두 정확히 같을 때만 허용합니다.

1. 원거래 금액
2. 영수증 항목 `line_total` 합계
3. 영수증 `recognized_total`
4. 참가자별 배분 금액 합계

## 영수증과 multipart

같은 source/creator의 DRAFT 영수증 분석을 다시 업로드하면 기존 분석 ID를 재사용하고
기존 배분과 품목을 지운 뒤 파일명과 합계를 초기화합니다. ALLOCATED 또는 USED 상태의
재업로드는 `SETTLEMENT-012`와 HTTP 409입니다. 같은 `(itemId,
appointmentMemberId)` 배분 쌍을 중복 전송하면 `SETTLEMENT-007`과 HTTP 400입니다.

WAR의 DispatcherServlet에는 multipart 설정이 명시돼 있습니다. 다음 환경 변수로 제한을
조정합니다.

| 환경 변수 | 기본값 | 설명 |
| --- | ---: | --- |
| `SETTLEMENT_RECEIPT_ALLOWED_CONTENT_TYPES` | `image/jpeg,image/png,application/pdf` | 허용 MIME 목록 |
| `SETTLEMENT_RECEIPT_ALLOWED_EXTENSIONS` | `jpg,jpeg,png,pdf` | 허용 확장자 목록 |
| `SETTLEMENT_RECEIPT_MAX_FILE_SIZE_BYTES` | `5242880` | 파일 하나의 최대 크기 |
| `SETTLEMENT_RECEIPT_MAX_REQUEST_SIZE_BYTES` | `6291456` | multipart 요청 최대 크기 |
| `SETTLEMENT_RECEIPT_FILE_SIZE_THRESHOLD_BYTES` | `0` | 디스크 기록 임계값 |
| `SETTLEMENT_RECEIPT_UPLOAD_TEMP_DIR` | JVM 임시 디렉터리 | 임시 파트 저장 위치 |

파일은 영속 저장하지 않으며 현재 구현은 영수증 메타데이터와 사용자가 확정한 품목만
MySQL에 보관합니다.
