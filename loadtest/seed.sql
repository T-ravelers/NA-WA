-- NA-WA 부하 테스트 전용 시드.
-- loadtest/seed.sh가 @vu_count와 @payee_pool_size를 설정한 뒤 실행한다.
-- 전용 nawa-loadtest 볼륨의 빈 DB를 전제로 하며, 재실행 전에는 README의 볼륨
-- 초기화 절차를 따른다.

SET SESSION cte_max_recursion_depth = 10000;

-- 공개 탐색 목록·상세와 약속이 공통으로 참조하는 이벤트 하나.
INSERT INTO explore_items (
    item_id, item_type, approval_status, visibility_status,
    appointment_count, participant_count, popularity_score,
    reviewed_by, reviewed_at
) VALUES (
    500000, 'EVENT', 'APPROVED', 'VISIBLE', 0, 0, 100.0000,
    1000000, CURRENT_TIMESTAMP
);

INSERT INTO event (
    event_id, event_type, event_kind, title, description, venue_name,
    start_date, end_date, status, region1, region2, address_road,
    latitude, longitude, is_free, view_count, favorite_count
) VALUES (
    500000, 'OFFICIAL', 'FESTIVAL', 'Load-test event',
    'Public synthetic data for local load testing', 'Load-test venue',
    '2026-08-01', '2027-12-31', 'ONGOING', 'Seoul', 'Jung-gu',
    '1 Load-test-ro', 37.5665000, 126.9780000, TRUE, 100000, 1000
);

-- 회원 ID 계약:
--   900000 + VU: 주 사용자
--   950000 + (VU % pool): QR 수취인
--   970000 + VU: 정산 납부 참여자
--   990000: 시나리오 1 공용 모집 약속 방장
INSERT INTO members (
    member_id, preferred_currency_code, display_name, preferred_language,
    onboarding_completed_at, account_type
)
WITH RECURSIVE seq(n) AS (
    SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < @vu_count
)
SELECT 900000 + n, 'KRW', CONCAT('loadtest-user-', n), 'en', CURRENT_TIMESTAMP, 'TRAVELER'
FROM seq;

INSERT INTO members (
    member_id, preferred_currency_code, display_name, preferred_language,
    onboarding_completed_at, account_type
)
WITH RECURSIVE seq(n) AS (
    SELECT 0 UNION ALL SELECT n + 1 FROM seq WHERE n + 1 < @payee_pool_size
)
SELECT 950000 + n, 'KRW', CONCAT('loadtest-payee-', n), 'en', CURRENT_TIMESTAMP, 'TRAVELER'
FROM seq;

INSERT INTO members (
    member_id, preferred_currency_code, display_name, preferred_language,
    onboarding_completed_at, account_type
)
WITH RECURSIVE seq(n) AS (
    SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < @vu_count
)
SELECT 970000 + n, 'KRW', CONCAT('loadtest-participant-', n), 'en', CURRENT_TIMESTAMP, 'TRAVELER'
FROM seq;

INSERT INTO members (
    member_id, preferred_currency_code, display_name, preferred_language,
    onboarding_completed_at, account_type
) VALUES (990000, 'KRW', 'loadtest-host', 'en', CURRENT_TIMESTAMP, 'TRAVELER');

INSERT INTO wallet_owners (member_id, owner_type)
SELECT member_id, 'MEMBER'
FROM members
WHERE (member_id BETWEEN 900001 AND 900000 + @vu_count)
   OR (member_id BETWEEN 950000 AND 950000 + @payee_pool_size - 1)
   OR (member_id BETWEEN 970001 AND 970000 + @vu_count)
   OR member_id = 990000;

INSERT INTO wallets (wallet_owner_id, currency_code, available_balance)
SELECT wallet_owner_id, 'KRW',
       CASE
           WHEN member_id BETWEEN 900001 AND 900000 + @vu_count THEN 200000.0000
           WHEN member_id BETWEEN 970001 AND 970000 + @vu_count THEN 100000.0000
           WHEN member_id = 990000 THEN 100000.0000
           ELSE 0.0000
       END
FROM wallet_owners
WHERE member_id IS NOT NULL
  AND ((member_id BETWEEN 900001 AND 900000 + @vu_count)
    OR (member_id BETWEEN 950000 AND 950000 + @payee_pool_size - 1)
    OR (member_id BETWEEN 970001 AND 970000 + @vu_count)
    OR member_id = 990000);

-- 사용자별 여정. QR 공동지출의 trip_expense_link와 기존 리포트가 공유한다.
INSERT INTO trips (trip_id, member_id, title, start_date, end_date)
WITH RECURSIVE seq(n) AS (
    SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < @vu_count
)
SELECT 600000 + n, 900000 + n, CONCAT('loadtest-trip-', n),
       CURRENT_DATE - INTERVAL 1 DAY, CURRENT_DATE + INTERVAL 1 DAY
FROM seq;

