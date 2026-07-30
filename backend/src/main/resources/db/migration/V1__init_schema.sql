SET NAMES utf8mb4;

CREATE TABLE currencies (
    currency_code CHAR(3) NOT NULL COMMENT 'ISO 4217 코드',
    currency_name VARCHAR(100) NOT NULL,
    decimal_places TINYINT NOT NULL DEFAULT 2,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at DATETIME NULL,
    CONSTRAINT pk_currencies PRIMARY KEY (currency_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE members (
    member_id BIGINT NOT NULL AUTO_INCREMENT,
    preferred_currency_code CHAR(3) NULL COMMENT '온보딩에서 선택한 자국 통화',
    display_name VARCHAR(50) NOT NULL,
    profile_image_url VARCHAR(500) NULL,
    nationality_code VARCHAR(10) NULL,
    preferred_language VARCHAR(10) NOT NULL DEFAULT 'en',
    member_status ENUM('ACTIVE','SUSPENDED','WITHDRAWN') NOT NULL DEFAULT 'ACTIVE',
    onboarding_completed_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at DATETIME NULL,
    CONSTRAINT pk_members PRIMARY KEY (member_id),
    CONSTRAINT fk_members_preferred_currency
        FOREIGN KEY (preferred_currency_code) REFERENCES currencies (currency_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE social_accounts (
    social_account_id BIGINT NOT NULL AUTO_INCREMENT,
    member_id BIGINT NOT NULL,
    provider VARCHAR(20) NOT NULL,
    provider_user_id VARCHAR(255) NOT NULL,
    provider_email VARCHAR(320) NULL,
    connected_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at DATETIME NULL,
    CONSTRAINT pk_social_accounts PRIMARY KEY (social_account_id),
    CONSTRAINT uq_social_accounts_provider_user UNIQUE (provider, provider_user_id),
    CONSTRAINT fk_social_accounts_member
        FOREIGN KEY (member_id) REFERENCES members (member_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE auth_refresh_tokens (
    refresh_token_id BIGINT NOT NULL AUTO_INCREMENT,
    member_id BIGINT NOT NULL,
    parent_token_id BIGINT NULL,
    token_family_id BINARY(16) NOT NULL,
    token_hash CHAR(64) NOT NULL,
    issued_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at DATETIME NOT NULL,
    rotated_at DATETIME NULL,
    revoked_at DATETIME NULL,
    reuse_detected_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at DATETIME NULL,
    CONSTRAINT pk_auth_refresh_tokens PRIMARY KEY (refresh_token_id),
    CONSTRAINT uq_auth_refresh_tokens_hash UNIQUE (token_hash),
    INDEX idx_auth_refresh_tokens_family (token_family_id),
    INDEX idx_auth_refresh_tokens_member (member_id),
    CONSTRAINT fk_auth_refresh_tokens_member
        FOREIGN KEY (member_id) REFERENCES members (member_id),
    CONSTRAINT fk_auth_refresh_tokens_parent
        FOREIGN KEY (parent_token_id) REFERENCES auth_refresh_tokens (refresh_token_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE wallet_owners (
    wallet_owner_id BIGINT NOT NULL AUTO_INCREMENT,
    member_id BIGINT NULL,
    owner_type ENUM('MEMBER','SYSTEM') NOT NULL,
    system_code VARCHAR(50) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at DATETIME NULL,
    CONSTRAINT pk_wallet_owners PRIMARY KEY (wallet_owner_id),
    CONSTRAINT uq_wallet_owners_member UNIQUE (member_id),
    CONSTRAINT uq_wallet_owners_system_code UNIQUE (system_code),
    CONSTRAINT chk_wallet_owners_identity CHECK (
        (owner_type = 'MEMBER' AND member_id IS NOT NULL AND system_code IS NULL)
        OR
        (owner_type = 'SYSTEM' AND member_id IS NULL AND system_code IS NOT NULL)
    ),
    CONSTRAINT fk_wallet_owners_member
        FOREIGN KEY (member_id) REFERENCES members (member_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE wallets (
    wallet_id BIGINT NOT NULL AUTO_INCREMENT,
    wallet_owner_id BIGINT NOT NULL,
    currency_code CHAR(3) NOT NULL COMMENT '현재 선불잔액 기준 통화는 KRW',
    available_balance DECIMAL(19, 4) NOT NULL DEFAULT 0,
    wallet_status ENUM('ACTIVE','SUSPENDED','CLOSED') NOT NULL DEFAULT 'ACTIVE',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '낙관적 락 번호',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at DATETIME NULL,
    CONSTRAINT pk_wallets PRIMARY KEY (wallet_id),
    CONSTRAINT uq_wallets_owner UNIQUE (wallet_owner_id),
    CONSTRAINT chk_wallets_available_balance CHECK (available_balance >= 0),
    CONSTRAINT chk_wallets_version CHECK (version >= 0),
    CONSTRAINT fk_wallets_owner
        FOREIGN KEY (wallet_owner_id) REFERENCES wallet_owners (wallet_owner_id),
    CONSTRAINT fk_wallets_currency
        FOREIGN KEY (currency_code) REFERENCES currencies (currency_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE trips (
    trip_id BIGINT NOT NULL AUTO_INCREMENT,
    member_id BIGINT NOT NULL,
    title VARCHAR(100) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    budget_amount DECIMAL(19, 4) NULL,
    companion_preference VARCHAR(30) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at DATETIME NULL,
    CONSTRAINT pk_trips PRIMARY KEY (trip_id),
    CONSTRAINT chk_trips_dates CHECK (start_date <= end_date),
    CONSTRAINT chk_trips_budget CHECK (budget_amount IS NULL OR budget_amount >= 0),
    CONSTRAINT fk_trips_member
        FOREIGN KEY (member_id) REFERENCES members (member_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE trip_regions (
    trip_id BIGINT NOT NULL,
    region_code VARCHAR(30) NOT NULL,
    region_name VARCHAR(100) NOT NULL,
    display_order SMALLINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at DATETIME NULL,
    CONSTRAINT pk_trip_regions PRIMARY KEY (trip_id, region_code),
    CONSTRAINT fk_trip_regions_trip
        FOREIGN KEY (trip_id) REFERENCES trips (trip_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE events (
    event_id BIGINT NOT NULL AUTO_INCREMENT,
    creator_id BIGINT NOT NULL,
    category_code VARCHAR(40) NOT NULL,
    event_type ENUM('OFFICIAL','UNOFFICIAL') NOT NULL DEFAULT 'UNOFFICIAL',
    thumbnail_url VARCHAR(500) NULL,
    latitude DECIMAL(10, 7) NOT NULL,
    longitude DECIMAL(10, 7) NOT NULL,
    start_at DATETIME NOT NULL,
    end_at DATETIME NOT NULL,
    event_status ENUM('UPCOMING','ONGOING','ENDED','CANCELLED') NOT NULL DEFAULT 'UPCOMING',
    visibility_status ENUM('VISIBLE','HIDDEN') NOT NULL DEFAULT 'VISIBLE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at DATETIME NULL,
    CONSTRAINT pk_events PRIMARY KEY (event_id),
    CONSTRAINT chk_events_latitude CHECK (latitude BETWEEN -90 AND 90),
    CONSTRAINT chk_events_longitude CHECK (longitude BETWEEN -180 AND 180),
    CONSTRAINT chk_events_dates CHECK (start_at < end_at),
    CONSTRAINT fk_events_creator
        FOREIGN KEY (creator_id) REFERENCES members (member_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE event_contents (
    event_id BIGINT NOT NULL,
    language_code VARCHAR(10) NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,
    place_name VARCHAR(200) NOT NULL,
    address VARCHAR(500) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at DATETIME NULL,
    CONSTRAINT pk_event_contents PRIMARY KEY (event_id, language_code),
    CONSTRAINT fk_event_contents_event
        FOREIGN KEY (event_id) REFERENCES events (event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE event_likes (
    event_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at DATETIME NULL,
    CONSTRAINT pk_event_likes PRIMARY KEY (event_id, member_id),
    CONSTRAINT fk_event_likes_event
        FOREIGN KEY (event_id) REFERENCES events (event_id),
    CONSTRAINT fk_event_likes_member
        FOREIGN KEY (member_id) REFERENCES members (member_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE event_views (
    event_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    view_count INT NOT NULL DEFAULT 1,
    last_viewed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '최초 조회 시각',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at DATETIME NULL,
    CONSTRAINT pk_event_views PRIMARY KEY (event_id, member_id),
    CONSTRAINT chk_event_views_count CHECK (view_count > 0),
    CONSTRAINT fk_event_views_event
        FOREIGN KEY (event_id) REFERENCES events (event_id),
    CONSTRAINT fk_event_views_member
        FOREIGN KEY (member_id) REFERENCES members (member_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE appointments (
    appointment_id BIGINT NOT NULL AUTO_INCREMENT,
    event_id BIGINT NOT NULL,
    host_member_id BIGINT NOT NULL,
    language_code VARCHAR(10) NOT NULL,
    appointment_name VARCHAR(100) NOT NULL,
    appointment_description VARCHAR(500) NULL,
    max_members INT NOT NULL,
    join_deadline DATETIME NOT NULL,
    deposit_amount DECIMAL(19, 4) NOT NULL,
    appointment_status ENUM('RECRUITING','CLOSED','CONFIRMED','COMPLETED','CANCELLED')
        NOT NULL DEFAULT 'RECRUITING',
    meeting_place VARCHAR(200) NULL,
    meeting_latitude DECIMAL(10, 7) NULL,
    meeting_longitude DECIMAL(10, 7) NULL,
    meeting_at DATETIME NULL,
    meeting_confirmed_at DATETIME NULL,
    notice_content TEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at DATETIME NULL,
    CONSTRAINT pk_appointments PRIMARY KEY (appointment_id),
    CONSTRAINT chk_appointments_max_members CHECK (max_members > 0),
    CONSTRAINT chk_appointments_deposit CHECK (deposit_amount >= 0),
    CONSTRAINT fk_appointments_event
        FOREIGN KEY (event_id) REFERENCES events (event_id),
    CONSTRAINT fk_appointments_host
        FOREIGN KEY (host_member_id) REFERENCES members (member_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE appointment_members (
    appointment_member_id BIGINT NOT NULL AUTO_INCREMENT,
    appointment_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    trip_id BIGINT NULL,
    membership_status ENUM('ACTIVE','LEFT','REMOVED') NOT NULL DEFAULT 'ACTIVE',
    attendance_status ENUM('PENDING','ATTENDED','NO_SHOW') NOT NULL DEFAULT 'PENDING',
    joined_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    left_at DATETIME NULL,
    attendance_confirmed_at DATETIME NULL,
    removal_reason VARCHAR(500) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at DATETIME NULL,
    CONSTRAINT pk_appointment_members PRIMARY KEY (appointment_member_id),
    CONSTRAINT uq_appointment_members_appointment_member UNIQUE (appointment_id, member_id),
    CONSTRAINT uq_appointment_members_appointment_row UNIQUE (appointment_id, appointment_member_id),
    CONSTRAINT fk_appointment_members_appointment
        FOREIGN KEY (appointment_id) REFERENCES appointments (appointment_id),
    CONSTRAINT fk_appointment_members_member
        FOREIGN KEY (member_id) REFERENCES members (member_id),
    CONSTRAINT fk_appointment_members_trip
        FOREIGN KEY (trip_id) REFERENCES trips (trip_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE member_reviews (
    review_id BIGINT NOT NULL AUTO_INCREMENT,
    appointment_id BIGINT NOT NULL,
    reviewer_appointment_member_id BIGINT NOT NULL,
    reviewed_appointment_member_id BIGINT NOT NULL,
    visibility_status ENUM('VISIBLE','HIDDEN') NOT NULL DEFAULT 'VISIBLE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at DATETIME NULL,
    CONSTRAINT pk_member_reviews PRIMARY KEY (review_id),
    CONSTRAINT uq_member_reviews_pair UNIQUE (
        appointment_id,
        reviewer_appointment_member_id,
        reviewed_appointment_member_id
    ),
    CONSTRAINT chk_member_reviews_different_members CHECK (
        reviewer_appointment_member_id <> reviewed_appointment_member_id
    ),
    CONSTRAINT fk_member_reviews_appointment
        FOREIGN KEY (appointment_id) REFERENCES appointments (appointment_id),
    CONSTRAINT fk_member_reviews_reviewer_appointment
        FOREIGN KEY (appointment_id, reviewer_appointment_member_id)
        REFERENCES appointment_members (appointment_id, appointment_member_id),
    CONSTRAINT fk_member_reviews_reviewed_appointment
        FOREIGN KEY (appointment_id, reviewed_appointment_member_id)
        REFERENCES appointment_members (appointment_id, appointment_member_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE member_review_scores (
    review_id BIGINT NOT NULL,
    review_category ENUM('PUNCTUALITY','MANNERS','COMMUNICATION') NOT NULL,
    rating TINYINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at DATETIME NULL,
    CONSTRAINT pk_member_review_scores PRIMARY KEY (review_id, review_category),
    CONSTRAINT chk_member_review_scores_rating CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT fk_member_review_scores_review
        FOREIGN KEY (review_id) REFERENCES member_reviews (review_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE reports (
    report_id BIGINT NOT NULL AUTO_INCREMENT,
    trip_id BIGINT NOT NULL,
    generation_status ENUM('GENERATING','COMPLETED','FAILED') NOT NULL DEFAULT 'GENERATING',
    locale VARCHAR(10) NOT NULL DEFAULT 'en',
    report_content JSON NULL,
    included_sections JSON NULL,
    editable_sections JSON NULL,
    visibility_selections JSON NULL,
    photolog_visible BOOLEAN NOT NULL DEFAULT TRUE,
    last_error_code VARCHAR(100) NULL,
    generated_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at DATETIME NULL,
    CONSTRAINT pk_reports PRIMARY KEY (report_id),
    INDEX idx_reports_trip_created (trip_id, created_at),
    CONSTRAINT fk_reports_trip
        FOREIGN KEY (trip_id) REFERENCES trips (trip_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE wallet_transfers (
    transfer_id BIGINT NOT NULL AUTO_INCREMENT,
    currency_code CHAR(3) NOT NULL,
    initiator_member_id BIGINT NULL COMMENT '시스템 자동 이체는 NULL',
    original_transfer_id BIGINT NULL COMMENT '환불·역거래의 원거래',
    transfer_number VARCHAR(50) NOT NULL COMMENT '사용자 조회·CS·감사용 거래 번호',
    transfer_type ENUM(
        'TOPUP',
        'QR_PAYMENT',
        'SETTLEMENT',
        'DEPOSIT_HOLD',
        'DEPOSIT_REFUND',
        'DEPOSIT_FORFEIT_DISTRIBUTION',
        'REVERSAL'
    ) NOT NULL,
    transfer_status ENUM('PENDING','COMPLETED','FAILED','CANCELLED','REVERSED')
        NOT NULL DEFAULT 'PENDING',
    amount DECIMAL(19, 4) NOT NULL,
    idempotency_key VARCHAR(100) NULL,
    spending_category VARCHAR(20) NULL,
    spending_type VARCHAR(20) NULL,
    memo VARCHAR(255) NULL,
    completed_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at DATETIME NULL,
    CONSTRAINT pk_wallet_transfers PRIMARY KEY (transfer_id),
    CONSTRAINT uq_wallet_transfers_number UNIQUE (transfer_number),
    CONSTRAINT uq_wallet_transfers_idempotency UNIQUE (idempotency_key),
    CONSTRAINT chk_wallet_transfers_amount CHECK (amount > 0),
    CONSTRAINT chk_wallet_transfers_completed CHECK (
        transfer_status <> 'COMPLETED' OR completed_at IS NOT NULL
    ),
    CONSTRAINT fk_wallet_transfers_currency
        FOREIGN KEY (currency_code) REFERENCES currencies (currency_code),
    CONSTRAINT fk_wallet_transfers_initiator
        FOREIGN KEY (initiator_member_id) REFERENCES members (member_id),
    CONSTRAINT fk_wallet_transfers_original
        FOREIGN KEY (original_transfer_id) REFERENCES wallet_transfers (transfer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE wallet_ledger_entries (
    ledger_entry_id BIGINT NOT NULL AUTO_INCREMENT,
    transfer_id BIGINT NOT NULL,
    wallet_id BIGINT NOT NULL,
    entry_type ENUM('DEBIT','CREDIT') NOT NULL,
    amount DECIMAL(19, 4) NOT NULL,
    balance_after DECIMAL(19, 4) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at DATETIME NULL,
    CONSTRAINT pk_wallet_ledger_entries PRIMARY KEY (ledger_entry_id),
    CONSTRAINT uq_wallet_ledger_transfer_wallet_type
        UNIQUE (transfer_id, wallet_id, entry_type),
    INDEX idx_wallet_ledger_wallet_created (wallet_id, created_at),
    CONSTRAINT chk_wallet_ledger_amount CHECK (amount > 0),
    CONSTRAINT chk_wallet_ledger_balance_after CHECK (balance_after >= 0),
    CONSTRAINT fk_wallet_ledger_transfer
        FOREIGN KEY (transfer_id) REFERENCES wallet_transfers (transfer_id),
    CONSTRAINT fk_wallet_ledger_wallet
        FOREIGN KEY (wallet_id) REFERENCES wallets (wallet_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE wallet_topups (
    topup_id BIGINT NOT NULL AUTO_INCREMENT,
    wallet_id BIGINT NOT NULL,
    source_currency_code CHAR(3) NOT NULL,
    transfer_id BIGINT NULL COMMENT '완료된 원화 충전 이체',
    topup_status ENUM('QUOTED','COMPLETED','FAILED','CANCELLED')
        NOT NULL DEFAULT 'QUOTED',
    source_amount DECIMAL(19, 4) NOT NULL,
    exchange_rate_krw_per_unit DECIMAL(20, 10) NOT NULL,
    krw_amount DECIMAL(19, 4) NOT NULL,
    quoted_at DATETIME NOT NULL,
    completed_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at DATETIME NULL,
    CONSTRAINT pk_wallet_topups PRIMARY KEY (topup_id),
    CONSTRAINT uq_wallet_topups_transfer UNIQUE (transfer_id),
    CONSTRAINT chk_wallet_topups_source_amount CHECK (source_amount > 0),
    CONSTRAINT chk_wallet_topups_exchange_rate CHECK (exchange_rate_krw_per_unit > 0),
    CONSTRAINT chk_wallet_topups_krw_amount CHECK (krw_amount > 0),
    CONSTRAINT chk_wallet_topups_completed CHECK (
        topup_status <> 'COMPLETED'
        OR (transfer_id IS NOT NULL AND completed_at IS NOT NULL)
    ),
    CONSTRAINT fk_wallet_topups_wallet
        FOREIGN KEY (wallet_id) REFERENCES wallets (wallet_id),
    CONSTRAINT fk_wallet_topups_source_currency
        FOREIGN KEY (source_currency_code) REFERENCES currencies (currency_code),
    CONSTRAINT fk_wallet_topups_transfer
        FOREIGN KEY (transfer_id) REFERENCES wallet_transfers (transfer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE qr_payment_codes (
    qr_payment_code_id BIGINT NOT NULL AUTO_INCREMENT,
    payee_wallet_id BIGINT NOT NULL,
    completed_transfer_id BIGINT NULL,
    qr_token VARCHAR(255) NOT NULL,
    amount DECIMAL(19, 4) NULL COMMENT 'NULL이면 결제자가 금액 입력',
    memo VARCHAR(255) NULL,
    payment_status ENUM('ACTIVE','COMPLETED','EXPIRED','CANCELLED')
        NOT NULL DEFAULT 'ACTIVE',
    expires_at DATETIME NOT NULL,
    completed_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at DATETIME NULL,
    CONSTRAINT pk_qr_payment_codes PRIMARY KEY (qr_payment_code_id),
    CONSTRAINT uq_qr_payment_codes_token UNIQUE (qr_token),
    CONSTRAINT uq_qr_payment_codes_transfer UNIQUE (completed_transfer_id),
    CONSTRAINT chk_qr_payment_codes_amount CHECK (amount IS NULL OR amount > 0),
    CONSTRAINT chk_qr_payment_codes_completed CHECK (
        payment_status <> 'COMPLETED'
        OR (completed_transfer_id IS NOT NULL AND completed_at IS NOT NULL)
    ),
    CONSTRAINT fk_qr_payment_codes_payee_wallet
        FOREIGN KEY (payee_wallet_id) REFERENCES wallets (wallet_id),
    CONSTRAINT fk_qr_payment_codes_completed_transfer
        FOREIGN KEY (completed_transfer_id) REFERENCES wallet_transfers (transfer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE trip_expense_links (
    trip_id BIGINT NOT NULL,
    ledger_entry_id BIGINT NOT NULL,
    appointment_member_id BIGINT NULL COMMENT '이벤트 관련 비용일 때만 저장',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at DATETIME NULL,
    CONSTRAINT pk_trip_expense_links PRIMARY KEY (trip_id, ledger_entry_id),
    CONSTRAINT uq_trip_expense_links_ledger UNIQUE (ledger_entry_id),
    CONSTRAINT fk_trip_expense_links_trip
        FOREIGN KEY (trip_id) REFERENCES trips (trip_id),
    CONSTRAINT fk_trip_expense_links_ledger
        FOREIGN KEY (ledger_entry_id) REFERENCES wallet_ledger_entries (ledger_entry_id),
    CONSTRAINT fk_trip_expense_links_appointment_member
        FOREIGN KEY (appointment_member_id)
        REFERENCES appointment_members (appointment_member_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE deposits (
    deposit_id BIGINT NOT NULL AUTO_INCREMENT,
    appointment_member_id BIGINT NOT NULL,
    held_transfer_id BIGINT NULL COMMENT '회원 지갑에서 SYSTEM_ESCROW로 이동한 거래',
    amount DECIMAL(19, 4) NOT NULL,
    deposit_status ENUM('PENDING','HELD','REFUNDED','FORFEITED','DISTRIBUTED','CANCELLED')
        NOT NULL DEFAULT 'PENDING',
    held_at DATETIME NULL,
    resolved_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at DATETIME NULL,
    CONSTRAINT pk_deposits PRIMARY KEY (deposit_id),
    CONSTRAINT uq_deposits_appointment_member UNIQUE (appointment_member_id),
    CONSTRAINT uq_deposits_held_transfer UNIQUE (held_transfer_id),
    CONSTRAINT chk_deposits_amount CHECK (amount >= 0),
    CONSTRAINT chk_deposits_held CHECK (
        deposit_status <> 'HELD'
        OR (held_transfer_id IS NOT NULL AND held_at IS NOT NULL)
    ),
    CONSTRAINT chk_deposits_resolved CHECK (
        deposit_status NOT IN ('REFUNDED','FORFEITED','DISTRIBUTED','CANCELLED')
        OR resolved_at IS NOT NULL
    ),
    CONSTRAINT fk_deposits_appointment_member
        FOREIGN KEY (appointment_member_id)
        REFERENCES appointment_members (appointment_member_id),
    CONSTRAINT fk_deposits_held_transfer
        FOREIGN KEY (held_transfer_id) REFERENCES wallet_transfers (transfer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE deposit_payout_batches (
    deposit_payout_batch_id BIGINT NOT NULL AUTO_INCREMENT,
    appointment_id BIGINT NOT NULL,
    resolved_by_member_id BIGINT NULL COMMENT '시스템 자동 처리면 NULL',
    resolution_status ENUM('PENDING','PROCESSING','COMPLETED','FAILED')
        NOT NULL DEFAULT 'PENDING',
    total_held_amount DECIMAL(19, 4) NOT NULL,
    total_refund_amount DECIMAL(19, 4) NOT NULL,
    total_forfeited_amount DECIMAL(19, 4) NOT NULL,
    total_distributed_amount DECIMAL(19, 4) NOT NULL,
    attendance_snapshot_at DATETIME NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    resolved_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at DATETIME NULL,
    CONSTRAINT pk_deposit_payout_batches PRIMARY KEY (deposit_payout_batch_id),
    CONSTRAINT uq_deposit_payout_batches_appointment UNIQUE (appointment_id),
    CONSTRAINT uq_deposit_payout_batches_idempotency UNIQUE (idempotency_key),
    CONSTRAINT chk_deposit_payout_batches_held CHECK (total_held_amount >= 0),
    CONSTRAINT chk_deposit_payout_batches_refund CHECK (total_refund_amount >= 0),
    CONSTRAINT chk_deposit_payout_batches_forfeited CHECK (total_forfeited_amount >= 0),
    CONSTRAINT chk_deposit_payout_batches_distributed CHECK (total_distributed_amount >= 0),
    CONSTRAINT chk_deposit_payout_batches_totals CHECK (
        resolution_status <> 'COMPLETED'
        OR (
            total_refund_amount + total_distributed_amount = total_held_amount
            AND total_distributed_amount = total_forfeited_amount
        )
    ),
    CONSTRAINT chk_deposit_payout_batches_completed CHECK (
        resolution_status <> 'COMPLETED' OR resolved_at IS NOT NULL
    ),
    CONSTRAINT fk_deposit_payout_batches_appointment
        FOREIGN KEY (appointment_id) REFERENCES appointments (appointment_id),
    CONSTRAINT fk_deposit_payout_batches_resolver
        FOREIGN KEY (resolved_by_member_id) REFERENCES members (member_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE deposit_payouts (
    deposit_payout_id BIGINT NOT NULL AUTO_INCREMENT,
    source_deposit_id BIGINT NOT NULL,
    recipient_appointment_member_id BIGINT NOT NULL,
    transfer_id BIGINT NOT NULL COMMENT 'SYSTEM_ESCROW에서 출석 회원 지갑으로 이동한 거래',
    deposit_payout_batch_id BIGINT NOT NULL,
    allocation_type ENUM('SELF_REFUND','NO_SHOW_SHARE') NOT NULL,
    amount DECIMAL(19, 4) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at DATETIME NULL,
    CONSTRAINT pk_deposit_payouts PRIMARY KEY (deposit_payout_id),
    CONSTRAINT uq_deposit_payouts_allocation UNIQUE (
        source_deposit_id,
        recipient_appointment_member_id,
        allocation_type
    ),
    CONSTRAINT uq_deposit_payouts_transfer UNIQUE (transfer_id),
    CONSTRAINT chk_deposit_payouts_amount CHECK (amount > 0),
    CONSTRAINT fk_deposit_payouts_source
        FOREIGN KEY (source_deposit_id) REFERENCES deposits (deposit_id),
    CONSTRAINT fk_deposit_payouts_recipient
        FOREIGN KEY (recipient_appointment_member_id)
        REFERENCES appointment_members (appointment_member_id),
    CONSTRAINT fk_deposit_payouts_transfer
        FOREIGN KEY (transfer_id) REFERENCES wallet_transfers (transfer_id),
    CONSTRAINT fk_deposit_payouts_batch
        FOREIGN KEY (deposit_payout_batch_id)
        REFERENCES deposit_payout_batches (deposit_payout_batch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE settlements (
    settlement_id BIGINT NOT NULL AUTO_INCREMENT,
    appointment_id BIGINT NOT NULL,
    created_by_member_id BIGINT NOT NULL,
    payer_member_id BIGINT NOT NULL,
    canceled_by_member_id BIGINT NULL,
    source_transfer_id BIGINT NOT NULL,
    settlement_status ENUM('DRAFT','REQUESTED','COMPLETED','CANCELLED','EXPIRED')
        NOT NULL DEFAULT 'DRAFT',
    split_method ENUM('EQUAL','ITEMIZED','GAME') NOT NULL,
    total_amount DECIMAL(19, 4) NOT NULL,
    payer_share_amount DECIMAL(19, 4) NOT NULL DEFAULT 0,
    receivable_amount DECIMAL(19, 4) NOT NULL,
    requested_at DATETIME NULL,
    memo VARCHAR(500) NULL,
    completed_at DATETIME NULL,
    canceled_at DATETIME NULL,
    cancel_reason VARCHAR(500) NULL,
    version BIGINT NOT NULL DEFAULT 0 COMMENT '낙관적 락 번호',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at DATETIME NULL,
    CONSTRAINT pk_settlements PRIMARY KEY (settlement_id),
    CONSTRAINT uq_settlements_source_transfer UNIQUE (source_transfer_id),
    CONSTRAINT chk_settlements_creator_is_payer CHECK (
        payer_member_id = created_by_member_id
    ),
    CONSTRAINT chk_settlements_total CHECK (total_amount > 0),
    CONSTRAINT chk_settlements_payer_share CHECK (payer_share_amount >= 0),
    CONSTRAINT chk_settlements_receivable CHECK (
        receivable_amount >= 0
        AND total_amount = payer_share_amount + receivable_amount
    ),
    CONSTRAINT chk_settlements_cancelled CHECK (
        settlement_status <> 'CANCELLED'
        OR (
            canceled_by_member_id IS NOT NULL
            AND canceled_by_member_id = created_by_member_id
            AND canceled_at IS NOT NULL
        )
    ),
    CONSTRAINT chk_settlements_version CHECK (version >= 0),
    CONSTRAINT fk_settlements_appointment
        FOREIGN KEY (appointment_id) REFERENCES appointments (appointment_id),
    CONSTRAINT fk_settlements_creator
        FOREIGN KEY (created_by_member_id) REFERENCES members (member_id),
    CONSTRAINT fk_settlements_payer
        FOREIGN KEY (payer_member_id) REFERENCES members (member_id),
    CONSTRAINT fk_settlements_canceller
        FOREIGN KEY (canceled_by_member_id) REFERENCES members (member_id),
    CONSTRAINT fk_settlements_source_transfer
        FOREIGN KEY (source_transfer_id) REFERENCES wallet_transfers (transfer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE settlement_items (
    settlement_item_id BIGINT NOT NULL AUTO_INCREMENT,
    settlement_id BIGINT NOT NULL,
    item_name VARCHAR(200) NOT NULL,
    unit_price DECIMAL(19, 4) NOT NULL,
    quantity DECIMAL(12, 3) NOT NULL,
    line_total DECIMAL(19, 4) NOT NULL,
    source_order SMALLINT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at DATETIME NULL,
    CONSTRAINT pk_settlement_items PRIMARY KEY (settlement_item_id),
    CONSTRAINT uq_settlement_items_order UNIQUE (settlement_id, source_order),
    INDEX idx_settlement_items_settlement (settlement_id),
    CONSTRAINT chk_settlement_items_unit_price CHECK (unit_price >= 0),
    CONSTRAINT chk_settlement_items_quantity CHECK (quantity > 0),
    CONSTRAINT chk_settlement_items_line_total CHECK (line_total >= 0),
    CONSTRAINT fk_settlement_items_settlement
        FOREIGN KEY (settlement_id) REFERENCES settlements (settlement_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE settlement_members (
    settlement_member_id BIGINT NOT NULL AUTO_INCREMENT,
    settlement_id BIGINT NOT NULL,
    appointment_member_id BIGINT NOT NULL,
    paid_transfer_id BIGINT NULL,
    share_amount DECIMAL(19, 4) NOT NULL,
    participant_status ENUM('ACTIVE','EXCLUDED') NOT NULL DEFAULT 'ACTIVE',
    excluded_at DATETIME NULL,
    exclusion_reason VARCHAR(500) NULL,
    request_status ENUM('NOT_REQUESTED','PENDING','PAID','CANCELLED','EXPIRED')
        NOT NULL DEFAULT 'NOT_REQUESTED',
    due_at DATETIME NULL,
    sent_at DATETIME NULL,
    paid_at DATETIME NULL,
    last_reminded_at DATETIME NULL,
    reminder_count INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at DATETIME NULL,
    CONSTRAINT pk_settlement_members PRIMARY KEY (settlement_member_id),
    CONSTRAINT uq_settlement_members_member UNIQUE (settlement_id, appointment_member_id),
    CONSTRAINT uq_settlement_members_paid_transfer UNIQUE (paid_transfer_id),
    CONSTRAINT chk_settlement_members_share CHECK (share_amount >= 0),
    CONSTRAINT chk_settlement_members_reminders CHECK (reminder_count >= 0),
    CONSTRAINT chk_settlement_members_paid CHECK (
        request_status <> 'PAID'
        OR (paid_transfer_id IS NOT NULL AND paid_at IS NOT NULL)
    ),
    CONSTRAINT fk_settlement_members_settlement
        FOREIGN KEY (settlement_id) REFERENCES settlements (settlement_id),
    CONSTRAINT fk_settlement_members_appointment_member
        FOREIGN KEY (appointment_member_id)
        REFERENCES appointment_members (appointment_member_id),
    CONSTRAINT fk_settlement_members_paid_transfer
        FOREIGN KEY (paid_transfer_id) REFERENCES wallet_transfers (transfer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE settlement_item_shares (
    settlement_item_share_id BIGINT NOT NULL AUTO_INCREMENT,
    settlement_item_id BIGINT NOT NULL,
    settlement_member_id BIGINT NOT NULL,
    allocated_quantity DECIMAL(12, 3) NOT NULL,
    allocated_amount DECIMAL(19, 4) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at DATETIME NULL,
    CONSTRAINT pk_settlement_item_shares PRIMARY KEY (settlement_item_share_id),
    CONSTRAINT uq_settlement_item_shares_member
        UNIQUE (settlement_item_id, settlement_member_id),
    CONSTRAINT chk_settlement_item_shares_quantity CHECK (allocated_quantity > 0),
    CONSTRAINT chk_settlement_item_shares_amount CHECK (allocated_amount >= 0),
    CONSTRAINT fk_settlement_item_shares_item
        FOREIGN KEY (settlement_item_id)
        REFERENCES settlement_items (settlement_item_id),
    CONSTRAINT fk_settlement_item_shares_member
        FOREIGN KEY (settlement_member_id)
        REFERENCES settlement_members (settlement_member_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
