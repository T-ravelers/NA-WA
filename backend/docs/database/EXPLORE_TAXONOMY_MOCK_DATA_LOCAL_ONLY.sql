-- Local mock data only. Do not run this file in a shared or production database.
-- Replaces duplicate sector/activity rows with the operational_v9 taxonomy.
-- The IDs mirror the 2026-08-06 handoff only for local preview; a future
-- taxonomy API/seed must provide the production identifiers and classifications.

START TRANSACTION;

DELETE FROM event_activity;
DELETE FROM place_activity;
DELETE FROM activity;
DELETE FROM sector;

INSERT INTO sector
    (sector_id, sector_code, label_ko, label_en, display_order, filter_on)
VALUES
    (1, 'BEAUTY', '뷰티', 'Beauty', 1, TRUE),
    (2, 'FOOD', '음식', 'Food', 2, TRUE),
    (3, 'SHOWS', '전시·공연', 'Shows', 3, TRUE),
    (4, 'SHOPPING', '쇼핑', 'Shopping', 4, TRUE);

INSERT INTO activity
    (activity_id, sector_id, activity_code, label_ko, label_en, display_order, filter_on)
VALUES
    (1, 1, 'MAKEUP_COSMETICS', '화장품', 'Makeup / Cosmetics', 10, TRUE),
    (2, 1, 'SKINCARE', '스킨케어', 'Skincare', 20, TRUE),
    (3, 1, 'PERFUME', '향수', 'Perfume', 30, TRUE),
    (4, 1, 'BEAUTY_DEVICE', '뷰티기기', 'Beauty Device', 40, TRUE),
    (5, 1, 'HAIRCARE', '헤어', 'Haircare', 50, TRUE),
    (6, 1, 'NAIL', '네일', 'Nail', 60, TRUE),
    (7, 1, 'SPA_SAUNA', '스파·사우나', 'Spa / Sauna', 70, TRUE),
    (8, 1, 'AESTHETIC_CLINIC', '피부·의원', 'Aesthetic Clinic', 80, TRUE),
    (9, 2, 'CAFE_DESSERT', '카페·디저트', 'Cafe / Dessert', 10, TRUE),
    (10, 2, 'RESTAURANT', '레스토랑', 'Restaurant', 20, TRUE),
    (11, 2, 'TOURIST_RESTAURANT', '관광식당', 'Tourist Restaurant', 30, TRUE),
    (12, 2, 'STREET_FOOD', '길거리음식', 'Street Food', 40, TRUE),
    (13, 2, 'BAR_LIQUOR', '주점·바', 'Bar / Liquor', 50, TRUE),
    (14, 2, 'TEA_HOUSE', '찻집', 'Tea House', 60, TRUE),
    (15, 2, 'SNACK', '간식·편의점', 'Snack', 70, TRUE),
    (16, 2, 'FOOD_FESTIVAL', '음식축제', 'Food Festival', 80, TRUE),
    (17, 3, 'CHARACTER_GOODS', '캐릭터굿즈', 'Character Goods', 10, TRUE),
    (18, 3, 'ANIME_WEBTOON', '애니·웹툰', 'Anime / Webtoon', 20, TRUE),
    (19, 3, 'FAN_MEETING', '팬미팅', 'Fan Meeting', 30, TRUE),
    (20, 3, 'GAME', '게임', 'Game', 40, TRUE),
    (21, 3, 'EXHIBITION', '전시', 'Exhibition', 50, TRUE),
    (22, 3, 'FESTIVAL', '축제', 'Festival', 60, TRUE),
    (23, 3, 'FILM_DRAMA', '영화·드라마', 'Film / Drama', 70, TRUE),
    (24, 3, 'CREATOR', '크리에이터', 'Creator', 80, TRUE),
    (25, 3, 'PERFORMANCE', '공연', 'Performance', 90, TRUE),
    (26, 3, 'EXPO_FAIR', '박람회', 'Expo / Fair', 100, TRUE),
    (27, 3, 'HERITAGE_FESTIVAL', '전통역사축제', 'Heritage Festival', 110, TRUE),
    (28, 3, 'NATURE_FESTIVAL', '생태자연축제', 'Nature Festival', 120, TRUE),
    (29, 3, 'TRADITIONAL_PERFORMANCE', '전통공연', 'Traditional Performance', 130, TRUE),
    (30, 3, 'CONCERT', '콘서트', 'Concert', 140, TRUE),
    (31, 3, 'K_POP_CONCERT', 'K-pop 콘서트', 'K-pop Concert', 145, TRUE),
    (32, 3, 'CLASSICAL_CONCERT', '클래식', 'Classical Concert', 150, TRUE),
    (33, 3, 'PLAY_THEATER', '연극', 'Play / Theater', 160, TRUE),
    (34, 3, 'MUSICAL', '뮤지컬', 'Musical', 170, TRUE),
    (35, 3, 'OPERA', '오페라', 'Opera', 180, TRUE),
    (36, 3, 'DANCE', '무용', 'Dance', 190, TRUE),
    (37, 3, 'NON_VERBAL', '넌버벌', 'Non-verbal', 200, TRUE),
    (38, 4, 'FASHION', '패션', 'Fashion', 10, TRUE),
    (39, 4, 'APPAREL', '의류', 'Apparel', 11, TRUE),
    (40, 4, 'BAGS_SHOES', '가방·신발', 'Bags / Shoes', 12, TRUE),
    (41, 4, 'JEWELRY_WATCH', '주얼리·시계', 'Jewelry / Watch', 13, TRUE),
    (42, 4, 'LIFESTYLE_HOMEWARE', '리빙·라이프', 'Lifestyle / Homeware', 20, TRUE),
    (43, 4, 'BOOK_STATIONERY', '문구·도서', 'Book / Stationery', 30, TRUE),
    (44, 4, 'ART_ILLUST', '아트·일러스트', 'Art / Illust', 40, TRUE),
    (45, 4, 'DIGITAL_TECH', '디지털·테크', 'Digital / Tech', 50, TRUE),
    (46, 4, 'KIDS_FAMILY', '키즈·패밀리', 'Kids / Family', 60, TRUE),
    (47, 4, 'PETS', '반려동물', 'Pets', 70, TRUE),
    (48, 4, 'SPORTS_LEISURE', '스포츠·레저', 'Sports / Leisure', 80, TRUE),
    (49, 4, 'TRAVEL_HOBBY', '여행·취미', 'Travel / Hobby', 90, TRUE),
    (50, 4, 'HEALTH_FITNESS', '건강·헬스', 'Health / Fitness', 95, TRUE),
    (51, 4, 'DEPARTMENT_STORE', '백화점', 'Department Store', 110, TRUE),
    (52, 4, 'TRADITIONAL_MARKET', '전통시장', 'Traditional Market', 120, TRUE),
    (53, 4, 'SHOPPING_MALL', '쇼핑몰', 'Shopping Mall', 130, TRUE),
    (54, 4, 'DUTY_FREE', '면세점', 'Duty-Free', 140, TRUE),
    (55, 4, 'SOUVENIRS', '기념품', 'Souvenirs', 150, TRUE),
    (56, 4, 'CRAFT_WORKSHOP', '공방·공예', 'Craft / Workshop', 160, TRUE);

