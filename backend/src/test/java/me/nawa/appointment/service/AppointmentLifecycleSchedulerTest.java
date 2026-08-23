package me.nawa.appointment.service;

import me.nawa.appointment.mapper.AppointmentMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;

@ExtendWith(MockitoExtension.class)
class AppointmentLifecycleSchedulerTest {

    @Mock
    private AppointmentMapper appointmentMapper;

    @InjectMocks
    private AppointmentLifecycleScheduler scheduler;

    // 시작을 먼저 반영해야 스케줄러가 오래 멈춰 있던 사이에 활동이 통째로 지나간
    // 약속이 한 주기에 종료까지 따라온다. 호출 여부만 보면 advanceLifecycle()의
    // 두 줄을 뒤집어도 통과하므로 순서까지 확인한다.
    @Test
    void advanceLifecycle_startsThenEndsDueAppointments() {
        scheduler.advanceLifecycle();

        InOrder inOrder = inOrder(appointmentMapper);
        inOrder.verify(appointmentMapper).startDueAppointments(any());
        inOrder.verify(appointmentMapper).endDueAppointments(any());
    }
}
