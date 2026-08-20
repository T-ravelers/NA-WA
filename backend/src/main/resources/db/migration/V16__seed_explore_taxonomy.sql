-- 탐색 분류 체계(sector · activity)를 채운다.
--
-- 왜 지금 넣나:
--   V3 가 두 테이블을 만들었지만 시드가 어느 마이그레이션에도 없다. 로컬 개발
--   DB 에만 값이 있어, 배포 환경은 두 테이블이 빈 채로 돌고 있었다.
--   실측(2026-08-20): 운영 activity 0건, sector 0건.
--
--   크롤러 파이프라인이 수집한 항목을 이 분류로 태깅하는데, 마스터가 없으면
--   event_activity 적재가 외래키 위반으로 실패한다. 실제로 실패했다.
--
-- ID 를 명시하는 이유:
--   파이프라인과 운영이 같은 activity_id 를 참조해야 한다. AUTO_INCREMENT 에
--   맡기면 실행 순서에 따라 값이 달라져 두 DB 의 분류가 어긋난다.
--
-- 비어 있을 때만 넣는다:
--   activity 는 PK(activity_id)와 UNIQUE(activity_code) 두 개의 유니크 키를
--   갖는다. ON DUPLICATE KEY UPDATE 로 덮으면, 같은 code 가 다른 id 로 들어
--   있는 DB 에서 엉뚱한 행을 갱신하거나 나머지 유니크 제약과 충돌해 1062 로
--   마이그레이션이 멈춘다. 운영은 0건이라 안전하지만 팀원 로컬 DB 가 그렇다.
--
--   시드의 일은 "빈 분류 체계를 채우는 것"이다. 이미 값이 있는 DB 는 손대지
--   않는다. 값이 다르다면 그건 시드가 조용히 덮을 문제가 아니라 사람이 봐야
--   할 문제다.

SET NAMES utf8mb4;

-- INSERT 문 안에서 대상 테이블을 조회할 수 없으므로 미리 판정한다.
SET @sector_empty   := (SELECT COUNT(*) = 0 FROM sector);
SET @activity_empty := (SELECT COUNT(*) = 0 FROM activity);

INSERT INTO sector (sector_id, sector_code, label_ko, label_en, display_order, filter_on)
SELECT * FROM (
  SELECT 1 AS sector_id, 'BEAUTY' AS sector_code, '뷰티' AS label_ko,
         'Beauty' AS label_en, 10 AS display_order, TRUE AS filter_on UNION ALL
  SELECT 2, 'FOOD', '음식', 'Food', 20, TRUE UNION ALL
  SELECT 3, 'SHOWS', '전시·공연', 'Shows', 30, TRUE UNION ALL
  SELECT 4, 'SHOPPING', '쇼핑', 'Shopping', 40, TRUE
) AS seed
WHERE @sector_empty;

