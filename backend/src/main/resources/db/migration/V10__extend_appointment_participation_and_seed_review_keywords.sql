-- 보증금 예치가 끝나기 전 약속과 참가자를 구분할 수 있도록 대기 상태를 추가한다.
ALTER TABLE appointments
    MODIFY COLUMN appointment_status
        ENUM(
            'PAYMENT_PENDING',
            'RECRUITING',
            'CLOSED',
            'CONFIRMED',
            'IN_PROGRESS',
            'COMPLETED',
            'CANCELLED'
        ) NOT NULL DEFAULT 'PAYMENT_PENDING';

ALTER TABLE appointment_members
    DROP CHECK chk_appointment_members_membership,
    MODIFY COLUMN membership_status
        ENUM('PENDING','ACTIVE','LEFT') NOT NULL DEFAULT 'PENDING',
    ADD CONSTRAINT chk_appointment_members_membership CHECK (
        (membership_status IN ('PENDING','ACTIVE') AND left_at IS NULL)
        OR (membership_status = 'LEFT' AND left_at IS NOT NULL)
    );

-- 화면 문구는 Vue i18n에서 관리하고 DB에는 안정적인 코드만 저장한다.
INSERT INTO member_review_keywords (keyword_code, display_order, is_active)
VALUES
    ('FRIENDLY', 1, TRUE),
    ('ON_TIME', 2, TRUE),
    ('CONSIDERATE', 3, TRUE),
    ('GOOD_COMMUNICATOR', 4, TRUE),
    ('WOULD_JOIN_AGAIN', 5, TRUE)
ON DUPLICATE KEY UPDATE keyword_code = keyword_code;
