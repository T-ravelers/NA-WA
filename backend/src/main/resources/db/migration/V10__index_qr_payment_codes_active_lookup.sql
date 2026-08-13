-- Support listing a wallet's own active, unexpired QR payment codes.
CREATE INDEX idx_qr_payment_codes_active_lookup
    ON qr_payment_codes (payee_wallet_id, payment_status, expires_at);
