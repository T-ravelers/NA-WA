# 지갑·거래·결제 ERD

회원·시스템 지갑과 복식 원장, 충전·QR 결제의 관계를 보여줍니다.

```mermaid
erDiagram
    MEMBERS o|--o| WALLET_OWNERS : owns
    WALLET_OWNERS ||--o| WALLETS : has
    CURRENCIES ||--o{ WALLETS : denominates
    CURRENCIES ||--o{ WALLET_TRANSFERS : denominates
    MEMBERS o|--o{ WALLET_TRANSFERS : initiates
    WALLET_TRANSFERS o|--o{ WALLET_TRANSFERS : reverses

    WALLET_TRANSFERS ||--o{ WALLET_LEDGER_ENTRIES : posts
    WALLETS ||--o{ WALLET_LEDGER_ENTRIES : records

    WALLETS ||--o{ WALLET_TOPUPS : receives
    CURRENCIES ||--o{ WALLET_TOPUPS : quoted_in
    WALLET_TRANSFERS o|--o| WALLET_TOPUPS : completes

    WALLETS ||--o{ QR_PAYMENT_CODES : payee
    WALLET_TRANSFERS o|--o| QR_PAYMENT_CODES : completes

    MEMBERS {
        BIGINT member_id PK
    }

    CURRENCIES {
        CHAR currency_code PK
    }

    WALLET_OWNERS {
        BIGINT wallet_owner_id PK
        BIGINT member_id FK, UK
        ENUM owner_type
        VARCHAR system_code UK
    }

    WALLETS {
        BIGINT wallet_id PK
        BIGINT wallet_owner_id FK, UK
        CHAR currency_code FK
        DECIMAL available_balance
        ENUM wallet_status
    }

    WALLET_TRANSFERS {
        BIGINT transfer_id PK
        CHAR currency_code FK
        BIGINT initiator_member_id FK
        BIGINT original_transfer_id FK
        VARCHAR transfer_number UK
        ENUM transfer_type
        ENUM transfer_status
        DECIMAL amount
    }

    WALLET_LEDGER_ENTRIES {
        BIGINT ledger_entry_id PK
        BIGINT transfer_id FK
        BIGINT wallet_id FK
        ENUM entry_type
        DECIMAL amount
        DECIMAL balance_after
    }

    WALLET_TOPUPS {
        BIGINT topup_id PK
        BIGINT wallet_id FK
        CHAR source_currency_code FK
        BIGINT transfer_id FK, UK
        ENUM topup_status
        DECIMAL source_amount
        DECIMAL krw_amount
    }

    QR_PAYMENT_CODES {
        BIGINT qr_payment_code_id PK
        BIGINT payee_wallet_id FK
        BIGINT completed_transfer_id FK, UK
        VARCHAR qr_token UK
        ENUM payment_status
        DECIMAL amount
    }
```

- `wallet_transfers`가 비즈니스 거래를 기록하고 원장 행이 차변·대변을 구성합니다.
- `wallet_owners`는 회원 지갑과 시스템 지갑을 같은 구조로 표현합니다.
- 충전과 QR 결제는 완료 시 생성된 transfer를 선택적으로 연결합니다.
