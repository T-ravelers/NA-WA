# 약속·참가·보증금 ERD

탐색 항목을 기반으로 만든 그룹 약속과 참가자, 보증금 예치·정산의 관계를
보여줍니다.

```mermaid
erDiagram
    EXPLORE_ITEMS ||--o{ APPOINTMENTS : scheduled_for
    MEMBERS ||--o{ APPOINTMENTS : hosts
    APPOINTMENTS ||--o{ APPOINTMENT_MEMBERS : has
    MEMBERS ||--o{ APPOINTMENT_MEMBERS : joins
    TRIPS o|--o{ APPOINTMENT_MEMBERS : links

    APPOINTMENT_MEMBERS ||--o| DEPOSITS : holds
    WALLET_TRANSFERS o|--o| DEPOSITS : records_hold

    APPOINTMENTS ||--o| DEPOSIT_PAYOUT_BATCHES : resolves
    MEMBERS o|--o{ DEPOSIT_PAYOUT_BATCHES : resolves_by
    DEPOSIT_PAYOUT_BATCHES ||--o{ DEPOSIT_PAYOUTS : contains
    DEPOSITS ||--o{ DEPOSIT_PAYOUTS : source
    APPOINTMENT_MEMBERS ||--o{ DEPOSIT_PAYOUTS : receives
    WALLET_TRANSFERS ||--o| DEPOSIT_PAYOUTS : records

    EXPLORE_ITEMS {
        BIGINT item_id PK
        ENUM item_type
    }

    MEMBERS {
        BIGINT member_id PK
    }

    TRIPS {
        BIGINT trip_id PK
    }

    APPOINTMENTS {
        BIGINT appointment_id PK
        BIGINT item_id FK
        BIGINT host_member_id FK
        ENUM appointment_status
        INT max_members
        DATETIME activity_start_at
        DATETIME activity_end_at
        DECIMAL deposit_amount
    }

    APPOINTMENT_MEMBERS {
        BIGINT appointment_member_id PK
        BIGINT appointment_id FK
        BIGINT member_id FK
        BIGINT trip_id FK
        ENUM membership_status
        ENUM attendance_status
    }

    DEPOSITS {
        BIGINT deposit_id PK
        BIGINT appointment_member_id FK, UK
        BIGINT held_transfer_id FK, UK
        DECIMAL amount
        ENUM deposit_status
    }

    DEPOSIT_PAYOUT_BATCHES {
        BIGINT deposit_payout_batch_id PK
        BIGINT appointment_id FK, UK
        BIGINT resolved_by_member_id FK
        ENUM resolution_reason
        ENUM resolution_status
    }

    DEPOSIT_PAYOUTS {
        BIGINT deposit_payout_id PK
        BIGINT deposit_payout_batch_id FK
        BIGINT source_deposit_id FK
        BIGINT recipient_appointment_member_id FK
        BIGINT transfer_id FK, UK
        ENUM allocation_type
        DECIMAL amount
    }

    WALLET_TRANSFERS {
        BIGINT transfer_id PK
        ENUM transfer_type
        ENUM transfer_status
    }
```

- 약속은 이벤트와 장소를 포함하는 `explore_items`를 참조합니다.
- 결제 API가 연결되기 전에는 약속 생성 확인을 완료 처리하지 않습니다. 생성 요청은
  `APPOINTMENT-008`을 반환하며 약속·방장 회원·보증금·지갑 거래를 기록하지 않습니다.
  결제 연동 후에는 결제 성공 콜백에서 `PENDING` → `HELD` 및 `PENDING` → `ACTIVE`
  전이를 같은 트랜잭션으로 수행합니다.
- 참여 요청도 결제 연동 전에는 `APPOINTMENT-008`을 반환하며 참가자 회원·보증금을
  기록하지 않습니다. 결제 연동 후 나간 참가자는 `LEFT`로 기록하며 같은 약속에는
  재참여할 수 없습니다.
- 한 참가 이력에는 보증금 행을 최대 하나만 연결합니다.
- 약속별 payout batch는 하나이며, 실제 금액 이동은 `wallet_transfers`가 기록합니다.
