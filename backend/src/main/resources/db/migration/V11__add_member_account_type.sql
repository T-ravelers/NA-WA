-- 가맹점 계정을 도입한다. 가맹점은 QR 생성과 매출 조회만 할 수 있고 결제는 할 수 없다.
-- 기존 회원은 모두 결제자(TRAVELER)이므로 DEFAULT로 보정하고 별도 백필을 두지 않는다.

ALTER TABLE members
    ADD COLUMN account_type ENUM('TRAVELER','MERCHANT') NOT NULL DEFAULT 'TRAVELER'
        COMMENT '가맹점 계정은 QR 생성과 매출 조회만 가능';
