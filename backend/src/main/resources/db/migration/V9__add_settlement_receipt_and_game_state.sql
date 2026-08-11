ALTER TABLE settlements
    ADD COLUMN idempotency_key VARCHAR(100) NULL AFTER source_transfer_id,
    ADD COLUMN request_fingerprint CHAR(64) NULL AFTER idempotency_key;

UPDATE settlements
SET idempotency_key = CONCAT('legacy-', settlement_id),
    request_fingerprint = SHA2(CONCAT('legacy-settlement-', settlement_id), 256)
WHERE idempotency_key IS NULL;

ALTER TABLE settlements
    MODIFY COLUMN idempotency_key VARCHAR(100) NOT NULL,
    MODIFY COLUMN request_fingerprint CHAR(64) NOT NULL,
    ADD CONSTRAINT uq_settlements_creator_idempotency
        UNIQUE (created_by_member_id, idempotency_key);

CREATE TABLE receipt_analyses (
    receipt_analysis_id BIGINT NOT NULL AUTO_INCREMENT,
    source_transfer_id BIGINT NOT NULL,
    appointment_id BIGINT NOT NULL,
    created_by_member_id BIGINT NOT NULL,
    original_file_name VARCHAR(255) NOT NULL,
    analysis_status ENUM('DRAFT','ALLOCATED','USED','CANCELLED') NOT NULL DEFAULT 'DRAFT',
    recognized_total DECIMAL(19, 4) NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at DATETIME NULL,
    CONSTRAINT pk_receipt_analyses PRIMARY KEY (receipt_analysis_id),
    CONSTRAINT uq_receipt_analyses_source_creator UNIQUE (source_transfer_id, created_by_member_id),
    CONSTRAINT chk_receipt_analyses_total CHECK (recognized_total >= 0),
    CONSTRAINT fk_receipt_analyses_source FOREIGN KEY (source_transfer_id)
        REFERENCES wallet_transfers (transfer_id),
    CONSTRAINT fk_receipt_analyses_appointment FOREIGN KEY (appointment_id)
        REFERENCES appointments (appointment_id),
    CONSTRAINT fk_receipt_analyses_creator FOREIGN KEY (created_by_member_id)
        REFERENCES members (member_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE receipt_analysis_items (
    receipt_analysis_item_id BIGINT NOT NULL AUTO_INCREMENT,
    receipt_analysis_id BIGINT NOT NULL,
    item_name VARCHAR(200) NOT NULL,
    unit_price DECIMAL(19, 4) NOT NULL,
    quantity DECIMAL(12, 3) NOT NULL,
    line_total DECIMAL(19, 4) NOT NULL,
    source_order SMALLINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at DATETIME NULL,
    CONSTRAINT pk_receipt_analysis_items PRIMARY KEY (receipt_analysis_item_id),
    CONSTRAINT uq_receipt_analysis_items_order UNIQUE (receipt_analysis_id, source_order),
    CONSTRAINT chk_receipt_analysis_items_price CHECK (unit_price >= 0),
    CONSTRAINT chk_receipt_analysis_items_quantity CHECK (quantity > 0),
    CONSTRAINT chk_receipt_analysis_items_total CHECK (line_total >= 0),
    CONSTRAINT fk_receipt_analysis_items_analysis FOREIGN KEY (receipt_analysis_id)
        REFERENCES receipt_analyses (receipt_analysis_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE receipt_item_allocations (
    receipt_item_allocation_id BIGINT NOT NULL AUTO_INCREMENT,
    receipt_analysis_item_id BIGINT NOT NULL,
    appointment_member_id BIGINT NOT NULL,
    allocated_quantity DECIMAL(12, 3) NOT NULL,
    allocated_amount DECIMAL(19, 4) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at DATETIME NULL,
    CONSTRAINT pk_receipt_item_allocations PRIMARY KEY (receipt_item_allocation_id),
    CONSTRAINT uq_receipt_item_allocations_member UNIQUE (receipt_analysis_item_id, appointment_member_id),
    CONSTRAINT chk_receipt_item_allocations_quantity CHECK (allocated_quantity > 0),
    CONSTRAINT chk_receipt_item_allocations_amount CHECK (allocated_amount >= 0),
    CONSTRAINT fk_receipt_item_allocations_item FOREIGN KEY (receipt_analysis_item_id)
        REFERENCES receipt_analysis_items (receipt_analysis_item_id),
    CONSTRAINT fk_receipt_item_allocations_member FOREIGN KEY (appointment_member_id)
        REFERENCES appointment_members (appointment_member_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE settlement_games (
    settlement_id BIGINT NOT NULL,
    game_type VARCHAR(30) NOT NULL,
    liable_count INT NOT NULL,
    game_status ENUM('WAITING_CONSENT','READY','COMPLETED','CANCELLED') NOT NULL DEFAULT 'WAITING_CONSENT',
    random_seed VARCHAR(64) NULL,
    completed_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_settlement_games PRIMARY KEY (settlement_id),
    CONSTRAINT chk_settlement_games_liable_count CHECK (liable_count > 0),
    CONSTRAINT chk_settlement_games_completed CHECK (
        game_status <> 'COMPLETED' OR (random_seed IS NOT NULL AND completed_at IS NOT NULL)
    ),
    CONSTRAINT fk_settlement_games_settlement FOREIGN KEY (settlement_id)
        REFERENCES settlements (settlement_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE settlement_game_members (
    settlement_id BIGINT NOT NULL,
    appointment_member_id BIGINT NOT NULL,
    consent_status ENUM('PENDING','AGREED','DECLINED') NOT NULL DEFAULT 'PENDING',
    is_liable BOOLEAN NOT NULL DEFAULT FALSE,
    responded_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_settlement_game_members PRIMARY KEY (settlement_id, appointment_member_id),
    CONSTRAINT fk_settlement_game_members_settlement FOREIGN KEY (settlement_id)
        REFERENCES settlements (settlement_id),
    CONSTRAINT fk_settlement_game_members_member FOREIGN KEY (appointment_member_id)
        REFERENCES appointment_members (appointment_member_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
