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
    void canTransitionTo_activityStarted_allowsInProgressFromFull() {
        assertTrue(AppointmentStatus.FULL.canTransitionTo(
                AppointmentStatus.IN_PROGRESS
        ));
    }

    // 정원이 차지 않은 약속은 FULL을 거치지 않고 활동 시작 시각을 맞는다.
    // 이 경로가 없으면 그런 약속이 활동 시작 뒤에도 모집 중으로 남는다.
    @Test
    void canTransitionTo_activityStarted_allowsInProgressFromRecruiting() {
        assertTrue(AppointmentStatus.RECRUITING.canTransitionTo(
                AppointmentStatus.IN_PROGRESS
        ));
    }

    @Test
    void canTransitionTo_capacityReached_allowsFull() {
        assertTrue(AppointmentStatus.RECRUITING.canTransitionTo(
                AppointmentStatus.FULL
        ));
    }

    // 정원이 찼다가 빈자리가 생기면 다시 모집으로 돌아간다.
    @Test
    void canTransitionTo_seatFreed_allowsRecruitingFromFull() {
        assertTrue(AppointmentStatus.FULL.canTransitionTo(
                AppointmentStatus.RECRUITING
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
