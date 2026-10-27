-- 브라우저가 알려 준 "이 기기로 푸시를 보내라"는 주소(구독)를 회원에 붙여 저장한다.
--
-- 한 회원이 기기마다 구독을 하나씩 가지므로 회원 하나에 여러 행이 붙는다. 반대로 endpoint는
-- 기기(브라우저) 하나를 가리키는 값이라 전체에서 하나뿐이어야 한다. 같은 기기에서 다른 계정이
-- 로그인하면 새 행을 만들지 않고 기존 행의 주인을 옮긴다 — 그러지 않으면 이전 계정의 알림이
-- 지금 그 기기를 쓰는 사람에게 계속 도착한다.
--
-- 해지는 행을 지운다. 푸시 서비스가 404·410으로 "이 구독은 끝났다"고 알려 줄 때도 같다.
-- 지워도 잃는 이력이 없고, 알림 자체는 notifications에 남아 있다.
--
-- language_code는 구독을 등록한 화면의 언어다. 백엔드에는 알림 문구를 만드는 i18n이 없어서
-- 서버는 이 값을 그대로 실어 보내고 문구는 서비스 워커가 만든다.

CREATE TABLE push_subscriptions (
    push_subscription_id BIGINT NOT NULL AUTO_INCREMENT,
    member_id BIGINT NOT NULL COMMENT '이 기기로 알림을 받는 회원',
    endpoint VARCHAR(512) NOT NULL COMMENT '푸시 서비스가 정해 준 기기별 주소',
    p256dh VARCHAR(128) NOT NULL COMMENT '본문 암호화에 쓰는 기기 공개키',
    auth_secret VARCHAR(64) NOT NULL COMMENT '본문 암호화에 쓰는 기기 비밀값',
    language_code VARCHAR(10) NOT NULL COMMENT '구독 등록 때 화면의 언어',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_push_subscriptions PRIMARY KEY (push_subscription_id),
    CONSTRAINT uk_push_subscriptions_endpoint UNIQUE (endpoint),
    CONSTRAINT fk_push_subscriptions_member
        FOREIGN KEY (member_id) REFERENCES members (member_id),
    CONSTRAINT chk_push_subscriptions_language
        CHECK (language_code IN ('en', 'ja', 'zh-TW', 'vi'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 발송은 "이 회원의 기기 전부"를 찾는다.
CREATE INDEX idx_push_subscriptions_member ON push_subscriptions (member_id);