INSERT INTO reports (
    report_id, trip_id, generation_status, locale, report_content, generated_at
)
WITH RECURSIVE seq(n) AS (
    SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < @vu_count
)
SELECT 600000 + n, 600000 + n, 'COMPLETED', 'en',
       JSON_OBJECT(
           'journey', JSON_OBJECT(
               'tripId', 600000 + n,
               'title', CONCAT('loadtest-trip-', n),
               'startDate', DATE_FORMAT(CURRENT_DATE - INTERVAL 1 DAY, '%Y-%m-%d'),
               'endDate', DATE_FORMAT(CURRENT_DATE + INTERVAL 1 DAY, '%Y-%m-%d')
           ),
           'days', JSON_ARRAY(),
           'analytics', JSON_OBJECT(
               'totalSpent', 0,
               'dailyAverage', 0,
               'categoryBreakdown', JSON_ARRAY(),
               'dailyTrend', JSON_ARRAY()
           )
       ),
       CURRENT_TIMESTAMP
FROM seq;

-- 시나리오 1의 참여 대상 약속들.
--
-- 정원이 큰 약속 하나에 전원을 몰지 않는다. joinAppointment는 약속 행을
-- FOR UPDATE로 잠그므로, 8,920 VU가 한 행에 몰리면 백엔드 처리량이 아니라
-- 그 행의 락 대기를 측정하게 된다 — 운영에서는 일어나지 않는 병목이다.
--
-- current_member_count는 ACTIVE 멤버를 세고 방장도 여기 포함된다.
-- 그래서 정원 N인 약속의 VU 자리는 N-1이다.
SET @members_per_appointment = 6;
SET @vu_slots_per_appointment = @members_per_appointment - 1;
SET @recruiting_count = CEIL(@vu_count / @vu_slots_per_appointment);

INSERT INTO appointments (
    appointment_id, item_id, host_member_id, language_code, appointment_name,
    appointment_description, max_members, deposit_amount, appointment_status,
    meeting_place, activity_start_at, activity_end_at
)
WITH RECURSIVE seq(n) AS (
    SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < @recruiting_count
)
SELECT 700000 + n, 500000, 990000, 'en',
       CONCAT('loadtest-recruiting-', n), 'Synthetic appointment for scenario 1',
       @members_per_appointment, 5000.0000, 'RECRUITING', 'Load-test venue',
       CURRENT_TIMESTAMP + INTERVAL 30 DAY,
       CURRENT_TIMESTAMP + INTERVAL 30 DAY + INTERVAL 2 HOUR
FROM seq;

-- 방장은 990000 하나를 모든 약속에 재사용한다. 방장은 측정 경로(참여)를 타지
-- 않으므로 나눌 이유가 없고, (appointment_id, member_id) UNIQUE도 걸리지 않는다.
INSERT INTO appointment_members (
    appointment_id, member_id, trip_id, membership_status, attendance_status
)
WITH RECURSIVE seq(n) AS (
    SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < @recruiting_count
)
SELECT 700000 + n, 990000, NULL, 'ACTIVE', 'PENDING' FROM seq;

-- 시나리오 2의 QR 공동지출·정산용 진행 중 약속.
INSERT INTO appointments (
    appointment_id, item_id, host_member_id, language_code, appointment_name,
    appointment_description, max_members, deposit_amount, appointment_status,
    meeting_place, activity_start_at, activity_end_at
)
WITH RECURSIVE seq(n) AS (
    SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < @vu_count
)
SELECT 710000 + n, 500000, 900000 + n, 'en',
       CONCAT('loadtest-shared-', n), 'Synthetic shared-spending appointment',
       2, 5000.0000, 'IN_PROGRESS', 'Load-test venue',
       CURRENT_TIMESTAMP - INTERVAL 1 HOUR, CURRENT_TIMESTAMP + INTERVAL 1 DAY
FROM seq;

INSERT INTO appointment_members (
    appointment_id, member_id, trip_id, membership_status, attendance_status
)
WITH RECURSIVE seq(n) AS (
    SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < @vu_count
)
SELECT 710000 + n, 900000 + n, 600000 + n, 'ACTIVE', 'PENDING' FROM seq
UNION ALL
SELECT 710000 + n, 970000 + n, NULL, 'ACTIVE', 'PENDING' FROM seq;

-- 시나리오 2의 출석 확정용: 주 사용자가 방장이고 활동은 이미 끝났다.
INSERT INTO appointments (
    appointment_id, item_id, host_member_id, language_code, appointment_name,
    appointment_description, max_members, deposit_amount, appointment_status,
    meeting_place, activity_start_at, activity_end_at
)
WITH RECURSIVE seq(n) AS (
    SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < @vu_count
)
SELECT 800000 + n, 500000, 900000 + n, 'en',
       CONCAT('loadtest-hosted-', n), 'Synthetic completed-activity appointment',
       2, 5000.0000, 'IN_PROGRESS', 'Load-test venue',
       CURRENT_TIMESTAMP - INTERVAL 2 DAY, CURRENT_TIMESTAMP - INTERVAL 1 DAY
