-- 시나리오 4(공개 프로필 집계, #582) 확인. 읽기만 한다.
--
-- 1. 시드가 의도한 만큼 후기를 깔았는가
-- 2. 부하 동안 프로필 집계 쿼리가 몇 번, 얼마나 오래 실행됐는가
--
-- 2는 실제로 실행된 문장의 통계(performance_schema)를 본다. 통계는 서버 기동 이후
-- 누적이라, 측정 직전에 README의 초기화 명령을 먼저 실행한다.
-- 읽은 행 수는 여기서 보지 않고 explain-member-profile.sh(EXPLAIN ANALYZE)로 잰다.
-- 같은 계획인데도 SUM_ROWS_EXAMINED가 실행 도중 약 4만에서 약 1만으로 바뀌어,
-- 파생 테이블을 채우며 읽은 행을 늘 세지는 않았다(#582 1만 건 측정).

SELECT 'reviews' AS item, COUNT(*) AS value
FROM member_reviews
WHERE appointment_id BETWEEN 4000001 AND 4009999
UNION ALL
SELECT 'review_scores', COUNT(*)
FROM member_review_scores rs
JOIN member_reviews mr ON mr.review_id = rs.review_id
WHERE mr.appointment_id BETWEEN 4000001 AND 4009999
UNION ALL
SELECT 'review_viewers', COUNT(*)
FROM appointment_members
WHERE appointment_id BETWEEN 4000001 AND 4009999
  AND member_id BETWEEN 900001 AND 909999
UNION ALL
SELECT 'appointment_members_total', COUNT(*)
FROM appointment_members;

-- 풀 회원 한 명이 받은 후기 수. 1만이면 약 10, 10만이면 약 100이어야 한다.
SELECT MIN(received) AS received_min,
       ROUND(AVG(received), 1) AS received_avg,
       MAX(received) AS received_max
FROM (
    SELECT am.member_id, COUNT(mr.review_id) AS received
    FROM appointment_members am
    LEFT JOIN member_reviews mr
           ON mr.appointment_id = am.appointment_id
          AND mr.reviewed_appointment_member_id = am.appointment_member_id
    WHERE am.member_id BETWEEN 980001 AND 981000
    GROUP BY am.member_id
) received_per_member;

-- 프로필 집계 쿼리의 실행 횟수와 서버 안에서 걸린 시간. executions는 k6의 프로필 요청
-- 수와 같아야 한다.
-- 결과 열 completion_rate로 고른다. 이 쿼리는 1,024바이트(performance_schema_max_digest_length)를
-- 넘어 문장 요약이 잘리므로, 뒤쪽에 나오는 member_review_scores로는 찾지 못한다.
SELECT COUNT_STAR AS executions,
       ROUND(AVG_TIMER_WAIT / 1000000000, 2) AS avg_ms,
       ROUND(MAX_TIMER_WAIT / 1000000000, 2) AS max_ms,
       SUM_CREATED_TMP_TABLES AS tmp_tables,
       SUM_SORT_ROWS AS sort_rows
FROM performance_schema.events_statements_summary_by_digest
WHERE SCHEMA_NAME = DATABASE()
  AND DIGEST_TEXT LIKE 'SELECT%`completion_rate`%';
