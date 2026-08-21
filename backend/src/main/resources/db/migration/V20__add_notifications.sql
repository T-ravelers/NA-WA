-- 정산에 무슨 일이 있었는지를 받는 사람 앞으로 한 줄씩 쌓아 두는 표다.
-- 사용자가 정산 목록을 직접 열어 보기 전까지는 요청이 왔는지도 알 수 없어서 만든다.
--
-- 이름·약속명·금액을 여기에 그대로 복사해 둔다. 나중에 상대가 이름을 바꾸거나 정산이
-- 지워져도, 그때 받은 알림은 받았던 그 문장 그대로 남아야 하기 때문이다. 매번 원본을
-- 다시 조인해 오면 지난 알림의 내용이 소리 없이 바뀐다.
--
-- read_at이 비어 있으면 아직 안 읽은 알림이다. 읽음 여부를 참/거짓으로 두지 않고 시각으로
-- 두면 "언제 읽었는지"까지 남아서 나중에 따져 볼 수 있다.

CREATE TABLE notifications (
    notification_id BIGINT NOT NULL AUTO_INCREMENT,
    recipient_member_id BIGINT NOT NULL COMMENT '이 알림을 받는 사람',
    notification_type ENUM(
        'SETTLEMENT_REQUESTED',
        'SETTLEMENT_PAID',
        'SETTLEMENT_COMPLETED'
    ) NOT NULL,
    settlement_id BIGINT NOT NULL COMMENT '눌렀을 때 열어 줄 정산',
    actor_name VARCHAR(50) NOT NULL COMMENT '알림을 만든 사람의 그때 이름',
    gathering_name VARCHAR(100) NOT NULL COMMENT '그때의 약속 이름',
    amount DECIMAL(19, 4) NOT NULL COMMENT '요청·지급은 그 사람 몫, 완료는 정산 총액',
    currency_code CHAR(3) NOT NULL,
    read_at DATETIME NULL COMMENT 'NULL이면 아직 안 읽음',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at DATETIME NULL,
    CONSTRAINT pk_notifications PRIMARY KEY (notification_id),
    CONSTRAINT fk_notifications_recipient
        FOREIGN KEY (recipient_member_id) REFERENCES members (member_id),
    CONSTRAINT fk_notifications_settlement
        FOREIGN KEY (settlement_id) REFERENCES settlements (settlement_id),
    CONSTRAINT fk_notifications_currency
        FOREIGN KEY (currency_code) REFERENCES currencies (currency_code),
    CONSTRAINT chk_notifications_amount CHECK (amount >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 목록도 미읽음 개수도 "내 알림을 최신순으로"만 읽는다. 두 질문이 인덱스 하나를 같이 쓴다.
CREATE INDEX idx_notifications_recipient_created
    ON notifications (recipient_member_id, created_at DESC);
