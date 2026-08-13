package me.nawa.appointment.service;

import me.nawa.appointment.domain.Appointment;
import me.nawa.appointment.domain.AppointmentStatus;
import me.nawa.appointment.mapper.AppointmentMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentLifecycleServiceTest {
    @Mock
    private AppointmentMapper appointmentMapper;
    @InjectMocks
    private AppointmentLifecycleService lifecycleService;

    @Test
    void recruitingAtDeadline_closesAndCancelsUnpaidMembers() {
        Appointment appointment = appointment(AppointmentStatus.RECRUITING);
        appointment.setJoinDeadline(LocalDateTime.now().minusMinutes(1));
        when(appointmentMapper.findAppointmentByIdForUpdate(10L))
                .thenReturn(appointment);
        when(appointmentMapper.updateStatus(10L, "RECRUITING", "CLOSED"))
                .thenReturn(1);
        when(appointmentMapper.countActiveMembers(10L)).thenReturn(1);
        when(appointmentMapper.countHeldDepositsForActiveMembers(10L))
                .thenReturn(0);

        assertEquals(
                AppointmentStatus.CLOSED,
                lifecycleService.advanceAppointment(10L)
        );
        verify(appointmentMapper).markPendingDepositsCancelled(eq(10L), any());
        verify(appointmentMapper).markPendingMembersLeft(10L);
    }

    @Test
    void closedWithAllActiveDepositsHeld_becomesConfirmed() {
        Appointment appointment = appointment(AppointmentStatus.CLOSED);
        when(appointmentMapper.findAppointmentByIdForUpdate(10L))
                .thenReturn(appointment);
        when(appointmentMapper.countActiveMembers(10L)).thenReturn(2);
        when(appointmentMapper.countHeldDepositsForActiveMembers(10L))
                .thenReturn(2);
        when(appointmentMapper.updateStatus(10L, "CLOSED", "CONFIRMED"))
                .thenReturn(1);

        assertEquals(
                AppointmentStatus.CONFIRMED,
                lifecycleService.advanceAppointment(10L)
        );
    }

    @Test
    void confirmedAtActivityStart_becomesInProgress() {
        Appointment appointment = appointment(AppointmentStatus.CONFIRMED);
        appointment.setActivityStartAt(LocalDateTime.now().minusSeconds(1));
        when(appointmentMapper.findAppointmentByIdForUpdate(10L))
                .thenReturn(appointment);
        when(appointmentMapper.updateStatus(10L, "CONFIRMED", "IN_PROGRESS"))
                .thenReturn(1);

        assertEquals(
                AppointmentStatus.IN_PROGRESS,
                lifecycleService.advanceAppointment(10L)
        );
    }

    private static Appointment appointment(AppointmentStatus status) {
        return Appointment.builder()
                .appointmentId(10L)
                .appointmentStatus(status)
                .maxMembers(5)
                .joinDeadline(LocalDateTime.now().plusDays(1))
                .activityStartAt(LocalDateTime.now().plusDays(2))
                .build();
    }
}