FROM seq;

INSERT INTO appointment_members (
    appointment_id, member_id, trip_id, membership_status, attendance_status
)
WITH RECURSIVE seq(n) AS (
    SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < @vu_count
)
SELECT 800000 + n, 900000 + n, NULL, 'ACTIVE', 'PENDING' FROM seq;

-- 출석 확정은 HELD 보증금을 요구한다. 회원 지갑 -> DEPOSIT_POOL 이체와
-- 원장을 시드에도 같이 남겨 런타임 정합성을 유지한다.
INSERT INTO wallet_transfers (
    currency_code, initiator_member_id, transfer_number, transfer_type,
    transfer_status, amount, idempotency_key, memo, completed_at
)
WITH RECURSIVE seq(n) AS (
    SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < @vu_count
)
SELECT 'KRW', 900000 + n, CONCAT('SEED-HOSTED-DEPOSIT-', n), 'DEPOSIT_HOLD',
       'COMPLETED', 5000.0000, CONCAT('seed-hosted-deposit-', n),
       'Load-test hosted appointment deposit', CURRENT_TIMESTAMP
FROM seq;

INSERT INTO wallet_ledger_entries (
    transfer_id, wallet_id, entry_type, amount, balance_after
)
SELECT wt.transfer_id, w.wallet_id, 'DEBIT', 5000.0000, 195000.0000
FROM wallet_transfers wt
JOIN wallet_owners wo ON wo.member_id = wt.initiator_member_id
JOIN wallets w ON w.wallet_owner_id = wo.wallet_owner_id
WHERE wt.idempotency_key LIKE 'seed-hosted-deposit-%';

INSERT INTO wallet_ledger_entries (
    transfer_id, wallet_id, entry_type, amount, balance_after
)
SELECT wt.transfer_id, pool.wallet_id, 'CREDIT', 5000.0000,
       5000.0000 * ROW_NUMBER() OVER (ORDER BY wt.transfer_id)
FROM wallet_transfers wt
JOIN wallet_owners pool_owner
  ON pool_owner.owner_type = 'SYSTEM' AND pool_owner.system_code = 'DEPOSIT_POOL'
JOIN wallets pool ON pool.wallet_owner_id = pool_owner.wallet_owner_id
WHERE wt.idempotency_key LIKE 'seed-hosted-deposit-%';

UPDATE wallets w
JOIN wallet_owners wo ON wo.wallet_owner_id = w.wallet_owner_id
SET w.available_balance = 195000.0000
WHERE wo.member_id BETWEEN 900001 AND 900000 + @vu_count;

UPDATE wallets pool
JOIN wallet_owners pool_owner ON pool_owner.wallet_owner_id = pool.wallet_owner_id
SET pool.available_balance = 5000.0000 * @vu_count
WHERE pool_owner.owner_type = 'SYSTEM' AND pool_owner.system_code = 'DEPOSIT_POOL';

INSERT INTO deposits (
    appointment_member_id, held_transfer_id, amount, deposit_status, held_at
)
SELECT am.appointment_member_id, wt.transfer_id, 5000.0000, 'HELD', CURRENT_TIMESTAMP
FROM appointment_members am
JOIN wallet_transfers wt
  ON wt.initiator_member_id = am.member_id
 AND wt.idempotency_key = CONCAT('seed-hosted-deposit-', am.member_id - 900000)
WHERE am.appointment_id = 800000 + (am.member_id - 900000)
  AND am.member_id BETWEEN 900001 AND 900000 + @vu_count;

-- 실행 후 계약과 건수를 즉시 확인할 수 있게 요약을 남긴다.
SELECT 'members' AS seeded, COUNT(*) AS count
FROM members
WHERE (member_id BETWEEN 900001 AND 900000 + @vu_count)
   OR (member_id BETWEEN 950000 AND 950000 + @payee_pool_size - 1)
   OR (member_id BETWEEN 970001 AND 970000 + @vu_count)
   OR member_id = 990000
UNION ALL
SELECT 'recruiting_appointments', COUNT(*) FROM appointments
WHERE appointment_id BETWEEN 700001 AND 700000 + @recruiting_count
UNION ALL
SELECT 'vu_slots_per_appointment', @vu_slots_per_appointment
UNION ALL
SELECT 'shared_appointments', COUNT(*) FROM appointments
WHERE appointment_id BETWEEN 710001 AND 710000 + @vu_count
UNION ALL
SELECT 'hosted_appointments', COUNT(*) FROM appointments
WHERE appointment_id BETWEEN 800001 AND 800000 + @vu_count
UNION ALL
SELECT 'reports', COUNT(*) FROM reports
WHERE report_id BETWEEN 600001 AND 600000 + @vu_count;
