-- Extend the Explore Event/Place schema for the V7 detail and list contracts.
-- This migration is intentionally separate from V1~V6. Do not edit an older migration.

-- ---------------------------------------------------------------------------
-- 1. Event source, classification, media and reservation metadata.
-- ---------------------------------------------------------------------------

ALTER TABLE event
    ADD COLUMN source VARCHAR(100) NULL AFTER pipeline_id,
    ADD COLUMN source_item_id VARCHAR(255) NULL AFTER source,
    ADD COLUMN event_kind ENUM(
        'POPUP',
        'CONCERT',
        'ETC',
        'FESTIVAL',
        'EXHIBITION'
    ) NOT NULL DEFAULT 'ETC' AFTER event_type,
    ADD COLUMN image_urls JSON NULL AFTER thumbnail_url,
    ADD COLUMN links JSON NULL AFTER source_url,
    ADD COLUMN reservation_url VARCHAR(500) NULL AFTER links,
    ADD COLUMN pre_reservation JSON NULL AFTER reservation_url,
    ADD COLUMN contact VARCHAR(200) NULL AFTER age_limit,
    ADD COLUMN organizer VARCHAR(200) NULL AFTER contact;

-- ---------------------------------------------------------------------------
-- 2. Place source, media and detail metadata.
-- ---------------------------------------------------------------------------

ALTER TABLE place
    ADD COLUMN source VARCHAR(100) NULL AFTER pipeline_id,
    ADD COLUMN source_item_id VARCHAR(255) NULL AFTER source,
    ADD COLUMN source_url VARCHAR(500) NULL AFTER thumbnail_url,
    ADD COLUMN image_urls JSON NULL AFTER source_url,
    ADD COLUMN address_detail VARCHAR(200) NULL AFTER address_road,
    ADD COLUMN menu_summary TEXT NULL AFTER closed_days;

-- ---------------------------------------------------------------------------
-- 3. Validate JSON columns and prevent duplicate external source records.
-- ---------------------------------------------------------------------------

ALTER TABLE event
    ADD CONSTRAINT chk_event_image_urls
        CHECK (image_urls IS NULL OR JSON_TYPE(image_urls) = 'ARRAY'),
    ADD CONSTRAINT chk_event_links
        CHECK (links IS NULL OR JSON_TYPE(links) = 'OBJECT'),
    ADD CONSTRAINT chk_event_pre_reservation
        CHECK (pre_reservation IS NULL OR JSON_TYPE(pre_reservation) = 'OBJECT');

ALTER TABLE place
    ADD CONSTRAINT chk_place_image_urls
        CHECK (image_urls IS NULL OR JSON_TYPE(image_urls) = 'ARRAY');

CREATE UNIQUE INDEX uq_event_source_item
    ON event (source, source_item_id);

CREATE UNIQUE INDEX uq_place_source_item
    ON place (source, source_item_id);

CREATE INDEX idx_event_kind_status
    ON event (event_kind, status);

CREATE INDEX idx_place_kind_active
    ON place (place_kind, is_active);

-- ---------------------------------------------------------------------------
-- 4. Event/Place content is stored in the base tables for the current MVP.
-- Remove the unused translation tables after all application SQL no longer
-- references them.
-- ---------------------------------------------------------------------------

DROP TABLE IF EXISTS place_translations;
DROP TABLE IF EXISTS event_translations;
