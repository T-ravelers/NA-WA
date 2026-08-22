-- ============================================================================
-- 리포트 비교 시연용 시드 (#401)
--
-- Flyway 밖에서 손으로 적용한다. 두 번 돌려도 결과가 같다 — 앞서 넣은 시드를 먼저 지운다.
--
--   docker compose exec -T mysql \
--     mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE" \
--     < backend/docs/database/seed/report-demo.sql
--
-- 넣는 것: 시연 계정(@host)과 같은 약속에 참가한 동료 3명과 네 사람의 여정 기간 QR 결제,
-- 같은 국적으로 리포트를 이미 만든 회원 2명. 호스트의 결제는 trip_expense_links에 걸지
-- 않는다 — 호스트는 시연에서 UI로 리포트를 만든다(걸어 두면 REPORT-008으로 막힌다).
--
-- 태그: 회원 display_name 'Seed Report %', 여정 title 'Seed Report %', 약속 'Seed Report
-- Appointment', 결제 idempotency_key 'seed:report-demo:%'. 지울 때 이 태그만 본다.
-- ============================================================================

-- ★ 시연 계정의 member_id로 바꾼다.
SET @host := 1;

-- ----------------------------------------------------------------------------
-- 1. 이전 시드 제거 (FK 역순)
-- ----------------------------------------------------------------------------
DELETE le
FROM wallet_ledger_entries le
JOIN wallet_transfers t ON t.transfer_id = le.transfer_id
WHERE t.idempotency_key LIKE 'seed:report-demo:%';

DELETE tel
FROM trip_expense_links tel
JOIN trips tr ON tr.trip_id = tel.trip_id
WHERE tr.title LIKE 'Seed Report %';

DELETE FROM reports
WHERE trip_id IN (SELECT trip_id FROM trips WHERE title LIKE 'Seed Report %');

DELETE ti
FROM trip_items ti
JOIN trips tr ON tr.trip_id = ti.trip_id
WHERE tr.title LIKE 'Seed Report %';

DELETE am
FROM appointment_members am
JOIN appointments a ON a.appointment_id = am.appointment_id
WHERE a.appointment_name = 'Seed Report Appointment';

DELETE FROM appointments WHERE appointment_name = 'Seed Report Appointment';

-- 승인된 이벤트가 하나도 없을 때만 만들었던 빈 탐색 항목.
DELETE FROM explore_items
WHERE created_by = @host AND reviewed_by = @host AND item_type = 'EVENT'
  AND appointment_count = 0 AND participant_count = 0;

DELETE FROM wallet_transfers WHERE idempotency_key LIKE 'seed:report-demo:%';

DELETE FROM trips WHERE title LIKE 'Seed Report %';

DELETE w
FROM wallets w
JOIN wallet_owners wo ON wo.wallet_owner_id = w.wallet_owner_id
JOIN members m ON m.member_id = wo.member_id
WHERE m.display_name LIKE 'Seed Report %';

DELETE wo
FROM wallet_owners wo
JOIN members m ON m.member_id = wo.member_id
WHERE m.display_name LIKE 'Seed Report %';

DELETE FROM members WHERE display_name LIKE 'Seed Report %';

-- ----------------------------------------------------------------------------
-- 2. 호스트 전제 — 국적(코호트 기준)과 KRW 지갑
-- ----------------------------------------------------------------------------
UPDATE members
SET nationality_code = COALESCE(nationality_code, 'KR')
WHERE member_id = @host;

SET @nat := (SELECT nationality_code FROM members WHERE member_id = @host);

SET @host_wallet := (
    SELECT w.wallet_id
    FROM wallets w
    JOIN wallet_owners wo ON wo.wallet_owner_id = w.wallet_owner_id
    WHERE wo.member_id = @host AND w.currency_code = 'KRW' AND w.deleted_at IS NULL
    ORDER BY w.wallet_id
    LIMIT 1
);

INSERT INTO wallet_owners (member_id, owner_type)
SELECT @host, 'MEMBER' FROM DUAL WHERE @host_wallet IS NULL;

INSERT INTO wallets (wallet_owner_id, currency_code, available_balance)
SELECT wo.wallet_owner_id, 'KRW', 2000000.0000
FROM wallet_owners wo
WHERE wo.member_id = @host AND @host_wallet IS NULL;

SET @host_wallet := COALESCE(@host_wallet, (
    SELECT w.wallet_id
    FROM wallets w
    JOIN wallet_owners wo ON wo.wallet_owner_id = w.wallet_owner_id
    WHERE wo.member_id = @host AND w.currency_code = 'KRW'
    ORDER BY w.wallet_id DESC
    LIMIT 1
));

-- ----------------------------------------------------------------------------
-- 3. 같은 약속 동료 3명 + 지갑
-- ----------------------------------------------------------------------------
INSERT INTO members (display_name, nationality_code) VALUES ('Seed Report Mina', @nat);
SET @mina := LAST_INSERT_ID();
INSERT INTO wallet_owners (member_id, owner_type) VALUES (@mina, 'MEMBER');
INSERT INTO wallets (wallet_owner_id, currency_code, available_balance)
VALUES (LAST_INSERT_ID(), 'KRW', 1500000.0000);
SET @mina_wallet := LAST_INSERT_ID();

INSERT INTO members (display_name, nationality_code) VALUES ('Seed Report Jae', @nat);
SET @jae := LAST_INSERT_ID();
INSERT INTO wallet_owners (member_id, owner_type) VALUES (@jae, 'MEMBER');
INSERT INTO wallets (wallet_owner_id, currency_code, available_balance)
VALUES (LAST_INSERT_ID(), 'KRW', 1500000.0000);
SET @jae_wallet := LAST_INSERT_ID();

INSERT INTO members (display_name, nationality_code) VALUES ('Seed Report Sora', @nat);
SET @sora := LAST_INSERT_ID();
INSERT INTO wallet_owners (member_id, owner_type) VALUES (@sora, 'MEMBER');
INSERT INTO wallets (wallet_owner_id, currency_code, available_balance)
VALUES (LAST_INSERT_ID(), 'KRW', 1500000.0000);
SET @sora_wallet := LAST_INSERT_ID();

-- ----------------------------------------------------------------------------
-- 4. 호스트의 종료된 여정 + 약속 1건 + 참가자 4명 + trip_items(CONFIRMED)
--    비교 API는 여정↔약속을 trip_items(CONFIRMED).appointment_id로 잇는다.
-- ----------------------------------------------------------------------------
SET @end := DATE_SUB(CURDATE(), INTERVAL 3 DAY);
SET @start := DATE_SUB(@end, INTERVAL 4 DAY);

INSERT INTO trips (member_id, title, start_date, end_date)
VALUES (@host, 'Seed Report Journey', @start, @end);
SET @trip := LAST_INSERT_ID();

-- 승인된 이벤트가 있으면 그것을 약속 대상으로 쓰고, 없으면 빈 항목을 하나 만든다.
SET @item := (
    SELECT item_id FROM explore_items
    WHERE item_type = 'EVENT' AND approval_status = 'APPROVED'
      AND visibility_status = 'VISIBLE' AND deleted_at IS NULL
    ORDER BY item_id
    LIMIT 1
);
INSERT INTO explore_items
    (created_by, reviewed_by, item_type, approval_status, visibility_status, reviewed_at)
SELECT @host, @host, 'EVENT', 'APPROVED', 'VISIBLE', NOW() FROM DUAL WHERE @item IS NULL;
SET @item := COALESCE(@item, (
    SELECT item_id FROM explore_items
    WHERE created_by = @host AND reviewed_by = @host AND item_type = 'EVENT'
    ORDER BY item_id DESC
    LIMIT 1
));

INSERT INTO appointments
    (item_id, host_member_id, language_code, appointment_name, max_members, deposit_amount,
     appointment_status, activity_start_at, activity_end_at)
VALUES
    (@item, @host, 'en', 'Seed Report Appointment', 5, 10000,
     'COMPLETED', TIMESTAMP(@end, '10:00:00'), TIMESTAMP(@end, '12:00:00'));
SET @appt := LAST_INSERT_ID();

INSERT INTO appointment_members (appointment_id, member_id, trip_id, membership_status)
VALUES
    (@appt, @host, @trip, 'ACTIVE'),
    (@appt, @mina, NULL, 'ACTIVE'),
    (@appt, @jae, NULL, 'ACTIVE'),
    (@appt, @sora, NULL, 'ACTIVE');

INSERT INTO trip_items
    (trip_id, item_id, appointment_id, visit_date, trip_item_status, confirmed_at)
VALUES (@trip, @item, @appt, @end, 'CONFIRMED', NOW());

-- ----------------------------------------------------------------------------
-- 5. 여정 기간 안의 QR 결제 — 결제자 본인 지갑의 DEBIT 원장 한 줄씩
--    호스트 합계 1,284,500(시안 수치). 호스트 결제는 리포트에 연결하지 않는다.
-- ----------------------------------------------------------------------------
INSERT INTO wallet_transfers
    (currency_code, initiator_member_id, transfer_number, transfer_type, transfer_status,
     amount, idempotency_key, spending_category, memo, completed_at)
VALUES
    ('KRW', @host, 'TXN-SEED-RPT-H1', 'QR_PAYMENT', 'COMPLETED', 539500.0000,
     'seed:report-demo:host:food', 'FOOD', 'Seed Report — night market', TIMESTAMP(@start, '12:00:00')),
    ('KRW', @host, 'TXN-SEED-RPT-H2', 'QR_PAYMENT', 'COMPLETED', 398200.0000,
     'seed:report-demo:host:shopping', 'SHOPPING', 'Seed Report — souvenirs', TIMESTAMP(DATE_ADD(@start, INTERVAL 1 DAY), '12:00:00')),
    ('KRW', @host, 'TXN-SEED-RPT-H3', 'QR_PAYMENT', 'COMPLETED', 218400.0000,
     'seed:report-demo:host:show', 'SHOW', 'Seed Report — concert', TIMESTAMP(DATE_ADD(@start, INTERVAL 2 DAY), '12:00:00')),
    ('KRW', @host, 'TXN-SEED-RPT-H4', 'QR_PAYMENT', 'COMPLETED', 128400.0000,
     'seed:report-demo:host:beauty', 'BEAUTY', 'Seed Report — spa', TIMESTAMP(DATE_ADD(@start, INTERVAL 3 DAY), '12:00:00')),
    ('KRW', @mina, 'TXN-SEED-RPT-M1', 'QR_PAYMENT', 'COMPLETED', 420000.0000,
     'seed:report-demo:mina:food', 'FOOD', 'Seed Report — brunch', TIMESTAMP(@start, '13:00:00')),
    ('KRW', @mina, 'TXN-SEED-RPT-M2', 'QR_PAYMENT', 'COMPLETED', 310000.0000,
     'seed:report-demo:mina:shopping', 'SHOPPING', 'Seed Report — outlet', TIMESTAMP(DATE_ADD(@start, INTERVAL 2 DAY), '13:00:00')),
    ('KRW', @mina, 'TXN-SEED-RPT-M3', 'QR_PAYMENT', 'COMPLETED', 248400.0000,
     'seed:report-demo:mina:beauty', 'BEAUTY', 'Seed Report — salon', TIMESTAMP(@end, '11:00:00')),
    ('KRW', @jae, 'TXN-SEED-RPT-J1', 'QR_PAYMENT', 'COMPLETED', 300000.0000,
     'seed:report-demo:jae:show', 'SHOW', 'Seed Report — musical', TIMESTAMP(DATE_ADD(@start, INTERVAL 1 DAY), '19:00:00')),
    ('KRW', @jae, 'TXN-SEED-RPT-J2', 'QR_PAYMENT', 'COMPLETED', 150000.0000,
     'seed:report-demo:jae:food', 'FOOD', 'Seed Report — bbq', TIMESTAMP(DATE_ADD(@start, INTERVAL 3 DAY), '18:00:00')),
    ('KRW', @jae, 'TXN-SEED-RPT-J3', 'QR_PAYMENT', 'COMPLETED', 60000.0000,
     'seed:report-demo:jae:transport', 'TRANSPORT', 'Seed Report — taxi', TIMESTAMP(@end, '09:00:00')),
    ('KRW', @sora, 'TXN-SEED-RPT-S1', 'QR_PAYMENT', 'COMPLETED', 520000.0000,
     'seed:report-demo:sora:shopping', 'SHOPPING', 'Seed Report — department store', TIMESTAMP(DATE_ADD(@start, INTERVAL 1 DAY), '15:00:00')),
    ('KRW', @sora, 'TXN-SEED-RPT-S2', 'QR_PAYMENT', 'COMPLETED', 130000.0000,
     'seed:report-demo:sora:food', 'FOOD', 'Seed Report — cafe', TIMESTAMP(DATE_ADD(@start, INTERVAL 2 DAY), '10:00:00')),
    ('KRW', @sora, 'TXN-SEED-RPT-S3', 'QR_PAYMENT', 'COMPLETED', 90000.0000,
     'seed:report-demo:sora:beauty', 'BEAUTY', 'Seed Report — nails', TIMESTAMP(@end, '14:00:00'));

INSERT INTO wallet_ledger_entries (transfer_id, wallet_id, entry_type, amount, balance_after)
SELECT t.transfer_id,
       CASE t.initiator_member_id
           WHEN @host THEN @host_wallet
           WHEN @mina THEN @mina_wallet
           WHEN @jae THEN @jae_wallet
           ELSE @sora_wallet
       END,
       'DEBIT',
       t.amount,
       1000000.0000
FROM wallet_transfers t
WHERE t.idempotency_key LIKE 'seed:report-demo:%';

-- ----------------------------------------------------------------------------
-- 6. SIMILAR용 — 같은 국적으로 리포트를 이미 만든 회원 2명 (지갑·결제 없이 스냅샷만)
-- ----------------------------------------------------------------------------
INSERT INTO members (display_name, nationality_code) VALUES ('Seed Report Hana', @nat);
SET @hana := LAST_INSERT_ID();
INSERT INTO trips (member_id, title, start_date, end_date)
VALUES (@hana, 'Seed Report Hana Journey', @start, @end);
SET @hana_trip := LAST_INSERT_ID();
INSERT INTO reports (trip_id, generation_status, locale, report_content, generated_at)
VALUES (@hana_trip, 'COMPLETED', 'en', JSON_OBJECT(
    'journey', JSON_OBJECT('tripId', @hana_trip, 'title', 'Seed Report Hana Journey',
                           'startDate', CAST(@start AS CHAR), 'endDate', CAST(@end AS CHAR)),
    'days', JSON_ARRAY(),
    'analytics', JSON_OBJECT(
        'totalSpent', 860000.0000, 'dailyAverage', 172000.00,
        'categoryBreakdown', JSON_ARRAY(
            JSON_OBJECT('category', 'FOOD', 'amount', 430000.0000, 'percentage', 50.00),
            JSON_OBJECT('category', 'SHOPPING', 'amount', 258000.0000, 'percentage', 30.00),
            JSON_OBJECT('category', 'SHOW', 'amount', 172000.0000, 'percentage', 20.00)),
        'dailyTrend', JSON_ARRAY())
), NOW());

INSERT INTO members (display_name, nationality_code) VALUES ('Seed Report Yuki', @nat);
SET @yuki := LAST_INSERT_ID();
INSERT INTO trips (member_id, title, start_date, end_date)
VALUES (@yuki, 'Seed Report Yuki Journey', @start, @end);
SET @yuki_trip := LAST_INSERT_ID();
INSERT INTO reports (trip_id, generation_status, locale, report_content, generated_at)
VALUES (@yuki_trip, 'COMPLETED', 'en', JSON_OBJECT(
    'journey', JSON_OBJECT('tripId', @yuki_trip, 'title', 'Seed Report Yuki Journey',
                           'startDate', CAST(@start AS CHAR), 'endDate', CAST(@end AS CHAR)),
    'days', JSON_ARRAY(),
    'analytics', JSON_OBJECT(
        'totalSpent', 1120000.0000, 'dailyAverage', 224000.00,
        'categoryBreakdown', JSON_ARRAY(
            JSON_OBJECT('category', 'SHOPPING', 'amount', 560000.0000, 'percentage', 50.00),
            JSON_OBJECT('category', 'BEAUTY', 'amount', 336000.0000, 'percentage', 30.00),
            JSON_OBJECT('category', 'FOOD', 'amount', 224000.0000, 'percentage', 20.00)),
        'dailyTrend', JSON_ARRAY())
), NOW());

-- ----------------------------------------------------------------------------
-- 7. 확인 — 시연 전에 눈으로 본다
-- ----------------------------------------------------------------------------
SELECT 'host' AS what, @host AS id, @nat AS nationality, @trip AS trip_id, @appt AS appointment_id
UNION ALL
SELECT 'seed members', COUNT(*), NULL, NULL, NULL FROM members WHERE display_name LIKE 'Seed Report %'
UNION ALL
SELECT 'seed transfers', COUNT(*), NULL, NULL, NULL FROM wallet_transfers WHERE idempotency_key LIKE 'seed:report-demo:%'
UNION ALL
SELECT 'seed reports', COUNT(*), NULL, NULL, NULL FROM reports
  WHERE trip_id IN (SELECT trip_id FROM trips WHERE title LIKE 'Seed Report %');
