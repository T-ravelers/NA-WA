package me.nawa.appointment.service;

import me.nawa.appointment.domain.Appointment;
import me.nawa.appointment.domain.AppointmentMember;
import me.nawa.appointment.domain.AppointmentStatus;
import me.nawa.appointment.domain.MembershipStatus;
import me.nawa.appointment.dto.request.AppointmentCreateRequest;
import me.nawa.appointment.dto.request.AppointmentAttendanceRequest;
import me.nawa.appointment.dto.request.AppointmentSearchRequest;
import me.nawa.appointment.dto.response.AppointmentDetailResponse;
import me.nawa.appointment.dto.response.AppointmentListResponse;
import me.nawa.appointment.dto.response.AppointmentMemberResponse;
import me.nawa.appointment.exception.AppointmentErrorCode;
import me.nawa.appointment.mapper.AppointmentMapper;
import me.nawa.common.exception.BusinessException;
import me.nawa.deposit.domain.Deposit;
import me.nawa.deposit.mapper.DepositMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
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
    void createAppointment_requiresPaymentIntegrationWithoutPersistingState() {
        AppointmentCreateRequest request = validRequest();
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> appointmentService.createAppointment(1L, request)
        );

        assertEquals(AppointmentErrorCode.PAYMENT_INTEGRATION_REQUIRED,
                exception.getErrorCode());
        verify(appointmentMapper, never()).insertAppointment(any());
        verify(depositMapper, never()).insert(any());
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

    @Test
    void createAppointment_missingItemType_rejectsRequest() {
        AppointmentCreateRequest request = validRequest();
        request.setItemType(null);

        assertThrows(
                BusinessException.class,
                () -> appointmentService.createAppointment(1L, request)
        );
        verify(appointmentMapper, never()).findAvailableItemType(any());
    }

    @Test
    void searchAppointments_returnsPagedSummaries() {
        AppointmentSearchRequest request = new AppointmentSearchRequest();
        request.setPage(1);
        request.setSize(2);
        Appointment appointment = appointment(10L, AppointmentStatus.RECRUITING);
        when(appointmentMapper.searchAppointments(request, 2))
                .thenReturn(List.of(appointment));
        when(appointmentMapper.countAppointments(request)).thenReturn(5L);

        AppointmentListResponse result =
                appointmentService.searchAppointments(request);

        assertEquals(1, result.getContent().size());
        assertEquals(10L, result.getContent().get(0).getAppointmentId());
        assertEquals(3, result.getTotalPages());
        assertEquals(true, result.isHasNext());
    }

    @Test
    void getAppointment_returnsActiveMembers() {
        Appointment appointment = appointment(10L, AppointmentStatus.RECRUITING);
        AppointmentMember host = AppointmentMember.builder()
                .appointmentMemberId(20L)
                .appointmentId(10L)
                .memberId(1L)
                .displayName("Host")
                .membershipStatus(MembershipStatus.ACTIVE)
                .host(true)
                .build();
        when(appointmentMapper.findAppointmentById(10L))
                .thenReturn(appointment);
        when(appointmentMapper.findActiveMembersByAppointmentId(10L))
                .thenReturn(List.of(host));

        AppointmentDetailResponse result =
                appointmentService.getAppointment(2L, 10L);

        assertEquals(10L, result.getAppointmentId());
        assertEquals(1, result.getMembers().size());
        assertEquals(true, result.getMembers().get(0).isHost());
    }

    @Test
    void getAppointment_paymentPendingForNonHost_returnsNotFound() {
        Appointment appointment = appointment(
                10L,
                AppointmentStatus.PAYMENT_PENDING
        );
        when(appointmentMapper.findAppointmentById(10L))
                .thenReturn(appointment);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> appointmentService.getAppointment(2L, 10L)
        );

        assertEquals(AppointmentErrorCode.APPOINTMENT_NOT_FOUND,
                exception.getErrorCode());
        verify(appointmentMapper, never())
                .findActiveMembersByAppointmentId(any());
    }

    @Test
    void joinAppointment_requiresPaymentIntegrationWithoutPersistingState() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> appointmentService.joinAppointment(2L, 10L)
        );

        assertEquals(AppointmentErrorCode.PAYMENT_INTEGRATION_REQUIRED,
                exception.getErrorCode());
        verify(appointmentMapper, never()).insertAppointmentMember(any());
        verify(depositMapper, never()).insert(any());
    }

    @Test
    void leaveAppointment_pendingMember_cancelsDepositAndLeaves() {
        Appointment appointment = appointment(
                10L,
                AppointmentStatus.RECRUITING
        );
        AppointmentMember member = AppointmentMember.builder()
                .appointmentMemberId(30L)
                .appointmentId(10L)
                .memberId(2L)
                .membershipStatus(MembershipStatus.PENDING)
                .build();
        Deposit deposit = mock(Deposit.class);
        when(deposit.getDepositId()).thenReturn(40L);
        when(deposit.isPending()).thenReturn(true);
        when(appointmentMapper.findAppointmentByIdForUpdate(10L))
                .thenReturn(appointment);
        when(appointmentMapper.findMemberByAppointmentAndMemberForUpdate(
                10L,
                2L
        )).thenReturn(member);
        when(depositMapper.findByAppointmentMemberId(30L))
                .thenReturn(deposit);
        when(depositMapper.markCancelled(eq(40L), any()))
                .thenReturn(1);
        when(appointmentMapper.markMemberLeft(30L)).thenReturn(1);

        appointmentService.leaveAppointment(2L, 10L);

        verify(depositMapper).markCancelled(eq(40L), any());
        verify(appointmentMapper).markMemberLeft(30L);
    }

    @Test
    void leaveAppointment_pendingHost_transfersHostAndLeaves() {
        Appointment appointment = appointment(
                10L,
                AppointmentStatus.PAYMENT_PENDING
        );
        AppointmentMember host = AppointmentMember.builder()
                .appointmentMemberId(20L)
                .appointmentId(10L)
                .memberId(1L)
                .membershipStatus(MembershipStatus.PENDING)
                .build();
        AppointmentMember successor = AppointmentMember.builder()
                .appointmentMemberId(30L)
                .appointmentId(10L)
                .memberId(2L)
                .membershipStatus(MembershipStatus.PENDING)
                .build();
        Deposit deposit = mock(Deposit.class);
        when(deposit.getDepositId()).thenReturn(40L);
        when(deposit.isPending()).thenReturn(true);
        when(appointmentMapper.findAppointmentByIdForUpdate(10L))
                .thenReturn(appointment);
        when(appointmentMapper.findMemberByAppointmentAndMemberForUpdate(
                10L, 1L
        )).thenReturn(host);
        when(appointmentMapper.findHostSuccessorForUpdate(10L, 1L))
                .thenReturn(successor);
        when(appointmentMapper.updateHostMember(10L, 1L, 2L))
                .thenReturn(1);
        when(depositMapper.findByAppointmentMemberId(20L))
                .thenReturn(deposit);
        when(depositMapper.markCancelled(eq(40L), any())).thenReturn(1);
        when(appointmentMapper.markMemberLeft(20L)).thenReturn(1);

        appointmentService.leaveAppointment(1L, 10L);

        verify(appointmentMapper).updateHostMember(10L, 1L, 2L);
        verify(appointmentMapper).markMemberLeft(20L);
    }

    @Test
    void leaveAppointment_hostOnly_rejectsCancellation() {
        Appointment appointment = appointment(
                10L,
                AppointmentStatus.PAYMENT_PENDING
        );
        AppointmentMember host = AppointmentMember.builder()
                .appointmentMemberId(20L)
                .appointmentId(10L)
                .memberId(1L)
                .membershipStatus(MembershipStatus.PENDING)
                .build();
        when(appointmentMapper.findAppointmentByIdForUpdate(10L))
                .thenReturn(appointment);
        when(appointmentMapper.findMemberByAppointmentAndMemberForUpdate(
                10L, 1L
        )).thenReturn(host);
        when(appointmentMapper.findHostSuccessorForUpdate(10L, 1L))
                .thenReturn(null);

        assertThrows(
                BusinessException.class,
                () -> appointmentService.leaveAppointment(1L, 10L)
        );

        verify(appointmentMapper, never()).markMemberLeft(any());
    }

    @Test
    void leaveAppointment_activeMember_requiresRefundIntegration() {
        Appointment appointment = appointment(
                10L,
                AppointmentStatus.RECRUITING
        );
        AppointmentMember member = AppointmentMember.builder()
                .appointmentMemberId(30L)
                .appointmentId(10L)
                .memberId(2L)
                .membershipStatus(MembershipStatus.ACTIVE)
                .build();
        when(appointmentMapper.findAppointmentByIdForUpdate(10L))
                .thenReturn(appointment);
        when(appointmentMapper.findMemberByAppointmentAndMemberForUpdate(
                10L, 2L
        )).thenReturn(member);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> appointmentService.leaveAppointment(2L, 10L)
        );

        assertEquals(AppointmentErrorCode.CANCELLATION_NOT_AVAILABLE,
                exception.getErrorCode());
        verify(depositMapper, never()).findByAppointmentMemberId(any());
        verify(appointmentMapper, never()).markMemberLeft(any());
    }

    @Test
    void leaveAppointment_activeHost_requiresRefundIntegration() {
        Appointment appointment = appointment(
                10L,
                AppointmentStatus.RECRUITING
        );
        AppointmentMember host = AppointmentMember.builder()
                .appointmentMemberId(20L)
                .appointmentId(10L)
                .memberId(1L)
                .membershipStatus(MembershipStatus.ACTIVE)
                .build();
        when(appointmentMapper.findAppointmentByIdForUpdate(10L))
                .thenReturn(appointment);
        when(appointmentMapper.findMemberByAppointmentAndMemberForUpdate(
                10L, 1L
        )).thenReturn(host);
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> appointmentService.leaveAppointment(1L, 10L)
        );

        assertEquals(AppointmentErrorCode.CANCELLATION_NOT_AVAILABLE,
                exception.getErrorCode());
        verify(appointmentMapper, never()).updateHostMember(any(), any(), any());
    }

    @Test
    void getMyParticipation_withoutHistory_returnsNotJoined() {
        when(appointmentMapper.findAppointmentById(10L))
                .thenReturn(appointment(10L, AppointmentStatus.RECRUITING));
        when(appointmentMapper.findMemberByAppointmentAndMember(10L, 2L))
                .thenReturn(null);

        assertEquals(false, appointmentService
                .getMyParticipation(2L, 10L)
                .isJoined());
    }

    @Test
    void confirmAttendance_requiresPaymentIntegration() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> appointmentService.confirmAttendance(
                        1L, 10L, attendanceRequest()
                )
        );

        assertEquals(AppointmentErrorCode.PAYMENT_INTEGRATION_REQUIRED,
                exception.getErrorCode());
        verify(appointmentMapper, never()).confirmAttendance(
                any(), any(), any()
        );
    }

    @Test
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

    private static Appointment appointment(
            Long appointmentId,
            AppointmentStatus status) {
        return Appointment.builder()
                .appointmentId(appointmentId)
                .itemId(100L)
                .itemType("EVENT")
                .hostMemberId(1L)
                .hostDisplayName("Host")
                .languageCode("en")
                .appointmentName("Seongsu K-Beauty Tour")
                .maxMembers(5)
                .currentMemberCount(1)
                .depositAmount(BigDecimal.valueOf(10_000))
                .appointmentStatus(status)
                .meetingPlace("Olive Young N Seongsu")
                .activityStartAt(LocalDateTime.of(2026, 8, 21, 18, 30))
                .activityEndAt(LocalDateTime.of(2026, 8, 21, 22, 0))
                .joinDeadline(LocalDateTime.of(2026, 8, 20, 18, 0))
                .build();
    }

    private static AppointmentAttendanceRequest attendanceRequest() {
        AppointmentAttendanceRequest.MemberAttendance host =
                new AppointmentAttendanceRequest.MemberAttendance();
        host.setMemberId(1L);
        host.setAttendanceStatus(
                me.nawa.deposit.domain.AttendanceStatus.ATTENDED
        );
        AppointmentAttendanceRequest.MemberAttendance guest =
                new AppointmentAttendanceRequest.MemberAttendance();
        guest.setMemberId(2L);
        guest.setAttendanceStatus(
                me.nawa.deposit.domain.AttendanceStatus.NO_SHOW
        );
        AppointmentAttendanceRequest request =
                new AppointmentAttendanceRequest();
        request.setMembers(List.of(host, guest));
        return request;
    }
}
