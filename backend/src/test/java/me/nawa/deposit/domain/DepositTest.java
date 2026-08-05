package me.nawa.deposit.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class DepositTest {

    private static final LocalDateTime HELD_AT =
        LocalDateTime.of(2026, 8, 5, 10, 0);

    private static final LocalDateTime RESOLVED_AT =
        LocalDateTime.of(2026, 8, 6, 10, 0);

    @Test
    void pending_createsDepositInPendingStatus() {
        Deposit deposit = Deposit.pending(
            1L,
            BigDecimal.valueOf(10_000)
        );

        assertEquals(DepositStatus.PENDING, deposit.getDepositStatus());
        assertEquals(1L, deposit.getAppointmentMemberId());
        assertEquals(
            0,
            BigDecimal.valueOf(10_000).compareTo(deposit.getAmount())
        );
    }

    @Test
    void hold_changesPendingDepositToHeld() {
        Deposit deposit = pendingDeposit();

        deposit.hold(100L, HELD_AT);

        assertEquals(DepositStatus.HELD, deposit.getDepositStatus());
        assertEquals(100L, deposit.getHeldTransferId());
        assertEquals(HELD_AT, deposit.getHeldAt());
    }

    @Test
    void cancel_changesPendingDepositToCancelled() {
        Deposit deposit = pendingDeposit();

        deposit.cancel(RESOLVED_AT);

        assertEquals(DepositStatus.CANCELLED, deposit.getDepositStatus());
        assertNull(deposit.getHeldTransferId());
        assertNull(deposit.getHeldAt());
        assertEquals(RESOLVED_AT, deposit.getResolvedAt());
    }

    @Test
    void refund_changesHeldDepositToRefunded() {
        Deposit deposit = heldDeposit();

        deposit.refund(RESOLVED_AT);

        assertEquals(DepositStatus.REFUNDED, deposit.getDepositStatus());
        assertEquals(RESOLVED_AT, deposit.getResolvedAt());
    }

    @Test
    void distribute_changesHeldDepositToDistributed() {
        Deposit deposit = heldDeposit();

        deposit.distribute(RESOLVED_AT);

        assertEquals(
            DepositStatus.DISTRIBUTED,
            deposit.getDepositStatus()
        );
        assertEquals(RESOLVED_AT, deposit.getResolvedAt());
    }

    @Test
    void refund_throwsExceptionWhenDepositIsPending() {
        Deposit deposit = pendingDeposit();

        assertThrows(
            IllegalStateException.class,
            () -> deposit.refund(RESOLVED_AT)
        );
    }

    private Deposit pendingDeposit() {
        return Deposit.pending(1L, BigDecimal.valueOf(10_000));
    }

    private Deposit heldDeposit() {
        Deposit deposit = pendingDeposit();
        deposit.hold(100L, HELD_AT);
        return deposit;
    }
}
