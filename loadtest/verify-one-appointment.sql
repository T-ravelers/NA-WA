-- 시나리오 3(한 약속 집중, #570) 실행 뒤 확인. 읽기만 한다.
-- 호출하는 쪽이 @appointment_id를 먼저 설정한다(README 참고).

-- 1. 정원 초과와 기록 불일치
--    active_members      : 화면의 참여 인원과 같은 기준(ACTIVE, 방장 포함)
--    live_memberships    : 탈퇴하지 않은 모든 참여(PENDING 포함). active_members와 같아야 한다
--    held_deposits       : 잡혀 있는 보증금. 방장은 시드에서 보증금이 없으므로 active_members - 1
--    journey_items       : 참여자 여정에 걸린 약속 항목. 방장은 시드에서 여정이 없으므로 active_members - 1
SELECT
    a.appointment_id,
    a.max_members,
    a.appointment_status,
    (SELECT COUNT(*) FROM appointment_members m
      WHERE m.appointment_id = a.appointment_id
        AND m.membership_status = 'ACTIVE'
        AND m.deleted_at IS NULL) AS active_members,
    (SELECT COUNT(*) FROM appointment_members m
      WHERE m.appointment_id = a.appointment_id
        AND m.membership_status <> 'LEFT'
        AND m.deleted_at IS NULL) AS live_memberships,
    (SELECT COUNT(*) FROM deposits d
       JOIN appointment_members m ON m.appointment_member_id = d.appointment_member_id
      WHERE m.appointment_id = a.appointment_id
        AND d.deposit_status = 'HELD') AS held_deposits,
    (SELECT COUNT(*) FROM trip_items ti
      WHERE ti.appointment_id = a.appointment_id
        AND ti.deleted_at IS NULL) AS journey_items
FROM appointments a
WHERE a.appointment_id = @appointment_id;

-- 2. 판정. 하나라도 NG면 결과를 해석하지 않는다.
SELECT
    IF(active_members <= max_members, 'OK', 'NG') AS within_capacity,
    IF(live_memberships = active_members, 'OK', 'NG') AS no_stuck_pending,
    IF(held_deposits = active_members - 1, 'OK', 'NG') AS deposits_match,
    IF(journey_items = active_members - 1, 'OK', 'NG') AS journey_items_match
FROM (
    SELECT
        a.max_members,
        (SELECT COUNT(*) FROM appointment_members m
          WHERE m.appointment_id = a.appointment_id
            AND m.membership_status = 'ACTIVE'
            AND m.deleted_at IS NULL) AS active_members,
        (SELECT COUNT(*) FROM appointment_members m
          WHERE m.appointment_id = a.appointment_id
            AND m.membership_status <> 'LEFT'
            AND m.deleted_at IS NULL) AS live_memberships,
        (SELECT COUNT(*) FROM deposits d
           JOIN appointment_members m ON m.appointment_member_id = d.appointment_member_id
          WHERE m.appointment_id = a.appointment_id
            AND d.deposit_status = 'HELD') AS held_deposits,
        (SELECT COUNT(*) FROM trip_items ti
          WHERE ti.appointment_id = a.appointment_id
            AND ti.deleted_at IS NULL) AS journey_items
    FROM appointments a
    WHERE a.appointment_id = @appointment_id
) counts;

-- 3. 행 락 대기. 서버 전체 누적값이라 실행 전후 차이로 읽는다.
--    Innodb_row_lock_waits     : 기다린 횟수
--    Innodb_row_lock_time      : 기다린 시간 합(ms)
--    Innodb_row_lock_time_max  : 가장 오래 기다린 한 번(ms). 서버 기동 뒤 최댓값이다
SHOW GLOBAL STATUS LIKE 'Innodb_row_lock%';
