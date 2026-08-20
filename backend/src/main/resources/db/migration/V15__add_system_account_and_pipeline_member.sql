-- 크롤러 파이프라인이 쓸 시스템 계정을 만든다.
--
-- 왜 필요한가:
--   explore_items.reviewed_by 는 members 를 참조한다. 파이프라인이 넣는 항목도
--   등록자가 있어야 한다. 지금까지 적재 SQL 은 1000000 을 상수로 썼는데,
--   그 회원은 배포 환경에 존재하지 않는다는 지적을 받았다(PR #254 리뷰).
--   회원을 여기서 만들어 환경에 상관없이 같은 ID 를 쓰게 한다.
--
--   사람 계정과 섞이지 않도록 account_type 에 SYSTEM 을 추가한다.
--   MERCHANT 를 도입할 때 쓴 방식(V11)을 그대로 따른다.
--
-- 이 계정은 로그인하지 않는다. OAuth 신원이 없으므로 oauth_accounts 에
-- 대응하는 행을 만들지 않는다. 토큰은 service-token 엔드포인트가 발급한다.

ALTER TABLE members
    MODIFY COLUMN account_type ENUM('TRAVELER','MERCHANT','SYSTEM') NOT NULL DEFAULT 'TRAVELER'
        COMMENT '가맹점은 QR 생성과 매출 조회만, SYSTEM 은 파이프라인 적재 전용';

-- ID 를 고정한다. 적재 SQL 과 백엔드가 같은 값을 참조해야 한다.
-- 지갑은 만들지 않는다. 결제하지 않는 계정이다.
INSERT INTO members (member_id, display_name, preferred_language, account_type)
SELECT 1000000, 'NA-WA 파이프라인', 'ko', 'SYSTEM'
WHERE NOT EXISTS (SELECT 1 FROM members WHERE member_id = 1000000);

-- 이 마이그레이션보다 먼저 회원이 들어간 환경이 있다. 일괄 적재를 이 PR 병합
-- 전에 해야 했고, 그때는 enum 에 SYSTEM 이 없어 TRAVELER 로 넣었다.
-- 사람 계정과 섞이지 않도록 여기서 올린다.
UPDATE members SET account_type = 'SYSTEM' WHERE member_id = 1000000;

-- 참고: member_id 를 명시 삽입하므로 AUTO_INCREMENT 가 1000001 로 점프한다.
-- 이후 가입자 ID 가 100만대에서 시작한다. 동작에 문제는 없다.
