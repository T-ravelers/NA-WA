package me.nawa.deposit.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class DepositPayoutTest {

    @Test
    void selfRefund_createsPayoutWithAttendedSnapshots() {
        DepositPayout payout = DepositPayout.selfRefund(
            10L,
            20L,
            20L,
            30L,
            40L,
            BigDecimal.valueOf(10_000)
        );

        assertEquals(AllocationType.SELF_REFUND, payout.getAllocationType());
        assertEquals(
            AttendanceStatus.ATTENDED,
            payout.getSourceAttendanceStatusSnapshot()
        );
        assertEquals(
            AttendanceStatus.ATTENDED,
            payout.getRecipientAttendanceStatusSnapshot()
        );
        assertTrue(payout.isSelfRefund());
        assertEquals(
            BigDecimal.valueOf(10_000),
            payout.getAmount()
        );
    }

    @Test
    void noShowShare_createsPayoutWithNoShowAndAttendedSnapshots() {
        DepositPayout payout = DepositPayout.noShowShare(
            10L,
            20L,
            21L,
            30L,
            40L,
            BigDecimal.valueOf(5_000)
        );

        assertEquals(
            AllocationType.NO_SHOW_SHARE,
            payout.getAllocationType()
        );
        assertEquals(
            AttendanceStatus.NO_SHOW,
            payout.getSourceAttendanceStatusSnapshot()
        );
        assertEquals(
            AttendanceStatus.ATTENDED,
            payout.getRecipientAttendanceStatusSnapshot()
        );
        assertTrue(payout.isNoShowShare());
    }

    @Test
    void cancellationRefund_createsPayoutForSameMember() {
        DepositPayout payout = DepositPayout.cancellationRefund(
            10L,
            20L,
            20L,
            30L,
            40L,
            AttendanceStatus.PENDING,
            AttendanceStatus.PENDING,
            BigDecimal.valueOf(10_000)
        );

        assertEquals(
            AllocationType.CANCELLATION_REFUND,
            payout.getAllocationType()
        );
        assertEquals(
            AttendanceStatus.PENDING,
            payout.getSourceAttendanceStatusSnapshot()
        );
        assertTrue(payout.isCancellationRefund());
    }

    @Test
    void selfRefund_throwsExceptionWhenMembersAreDifferent() {
        assertThrows(
            IllegalArgumentException.class,
            () -> DepositPayout.selfRefund(
                10L,
                20L,
                21L,
                30L,
                40L,
                BigDecimal.valueOf(10_000)
            )
        );
    }

    @Test
    void noShowShare_throwsExceptionWhenMembersAreSame() {
        assertThrows(
            IllegalArgumentException.class,
            () -> DepositPayout.noShowShare(
                10L,
                20L,
                20L,
                30L,
                40L,
                BigDecimal.valueOf(5_000)
            )
        );
    }

    @Test
    void cancellationRefund_throwsExceptionWhenMembersAreDifferent() {
        assertThrows(
            IllegalArgumentException.class,
            () -> DepositPayout.cancellationRefund(
                10L,
                20L,
                21L,
                30L,
                40L,
                AttendanceStatus.PENDING,
                AttendanceStatus.PENDING,
                BigDecimal.valueOf(10_000)
            )
        );
    }

    @Test
    void payout_throwsExceptionWhenAmountIsNotPositiveInteger() {
        assertThrows(
            IllegalArgumentException.class,
            () -> DepositPayout.selfRefund(
                10L,
                20L,
                20L,
                30L,
                40L,
                BigDecimal.ZERO
            )
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> DepositPayout.selfRefund(
                10L,
                20L,
                20L,
                30L,
                40L,
                BigDecimal.valueOf(1_000.5)
            )
        );
    }
}
