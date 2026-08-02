# ERD rev4.9 보증금 보완 설계

## 목표

`nawa rev4.8 확정-snapshot.json`을 V1 및 V2가 모두 반영된 기준 파일로 사용해,
보증금 지급의 상태 의미·취소 환불·출석 근거·송금 검증 규칙을 명확하게 표현한
`nawa rev4.9 확정-snapshot.json`을 저장소 루트에 생성한다.

이번 변경은 ERD snapshot JSON에만 적용한다. Flyway V1·V2 SQL과 애플리케이션 코드는
수정하지 않는다.

## 기준 보존 범위

- rev4.8의 29개 테이블, 기존 PK·FK, 엔티티·필드 ID, 좌표와 색상을 보존한다.
- V2에서 반영한 `wallets.version` 제거와 `wallet_topups`의 Stripe 관련 네 컬럼을
  그대로 보존한다.
- 보증금 보완에 필요한 필드·ENUM·comment만 변경한다.
- 신규 필드에는 전체 snapshot에서 중복되지 않는 `_id`를 부여한다.
- snapshot의 `createdAt`은 rev4.9 생성 시각으로 갱신한다.

## 보증금 구조 변경

### deposits

`deposit_status`를 다음 상태로 정리한다.

- `PENDING`: 아직 보증금을 맡기지 않은 상태
- `HELD`: 회원 지갑에서 `SYSTEM_ESCROW`로 보증금이 정상 이동한 상태
- `REFUNDED`: 출석 또는 약속 취소로 원소유자에게 전액 환불된 상태
- `DISTRIBUTED`: 노쇼 보증금 전액이 출석자들에게 분배된 상태
- `CANCELLED`: 보증금을 맡기기 전에 참가 또는 약속이 취소된 상태

중간 상태와 최종 상태가 섞이는 문제를 없애기 위해 `FORFEITED`를 제거한다.
이미 `HELD`인 보증금은 `CANCELLED`로 바로 변경할 수 없고,
`CANCELLATION_REFUND` 지급을 완료한 뒤 `REFUNDED`가 되어야 한다.

`held_transfer_id` comment에는 다음 검증을 명시한다.

- 거래 종류는 `DEPOSIT_HOLD`, 상태는 `COMPLETED`여야 한다.
- 거래 금액은 `deposits.amount`와 같아야 한다.
- DEBIT 지갑 주인은 해당 `appointment_member`의 회원이어야 한다.
- CREDIT 지갑 주인은 `SYSTEM_ESCROW`여야 한다.
- DEBIT·CREDIT 합계와 통화가 일치해야 한다.

### deposit_payout_batches

`resolution_reason ENUM('APPOINTMENT_COMPLETED','APPOINTMENT_CANCELLED') NOT NULL`을
추가한다.

- `APPOINTMENT_COMPLETED`: 출석 결과에 따라 자기 보증금 환불과 노쇼 보증금 분배를 처리한다.
- `APPOINTMENT_CANCELLED`: 이미 보관된 모든 보증금을 원소유자에게 환불한다.

완료 배치에는 다음 합계 규칙을 적용한다.

- `total_refund_amount + total_distributed_amount = total_held_amount`
- `total_distributed_amount = total_forfeited_amount`
- 약속 취소 배치는 `total_forfeited_amount = total_distributed_amount = 0`
- `resolved_at`은 `COMPLETED`일 때 필수다.

노쇼 보증금이 최소 통화 단위로 정확히 나누어지지 않으면 기본 몫을 먼저 배분하고,
남은 최소 단위는 `recipient_appointment_member_id` 오름차순으로 한 단위씩 지급한다.

### deposit_payouts

`allocation_type`을 다음과 같이 확장한다.

- `SELF_REFUND`: 출석자에게 자기 보증금을 환불
- `NO_SHOW_SHARE`: 노쇼한 사람의 보증금을 출석자에게 분배
- `CANCELLATION_REFUND`: 약속 취소로 원소유자에게 환불

지급 당시 판단 근거를 보존하기 위해 다음 필드를 추가한다.

- `source_attendance_status_snapshot ENUM('PENDING','ATTENDED','NO_SHOW') NOT NULL`
- `recipient_attendance_status_snapshot ENUM('PENDING','ATTENDED','NO_SHOW') NOT NULL`

유형별 규칙은 다음과 같다.

- `SELF_REFUND`: 원소유자와 수취자가 같고 두 snapshot이 `ATTENDED`여야 한다.
- `NO_SHOW_SHARE`: 원소유자는 `NO_SHOW`, 수취자는 `ATTENDED`여야 한다.
- `CANCELLATION_REFUND`: 원소유자와 수취자가 같아야 하며 출석 상태는 제한하지 않는다.
- source deposit, 수취자, payout batch는 모두 같은 약속에 속해야 한다.
- source deposit별 payout 합계는 `deposits.amount`와 일치해야 한다.

`transfer_id`는 다음 조건을 만족해야 한다.

- 지급 유형에 따라 거래 종류가 `DEPOSIT_REFUND` 또는
  `DEPOSIT_FORFEIT_DISTRIBUTION`이어야 한다.
- 거래 상태는 `COMPLETED`, 거래 금액은 `deposit_payouts.amount`와 같아야 한다.
- 정확히 한 개의 활성 DEBIT 원장은 `SYSTEM_ESCROW`, 한 개의 활성 CREDIT 원장은
  수취자의 회원 지갑을 가리켜야 한다.
- DEBIT·CREDIT 합계와 통화가 일치해야 한다.

존재하지 않는 `deposit_resolution` 명칭은 모두 `deposit_payout_batches`로 수정한다.

## 오류 및 재처리 원칙

- 배치 생성과 각 payout·transfer·ledger 기록은 같은 서비스 트랜잭션 경계에서 처리한다.
- `idempotency_key`로 같은 약속의 중복 해소를 막는다.
- 일부 지급 실패 시 배치를 `COMPLETED`로 만들지 않는다.
- 금융 기록은 soft delete하지 않고 역거래로 정정한다.

## 검증 기준

1. rev4.8 JSON이 정상 파싱되고 V1+V2의 테이블·컬럼 결과를 포함한다.
2. rev4.9도 정상 파싱되며 29개 테이블과 기존 관계를 보존한다.
3. 모든 엔티티·필드 `_id`가 중복되지 않는다.
4. 모든 FK의 대상 엔티티와 필드가 존재한다.
5. `wallets.version`은 없고 `wallet_topups`의 V2 네 컬럼은 유지된다.
6. `deposit_status`에 `FORFEITED`가 없고 다섯 상태만 존재한다.
7. `resolution_reason`, `CANCELLATION_REFUND`, 두 출석 snapshot 필드가 존재한다.
8. 잘못된 `deposit_resolution` 문자열이 남아 있지 않는다.
9. rev4.8 대비 변경이 이 문서의 보증금 범위를 벗어나지 않는다.

## 최종 설명 산출물

- rev4.8에서 rev4.9로 바뀐 필드와 이유
- 생성 파일 검증 결과
- 정산 액터 관점의 Mermaid `sequenceDiagram` 1개
- 정산 액터와 테이블 JOIN 관계를 보여주는 Mermaid `flowchart` 1개
