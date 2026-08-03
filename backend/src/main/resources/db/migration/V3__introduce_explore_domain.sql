-- Align the V1 + V2 schema with NA-WA ERD rev5.0.
-- MySQL 8.0. Sector/Activity master rows are intentionally not seeded here.

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 1. Apply the rev4.9 deposit lifecycle changes missing from V1 + V2.
-- ---------------------------------------------------------------------------

UPDATE deposits
SET deposit_status = 'DISTRIBUTED',
    resolved_at = COALESCE(resolved_at, updated_at, CURRENT_TIMESTAMP)
WHERE deposit_status = 'FORFEITED';

ALTER TABLE deposits
    DROP CHECK chk_deposits_held,
    DROP CHECK chk_deposits_resolved,
    MODIFY COLUMN deposit_status
        ENUM('PENDING','HELD','REFUNDED','DISTRIBUTED','CANCELLED')
        NOT NULL DEFAULT 'PENDING',
    ADD CONSTRAINT chk_deposits_lifecycle CHECK (
        (deposit_status = 'PENDING'
            AND held_transfer_id IS NULL AND held_at IS NULL AND resolved_at IS NULL)
        OR (deposit_status = 'CANCELLED'
            AND held_transfer_id IS NULL AND held_at IS NULL AND resolved_at IS NOT NULL)
        OR (deposit_status = 'HELD'
            AND held_transfer_id IS NOT NULL AND held_at IS NOT NULL AND resolved_at IS NULL)
        OR (deposit_status IN ('REFUNDED','DISTRIBUTED')
            AND held_transfer_id IS NOT NULL AND held_at IS NOT NULL AND resolved_at IS NOT NULL)
    );

ALTER TABLE deposit_payout_batches
    ADD COLUMN resolution_reason
        ENUM('APPOINTMENT_COMPLETED','APPOINTMENT_CANCELLED') NULL
        AFTER resolved_by_member_id;

UPDATE deposit_payout_batches batch
JOIN appointments appointment
    ON appointment.appointment_id = batch.appointment_id
SET batch.resolution_reason = CASE
    WHEN appointment.appointment_status = 'CANCELLED' THEN 'APPOINTMENT_CANCELLED'
    ELSE 'APPOINTMENT_COMPLETED'
END;

ALTER TABLE deposit_payout_batches
    MODIFY COLUMN resolution_reason
        ENUM('APPOINTMENT_COMPLETED','APPOINTMENT_CANCELLED') NOT NULL,
    ADD CONSTRAINT chk_deposit_payout_batches_reason CHECK (
        resolution_reason <> 'APPOINTMENT_CANCELLED'
        OR (total_forfeited_amount = 0 AND total_distributed_amount = 0)
    );

ALTER TABLE deposit_payouts
    MODIFY COLUMN allocation_type
        ENUM('SELF_REFUND','NO_SHOW_SHARE','CANCELLATION_REFUND') NOT NULL,
    ADD COLUMN source_attendance_status_snapshot
        ENUM('PENDING','ATTENDED','NO_SHOW') NULL
        AFTER allocation_type,
    ADD COLUMN recipient_attendance_status_snapshot
        ENUM('PENDING','ATTENDED','NO_SHOW') NULL
        AFTER source_attendance_status_snapshot;

UPDATE deposit_payouts payout
JOIN deposits deposit
    ON deposit.deposit_id = payout.source_deposit_id
JOIN appointment_members source_member
    ON source_member.appointment_member_id = deposit.appointment_member_id
JOIN appointment_members recipient_member
    ON recipient_member.appointment_member_id = payout.recipient_appointment_member_id
SET payout.source_attendance_status_snapshot = source_member.attendance_status,
    payout.recipient_attendance_status_snapshot = recipient_member.attendance_status;

