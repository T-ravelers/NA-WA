-- Local mock data only. Do not run this file in a shared or production database.
-- Replaces duplicate sector/activity rows with the Explore taxonomy.

START TRANSACTION;

DELETE FROM event_activity;
DELETE FROM place_activity;
DELETE FROM activity;
DELETE FROM sector;

INSERT INTO sector
    (sector_id, sector_code, label_ko, label_en, display_order, filter_on)
VALUES
    (1, 'FOOD', '음식', 'Food', 1, TRUE),
    (2, 'BEAUTY', '뷰티', 'Beauty', 2, TRUE),
    (3, 'SHOPPING', '쇼핑', 'Shopping', 3, TRUE),
    (4, 'SHOWS', '전시·공연', 'Shows', 4, TRUE);

INSERT INTO activity
    (activity_id, sector_id, activity_code, label_ko, label_en, display_order, filter_on)
VALUES
    (101, 1, 'CAFE_DESSERT', '카페·디저트', 'Cafe / Dessert', 1, TRUE),
    (102, 1, 'FOOD_FESTIVAL', '푸드 페스티벌', 'Food Festival', 2, TRUE),
    (103, 1, 'RESTAURANT', '레스토랑', 'Restaurant', 3, TRUE),
    (104, 1, 'BAR_LIQUOR', '바·주류', 'Bar / Liquor', 4, TRUE),
    (105, 1, 'SNACK', '과자·음료', 'Snack', 5, TRUE),
    (106, 1, 'OTHER', '기타', 'Other', 6, TRUE),
    (201, 2, 'K_BEAUTY', 'K-뷰티', 'K-Beauty', 1, TRUE),
    (202, 2, 'MAKEUP_COSMETICS', '메이크업·화장품', 'Makeup / Cosmetics', 2, TRUE),
    (203, 2, 'PERFUME', '향수', 'Perfume', 3, TRUE),
    (204, 2, 'BEAUTY_DEVICE', '뷰티 디바이스', 'Beauty Device', 4, TRUE),
    (205, 2, 'OTHER', '기타', 'Other', 5, TRUE),
    (301, 3, 'FASHION', '패션', 'Fashion', 1, TRUE),
    (302, 3, 'LIFESTYLE_HOMEWARE', '라이프스타일·홈웨어', 'Lifestyle / Homeware', 2, TRUE),
    (303, 3, 'BOOK_STATIONERY', '도서·문구', 'Book / Stationery', 3, TRUE),
    (304, 3, 'KIDS_FAMILY', '키즈·패밀리', 'Kids / Family', 4, TRUE),
    (305, 3, 'TRAVEL_HOBBY', '여행·취미', 'Travel / Hobby', 5, TRUE),
    (306, 3, 'SPORTS_LEISURE', '스포츠·레저', 'Sports / Leisure', 6, TRUE),
    (307, 3, 'DIGITAL_TECH', '디지털·테크', 'Digital / Tech', 7, TRUE),
    (308, 3, 'ART_ILLUST', '아트·일러스트', 'Art / Illust', 8, TRUE),
    (309, 3, 'JEWELRY_WATCH', '주얼리·시계', 'Jewelry / Watch', 9, TRUE),
    (310, 3, 'PETS', '반려동물', 'Pets', 10, TRUE),
    (311, 3, 'HEALTH_FITNESS', '건강·헬스', 'Health / Fitness', 11, TRUE),
    (312, 3, 'OTHER', '기타', 'Other', 12, TRUE),
    (401, 4, 'CHARACTER_GOODS', '캐릭터 굿즈', 'Character Goods', 1, TRUE),
    (402, 4, 'FESTIVAL', '페스티벌', 'Festival', 2, TRUE),
    (403, 4, 'ANIME_WEBTOON', '애니·웹툰', 'Anime / Webtoon', 3, TRUE),
    (404, 4, 'FAN_MEETING', '팬미팅', 'Fan Meeting', 4, TRUE),
    (405, 4, 'GAME', '게임', 'Game', 5, TRUE),
    (406, 4, 'EXHIBITION', '전시', 'Exhibition', 6, TRUE),
    (407, 4, 'PERFORMANCE', '공연', 'Performance', 7, TRUE),
    (408, 4, 'EXPO_FAIR', '엑스포·페어', 'Expo / Fair', 8, TRUE),
    (409, 4, 'HERITAGE_FESTIVAL', '전통·역사 축제', 'Heritage Festival', 9, TRUE),
    (410, 4, 'FILM_DRAMA', '영화·드라마', 'Film / Drama', 10, TRUE),
    (411, 4, 'TRADITIONAL_PERFORMANCE', '전통 공연', 'Traditional Performance', 11, TRUE),
    (412, 4, 'CONCERT', '콘서트', 'Concert', 12, TRUE),
    (413, 4, 'NATURE_FESTIVAL', '생태·자연 축제', 'Nature Festival', 13, TRUE),
    (414, 4, 'PLAY_THEATER', '연극', 'Play / Theater', 14, TRUE),
    (415, 4, 'CLASSICAL_CONCERT', '클래식 콘서트', 'Classical Concert', 15, TRUE),
    (416, 4, 'CREATOR', '크리에이터', 'Creator', 16, TRUE),
    (417, 4, 'MUSICAL', '뮤지컬', 'Musical', 17, TRUE),
    (418, 4, 'OPERA', '오페라', 'Opera', 18, TRUE),
    (419, 4, 'DANCE', '무용', 'Dance', 19, TRUE),
    (420, 4, 'NON_VERBAL', '넌버벌', 'Non-verbal', 20, TRUE),
    (421, 4, 'SPORTS', '스포츠', 'Sports', 21, TRUE),
    (422, 4, 'OTHER', '기타', 'Other', 22, TRUE);

