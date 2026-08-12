package me.nawa.appointment.service;

import me.nawa.appointment.domain.Appointment;
import me.nawa.appointment.domain.AppointmentMember;
import me.nawa.appointment.domain.AppointmentStatus;
import me.nawa.appointment.domain.MembershipStatus;
import me.nawa.appointment.dto.request.AppointmentCreateRequest;
import me.nawa.appointment.mapper.AppointmentMapper;
import me.nawa.common.exception.BusinessException;
import me.nawa.deposit.domain.Deposit;
import me.nawa.deposit.domain.DepositStatus;
import me.nawa.deposit.mapper.DepositMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentServiceTest {
    @Mock
    private AppointmentMapper appointmentMapper;
    @Mock
    private DepositMapper depositMapper;
    @InjectMocks
    private AppointmentService appointmentService;

    @Test
    void createAppointment_createsPendingHostAndDeposit() {
        AppointmentCreateRequest request = validRequest();
        when(appointmentMapper.findAvailableItemType(100L))
                .thenReturn("EVENT");
        doAnswer(invocation -> {
            Appointment appointment = invocation.getArgument(0);
            appointment.setAppointmentId(10L);
            return 1;
        }).when(appointmentMapper).insertAppointment(any(Appointment.class));
        doAnswer(invocation -> {
            AppointmentMember member = invocation.getArgument(0);
            member.setAppointmentMemberId(20L);
            return 1;
        }).when(appointmentMapper).insertAppointmentMember(
                any(AppointmentMember.class)
        );
        when(depositMapper.insert(any(Deposit.class))).thenReturn(1);

        Appointment result = appointmentService.createAppointment(1L, request);

        assertEquals(10L, result.getAppointmentId());
        assertEquals(AppointmentStatus.PAYMENT_PENDING,
                result.getAppointmentStatus());
        assertEquals(0, result.getCurrentMemberCount());

        ArgumentCaptor<AppointmentMember> memberCaptor =
                ArgumentCaptor.forClass(AppointmentMember.class);
        verify(appointmentMapper).insertAppointmentMember(
                memberCaptor.capture()
        );
        assertEquals(MembershipStatus.PENDING,
                memberCaptor.getValue().getMembershipStatus());

        ArgumentCaptor<Deposit> depositCaptor =
                ArgumentCaptor.forClass(Deposit.class);
        verify(depositMapper).insert(depositCaptor.capture());
        assertEquals(DepositStatus.PENDING,
                depositCaptor.getValue().getDepositStatus());
        assertEquals(BigDecimal.valueOf(10_000),
                depositCaptor.getValue().getAmount());
    }

    @Test
    void createAppointment_mismatchedItemType_rejectsRequest() {
        AppointmentCreateRequest request = validRequest();
        when(appointmentMapper.findAvailableItemType(100L))
                .thenReturn("PLACE");

        assertThrows(
                BusinessException.class,
                () -> appointmentService.createAppointment(1L, request)
        );
        verify(appointmentMapper, never()).insertAppointment(any());
    }

    @Test
    void createAppointment_invalidDeposit_rejectsRequest() {
        AppointmentCreateRequest request = validRequest();
        request.setDepositAmount(BigDecimal.valueOf(4_999));

        assertThrows(
                BusinessException.class,
                () -> appointmentService.createAppointment(1L, request)
        );
        verify(appointmentMapper, never()).findAvailableItemType(any());
    }

    private static AppointmentCreateRequest validRequest() {
        AppointmentCreateRequest request = new AppointmentCreateRequest();
        request.setItemId(100L);
        request.setItemType("EVENT");
        request.setLanguageCode("en");
        request.setAppointmentName("Seongsu K-Beauty Tour");
        request.setMaxMembers(5);
        request.setDepositAmount(BigDecimal.valueOf(10_000));
        request.setMeetingPlace("Olive Young N Seongsu");
        request.setMeetingAddress("Seongdong-gu, Seoul");
        request.setJoinDeadline(LocalDateTime.of(2026, 8, 20, 18, 0));
        request.setActivityStartAt(LocalDateTime.of(2026, 8, 21, 18, 30));
        request.setActivityEndAt(LocalDateTime.of(2026, 8, 21, 22, 0));
        return request;
    }
}