ALTER TABLE deposit_payouts
    MODIFY COLUMN source_attendance_status_snapshot
        ENUM('PENDING','ATTENDED','NO_SHOW') NOT NULL,
    MODIFY COLUMN recipient_attendance_status_snapshot
        ENUM('PENDING','ATTENDED','NO_SHOW') NOT NULL,
    ADD CONSTRAINT chk_deposit_payouts_allocation CHECK (
        (allocation_type = 'SELF_REFUND'
            AND source_attendance_status_snapshot = 'ATTENDED'
            AND recipient_attendance_status_snapshot = 'ATTENDED')
        OR (allocation_type = 'NO_SHOW_SHARE'
            AND source_attendance_status_snapshot = 'NO_SHOW'
            AND recipient_attendance_status_snapshot = 'ATTENDED')
        OR allocation_type = 'CANCELLATION_REFUND'
    );

-- ---------------------------------------------------------------------------
-- 2. Create the Explore classification and shared parent tables.
-- Sector/Activity seed data is managed separately from the schema migration.
-- ---------------------------------------------------------------------------

CREATE TABLE sector (
    sector_id BIGINT NOT NULL AUTO_INCREMENT,
    sector_code VARCHAR(40) NOT NULL,
    label_ko VARCHAR(100) NOT NULL,
    label_en VARCHAR(100) NOT NULL,
    display_order SMALLINT NOT NULL DEFAULT 0,
    filter_on BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at DATETIME NULL,
    CONSTRAINT pk_sector PRIMARY KEY (sector_id),
    CONSTRAINT uq_sector_code UNIQUE (sector_code),
    CONSTRAINT chk_sector_display_order CHECK (display_order >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE activity (
    activity_id BIGINT NOT NULL AUTO_INCREMENT,
    sector_id BIGINT NOT NULL,
    activity_code VARCHAR(60) NOT NULL,
    label_ko VARCHAR(100) NOT NULL,
    label_en VARCHAR(100) NOT NULL,
    display_order SMALLINT NOT NULL DEFAULT 0,
    filter_on BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at DATETIME NULL,
    CONSTRAINT pk_activity PRIMARY KEY (activity_id),
    CONSTRAINT uq_activity_code UNIQUE (activity_code),
    INDEX idx_activity_sector_order (sector_id, display_order),
    CONSTRAINT chk_activity_display_order CHECK (display_order >= 0),
    CONSTRAINT fk_activity_sector
        FOREIGN KEY (sector_id) REFERENCES sector (sector_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE explore_items (
    item_id BIGINT NOT NULL AUTO_INCREMENT,
    created_by BIGINT NULL COMMENT '파이프라인 등록은 NULL 가능',
    reviewed_by BIGINT NULL COMMENT '자동 승인은 시스템 회원, 수동 승인은 관리자 회원',
    item_type ENUM('EVENT','PLACE') NOT NULL,
    approval_status ENUM('REVIEW_REQUIRED','APPROVED','REJECTED')
        NOT NULL DEFAULT 'REVIEW_REQUIRED',
    visibility_status ENUM('VISIBLE','HIDDEN') NOT NULL DEFAULT 'HIDDEN',
    appointment_count BIGINT NOT NULL DEFAULT 0,
    participant_count BIGINT NOT NULL DEFAULT 0 COMMENT '누적 참여자 수',
    popularity_score DECIMAL(12, 4) NOT NULL DEFAULT 0,
    reviewed_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at DATETIME NULL,
    CONSTRAINT pk_explore_items PRIMARY KEY (item_id),
    INDEX idx_explore_items_list
        (item_type, approval_status, visibility_status, deleted_at),
    CONSTRAINT chk_explore_items_counts CHECK (
        appointment_count >= 0
        AND participant_count >= 0
        AND popularity_score >= 0
    ),
    CONSTRAINT chk_explore_items_review CHECK (
        (approval_status = 'REVIEW_REQUIRED'
            AND visibility_status = 'HIDDEN'
            AND reviewed_by IS NULL
            AND reviewed_at IS NULL)
        OR (approval_status = 'REJECTED'
            AND visibility_status = 'HIDDEN'
            AND reviewed_by IS NOT NULL
            AND reviewed_at IS NOT NULL)
        OR (approval_status = 'APPROVED'
            AND reviewed_by IS NOT NULL
            AND reviewed_at IS NOT NULL)
    ),
    CONSTRAINT fk_explore_items_creator
        FOREIGN KEY (created_by) REFERENCES members (member_id),
    CONSTRAINT fk_explore_items_reviewer
        FOREIGN KEY (reviewed_by) REFERENCES members (member_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------------
-- 3. Create Event/Place subtype, classification, translation and interaction.
-- ---------------------------------------------------------------------------

CREATE TABLE event (
    event_id BIGINT NOT NULL,
    pipeline_id CHAR(36) NULL,
    event_type ENUM('OFFICIAL','UNOFFICIAL') NOT NULL DEFAULT 'UNOFFICIAL',
    title VARCHAR(200) NOT NULL,
    subtitle VARCHAR(300) NULL,
    description TEXT NULL,
    venue_name VARCHAR(200) NULL,
    thumbnail_url VARCHAR(500) NULL,
    source_url VARCHAR(500) NULL,
    source_hashtags JSON NULL,
    program_text TEXT NULL,
    start_date DATE NOT NULL,
    end_date DATE NULL,
    status ENUM('SCHEDULED','ONGOING','ENDED') NOT NULL DEFAULT 'SCHEDULED',
    is_permanent BOOLEAN NOT NULL DEFAULT FALSE,
    operating_hours JSON NULL,
    open_days JSON NULL,
    open_weekend BOOLEAN NULL,
    opens_late BOOLEAN NULL,
    region1 VARCHAR(50) NULL,
    region2 VARCHAR(50) NULL,
    region3 VARCHAR(50) NULL,
    address_road VARCHAR(500) NULL,
    latitude DECIMAL(10, 7) NULL,
    longitude DECIMAL(10, 7) NULL,
    has_photo_zone BOOLEAN NULL,
    is_experience BOOLEAN NULL,
    age_limit VARCHAR(100) NULL,
    is_free BOOLEAN NULL,
    price_text VARCHAR(300) NULL,
    has_benefit BOOLEAN NULL,
    reservable BOOLEAN NULL,
    view_count BIGINT NOT NULL DEFAULT 0,
    favorite_count BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at DATETIME NULL,
    CONSTRAINT pk_event PRIMARY KEY (event_id),
    CONSTRAINT uq_event_pipeline UNIQUE (pipeline_id),
    INDEX idx_event_status_dates (status, start_date, end_date),
    INDEX idx_event_region (region1, region2, region3),
    CONSTRAINT chk_event_period CHECK (
        (is_permanent = TRUE AND end_date IS NULL)
        OR (is_permanent = FALSE AND end_date IS NOT NULL AND start_date <= end_date)
    ),
    CONSTRAINT chk_event_coordinates CHECK (
        (latitude IS NULL AND longitude IS NULL)
        OR (latitude BETWEEN -90 AND 90 AND longitude BETWEEN -180 AND 180)
    ),
    CONSTRAINT chk_event_source_hashtags CHECK (
        source_hashtags IS NULL OR JSON_TYPE(source_hashtags) = 'ARRAY'
    ),
    CONSTRAINT chk_event_counts CHECK (view_count >= 0 AND favorite_count >= 0),
    CONSTRAINT fk_event_explore_item
        FOREIGN KEY (event_id) REFERENCES explore_items (item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE place (
    place_id BIGINT NOT NULL,
    pipeline_id CHAR(36) NULL,
    name VARCHAR(200) NOT NULL,
    brand VARCHAR(100) NULL,
    branch VARCHAR(100) NULL,
    place_kind VARCHAR(100) NULL,
    thumbnail_url VARCHAR(500) NULL,
    source_hashtags JSON NULL,
    region1 VARCHAR(50) NULL,
    region2 VARCHAR(50) NULL,
    region3 VARCHAR(50) NULL,
    address_road VARCHAR(500) NULL,
    postal_code VARCHAR(20) NULL,
    latitude DECIMAL(10, 7) NULL,
    longitude DECIMAL(10, 7) NULL,
    opening_hours JSON NULL,
    closed_days JSON NULL,
    tel VARCHAR(30) NULL,
    has_foreign_lang BOOLEAN NULL,
    has_parking BOOLEAN NULL,
    reservable BOOLEAN NULL,
    takeout_available BOOLEAN NULL,
    card_payment_available BOOLEAN NULL,
    smoke_free BOOLEAN NULL,
    kid_facility BOOLEAN NULL,
    has_restroom BOOLEAN NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    view_count BIGINT NOT NULL DEFAULT 0,
    favorite_count BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at DATETIME NULL,
    CONSTRAINT pk_place PRIMARY KEY (place_id),
    CONSTRAINT uq_place_pipeline UNIQUE (pipeline_id),
    INDEX idx_place_active_region (is_active, region1, region2, region3),
    CONSTRAINT chk_place_coordinates CHECK (
        (latitude IS NULL AND longitude IS NULL)
        OR (latitude BETWEEN -90 AND 90 AND longitude BETWEEN -180 AND 180)
    ),
    CONSTRAINT chk_place_source_hashtags CHECK (
        source_hashtags IS NULL OR JSON_TYPE(source_hashtags) = 'ARRAY'
    ),
    CONSTRAINT chk_place_counts CHECK (view_count >= 0 AND favorite_count >= 0),
    CONSTRAINT fk_place_explore_item
        FOREIGN KEY (place_id) REFERENCES explore_items (item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE event_activity (
    event_id BIGINT NOT NULL,
    activity_id BIGINT NOT NULL,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at DATETIME NULL,
    CONSTRAINT pk_event_activity PRIMARY KEY (event_id, activity_id),
    INDEX idx_event_activity_activity (activity_id, event_id),
    CONSTRAINT fk_event_activity_event
        FOREIGN KEY (event_id) REFERENCES event (event_id),
    CONSTRAINT fk_event_activity_activity
        FOREIGN KEY (activity_id) REFERENCES activity (activity_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE place_activity (
    place_id BIGINT NOT NULL,
    activity_id BIGINT NOT NULL,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at DATETIME NULL,
    CONSTRAINT pk_place_activity PRIMARY KEY (place_id, activity_id),
    INDEX idx_place_activity_activity (activity_id, place_id),
    CONSTRAINT fk_place_activity_place
        FOREIGN KEY (place_id) REFERENCES place (place_id),
    CONSTRAINT fk_place_activity_activity
        FOREIGN KEY (activity_id) REFERENCES activity (activity_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE event_translations (
    event_id BIGINT NOT NULL,
    language_code VARCHAR(10) NOT NULL,
    title VARCHAR(200) NOT NULL,
    subtitle VARCHAR(300) NULL,
    description TEXT NULL,
    venue_name VARCHAR(200) NULL,
    program_text TEXT NULL,
    age_limit VARCHAR(100) NULL,
    price_text VARCHAR(300) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at DATETIME NULL,
    CONSTRAINT pk_event_translations PRIMARY KEY (event_id, language_code),
    CONSTRAINT chk_event_translations_additional_language CHECK (language_code <> 'ko'),
    CONSTRAINT fk_event_translations_event
        FOREIGN KEY (event_id) REFERENCES event (event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE place_translations (
    place_id BIGINT NOT NULL,
    language_code VARCHAR(10) NOT NULL,
    name VARCHAR(200) NOT NULL,
    brand VARCHAR(100) NULL,
    branch VARCHAR(100) NULL,
    place_kind VARCHAR(100) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at DATETIME NULL,
    CONSTRAINT pk_place_translations PRIMARY KEY (place_id, language_code),
    CONSTRAINT chk_place_translations_additional_language CHECK (language_code <> 'ko'),
    CONSTRAINT fk_place_translations_place
        FOREIGN KEY (place_id) REFERENCES place (place_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE explore_item_likes (
    item_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at DATETIME NULL,
    CONSTRAINT pk_explore_item_likes PRIMARY KEY (item_id, member_id),
    INDEX idx_explore_item_likes_member_created (member_id, created_at),
    CONSTRAINT fk_explore_item_likes_item
        FOREIGN KEY (item_id) REFERENCES explore_items (item_id),
    CONSTRAINT fk_explore_item_likes_member
        FOREIGN KEY (member_id) REFERENCES members (member_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE explore_item_views (
    item_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    view_count INT NOT NULL DEFAULT 1,
    last_viewed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at DATETIME NULL,
    CONSTRAINT pk_explore_item_views PRIMARY KEY (item_id, member_id),
    INDEX idx_explore_item_views_member_recent (member_id, last_viewed_at),
    CONSTRAINT chk_explore_item_views_count CHECK (view_count > 0),
    CONSTRAINT fk_explore_item_views_item
        FOREIGN KEY (item_id) REFERENCES explore_items (item_id),
    CONSTRAINT fk_explore_item_views_member
        FOREIGN KEY (member_id) REFERENCES members (member_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------------
-- 4. Migrate the legacy Event rows while preserving their IDs.
-- Korean content becomes the base row; other languages become translations.
-- ---------------------------------------------------------------------------

INSERT INTO explore_items (
    item_id,
    created_by,
    reviewed_by,
    item_type,
    approval_status,
    visibility_status,
    appointment_count,
    participant_count,
    popularity_score,
    reviewed_at,
    created_at,
    updated_at,
    deleted_at
)
SELECT
    legacy_event.event_id,
    legacy_event.creator_id,
    legacy_event.creator_id,
    'EVENT',
    'APPROVED',
    CASE
        WHEN legacy_event.event_status = 'CANCELLED' THEN 'HIDDEN'
        ELSE legacy_event.visibility_status
    END,
    (
        SELECT COUNT(*)
        FROM appointments appointment
        WHERE appointment.event_id = legacy_event.event_id
          AND appointment.deleted_at IS NULL
    ),
    (
        SELECT COUNT(*)
        FROM appointments appointment
        JOIN appointment_members appointment_member
            ON appointment_member.appointment_id = appointment.appointment_id
        WHERE appointment.event_id = legacy_event.event_id
    ),
    0,
    legacy_event.created_at,
    legacy_event.created_at,
    legacy_event.updated_at,
    legacy_event.deleted_at
FROM events legacy_event;

INSERT INTO event (
    event_id,
    pipeline_id,
    event_type,
    title,
    subtitle,
    description,
    venue_name,
    thumbnail_url,
    source_url,
    source_hashtags,
    program_text,
    start_date,
    end_date,
    status,
    is_permanent,
    operating_hours,
    open_days,
    open_weekend,
    opens_late,
    region1,
    region2,
    region3,
    address_road,
    latitude,
    longitude,
    has_photo_zone,
    is_experience,
    age_limit,
    is_free,
    price_text,
    has_benefit,
    reservable,
    view_count,
    favorite_count,
    created_at,
    updated_at,
    deleted_at
)
SELECT
    legacy_event.event_id,
    NULL,
    legacy_event.event_type,
    COALESCE(content.title_ko, content.title_fallback,
        CONCAT('Legacy Event #', legacy_event.event_id)),
    NULL,
    COALESCE(content.description_ko, content.description_fallback),
    COALESCE(content.venue_name_ko, content.venue_name_fallback),
    legacy_event.thumbnail_url,
    NULL,
    JSON_ARRAY(legacy_event.category_code),
    NULL,
    DATE(legacy_event.start_at),
    DATE(legacy_event.end_at),
    CASE
        WHEN legacy_event.event_status = 'UPCOMING' THEN 'SCHEDULED'
        WHEN legacy_event.event_status = 'ONGOING' THEN 'ONGOING'
        WHEN legacy_event.event_status = 'ENDED' THEN 'ENDED'
        WHEN DATE(legacy_event.end_at) < CURRENT_DATE THEN 'ENDED'
        WHEN DATE(legacy_event.start_at) > CURRENT_DATE THEN 'SCHEDULED'
        ELSE 'ONGOING'
    END,
    FALSE,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    COALESCE(content.address_ko, content.address_fallback),
    legacy_event.latitude,
    legacy_event.longitude,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    COALESCE((
        SELECT SUM(legacy_view.view_count)
        FROM event_views legacy_view
        WHERE legacy_view.event_id = legacy_event.event_id
          AND legacy_view.deleted_at IS NULL
    ), 0),
    (
        SELECT COUNT(*)
        FROM event_likes legacy_like
        WHERE legacy_like.event_id = legacy_event.event_id
          AND legacy_like.deleted_at IS NULL
    ),
    legacy_event.created_at,
    legacy_event.updated_at,
    legacy_event.deleted_at
FROM events legacy_event
LEFT JOIN (
    SELECT
        event_id,
        MAX(CASE WHEN language_code = 'ko' THEN title END) AS title_ko,
        MIN(title) AS title_fallback,
        MAX(CASE WHEN language_code = 'ko' THEN description END) AS description_ko,
        MIN(description) AS description_fallback,
        MAX(CASE WHEN language_code = 'ko' THEN place_name END) AS venue_name_ko,
        MIN(place_name) AS venue_name_fallback,
        MAX(CASE WHEN language_code = 'ko' THEN address END) AS address_ko,
        MIN(address) AS address_fallback
    FROM event_contents
    WHERE deleted_at IS NULL
    GROUP BY event_id
) content ON content.event_id = legacy_event.event_id;

INSERT INTO event_translations (
    event_id,
    language_code,
    title,
    subtitle,
    description,
    venue_name,
    program_text,
    age_limit,
    price_text,
    created_at,
    updated_at,
    deleted_at
)
SELECT
    legacy_content.event_id,
    legacy_content.language_code,
    legacy_content.title,
    NULL,
    legacy_content.description,
    legacy_content.place_name,
    NULL,
    NULL,
    NULL,
    legacy_content.created_at,
    legacy_content.updated_at,
    legacy_content.deleted_at
FROM event_contents legacy_content
WHERE legacy_content.language_code <> 'ko';

INSERT INTO explore_item_likes (
    item_id, member_id, created_at, updated_at, deleted_at
)
SELECT event_id, member_id, created_at, updated_at, deleted_at
FROM event_likes;

INSERT INTO explore_item_views (
    item_id,
    member_id,
    view_count,
    last_viewed_at,
    created_at,
    updated_at,
    deleted_at
)
SELECT
    event_id,
    member_id,
    view_count,
    last_viewed_at,
    created_at,
    updated_at,
    deleted_at
FROM event_views;

-- ---------------------------------------------------------------------------
-- 5. Rewire Appointments to Explore and explicit host-defined activity time.
-- Legacy rows use the old Event time only as a one-time migration fallback.
-- ---------------------------------------------------------------------------

ALTER TABLE appointments
    DROP FOREIGN KEY fk_appointments_event,
    ADD COLUMN item_id BIGINT NULL AFTER appointment_id,
    ADD COLUMN meeting_address VARCHAR(500) NULL AFTER meeting_place,
    ADD COLUMN activity_start_at DATETIME NULL AFTER meeting_longitude,
    ADD COLUMN activity_end_at DATETIME NULL AFTER activity_start_at;

UPDATE appointments appointment
JOIN events legacy_event
    ON legacy_event.event_id = appointment.event_id
LEFT JOIN (
    SELECT
        event_id,
        COALESCE(
            MAX(CASE WHEN language_code = 'ko' THEN address END),
            MIN(address)
        ) AS address_road
    FROM event_contents
    WHERE deleted_at IS NULL
    GROUP BY event_id
) content ON content.event_id = legacy_event.event_id
SET appointment.item_id = legacy_event.event_id,
    appointment.meeting_address = content.address_road,
    appointment.activity_start_at = legacy_event.start_at,
    appointment.activity_end_at = legacy_event.end_at,
    appointment.join_deadline = LEAST(appointment.join_deadline, legacy_event.start_at),
    appointment.meeting_latitude = CASE
        WHEN appointment.meeting_latitude IS NULL OR appointment.meeting_longitude IS NULL
            THEN NULL
        ELSE appointment.meeting_latitude
    END,
    appointment.meeting_longitude = CASE
        WHEN appointment.meeting_latitude IS NULL OR appointment.meeting_longitude IS NULL
            THEN NULL
        ELSE appointment.meeting_longitude
    END;

ALTER TABLE appointments
    DROP COLUMN event_id,
    DROP COLUMN meeting_at,
    DROP COLUMN meeting_confirmed_at,
    DROP COLUMN notice_content,
    MODIFY COLUMN item_id BIGINT NOT NULL,
    MODIFY COLUMN activity_start_at DATETIME NOT NULL,
    MODIFY COLUMN activity_end_at DATETIME NOT NULL,
    MODIFY COLUMN appointment_status
        ENUM('RECRUITING','CLOSED','CONFIRMED','IN_PROGRESS','COMPLETED','CANCELLED')
        NOT NULL DEFAULT 'RECRUITING',
    ADD INDEX idx_appointments_item_status (item_id, appointment_status),
    ADD CONSTRAINT chk_appointments_schedule CHECK (
        join_deadline <= activity_start_at
        AND activity_start_at < activity_end_at
    ),
    ADD CONSTRAINT chk_appointments_meeting_coordinates CHECK (
        (meeting_latitude IS NULL AND meeting_longitude IS NULL)
        OR (
            meeting_latitude BETWEEN -90 AND 90
            AND meeting_longitude BETWEEN -180 AND 180
        )
    ),
    ADD CONSTRAINT fk_appointments_item
        FOREIGN KEY (item_id) REFERENCES explore_items (item_id);

-- ---------------------------------------------------------------------------
-- 6. Remove the unsupported host-removal lifecycle from Appointment Members.
-- Legacy/test REMOVED rows are normalized to LEFT before the ENUM is reduced.
-- ---------------------------------------------------------------------------

UPDATE appointment_members
SET left_at = CASE
        WHEN membership_status IN ('LEFT','REMOVED')
            THEN COALESCE(left_at, updated_at, created_at)
        ELSE NULL
    END,
    attendance_confirmed_at = CASE
        WHEN attendance_status IN ('ATTENDED','NO_SHOW')
            THEN COALESCE(attendance_confirmed_at, updated_at, created_at)
        ELSE NULL
    END,
    membership_status = CASE
        WHEN membership_status = 'REMOVED' THEN 'LEFT'
        ELSE membership_status
    END;

ALTER TABLE appointment_members
    MODIFY COLUMN membership_status
        ENUM('ACTIVE','LEFT') NOT NULL DEFAULT 'ACTIVE',
    DROP COLUMN removal_reason,
    ADD CONSTRAINT chk_appointment_members_membership CHECK (
        (membership_status = 'ACTIVE' AND left_at IS NULL)
        OR (membership_status = 'LEFT' AND left_at IS NOT NULL)
    ),
    ADD CONSTRAINT chk_appointment_members_attendance CHECK (
        (attendance_status = 'PENDING' AND attendance_confirmed_at IS NULL)
        OR (attendance_status IN ('ATTENDED','NO_SHOW')
            AND attendance_confirmed_at IS NOT NULL)
    );

-- ---------------------------------------------------------------------------
-- 7. Remove the migrated legacy Event tables.
-- ---------------------------------------------------------------------------

DROP TABLE event_contents;
DROP TABLE event_likes;
DROP TABLE event_views;
DROP TABLE events;

-- ---------------------------------------------------------------------------
-- 8. Add member-review keyword master and selections. Rows are seeded later.
-- ---------------------------------------------------------------------------

CREATE TABLE member_review_keywords (
    keyword_id BIGINT NOT NULL AUTO_INCREMENT,
    keyword_code VARCHAR(50) NOT NULL COMMENT 'Vue i18n 번역 키',
    display_order SMALLINT NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at DATETIME NULL,
    CONSTRAINT pk_member_review_keywords PRIMARY KEY (keyword_id),
    CONSTRAINT uq_member_review_keywords_code UNIQUE (keyword_code),
    CONSTRAINT chk_member_review_keywords_order CHECK (display_order >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE member_review_keyword_selections (
    review_id BIGINT NOT NULL,
    keyword_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at DATETIME NULL,
    CONSTRAINT pk_member_review_keyword_selections PRIMARY KEY (review_id, keyword_id),
    INDEX idx_member_review_keyword_selections_keyword (keyword_id, review_id),
    CONSTRAINT fk_member_review_keyword_selections_review
        FOREIGN KEY (review_id) REFERENCES member_reviews (review_id),
    CONSTRAINT fk_member_review_keyword_selections_keyword
        FOREIGN KEY (keyword_id) REFERENCES member_review_keywords (keyword_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
