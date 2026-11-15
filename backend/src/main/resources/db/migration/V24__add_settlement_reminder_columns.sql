-- 오래 내지 않는 정산 구성원에게 서버가 다시 알려 주려고, 구성원마다 "마지막으로 언제 몇 번
-- 알렸는지"를 남긴다. V1에 있던 두 열을 V9가 정산 도메인을 줄이면서 지웠고(그때는 쓰는 곳이
-- 없었다), 리마인더가 생기면서 다시 쓰이므로 되살린다.
--
-- last_reminded_at이 비어 있으면 아직 한 번도 안 보냈다는 뜻이다. 이 시각은 "간격이 지났나"를
-- 판단하는 기준이라 DB 시계가 아니라 앱이 넘긴 값만 적는다. 같은 행의 updated_at은 DB 시계로
-- 바뀌지만 판단에는 쓰지 않는다.
--
-- 이미 쌓인 구성원은 횟수 0, 시각 NULL로 시작한다. 요청한 지 오래된 미납 건은 정산 생성 시각
-- (settlements.created_at)만 보고 첫 리마인더 대상이 된다.

ALTER TABLE settlement_members
    ADD COLUMN last_reminded_at DATETIME NULL
        COMMENT '마지막으로 리마인더를 적은 시각. NULL이면 아직 안 보냄' AFTER paid_at,
    ADD COLUMN reminder_count INT NOT NULL DEFAULT 0
        COMMENT '지금까지 적은 리마인더 횟수. 상한은 앱이 정한다' AFTER last_reminded_at;

-- 리마인더 대상은 "아직 안 낸(PENDING) 구성원"이고, 실행은 그 행을 번호 순서로 끊어서 읽는다.
-- 정산이 끝난 구성원(PAID)이 대부분이 되어도 읽는 범위가 미납 행으로 좁혀지게 한다.
--
-- InnoDB의 보조 인덱스는 기본키를 끝에 알아서 붙이지만, 이 읽기는 그 뒤쪽 순서(번호)에 기대어
-- "다음 100개"를 고른다. 의존한다는 것이 보이도록 두 번째 열을 일부러 적어 둔다.
CREATE INDEX idx_settlement_members_request_status
    ON settlement_members (request_status, settlement_member_id);

-- 알림 종류에 리마인더를 더한다. ENUM의 끝에 붙이는 변경은 저장된 값 번호가 밀리지 않아 표를
-- 다시 쓰지 않고 끝난다. 중간에 끼우면 기존 행이 전부 다시 쓰이므로 순서를 바꾸지 않는다.
ALTER TABLE notifications
    MODIFY COLUMN notification_type ENUM(
        'SETTLEMENT_REQUESTED',
        'SETTLEMENT_PAID',
        'SETTLEMENT_COMPLETED',
        'SETTLEMENT_REMINDER'
    ) NOT NULL;
