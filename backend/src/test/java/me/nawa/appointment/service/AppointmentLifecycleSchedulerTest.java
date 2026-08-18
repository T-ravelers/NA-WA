package me.nawa.appointment.service;

import me.nawa.appointment.mapper.AppointmentMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AppointmentLifecycleSchedulerTest {

    @Mock
    private AppointmentMapper appointmentMapper;

    @InjectMocks
    private AppointmentLifecycleScheduler scheduler;

    @Test
    void advanceLifecycle_closesExpiredRecruitingThenStartsDueClosed() {
        scheduler.advanceLifecycle();

        verify(appointmentMapper).closeExpiredRecruitingAppointments();
        verify(appointmentMapper).startDueClosedAppointments();
    }
}