ALTER TABLE sector AUTO_INCREMENT = 5;
ALTER TABLE activity AUTO_INCREMENT = 57;

-- Distribute local events across the taxonomy so every filter can be previewed.
-- This is deterministic mock classification only; crawler classification must
-- replace these links before the data is used outside local development.
INSERT INTO event_activity (event_id, activity_id, is_primary)
SELECT ranked.event_id,
       MOD(ranked.row_no - 1, 56) + 1,
       TRUE
FROM (
    SELECT
        e.event_id,
        ROW_NUMBER() OVER (ORDER BY e.event_id) AS row_no
    FROM event e
    JOIN explore_items ei ON ei.item_id = e.event_id
    WHERE ei.item_type = 'EVENT'
      AND ei.approval_status = 'APPROVED'
      AND ei.visibility_status = 'VISIBLE'
      AND ei.deleted_at IS NULL
      AND e.deleted_at IS NULL
      AND e.status IN ('SCHEDULED', 'ONGOING')
) ranked;

-- Map operational_v9 source place_kind labels to representative activities so
-- Place category filters can also be previewed locally. The beauty source has
-- additional per-place links to PERFUME and BEAUTY_DEVICE; the local seed
-- keeps the common MAKEUP_COSMETICS + SKINCARE links so those filters have a
-- stable preview without importing the entire operational dataset.
INSERT INTO place_activity (place_id, activity_id, is_primary)
SELECT p.place_id, mapping.activity_id, mapping.is_primary
FROM place p
JOIN (
    SELECT '뷰티매장' AS place_kind, 1 AS activity_id, TRUE AS is_primary
    UNION ALL SELECT '뷰티매장', 2, FALSE
    UNION ALL SELECT '카페', 9, TRUE
    UNION ALL SELECT '제과', 9, TRUE
    UNION ALL SELECT '기타음료점', 9, TRUE
    UNION ALL SELECT '찻집', 14, TRUE
    UNION ALL SELECT '관광식당', 11, TRUE
    UNION ALL SELECT '모범음식점', 11, TRUE
    UNION ALL SELECT '서양식', 10, TRUE
    UNION ALL SELECT '일식', 10, TRUE
    UNION ALL SELECT '중식', 10, TRUE
    UNION ALL SELECT '기타외국식', 10, TRUE
    UNION ALL SELECT '퓨전음식', 10, TRUE
    UNION ALL SELECT '김밥 분식', 12, TRUE
    UNION ALL SELECT '이동음식', 12, TRUE
    UNION ALL SELECT '바/펍', 13, TRUE
    UNION ALL SELECT '상설시장', 52, TRUE
    UNION ALL SELECT '비상설시장', 52, TRUE
    UNION ALL SELECT '복합쇼핑몰', 53, TRUE
    UNION ALL SELECT '아웃렛', 53, TRUE
    UNION ALL SELECT '백화점', 51, TRUE
    UNION ALL SELECT '관광기념품/특산물판매점', 55, TRUE
    UNION ALL SELECT '공방/공예품점', 56, TRUE
) mapping ON mapping.place_kind = p.place_kind
WHERE p.deleted_at IS NULL
  AND p.is_active = TRUE
  AND p.place_kind IS NOT NULL;

COMMIT;
