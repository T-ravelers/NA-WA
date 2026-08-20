-- 정산에 붙일 영수증 사진의 위치를 기록한다. 사진 자체는 S3에 있고 여기에는 그 주소만 둔다.
-- settlement_id가 NULL인 행은 아직 정산에 붙지 않은 "초안"이다. 사용자가 사진을 먼저 올리고
-- 품목을 확정한 뒤 정산을 만들 때 이 행에 정산 번호가 채워진다.
-- settlement_id에 UNIQUE를 걸면 MySQL은 NULL을 여러 개 허용하므로, 초안끼리는 부딪히지
-- 않으면서 "정산 하나에 사진 한 장"이 스키마로 지켜진다.

CREATE TABLE settlement_receipts (
    settlement_receipt_id BIGINT NOT NULL AUTO_INCREMENT,
    uploaded_by_member_id BIGINT NOT NULL,
    settlement_id BIGINT NULL COMMENT 'NULL이면 아직 정산에 붙지 않은 초안',
    object_key VARCHAR(255) NOT NULL COMMENT 'S3 객체 키. receipts/ 로 시작해야 한다',
    content_type VARCHAR(100) NOT NULL,
    byte_size INT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at DATETIME NULL,
    CONSTRAINT pk_settlement_receipts PRIMARY KEY (settlement_receipt_id),
    CONSTRAINT uq_settlement_receipts_settlement UNIQUE (settlement_id),
    CONSTRAINT uq_settlement_receipts_object_key UNIQUE (object_key),
    CONSTRAINT chk_settlement_receipts_byte_size CHECK (byte_size > 0),
    CONSTRAINT fk_settlement_receipts_uploader
        FOREIGN KEY (uploaded_by_member_id) REFERENCES members (member_id),
    CONSTRAINT fk_settlement_receipts_settlement
        FOREIGN KEY (settlement_id) REFERENCES settlements (settlement_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_settlement_receipts_uploader
    ON settlement_receipts (uploaded_by_member_id);
