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

## 회원은 지갑 정체성을 하나만 가집니다

`uq_wallet_owners_member (member_id)`와 `uq_wallets_owner (wallet_owner_id)`에는
`deleted_at`이 들어 있지 않습니다. 그래서 soft-delete된 행이 남아 있으면 같은 회원으로
새 지갑을 만들 수 없습니다. 반면 조회 경로(`WalletMapper.findByMemberId`)는
`deleted_at IS NULL`인 지갑만 봅니다.

`WalletProvisioningServiceImpl.provisionForMember()`는 이 교착을 다음 규칙으로 풉니다.

- 행이 없으면 만듭니다. 남아 있으면 `deleted_at`만 `NULL`로 되돌립니다.
- `wallet_id`, 잔액, `wallet_status`, 통화, 원장·거래 이력은 그대로 둡니다.
  `wallet_status`를 `ACTIVE`로 되돌리지 않는 이유는 `SUSPENDED`·`CLOSED` 지갑을
  재가입으로 우회시키지 않기 위해서입니다.
- 성공 판정은 UPDATE의 변경 행 수가 아니라 확보한 ID로 합니다. 이미 `deleted_at`이
  `NULL`인 행에는 복구 UPDATE가 0행을 남기기 때문입니다.

**탈퇴 회원의 재활성화 정책은 여기서 정하지 않습니다.** 잔액 승계, 소셜 계정 복원,
UNIQUE 제약 변경 여부는 탈퇴 기능을 도입하는 시점에 별도로 결정합니다. 현재 `main`에는
`wallets`·`wallet_owners`의 `deleted_at`을 세팅하는 코드가 없습니다.
