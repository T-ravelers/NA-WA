-- 지갑 생성에 필요한 기준 통화(KRW)를 시드하고, 지갑이 없는 기존 회원에게 지갑을 만들어 준다.
-- V1의 fk_wallets_currency 때문에 currencies에 KRW 행이 없으면 지갑을 단 하나도 만들 수 없었다.
-- 현재 코드가 지원하는 충전 통화는 KRW 단일이므로(TopupServiceImpl) 다른 통화는 시드하지 않는다.

INSERT INTO currencies (currency_code, currency_name, decimal_places, is_active)
VALUES ('KRW', 'South Korean Won', 0, TRUE)
ON DUPLICATE KEY UPDATE currency_code = currency_code;

-- 소셜 로그인 가입 시점에 지갑을 만들지 않던 기간에 생성된 회원들을 보정한다.
INSERT INTO wallet_owners (member_id, owner_type)
SELECT m.member_id, 'MEMBER'
FROM members m
LEFT JOIN wallet_owners o ON o.member_id = m.member_id
WHERE m.deleted_at IS NULL
  AND o.wallet_owner_id IS NULL;

INSERT INTO wallets (wallet_owner_id, currency_code)
SELECT o.wallet_owner_id, 'KRW'
FROM wallet_owners o
LEFT JOIN wallets w ON w.wallet_owner_id = o.wallet_owner_id
WHERE o.owner_type = 'MEMBER'
  AND o.deleted_at IS NULL
  AND w.wallet_id IS NULL;
