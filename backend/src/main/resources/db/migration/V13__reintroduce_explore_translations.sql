-- 탐색 번역 테이블 재도입과 파이프라인 적재용 제약 완화.
-- 원본 이슈: T-ravelers/NA-WA#239
-- V11 은 V11__add_member_account_type (PR #249) 이 선점했다.
-- 번호만 V13 으로 올렸고 내용은 동일하다.
-- 이 파일은 백엔드 저장소의 backend/src/main/resources/db/migration/ 에 들어간다.

SET NAMES utf8mb4;

-- 1. 상시 운영이면서 종료일이 있는 데이터를 허용하도록 기간 제약을 완화한다.
--    두 값이 모두 있을 때만 순서를 검사한다.
ALTER TABLE event DROP CHECK chk_event_period;
ALTER TABLE event
    ADD CONSTRAINT chk_event_period CHECK (
        start_date IS NULL OR end_date IS NULL OR start_date <= end_date
    );

-- 2. 회차·등급별 가격 안내문이 300자를 넘는 사례가 있어 길이 제한을 푼다.
ALTER TABLE event MODIFY COLUMN price_text TEXT NULL;

-- 3. V7에서 제거한 번역 테이블을 파이프라인 번역 구조에 맞춰 재도입한다.
--    한국어 원문은 event/place 본체에 유지하고, 여기에는 en/ja/zh-TW/vi만 저장한다.
CREATE TABLE event_translations (
    event_id         BIGINT       NOT NULL,
    language_code    VARCHAR(10)  NOT NULL,
    title            VARCHAR(300) NULL,
    description      TEXT NULL,
    operating_hours  TEXT NULL,
    address_display  TEXT NULL,
    venue_detail     TEXT NULL,
    age_limit        VARCHAR(200) NULL,
    organizer        VARCHAR(300) NULL,
    price_text       TEXT NULL,
    created_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at       DATETIME NULL,
    CONSTRAINT pk_event_translations PRIMARY KEY (event_id, language_code),
    CONSTRAINT chk_event_translations_language
        CHECK (language_code IN ('en','ja','zh-TW','vi')),
    CONSTRAINT fk_event_translations_event
        FOREIGN KEY (event_id) REFERENCES event (event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE place_translations (
    place_id           BIGINT       NOT NULL,
    language_code      VARCHAR(10)  NOT NULL,
    name               VARCHAR(300) NULL,
    brand              VARCHAR(200) NULL,
    branch             VARCHAR(200) NULL,
    address_display    TEXT NULL,
    address_detail     TEXT NULL,
    opening_hours_text TEXT NULL,
    closed_days_text   TEXT NULL,
    menu_summary       TEXT NULL,
    created_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at         DATETIME NULL,
    CONSTRAINT pk_place_translations PRIMARY KEY (place_id, language_code),
    CONSTRAINT chk_place_translations_language
        CHECK (language_code IN ('en','ja','zh-TW','vi')),
    CONSTRAINT fk_place_translations_place
        FOREIGN KEY (place_id) REFERENCES place (place_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
