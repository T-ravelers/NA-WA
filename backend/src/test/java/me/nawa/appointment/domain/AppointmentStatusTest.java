package me.nawa.appointment.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppointmentStatusTest {

    @Test
    void canTransitionTo_paymentCompleted_allowsRecruiting() {
        assertTrue(AppointmentStatus.PAYMENT_PENDING.canTransitionTo(
                AppointmentStatus.RECRUITING
        ));
    }

    @Test
    void canTransitionTo_attendanceConfirmed_allowsCompleted() {
        assertTrue(AppointmentStatus.IN_PROGRESS.canTransitionTo(
                AppointmentStatus.COMPLETED
        ));
    }

    @Test
    void canTransitionTo_terminalStatus_rejectsTransition() {
        assertFalse(AppointmentStatus.COMPLETED.canTransitionTo(
                AppointmentStatus.RECRUITING
        ));
        assertFalse(AppointmentStatus.CANCELLED.canTransitionTo(
                AppointmentStatus.RECRUITING
        ));
    }
}
