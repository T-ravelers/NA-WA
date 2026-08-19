-- 탐색 번역 테이블 재도입과 파이프라인 적재용 제약 완화.
-- 원본 이슈: T-ravelers/NA-WA#239
-- V11 은 V11__add_member_account_type (PR #249) 이 선점했다.
-- 번호만 V13 으로 올렸고 내용은 동일하다.

SET NAMES utf8mb4;

-- 1. 회차·등급별 가격 안내문이 300자를 넘는 사례가 있어 길이 제한을 푼다.
ALTER TABLE event MODIFY COLUMN price_text TEXT NULL;

-- chk_event_period 는 건드리지 않는다.
-- 리뷰에서 지적된 대로 기존 제약은 순서 검사가 아니라 불변식이었다.
-- "상시 + 종료일" 77건은 크롤러가 end_date 를 진실로 보고 is_permanent 를
-- 정규화하도록 고쳤다. 그 결과 기존 제약 위반이 0이 되어 완화가 불필요해졌다.

-- 2. V7에서 제거한 번역 테이블을 파이프라인 번역 구조에 맞춰 재도입한다.
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
