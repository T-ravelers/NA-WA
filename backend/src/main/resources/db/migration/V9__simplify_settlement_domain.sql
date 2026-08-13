-- V1의 정산 스키마를 축소된 도메인 계약으로 전환한다.
-- V9는 아직 어느 환경에도 적용되지 않았으므로 기존 V1 데이터도 함께 정규화한다.

UPDATE settlements
SET split_method = CASE
        WHEN split_method = 'GAME' THEN 'EQUAL'
        ELSE split_method
    END,
    settlement_status = CASE
        WHEN settlement_status = 'COMPLETED' THEN 'COMPLETED'
        ELSE 'REQUESTED'
    END;

-- 취소 관련 열을 삭제하기 전에 해당 FK와 CHECK를 먼저 제거한다.
ALTER TABLE settlements
    DROP FOREIGN KEY fk_settlements_canceller;

ALTER TABLE settlements
    DROP CHECK chk_settlements_cancelled;

ALTER TABLE settlements
    DROP COLUMN canceled_by_member_id,
    DROP COLUMN requested_at,
    DROP COLUMN canceled_at,
    DROP COLUMN cancel_reason,
    MODIFY COLUMN settlement_status ENUM('REQUESTED', 'COMPLETED')
        NOT NULL DEFAULT 'REQUESTED',
    MODIFY COLUMN split_method ENUM('EQUAL', 'ITEMIZED') NOT NULL,
    ADD COLUMN idempotency_key VARCHAR(100) NULL AFTER source_transfer_id,
    ADD COLUMN request_fingerprint CHAR(64) NULL AFTER idempotency_key;

-- 이미 존재하는 정산은 새 NOT NULL 멱등성 열을 적용하기 전에 고유한 legacy 값으로 채운다.
UPDATE settlements
SET idempotency_key = COALESCE(idempotency_key, CONCAT('legacy-', settlement_id)),
    request_fingerprint = COALESCE(
        request_fingerprint,
        SHA2(CONCAT('legacy-settlement-', settlement_id), 256)
    );

ALTER TABLE settlements
    MODIFY COLUMN idempotency_key VARCHAR(100) NOT NULL,
    MODIFY COLUMN request_fingerprint CHAR(64) NOT NULL,
    ADD CONSTRAINT uq_settlements_creator_idempotency
        UNIQUE (created_by_member_id, idempotency_key);

UPDATE settlement_members
SET request_status = CASE
        WHEN request_status = 'PAID' THEN 'PAID'
        WHEN request_status = 'NOT_REQUESTED' THEN 'NOT_REQUESTED'
        ELSE 'PENDING'
    END;

-- 리마인드 열을 삭제하기 전에 이 열을 참조하는 CHECK를 제거한다.
ALTER TABLE settlement_members
    DROP CHECK chk_settlement_members_reminders;

ALTER TABLE settlement_members
    DROP COLUMN participant_status,
    DROP COLUMN excluded_at,
    DROP COLUMN exclusion_reason,
    DROP COLUMN due_at,
    DROP COLUMN sent_at,
    DROP COLUMN last_reminded_at,
    DROP COLUMN reminder_count,
    MODIFY COLUMN request_status ENUM('NOT_REQUESTED', 'PENDING', 'PAID')
        NOT NULL DEFAULT 'NOT_REQUESTED',
    ADD COLUMN payment_idempotency_key VARCHAR(100) NULL AFTER paid_transfer_id;
