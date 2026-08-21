-- 참여 마감 시각을 없애고, 시간 기반 "모집 마감"을 뜻하던 CLOSED를 정원 충족만
-- 뜻하는 FULL로 재정의한다. 도달 경로가 없던 CONFIRMED도 함께 걷어낸다.
--
-- ENUM에서 값을 빼면 그 값을 가진 행은 빈 문자열이 되므로, 새 값을 먼저 넣고
-- 데이터를 옮긴 뒤에 옛 값을 뺀다. 세 단계의 순서를 바꾸면 데이터가 사라진다.

-- 1) FULL을 추가한다. 옛 값(CLOSED·CONFIRMED)은 아직 남겨 둔다.
ALTER TABLE appointments
    MODIFY COLUMN appointment_status
        ENUM(
            'PAYMENT_PENDING',
            'RECRUITING',
            'CLOSED',
            'CONFIRMED',
            'FULL',
            'IN_PROGRESS',
            'COMPLETED',
            'CANCELLED'
        ) NOT NULL DEFAULT 'PAYMENT_PENDING';

-- 2) 옛 값을 실제 정원 충족 여부로 옮긴다. 마감 시각이 지나 CLOSED가 된 약속은
--    정원이 차지 않았을 수 있어, 그대로 FULL로 부르면 사실과 달라진다.
--    정원이 덜 찬 약속을 RECRUITING으로 되돌리면 활동 시작이 이미 지난 행이
--    생기지만, 새로 추가한 RECRUITING → IN_PROGRESS 전이를 스케줄러가 다음
--    주기에 정리하고 조회 응답은 그 사이에도 IN_PROGRESS로 계산해 보여준다.
UPDATE appointments a
SET a.appointment_status = CASE
        WHEN (
            SELECT COUNT(*)
            FROM appointment_members m
            WHERE m.appointment_id = a.appointment_id
              AND m.membership_status = 'ACTIVE'
              AND m.deleted_at IS NULL
        ) >= a.max_members THEN 'FULL'
        ELSE 'RECRUITING'
    END
WHERE a.appointment_status IN ('CLOSED', 'CONFIRMED');

-- 3) 옮기고 난 뒤에야 옛 값을 뺀다.
ALTER TABLE appointments
    MODIFY COLUMN appointment_status
        ENUM(
            'PAYMENT_PENDING',
            'RECRUITING',
            'FULL',
            'IN_PROGRESS',
            'COMPLETED',
            'CANCELLED'
        ) NOT NULL DEFAULT 'PAYMENT_PENDING';

-- 4) 참여 마감 컬럼을 걷어낸다. chk_appointments_schedule(V3)이 join_deadline과
--    활동 시간 순서를 함께 검사하고 있어 컬럼만 떼어낼 수 없다. 제약을 통째로
--    지우고, 마감과 무관한 나머지 절반만 새 제약으로 다시 세운다.
ALTER TABLE appointments
    DROP CHECK chk_appointments_schedule;

ALTER TABLE appointments
    DROP COLUMN join_deadline;

ALTER TABLE appointments
    ADD CONSTRAINT chk_appointments_activity_window CHECK (
        activity_start_at < activity_end_at
    );
