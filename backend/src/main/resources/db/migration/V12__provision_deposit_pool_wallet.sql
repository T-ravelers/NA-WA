-- 약속 보증금을 예치·보관하는 내부 시스템 지갑을 시드한다.
-- wallet_owners.owner_type = 'SYSTEM'은 V1부터 있었지만, 이 행을 실제로 만드는
-- 마이그레이션은 없었다. 이 지갑이 없으면 보증금 예치(회원 지갑 -> 이 지갑) 자체가
-- 이체 상대를 찾지 못해 전부 실패한다.

INSERT INTO wallet_owners (member_id, owner_type, system_code)
VALUES (NULL, 'SYSTEM', 'DEPOSIT_POOL')
ON DUPLICATE KEY UPDATE system_code = system_code;

INSERT INTO wallets (wallet_owner_id, currency_code)
SELECT o.wallet_owner_id, 'KRW'
FROM wallet_owners o
LEFT JOIN wallets w ON w.wallet_owner_id = o.wallet_owner_id
WHERE o.owner_type = 'SYSTEM'
  AND o.system_code = 'DEPOSIT_POOL'
  AND o.deleted_at IS NULL
  AND w.wallet_id IS NULL;
