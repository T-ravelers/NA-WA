ALTER TABLE wallets
DROP CHECK chk_wallets_version,
    DROP COLUMN version;

ALTER TABLE wallet_topups
    ADD COLUMN provider VARCHAR(20) NULL
        COMMENT '결제 공급자',
    ADD COLUMN provider_payment_id VARCHAR(100) NULL
        COMMENT 'Stripe PaymentIntent ID',
    ADD COLUMN provider_status VARCHAR(50) NULL
        COMMENT '결제 공급자의 원본 상태',
    ADD COLUMN idempotency_key VARCHAR(100) NULL
        COMMENT '중복 충전 방지 키',
    ADD CONSTRAINT uq_wallet_topups_provider_payment
        UNIQUE (provider, provider_payment_id),
    ADD CONSTRAINT uq_wallet_topups_idempotency
        UNIQUE (idempotency_key);
