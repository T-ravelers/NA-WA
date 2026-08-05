-- Align the wallet transfer type with the no-show domain terminology.
-- Keep the legacy enum value temporarily so existing rows can be converted.
ALTER TABLE wallet_transfers
    MODIFY COLUMN transfer_type ENUM(
        'TOPUP',
        'QR_PAYMENT',
        'SETTLEMENT',
        'DEPOSIT_HOLD',
        'DEPOSIT_REFUND',
        'DEPOSIT_FORFEIT_DISTRIBUTION',
        'DEPOSIT_NO_SHOW_DISTRIBUTION',
        'REVERSAL'
    ) NOT NULL;

UPDATE wallet_transfers
SET transfer_type = 'DEPOSIT_NO_SHOW_DISTRIBUTION'
WHERE transfer_type = 'DEPOSIT_FORFEIT_DISTRIBUTION';

ALTER TABLE wallet_transfers
    MODIFY COLUMN transfer_type ENUM(
        'TOPUP',
        'QR_PAYMENT',
        'SETTLEMENT',
        'DEPOSIT_HOLD',
        'DEPOSIT_REFUND',
        'DEPOSIT_NO_SHOW_DISTRIBUTION',
        'REVERSAL'
    ) NOT NULL;

ALTER TABLE deposits
    ADD CONSTRAINT chk_deposits_amount_policy
        CHECK (amount BETWEEN 5000 AND 50000);

ALTER TABLE appointments
    ADD CONSTRAINT chk_appointments_deposit_policy
        CHECK (deposit_amount BETWEEN 5000 AND 50000);

ALTER TABLE deposit_payout_batches
    DROP CHECK chk_deposit_payout_batches_refund,
    DROP CHECK chk_deposit_payout_batches_forfeited,
    DROP CHECK chk_deposit_payout_batches_distributed,
    DROP CHECK chk_deposit_payout_batches_totals,
    DROP CHECK chk_deposit_payout_batches_reason,
    RENAME COLUMN total_refund_amount TO total_refunded_amount,
    RENAME COLUMN total_forfeited_amount TO total_no_show_amount,
    RENAME COLUMN total_distributed_amount
        TO total_no_show_distributed_amount;

ALTER TABLE deposit_payout_batches
    ADD CONSTRAINT chk_deposit_payout_batches_refunded
        CHECK (total_refunded_amount >= 0),
    ADD CONSTRAINT chk_deposit_payout_batches_no_show
        CHECK (total_no_show_amount >= 0),
    ADD CONSTRAINT chk_deposit_payout_batches_no_show_distributed
        CHECK (total_no_show_distributed_amount >= 0),
    ADD CONSTRAINT chk_deposit_payout_batches_totals
        CHECK (
            resolution_status <> 'COMPLETED'
                OR (
                total_refunded_amount + total_no_show_amount
                    = total_held_amount
                    AND total_no_show_distributed_amount
                    = total_no_show_amount
                )
            ),
    ADD CONSTRAINT chk_deposit_payout_batches_reason
        CHECK (
            resolution_reason <> 'APPOINTMENT_CANCELLED'
                OR (
                total_no_show_amount = 0
                    AND total_no_show_distributed_amount = 0
                )
            );

ALTER TABLE deposit_payouts
    ADD CONSTRAINT chk_deposit_payouts_cancellation_refund_snapshot
        CHECK (
            allocation_type <> 'CANCELLATION_REFUND'
                OR source_attendance_status_snapshot
                = recipient_attendance_status_snapshot
            );