ALTER TABLE sector AUTO_INCREMENT = 5;
ALTER TABLE activity AUTO_INCREMENT = 423;

-- Distribute local events across the taxonomy so every filter can be previewed.
-- This is deterministic mock classification only; crawler classification must
-- replace these links before the data is used outside local development.
INSERT INTO event_activity (event_id, activity_id, is_primary)
SELECT ranked.event_id,
       CASE MOD(ranked.row_no - 1, 45)
           WHEN 0 THEN 101
           WHEN 1 THEN 102
           WHEN 2 THEN 103
           WHEN 3 THEN 104
           WHEN 4 THEN 105
           WHEN 5 THEN 106
           WHEN 6 THEN 201
           WHEN 7 THEN 202
           WHEN 8 THEN 203
           WHEN 9 THEN 204
           WHEN 10 THEN 205
           WHEN 11 THEN 301
           WHEN 12 THEN 302
           WHEN 13 THEN 303
           WHEN 14 THEN 304
           WHEN 15 THEN 305
           WHEN 16 THEN 306
           WHEN 17 THEN 307
           WHEN 18 THEN 308
           WHEN 19 THEN 309
           WHEN 20 THEN 310
           WHEN 21 THEN 311
           WHEN 22 THEN 312
           WHEN 23 THEN 401
           WHEN 24 THEN 402
           WHEN 25 THEN 403
           WHEN 26 THEN 404
           WHEN 27 THEN 405
           WHEN 28 THEN 406
           WHEN 29 THEN 407
           WHEN 30 THEN 408
           WHEN 31 THEN 409
           WHEN 32 THEN 410
           WHEN 33 THEN 411
           WHEN 34 THEN 412
           WHEN 35 THEN 413
           WHEN 36 THEN 414
           WHEN 37 THEN 415
           WHEN 38 THEN 416
           WHEN 39 THEN 417
           WHEN 40 THEN 418
           WHEN 41 THEN 419
           WHEN 42 THEN 420
           WHEN 43 THEN 421
           WHEN 44 THEN 422
       END,
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

COMMIT;
