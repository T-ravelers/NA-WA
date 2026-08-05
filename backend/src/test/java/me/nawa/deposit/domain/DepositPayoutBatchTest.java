package me.nawa.deposit.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class DepositPayoutBatchTest {

    private static final LocalDateTime SNAPSHOT_AT =
        LocalDateTime.of(2026, 8, 5, 10, 0);

    private static final LocalDateTime RESOLVED_AT =
        LocalDateTime.of(2026, 8, 6, 10, 0);

    @Test
    void pending_createsBatchInPendingStatus() {
        DepositPayoutBatch batch = pendingBatch();

        assertEquals(
            ResolutionStatus.PENDING,
            batch.getResolutionStatus()
        );
        assertEquals(1L, batch.getAppointmentId());
        assertEquals(
            ResolutionReason.APPOINTMENT_COMPLETED,
            batch.getResolutionReason()
        );
        assertEquals(BigDecimal.ZERO, batch.getTotalRefundedAmount());
        assertEquals(BigDecimal.ZERO, batch.getTotalNoShowAmount());
    }

    @Test
    void startProcessing_changesPendingBatchToProcessing() {
        DepositPayoutBatch batch = pendingBatch();

        batch.startProcessing();

        assertTrue(batch.isProcessing());
    }

    @Test
    void complete_changesProcessingBatchToCompleted() {
        DepositPayoutBatch batch = pendingBatch();
        batch.startProcessing();

        batch.complete(
            BigDecimal.valueOf(10_000),
            BigDecimal.valueOf(10_000),
            BigDecimal.valueOf(10_000),
            99L,
            RESOLVED_AT
        );

        assertTrue(batch.isCompleted());
        assertEquals(
            BigDecimal.valueOf(10_000),
            batch.getTotalRefundedAmount()
        );
        assertEquals(
            BigDecimal.valueOf(10_000),
            batch.getTotalNoShowAmount()
        );
        assertEquals(
            BigDecimal.valueOf(10_000),
            batch.getTotalNoShowDistributedAmount()
        );
        assertEquals(RESOLVED_AT, batch.getResolvedAt());
        assertEquals(99L, batch.getResolvedByMemberId());
    }

    @Test
    void fail_changesProcessingBatchToFailedAndAllowsRetry() {
        DepositPayoutBatch batch = pendingBatch();
        batch.startProcessing();

        batch.fail();

        assertTrue(batch.isFailed());

        batch.startProcessing();

        assertTrue(batch.isProcessing());
    }

    @Test
    void complete_throwsExceptionWhenAmountsDoNotMatch() {
        DepositPayoutBatch batch = pendingBatch();
        batch.startProcessing();

        assertThrows(
            IllegalArgumentException.class,
            () -> batch.complete(
                BigDecimal.valueOf(9_000),
                BigDecimal.valueOf(10_000),
                BigDecimal.valueOf(10_000),
                null,
                RESOLVED_AT
            )
        );
    }

    @Test
    void complete_throwsExceptionWhenDistributedAmountDoesNotMatchNoShowAmount() {
        DepositPayoutBatch batch = pendingBatch();
        batch.startProcessing();

        assertThrows(
            IllegalArgumentException.class,
            () -> batch.complete(
                BigDecimal.valueOf(10_000),
                BigDecimal.valueOf(10_000),
                BigDecimal.valueOf(9_000),
                null,
                RESOLVED_AT
            )
        );
    }

    @Test
    void complete_throwsExceptionWhenBatchIsPending() {
        DepositPayoutBatch batch = pendingBatch();

        assertThrows(
            IllegalStateException.class,
            () -> batch.complete(
                BigDecimal.valueOf(20_000),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null,
                RESOLVED_AT
            )
        );
    }

    @Test
    void cancelledBatch_rejectsNoShowAmounts() {
        DepositPayoutBatch batch = DepositPayoutBatch.pending(
            1L,
            ResolutionReason.APPOINTMENT_CANCELLED,
            BigDecimal.valueOf(20_000),
            SNAPSHOT_AT,
            "appointment-1-cancelled"
        );
        batch.startProcessing();

        assertThrows(
            IllegalArgumentException.class,
            () -> batch.complete(
                BigDecimal.valueOf(10_000),
                BigDecimal.valueOf(10_000),
                BigDecimal.valueOf(10_000),
                null,
                RESOLVED_AT
            )
        );
    }

    private DepositPayoutBatch pendingBatch() {
        return DepositPayoutBatch.pending(
            1L,
            ResolutionReason.APPOINTMENT_COMPLETED,
            BigDecimal.valueOf(20_000),
            SNAPSHOT_AT,
            "appointment-1-completed"
        );
    }
}