INSERT INTO activity (activity_id, sector_id, activity_code, label_ko, label_en, display_order, filter_on)
SELECT * FROM (
  SELECT 1 AS activity_id, 1 AS sector_id, 'MAKEUP_COSMETICS' AS activity_code,
         '화장품' AS label_ko, 'Makeup / Cosmetics' AS label_en,
         10 AS display_order, TRUE AS filter_on UNION ALL
  SELECT 2, 1, 'SKINCARE', '스킨케어', 'Skincare', 20, TRUE UNION ALL
  SELECT 3, 1, 'PERFUME', '향수', 'Perfume', 30, TRUE UNION ALL
  SELECT 4, 1, 'BEAUTY_DEVICE', '뷰티기기', 'Beauty Device', 40, TRUE UNION ALL
  SELECT 5, 1, 'HAIRCARE', '헤어', 'Haircare', 50, TRUE UNION ALL
  SELECT 6, 1, 'NAIL', '네일', 'Nail', 60, TRUE UNION ALL
  SELECT 7, 1, 'SPA_SAUNA', '스파·사우나', 'Spa / Sauna', 70, TRUE UNION ALL
  SELECT 8, 1, 'AESTHETIC_CLINIC', '피부·의원', 'Aesthetic Clinic', 80, TRUE UNION ALL
  SELECT 9, 2, 'CAFE_DESSERT', '카페·디저트', 'Cafe / Dessert', 10, TRUE UNION ALL
  SELECT 10, 2, 'RESTAURANT', '레스토랑', 'Restaurant', 20, TRUE UNION ALL
  SELECT 11, 2, 'TOURIST_RESTAURANT', '관광식당', 'Tourist Restaurant', 30, TRUE UNION ALL
  SELECT 12, 2, 'STREET_FOOD', '길거리음식', 'Street Food', 40, TRUE UNION ALL
  SELECT 13, 2, 'BAR_LIQUOR', '주점·바', 'Bar / Liquor', 50, TRUE UNION ALL
  SELECT 14, 2, 'TEA_HOUSE', '찻집', 'Tea House', 60, TRUE UNION ALL
  SELECT 15, 2, 'SNACK', '간식·편의점', 'Snack', 70, TRUE UNION ALL
  SELECT 16, 2, 'FOOD_FESTIVAL', '음식축제', 'Food Festival', 80, TRUE UNION ALL
  SELECT 17, 3, 'CHARACTER_GOODS', '캐릭터굿즈', 'Character Goods', 10, TRUE UNION ALL
  SELECT 18, 3, 'ANIME_WEBTOON', '애니·웹툰', 'Anime / Webtoon', 20, TRUE UNION ALL
  SELECT 19, 3, 'FAN_MEETING', '팬미팅', 'Fan Meeting', 30, TRUE UNION ALL
  SELECT 20, 3, 'GAME', '게임', 'Game', 40, TRUE UNION ALL
  SELECT 21, 3, 'EXHIBITION', '전시', 'Exhibition', 50, TRUE UNION ALL
  SELECT 22, 3, 'FESTIVAL', '축제', 'Festival', 60, TRUE UNION ALL
  SELECT 23, 3, 'FILM_DRAMA', '영화·드라마', 'Film / Drama', 70, TRUE UNION ALL
  SELECT 24, 3, 'CREATOR', '크리에이터', 'Creator', 80, TRUE UNION ALL
  SELECT 25, 3, 'PERFORMANCE', '공연', 'Performance', 90, TRUE UNION ALL
  SELECT 26, 3, 'EXPO_FAIR', '박람회', 'Expo / Fair', 100, TRUE UNION ALL
  SELECT 27, 3, 'HERITAGE_FESTIVAL', '전통역사축제', 'Heritage Festival', 110, TRUE UNION ALL
  SELECT 28, 3, 'NATURE_FESTIVAL', '생태자연축제', 'Nature Festival', 120, TRUE UNION ALL
  SELECT 29, 3, 'TRADITIONAL_PERFORMANCE', '전통공연', 'Traditional Performance', 130, TRUE UNION ALL
  SELECT 30, 3, 'CONCERT', '콘서트', 'Concert', 140, TRUE UNION ALL
  SELECT 31, 3, 'K_POP_CONCERT', 'K-pop 콘서트', 'K-pop Concert', 145, TRUE UNION ALL
  SELECT 32, 3, 'CLASSICAL_CONCERT', '클래식', 'Classical Concert', 150, TRUE UNION ALL
  SELECT 33, 3, 'PLAY_THEATER', '연극', 'Play / Theater', 160, TRUE UNION ALL
  SELECT 34, 3, 'MUSICAL', '뮤지컬', 'Musical', 170, TRUE UNION ALL
  SELECT 35, 3, 'OPERA', '오페라', 'Opera', 180, TRUE UNION ALL
  SELECT 36, 3, 'DANCE', '무용', 'Dance', 190, TRUE UNION ALL
  SELECT 37, 3, 'NON_VERBAL', '넌버벌', 'Non-verbal', 200, TRUE UNION ALL
  SELECT 38, 4, 'FASHION', '패션', 'Fashion', 10, TRUE UNION ALL
  SELECT 39, 4, 'APPAREL', '의류', 'Apparel', 11, TRUE UNION ALL
  SELECT 40, 4, 'BAGS_SHOES', '가방·신발', 'Bags / Shoes', 12, TRUE UNION ALL
  SELECT 41, 4, 'JEWELRY_WATCH', '주얼리·시계', 'Jewelry / Watch', 13, TRUE UNION ALL
  SELECT 42, 4, 'LIFESTYLE_HOMEWARE', '리빙·라이프', 'Lifestyle / Homeware', 20, TRUE UNION ALL
  SELECT 43, 4, 'BOOK_STATIONERY', '문구·도서', 'Book / Stationery', 30, TRUE UNION ALL
  SELECT 44, 4, 'ART_ILLUST', '아트·일러스트', 'Art / Illust', 40, TRUE UNION ALL
  SELECT 45, 4, 'DIGITAL_TECH', '디지털·테크', 'Digital / Tech', 50, TRUE UNION ALL
  SELECT 46, 4, 'KIDS_FAMILY', '키즈·패밀리', 'Kids / Family', 60, TRUE UNION ALL
  SELECT 47, 4, 'PETS', '반려동물', 'Pets', 70, TRUE UNION ALL
  SELECT 48, 4, 'SPORTS_LEISURE', '스포츠·레저', 'Sports / Leisure', 80, TRUE UNION ALL
  SELECT 49, 4, 'TRAVEL_HOBBY', '여행·취미', 'Travel / Hobby', 90, TRUE UNION ALL
  SELECT 50, 4, 'HEALTH_FITNESS', '건강·헬스', 'Health / Fitness', 95, TRUE UNION ALL
  SELECT 51, 4, 'DEPARTMENT_STORE', '백화점', 'Department Store', 110, TRUE UNION ALL
  SELECT 52, 4, 'TRADITIONAL_MARKET', '전통시장', 'Traditional Market', 120, TRUE UNION ALL
  SELECT 53, 4, 'SHOPPING_MALL', '쇼핑몰', 'Shopping Mall', 130, TRUE UNION ALL
  SELECT 54, 4, 'DUTY_FREE', '면세점', 'Duty-Free', 140, TRUE UNION ALL
  SELECT 55, 4, 'SOUVENIRS', '기념품', 'Souvenirs', 150, TRUE UNION ALL
  SELECT 56, 4, 'CRAFT_WORKSHOP', '공방·공예', 'Craft / Workshop', 160, TRUE
) AS seed
WHERE @activity_empty;
