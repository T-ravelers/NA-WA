package me.nawa.deposit.domain;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class DepositEnumTest {

    @Test
    void allocationType_containsMvpPayoutTypes() throws Exception {
        Class<?> allocationType = Class.forName(
            "me.nawa.deposit.domain.AllocationType"
        );

        assertNotNull(allocationType);
        assertTrue(allocationType.isEnum());
        assertArrayEquals(
            new String[]{
                "SELF_REFUND",
                "NO_SHOW_SHARE",
                "CANCELLATION_REFUND"
            },
            Arrays.stream(allocationType.getEnumConstants())
                .map(value -> ((Enum<?>) value).name())
                .toArray(String[]::new)
        );
    }

    @Test
    void attendanceStatus_containsPayoutSnapshotTypes() throws Exception {
        Class<?> attendanceStatus = Class.forName(
            "me.nawa.deposit.domain.AttendanceStatus"
        );

        assertNotNull(attendanceStatus);
        assertTrue(attendanceStatus.isEnum());
        assertArrayEquals(
            new String[]{"PENDING", "ATTENDED", "NO_SHOW"},
            Arrays.stream(attendanceStatus.getEnumConstants())
                .map(value -> ((Enum<?>) value).name())
                .toArray(String[]::new)
        );
    }
}
