package me.nawa.appointment.service;

import me.nawa.appointment.domain.Appointment;
import me.nawa.appointment.domain.AppointmentMember;
import me.nawa.appointment.domain.AppointmentStatus;
import me.nawa.appointment.domain.MembershipStatus;
import me.nawa.appointment.domain.MyOngoingAppointment;
import me.nawa.appointment.dto.request.AppointmentCreateRequest;
import me.nawa.appointment.dto.request.AppointmentAttendanceRequest;
import me.nawa.appointment.dto.request.AppointmentSearchRequest;
import me.nawa.appointment.dto.response.AppointmentDetailResponse;
import me.nawa.appointment.dto.response.AppointmentListResponse;
import me.nawa.appointment.dto.response.AppointmentMemberResponse;
import me.nawa.appointment.dto.response.MyOngoingAppointmentResponse;
import me.nawa.appointment.exception.AppointmentErrorCode;
import me.nawa.appointment.mapper.AppointmentMapper;
import me.nawa.common.exception.BusinessException;
import me.nawa.common.exception.CommonErrorCode;
import me.nawa.deposit.domain.AttendanceStatus;
import me.nawa.deposit.domain.Deposit;
import me.nawa.deposit.domain.DepositPayoutBatch;
import me.nawa.deposit.domain.ResolutionReason;
import me.nawa.deposit.mapper.DepositMapper;
import me.nawa.deposit.mapper.DepositPayoutBatchMapper;
import me.nawa.journey.domain.Journey;
import me.nawa.journey.exception.JourneyErrorCode;
import me.nawa.journey.mapper.JourneyMapper;
import me.nawa.wallet.domain.SystemWalletCode;
import me.nawa.wallet.domain.enums.TransferType;
import me.nawa.wallet.service.WalletTransferService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentServiceTest {
    // 절대 날짜로 고정하면 그 시점이 지나는 순간 activityStartAt이 과거가 되어
    // validRequest()를 쓰는 테스트가 전부 깨진다. 실행 시점 기준 상대 날짜로 둔다.
    private static final LocalDate VISIT_DATE = LocalDate.now().plusDays(7);
    private static final LocalDate JOURNEY_START_DATE = LocalDate.now();
    private static final LocalDate JOURNEY_END_DATE = VISIT_DATE.plusDays(30);

    @Mock
    private AppointmentMapper appointmentMapper;
    @Mock
    private DepositMapper depositMapper;
    @Mock
    private DepositPayoutBatchMapper depositPayoutBatchMapper;
    @Mock
    private WalletTransferService walletTransferService;
    @Mock
    private JourneyMapper journeyMapper;
    @InjectMocks
    private AppointmentService appointmentService;

    @Test
    void createAppointment_success_holdsHostDepositAndBecomesRecruiting() {
        AppointmentCreateRequest request = validRequest();
        when(appointmentMapper.findAvailableItemType(100L)).thenReturn("EVENT");
        when(journeyMapper.findJourneyByIdForUpdate(1L)).thenReturn(
                Journey.builder()
                        .tripId(1L)
                        .memberId(1L)
                        .startDate(JOURNEY_START_DATE)
                        .endDate(JOURNEY_END_DATE)
                        .build()
        );
        stubInsertAppointment(10L);
        stubInsertAppointmentMember(20L);
        when(depositMapper.insert(any())).thenReturn(1);
        when(walletTransferService.transferToSystemWallet(
                eq(1L), eq(1L), eq(SystemWalletCode.DEPOSIT_POOL),
                eq(BigDecimal.valueOf(10_000)),
                eq(TransferType.DEPOSIT_HOLD.name()), anyString()
        )).thenReturn(500L);
        when(depositMapper.markHeld(any(), eq(500L), any())).thenReturn(1);
        when(appointmentMapper.markMemberActive(20L)).thenReturn(1);
        when(appointmentMapper.updateAppointmentStatus(
                10L, AppointmentStatus.PAYMENT_PENDING, AppointmentStatus.RECRUITING
        )).thenReturn(1);

        Appointment result = appointmentService.createAppointment(1L, request);

        assertEquals(10L, result.getAppointmentId());
        assertEquals(AppointmentStatus.RECRUITING, result.getAppointmentStatus());
        assertEquals(1, result.getCurrentMemberCount());
        verify(depositMapper).markHeld(any(), eq(500L), any());
        verify(appointmentMapper).markMemberActive(20L);
    }

    @Test
    void toCreatedResponse_includesActiveMembers() {
        Appointment appointment = appointment(10L, AppointmentStatus.RECRUITING);
        AppointmentMember host = AppointmentMember.builder()
                .appointmentMemberId(20L)
                .memberId(1L)
                .membershipStatus(MembershipStatus.ACTIVE)
                .host(true)
                .build();
        when(appointmentMapper.findActiveMembersByAppointmentId(10L))
                .thenReturn(List.of(host));

        AppointmentDetailResponse result =
                appointmentService.toCreatedResponse(appointment);

        assertEquals(1, result.getMembers().size());
        assertEquals(true, result.getMembers().get(0).isHost());
    }

    @Test
    void createAppointment_itemNotAvailable_rejectsRequest() {
        AppointmentCreateRequest request = validRequest();
        when(appointmentMapper.findAvailableItemType(100L)).thenReturn(null);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> appointmentService.createAppointment(1L, request)
        );

        assertEquals(CommonErrorCode.INVALID_INPUT, exception.getErrorCode());
        verify(appointmentMapper, never()).insertAppointment(any());
    }

    @Test
    void createAppointment_journeyNotFound_rejectsRequest() {
        AppointmentCreateRequest request = validRequest();
        when(appointmentMapper.findAvailableItemType(100L)).thenReturn("EVENT");
        when(journeyMapper.findJourneyByIdForUpdate(1L)).thenReturn(null);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> appointmentService.createAppointment(1L, request)
        );

        assertEquals(JourneyErrorCode.JOURNEY_NOT_FOUND, exception.getErrorCode());
        verify(appointmentMapper, never()).insertAppointment(any());
    }

    @Test
    void createAppointment_journeyNotOwned_rejectsRequest() {
        AppointmentCreateRequest request = validRequest();
        when(appointmentMapper.findAvailableItemType(100L)).thenReturn("EVENT");
        when(journeyMapper.findJourneyByIdForUpdate(1L)).thenReturn(
                Journey.builder()
                        .tripId(1L)
                        .memberId(2L)
                        .startDate(JOURNEY_START_DATE)
                        .endDate(JOURNEY_END_DATE)
                        .build()
        );

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> appointmentService.createAppointment(1L, request)
        );

        assertEquals(JourneyErrorCode.JOURNEY_FORBIDDEN, exception.getErrorCode());
        verify(appointmentMapper, never()).insertAppointment(any());
    }

    @Test
    void createAppointment_visitDateOutsideJourneyRange_rejectsRequest() {
        AppointmentCreateRequest request = validRequest();
        when(appointmentMapper.findAvailableItemType(100L)).thenReturn("EVENT");
        when(journeyMapper.findJourneyByIdForUpdate(1L)).thenReturn(
                Journey.builder()
                        .tripId(1L)
                        .memberId(1L)
                        .startDate(VISIT_DATE.plusDays(60))
                        .endDate(VISIT_DATE.plusDays(90))
                        .build()
        );

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> appointmentService.createAppointment(1L, request)
        );

        assertEquals(
                JourneyErrorCode.JOURNEY_ITEM_DATE_OUT_OF_RANGE,
                exception.getErrorCode()
        );
        verify(appointmentMapper, never()).insertAppointment(any());
    }

    @Test
    void createAppointment_journeyItemAlreadyExists_rejectsRequest() {
        AppointmentCreateRequest request = validRequest();
        when(appointmentMapper.findAvailableItemType(100L)).thenReturn("EVENT");
        when(journeyMapper.findJourneyByIdForUpdate(1L)).thenReturn(
                Journey.builder()
                        .tripId(1L)
                        .memberId(1L)
                        .startDate(JOURNEY_START_DATE)
                        .endDate(JOURNEY_END_DATE)
                        .build()
        );
        when(journeyMapper.existsJourneyItem(1L, 100L, VISIT_DATE))
                .thenReturn(true);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> appointmentService.createAppointment(1L, request)
        );

        assertEquals(JourneyErrorCode.JOURNEY_ITEM_DUPLICATE, exception.getErrorCode());
        verify(appointmentMapper, never()).insertAppointment(any());
    }

    @Test
    void createAppointment_journeyItemRaceCondition_rejectsRequestAfterInsert() {
        AppointmentCreateRequest request = validRequest();
        when(appointmentMapper.findAvailableItemType(100L)).thenReturn("EVENT");
        when(journeyMapper.findJourneyByIdForUpdate(1L)).thenReturn(
                Journey.builder()
                        .tripId(1L)
                        .memberId(1L)
                        .startDate(JOURNEY_START_DATE)
                        .endDate(JOURNEY_END_DATE)
                        .build()
        );
        stubInsertAppointment(10L);
        org.mockito.Mockito.doThrow(new DuplicateKeyException("duplicate"))
                .when(journeyMapper).insertConfirmedJourneyItem(any());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> appointmentService.createAppointment(1L, request)
        );

        assertEquals(JourneyErrorCode.JOURNEY_ITEM_DUPLICATE, exception.getErrorCode());
        verify(appointmentMapper, never()).insertAppointmentMember(any());
    }

    @Test
    void createAppointment_activityStartInPast_rejectsRequest() {
        AppointmentCreateRequest request = validRequest();
        request.setVisitDate(LocalDate.now());
        request.setActivityStartTime(LocalTime.now().minusMinutes(1));
        request.setActivityEndTime(LocalTime.now().plusHours(1));

        assertThrows(
                BusinessException.class,
                () -> appointmentService.createAppointment(1L, request)
        );
        verify(appointmentMapper, never()).findAvailableItemType(any());
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

    private void stubInsertAppointment(long appointmentId) {
        when(appointmentMapper.insertAppointment(any())).thenAnswer(invocation -> {
            Appointment appointment = invocation.getArgument(0);
            appointment.setAppointmentId(appointmentId);
            return 1;
        });
    }

    private void stubInsertAppointmentMember(long appointmentMemberId) {
        when(appointmentMapper.insertAppointmentMember(any())).thenAnswer(invocation -> {
            AppointmentMember member = invocation.getArgument(0);
            member.setAppointmentMemberId(appointmentMemberId);
            return 1;
        });
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
    void getAppointment_capacityReached_showsFullBeforeSchedulerCatchesUp() {
        Appointment appointment = appointment(10L, AppointmentStatus.RECRUITING);
        appointment.setCurrentMemberCount(appointment.getMaxMembers());
        when(appointmentMapper.findAppointmentById(10L))
                .thenReturn(appointment);
        when(appointmentMapper.findActiveMembersByAppointmentId(10L))
                .thenReturn(List.of());

        AppointmentDetailResponse result =
                appointmentService.getAppointment(2L, 10L);

        assertEquals(AppointmentStatus.FULL, result.getAppointmentStatus());
    }

    // 정원이 차지 않은 약속은 FULL을 거치지 않고 RECRUITING인 채로 활동 시작
    // 시각을 맞는다. 표시 계산이 이 경로를 빼면 활동이 시작돼도 모집 중으로 보인다.
    @Test
    void getAppointment_recruitingAfterActivityStart_showsInProgress() {
        Appointment appointment = appointment(10L, AppointmentStatus.RECRUITING);
        appointment.setActivityStartAt(LocalDateTime.now().minusMinutes(1));
        when(appointmentMapper.findAppointmentById(10L))
                .thenReturn(appointment);
        when(appointmentMapper.findActiveMembersByAppointmentId(10L))
                .thenReturn(List.of());

        AppointmentDetailResponse result =
                appointmentService.getAppointment(2L, 10L);

        assertEquals(AppointmentStatus.IN_PROGRESS, result.getAppointmentStatus());
    }

    @Test
    void getAppointment_afterActivityStart_showsInProgressBeforeSchedulerCatchesUp() {
        Appointment appointment = appointment(10L, AppointmentStatus.FULL);
        appointment.setActivityStartAt(LocalDateTime.now().minusMinutes(1));
        when(appointmentMapper.findAppointmentById(10L))
                .thenReturn(appointment);
        when(appointmentMapper.findActiveMembersByAppointmentId(10L))
                .thenReturn(List.of());

        AppointmentDetailResponse result =
                appointmentService.getAppointment(2L, 10L);

        assertEquals(AppointmentStatus.IN_PROGRESS, result.getAppointmentStatus());
    }

    @Test
    void getAppointment_afterActivityEnd_showsAwaitingAttendance() {
        Appointment appointment =
                endedAppointment(10L, AppointmentStatus.IN_PROGRESS);
        when(appointmentMapper.findAppointmentById(10L))
                .thenReturn(appointment);
        when(appointmentMapper.findActiveMembersByAppointmentId(10L))
                .thenReturn(List.of());

        AppointmentDetailResponse result =
                appointmentService.getAppointment(2L, 10L);

        assertEquals(AppointmentStatus.AWAITING_ATTENDANCE,
                result.getAppointmentStatus());
    }

    // 스케줄러가 FULL → IN_PROGRESS를 아직 못 따라잡은 채 활동이 끝났어도,
    // 표시 계산이 FULL → IN_PROGRESS → AWAITING_ATTENDANCE를 연쇄로 거쳐
    // 최종 표시 상태에 도달해야 한다.
    @Test
    void getAppointment_fullInDbAfterActivityEnd_showsAwaitingAttendance() {
        Appointment appointment =
                endedAppointment(10L, AppointmentStatus.FULL);
        when(appointmentMapper.findAppointmentById(10L))
                .thenReturn(appointment);
        when(appointmentMapper.findActiveMembersByAppointmentId(10L))
                .thenReturn(List.of());

        AppointmentDetailResponse result =
                appointmentService.getAppointment(2L, 10L);

        assertEquals(AppointmentStatus.AWAITING_ATTENDANCE,
                result.getAppointmentStatus());
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
    void joinAppointment_success_holdsDepositAndActivatesMember() {
        Appointment appointment = appointment(10L, AppointmentStatus.RECRUITING);
        when(appointmentMapper.findAppointmentByIdForUpdate(10L))
                .thenReturn(appointment);
        when(appointmentMapper.findMemberByAppointmentAndMemberForUpdate(10L, 2L))
                .thenReturn(null);
        stubInsertAppointmentMember(30L);
        when(depositMapper.insert(any())).thenReturn(1);
        when(walletTransferService.transferToSystemWallet(
                eq(2L), eq(2L), eq(SystemWalletCode.DEPOSIT_POOL),
                eq(BigDecimal.valueOf(10_000)),
                eq(TransferType.DEPOSIT_HOLD.name()), anyString()
        )).thenReturn(501L);
        when(depositMapper.markHeld(any(), eq(501L), any())).thenReturn(1);
        when(appointmentMapper.markMemberActive(30L)).thenReturn(1);
        AppointmentMember active = AppointmentMember.builder()
                .appointmentMemberId(30L)
                .appointmentId(10L)
                .memberId(2L)
                .membershipStatus(MembershipStatus.ACTIVE)
                .build();
        when(appointmentMapper.findMemberByIdForUpdate(10L, 30L))
                .thenReturn(active);

        AppointmentMemberResponse result =
                appointmentService.joinAppointment(2L, 10L);

        assertEquals(MembershipStatus.ACTIVE, result.getMembershipStatus());
        verify(appointmentMapper).markMemberActive(30L);
    }

    @Test
    void joinAppointment_fillsLastSlot_closesRecruitingSynchronously() {
        Appointment appointment = appointment(10L, AppointmentStatus.RECRUITING);
        appointment.setCurrentMemberCount(appointment.getMaxMembers() - 1);
        when(appointmentMapper.findAppointmentByIdForUpdate(10L))
                .thenReturn(appointment);
        when(appointmentMapper.findMemberByAppointmentAndMemberForUpdate(10L, 2L))
                .thenReturn(null);
        stubInsertAppointmentMember(30L);
        when(depositMapper.insert(any())).thenReturn(1);
        when(walletTransferService.transferToSystemWallet(
                eq(2L), eq(2L), eq(SystemWalletCode.DEPOSIT_POOL),
                eq(BigDecimal.valueOf(10_000)),
                eq(TransferType.DEPOSIT_HOLD.name()), anyString()
        )).thenReturn(501L);
        when(depositMapper.markHeld(any(), eq(501L), any())).thenReturn(1);
        when(appointmentMapper.markMemberActive(30L)).thenReturn(1);
        when(appointmentMapper.updateAppointmentStatus(
                10L, AppointmentStatus.RECRUITING, AppointmentStatus.FULL
        )).thenReturn(1);
        when(appointmentMapper.findMemberByIdForUpdate(10L, 30L))
                .thenReturn(AppointmentMember.builder()
                        .appointmentMemberId(30L)
                        .appointmentId(10L)
                        .memberId(2L)
                        .membershipStatus(MembershipStatus.ACTIVE)
                        .build());

        appointmentService.joinAppointment(2L, 10L);

        verify(appointmentMapper).updateAppointmentStatus(
                10L, AppointmentStatus.RECRUITING, AppointmentStatus.FULL
        );
    }

    @Test
    void joinAppointment_notRecruiting_rejectsJoin() {
        Appointment appointment = appointment(10L, AppointmentStatus.FULL);
        when(appointmentMapper.findAppointmentByIdForUpdate(10L))
                .thenReturn(appointment);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> appointmentService.joinAppointment(2L, 10L)
        );

        assertEquals(AppointmentErrorCode.JOIN_NOT_AVAILABLE,
                exception.getErrorCode());
        verify(appointmentMapper, never()).insertAppointmentMember(any());
    }

    // 참여 마감 시각이 없어진 뒤로 참여를 막는 시간 경계는 활동 시작뿐이다.
    // 스케줄러가 아직 IN_PROGRESS로 못 옮긴 RECRUITING 약속에도 적용된다.
    @Test
    void joinAppointment_afterActivityStart_rejectsJoin() {
        Appointment appointment = appointment(10L, AppointmentStatus.RECRUITING);
        appointment.setActivityStartAt(LocalDateTime.now().minusMinutes(1));
        when(appointmentMapper.findAppointmentByIdForUpdate(10L))
                .thenReturn(appointment);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> appointmentService.joinAppointment(2L, 10L)
        );

        assertEquals(AppointmentErrorCode.JOIN_NOT_AVAILABLE,
                exception.getErrorCode());
        verify(appointmentMapper, never()).insertAppointmentMember(any());
    }

    @Test
    void joinAppointment_atCapacity_rejectsJoin() {
        Appointment appointment = appointment(10L, AppointmentStatus.RECRUITING);
        appointment.setCurrentMemberCount(appointment.getMaxMembers());
        when(appointmentMapper.findAppointmentByIdForUpdate(10L))
                .thenReturn(appointment);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> appointmentService.joinAppointment(2L, 10L)
        );

        assertEquals(AppointmentErrorCode.JOIN_NOT_AVAILABLE,
                exception.getErrorCode());
        verify(appointmentMapper, never()).insertAppointmentMember(any());
    }

    @Test
    void joinAppointment_alreadyJoined_rejectsJoin() {
        Appointment appointment = appointment(10L, AppointmentStatus.RECRUITING);
        when(appointmentMapper.findAppointmentByIdForUpdate(10L))
                .thenReturn(appointment);
        when(appointmentMapper.findMemberByAppointmentAndMemberForUpdate(10L, 2L))
                .thenReturn(AppointmentMember.builder()
                        .appointmentMemberId(30L)
                        .membershipStatus(MembershipStatus.ACTIVE)
                        .build());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> appointmentService.joinAppointment(2L, 10L)
        );

        assertEquals(AppointmentErrorCode.ALREADY_JOINED,
                exception.getErrorCode());
        verify(appointmentMapper, never()).insertAppointmentMember(any());
    }

    @Test
    void joinAppointment_rejoinsAfterLeaving_revivesExistingMemberAndDeposit() {
        Appointment appointment = appointment(10L, AppointmentStatus.RECRUITING);
        when(appointmentMapper.findAppointmentByIdForUpdate(10L))
                .thenReturn(appointment);
        AppointmentMember left = AppointmentMember.builder()
                .appointmentMemberId(30L)
                .appointmentId(10L)
                .memberId(2L)
                .membershipStatus(MembershipStatus.LEFT)
                .build();
        when(appointmentMapper.findMemberByAppointmentAndMemberForUpdate(10L, 2L))
                .thenReturn(left);
        when(appointmentMapper.reviveLeftMember(30L)).thenReturn(1);
        Deposit refundedDeposit = Deposit.pending(30L, BigDecimal.valueOf(10_000));
        refundedDeposit.hold(400L, LocalDateTime.now());
        refundedDeposit.refund(LocalDateTime.now());
        when(depositMapper.findByAppointmentMemberId(30L))
                .thenReturn(refundedDeposit);
        when(depositMapper.revive(any())).thenReturn(1);
        when(walletTransferService.transferToSystemWallet(
                eq(2L), eq(2L), eq(SystemWalletCode.DEPOSIT_POOL),
                eq(BigDecimal.valueOf(10_000)),
                eq(TransferType.DEPOSIT_HOLD.name()), anyString()
        )).thenReturn(501L);
        when(depositMapper.markHeld(any(), eq(501L), any())).thenReturn(1);
        when(appointmentMapper.markMemberActive(30L)).thenReturn(1);
        when(appointmentMapper.findMemberByIdForUpdate(10L, 30L))
                .thenReturn(AppointmentMember.builder()
                        .appointmentMemberId(30L)
                        .appointmentId(10L)
                        .memberId(2L)
                        .membershipStatus(MembershipStatus.ACTIVE)
                        .build());

        AppointmentMemberResponse result =
                appointmentService.joinAppointment(2L, 10L);

        assertEquals(MembershipStatus.ACTIVE, result.getMembershipStatus());
        verify(appointmentMapper).reviveLeftMember(30L);
        verify(depositMapper).revive(refundedDeposit.getDepositId());
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
    void leaveAppointment_pendingHost_rejectsCancellation() {
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

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> appointmentService.leaveAppointment(1L, 10L)
        );

        assertEquals(AppointmentErrorCode.CANCELLATION_NOT_AVAILABLE,
                exception.getErrorCode());
        verify(depositMapper, never()).findByAppointmentMemberId(any());
        verify(appointmentMapper, never()).markMemberLeft(any());
    }

    @Test
    void leaveAppointment_activeMember_refundsDepositAndLeaves() {
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
        Deposit deposit = mock(Deposit.class);
        when(deposit.getDepositId()).thenReturn(40L);
        when(deposit.getAmount()).thenReturn(BigDecimal.valueOf(10_000));
        when(deposit.isPending()).thenReturn(false);
        when(deposit.isHeld()).thenReturn(true);
        when(appointmentMapper.findAppointmentByIdForUpdate(10L))
                .thenReturn(appointment);
        when(appointmentMapper.findMemberByAppointmentAndMemberForUpdate(
                10L, 2L
        )).thenReturn(member);
        when(depositMapper.findByAppointmentMemberId(30L))
                .thenReturn(deposit);
        when(walletTransferService.transferFromSystemWallet(
                eq(2L), eq(SystemWalletCode.DEPOSIT_POOL), eq(2L),
                eq(BigDecimal.valueOf(10_000)),
                eq(TransferType.DEPOSIT_REFUND.name()), anyString()
        )).thenReturn(600L);
        when(depositMapper.markRefunded(eq(40L), any())).thenReturn(1);
        when(appointmentMapper.markMemberLeft(30L)).thenReturn(1);

        appointmentService.leaveAppointment(2L, 10L);

        verify(walletTransferService).transferFromSystemWallet(
                eq(2L), eq(SystemWalletCode.DEPOSIT_POOL), eq(2L),
                eq(BigDecimal.valueOf(10_000)),
                eq(TransferType.DEPOSIT_REFUND.name()), anyString()
        );
        verify(deposit).refund(any());
        verify(depositMapper).markRefunded(eq(40L), any());
        verify(appointmentMapper).markMemberLeft(30L);
    }

    @Test
    void leaveAppointment_fullByCapacity_reopensRecruiting() {
        Appointment appointment = appointment(
                10L,
                AppointmentStatus.FULL
        );
        AppointmentMember member = AppointmentMember.builder()
                .appointmentMemberId(30L)
                .appointmentId(10L)
                .memberId(2L)
                .membershipStatus(MembershipStatus.ACTIVE)
                .build();
        Deposit deposit = mock(Deposit.class);
        when(deposit.getDepositId()).thenReturn(40L);
        when(deposit.getAmount()).thenReturn(BigDecimal.valueOf(10_000));
        when(deposit.isPending()).thenReturn(false);
        when(deposit.isHeld()).thenReturn(true);
        when(appointmentMapper.findAppointmentByIdForUpdate(10L))
                .thenReturn(appointment);
        when(appointmentMapper.findMemberByAppointmentAndMemberForUpdate(
                10L, 2L
        )).thenReturn(member);
        when(depositMapper.findByAppointmentMemberId(30L))
                .thenReturn(deposit);
        when(walletTransferService.transferFromSystemWallet(
                eq(2L), eq(SystemWalletCode.DEPOSIT_POOL), eq(2L),
                eq(BigDecimal.valueOf(10_000)),
                eq(TransferType.DEPOSIT_REFUND.name()), anyString()
        )).thenReturn(600L);
        when(depositMapper.markRefunded(eq(40L), any())).thenReturn(1);
        when(appointmentMapper.markMemberLeft(30L)).thenReturn(1);
        when(appointmentMapper.updateAppointmentStatus(
                10L, AppointmentStatus.FULL, AppointmentStatus.RECRUITING
        )).thenReturn(1);

        appointmentService.leaveAppointment(2L, 10L);

        verify(appointmentMapper).updateAppointmentStatus(
                10L, AppointmentStatus.FULL, AppointmentStatus.RECRUITING
        );
    }

    @Test
    void leaveAppointment_activeHost_rejectsCancellation() {
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
        verify(appointmentMapper, never()).markMemberLeft(any());
    }

    // 정원이 차서 FULL이던 약속이라도 활동이 시작된 뒤의 탈퇴는 노쇼로 굳는다.
    // 스케줄러가 아직 IN_PROGRESS로 못 옮긴 상태에서도 마찬가지이고, 이때는
    // 빈자리가 생겨도 새로 참여할 수 없으므로 RECRUITING으로 되돌리지 않는다.
    @Test
    void leaveAppointment_fullAfterActivityStart_marksNoShowWithoutReopening() {
        Appointment appointment = appointment(
                10L,
                AppointmentStatus.FULL
        );
        appointment.setActivityStartAt(LocalDateTime.now().minusHours(1));
        appointment.setActivityEndAt(LocalDateTime.now().plusHours(1));
        AppointmentMember member = AppointmentMember.builder()
                .appointmentMemberId(30L)
                .appointmentId(10L)
                .memberId(2L)
                .membershipStatus(MembershipStatus.ACTIVE)
                .build();
        Deposit deposit = mock(Deposit.class);
        when(deposit.isHeld()).thenReturn(true);
        when(appointmentMapper.findAppointmentByIdForUpdate(10L))
                .thenReturn(appointment);
        when(appointmentMapper.findMemberByAppointmentAndMemberForUpdate(
                10L, 2L
        )).thenReturn(member);
        when(depositMapper.findByAppointmentMemberId(30L))
                .thenReturn(deposit);
        when(appointmentMapper.updateAttendance(
                eq(30L), eq(AttendanceStatus.NO_SHOW), any()
        )).thenReturn(1);
        when(appointmentMapper.markMemberLeft(30L)).thenReturn(1);

        appointmentService.leaveAppointment(2L, 10L);

        verify(appointmentMapper).updateAttendance(
                eq(30L), eq(AttendanceStatus.NO_SHOW), any()
        );
        verify(appointmentMapper).markMemberLeft(30L);
        verify(depositMapper, never()).markRefunded(any(), any());
        verify(appointmentMapper, never())
                .updateAppointmentStatus(any(), any(), any());
    }

    // 활동 중 탈퇴는 노쇼로 굳는다. 보증금은 환급하지 않고 HELD로 남겨,
    // 출석 확정 후 정산 배치가 출석 회원에게 분배한다.
    @Test
    void leaveAppointment_duringActivity_marksNoShowAndKeepsDeposit() {
        Appointment appointment = appointment(
                10L,
                AppointmentStatus.IN_PROGRESS
        );
        appointment.setActivityStartAt(LocalDateTime.now().minusHours(1));
        appointment.setActivityEndAt(LocalDateTime.now().plusHours(1));
        AppointmentMember member = AppointmentMember.builder()
                .appointmentMemberId(30L)
                .appointmentId(10L)
                .memberId(2L)
                .membershipStatus(MembershipStatus.ACTIVE)
                .build();
        Deposit deposit = mock(Deposit.class);
        when(deposit.isHeld()).thenReturn(true);
        when(appointmentMapper.findAppointmentByIdForUpdate(10L))
                .thenReturn(appointment);
        when(appointmentMapper.findMemberByAppointmentAndMemberForUpdate(
                10L, 2L
        )).thenReturn(member);
        when(depositMapper.findByAppointmentMemberId(30L))
                .thenReturn(deposit);
        when(appointmentMapper.updateAttendance(
                eq(30L), eq(AttendanceStatus.NO_SHOW), any()
        )).thenReturn(1);
        when(appointmentMapper.markMemberLeft(30L)).thenReturn(1);

        appointmentService.leaveAppointment(2L, 10L);

        verify(appointmentMapper).updateAttendance(
                eq(30L), eq(AttendanceStatus.NO_SHOW), any());
        verify(appointmentMapper).markMemberLeft(30L);
        verify(walletTransferService, never()).transferFromSystemWallet(
                any(), any(), anyLong(), any(), any(), any());
        verify(depositMapper, never()).markRefunded(any(), any());
    }

    @Test
    void leaveAppointment_afterActivityEnd_rejectsCancellation() {
        Appointment appointment = endedAppointment(
                10L, AppointmentStatus.IN_PROGRESS);
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
    void getMyOngoingAppointments_returnsTripLinkedAppointments() {
        MyOngoingAppointment appointment = new MyOngoingAppointment(
                10L,
                "Seoul Night Tour",
                5L,
                "Gwanghwamun Square",
                LocalDateTime.of(2026, 8, 21, 18, 30),
                LocalDateTime.of(2026, 8, 21, 22, 0),
                100L,
                "EVENT",
                "IN_PROGRESS"
        );
        when(appointmentMapper.findMyOngoingAppointments(eq(1L), eq(false), any()))
                .thenReturn(List.of(appointment));

        List<MyOngoingAppointmentResponse> result =
                appointmentService.getMyOngoingAppointments(1L, "ONGOING");

        assertEquals(1, result.size());
        assertEquals(10L, result.get(0).appointmentId());
        assertEquals(5L, result.get(0).tripId());
        assertEquals(100L, result.get(0).itemId());
        assertEquals("EVENT", result.get(0).itemType());
        assertEquals("IN_PROGRESS", result.get(0).appointmentStatus());
    }

    @Test
    void getMyOngoingAppointments_allScope_includesFinishedAppointments() {
        when(appointmentMapper.findMyOngoingAppointments(eq(1L), eq(true), any()))
                .thenReturn(List.of());

        appointmentService.getMyOngoingAppointments(1L, "ALL");

        verify(appointmentMapper)
                .findMyOngoingAppointments(eq(1L), eq(true), any());
    }

    @Test
    void getMyOngoingAppointments_invalidMemberId_rejectsRequest() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> appointmentService.getMyOngoingAppointments(0L, "ONGOING")
        );

        assertEquals(CommonErrorCode.INVALID_INPUT, exception.getErrorCode());
    }

    @Test
    void getMyOngoingAppointments_unknownScope_rejectsRequest() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> appointmentService.getMyOngoingAppointments(1L, "PAST")
        );

        assertEquals(CommonErrorCode.INVALID_INPUT, exception.getErrorCode());
    }

    @Test
    void confirmAttendance_success_completesAppointmentAndCreatesPendingBatch() {
        Appointment appointment = endedAppointment(
                10L, AppointmentStatus.IN_PROGRESS);
        AppointmentMember host = AppointmentMember.builder()
                .appointmentMemberId(20L)
                .appointmentId(10L)
                .memberId(1L)
                .build();
        AppointmentMember guest = AppointmentMember.builder()
                .appointmentMemberId(21L)
                .appointmentId(10L)
                .memberId(2L)
                .build();
        Deposit hostDeposit = mock(Deposit.class);
        when(hostDeposit.isHeld()).thenReturn(true);
        when(hostDeposit.getAmount()).thenReturn(BigDecimal.valueOf(10_000));
        Deposit guestDeposit = mock(Deposit.class);
        when(guestDeposit.isHeld()).thenReturn(true);
        when(guestDeposit.getAmount()).thenReturn(BigDecimal.valueOf(10_000));
        when(appointmentMapper.findAppointmentByIdForUpdate(10L))
                .thenReturn(appointment);
        when(appointmentMapper.findActiveMembersByAppointmentId(10L))
                .thenReturn(List.of(host, guest));
        when(appointmentMapper.updateAttendance(
                eq(20L), eq(AttendanceStatus.ATTENDED), any()
        )).thenReturn(1);
        when(appointmentMapper.updateAttendance(
                eq(21L), eq(AttendanceStatus.NO_SHOW), any()
        )).thenReturn(1);
        when(depositMapper.findByAppointmentMemberId(20L))
                .thenReturn(hostDeposit);
        when(depositMapper.findByAppointmentMemberId(21L))
                .thenReturn(guestDeposit);
        when(appointmentMapper.updateAppointmentStatus(
                10L,
                AppointmentStatus.IN_PROGRESS,
                AppointmentStatus.COMPLETED
        )).thenReturn(1);
        when(depositPayoutBatchMapper.insert(any())).thenReturn(1);

        appointmentService.confirmAttendance(1L, 10L, attendanceRequest());

        verify(appointmentMapper).updateAppointmentStatus(
                10L,
                AppointmentStatus.IN_PROGRESS,
                AppointmentStatus.COMPLETED
        );
        ArgumentCaptor<DepositPayoutBatch> batchCaptor =
                ArgumentCaptor.forClass(DepositPayoutBatch.class);
        verify(depositPayoutBatchMapper).insert(batchCaptor.capture());
        DepositPayoutBatch batch = batchCaptor.getValue();
        assertEquals(10L, batch.getAppointmentId());
        assertEquals(ResolutionReason.APPOINTMENT_COMPLETED,
                batch.getResolutionReason());
        assertEquals(0, BigDecimal.valueOf(20_000)
                .compareTo(batch.getTotalHeldAmount()));
    }

    // 활동 중에 나가 노쇼로 굳은 LEFT 회원의 보증금(HELD)도 이 배치가 분배할
    // 몫이므로 합산에 포함돼야 한다.
    @Test
    void confirmAttendance_leftNoShowDeposit_includedInBatchTotal() {
        Appointment appointment = endedAppointment(
                10L, AppointmentStatus.IN_PROGRESS);
        AppointmentMember host = AppointmentMember.builder()
                .appointmentMemberId(20L)
                .appointmentId(10L)
                .memberId(1L)
                .build();
        AppointmentMember guest = AppointmentMember.builder()
                .appointmentMemberId(21L)
                .appointmentId(10L)
                .memberId(2L)
                .build();
        AppointmentMember leftNoShow = AppointmentMember.builder()
                .appointmentMemberId(22L)
                .appointmentId(10L)
                .memberId(3L)
                .membershipStatus(MembershipStatus.LEFT)
                .attendanceStatus(AttendanceStatus.NO_SHOW)
                .build();
        Deposit hostDeposit = mock(Deposit.class);
        when(hostDeposit.isHeld()).thenReturn(true);
        when(hostDeposit.getAmount()).thenReturn(BigDecimal.valueOf(10_000));
        Deposit guestDeposit = mock(Deposit.class);
        when(guestDeposit.isHeld()).thenReturn(true);
        when(guestDeposit.getAmount()).thenReturn(BigDecimal.valueOf(10_000));
        Deposit leftDeposit = mock(Deposit.class);
        when(leftDeposit.isHeld()).thenReturn(true);
        when(leftDeposit.getAmount()).thenReturn(BigDecimal.valueOf(10_000));
        when(appointmentMapper.findAppointmentByIdForUpdate(10L))
                .thenReturn(appointment);
        when(appointmentMapper.findActiveMembersByAppointmentId(10L))
                .thenReturn(List.of(host, guest));
        when(appointmentMapper.findLeftNoShowMembersByAppointmentId(10L))
                .thenReturn(List.of(leftNoShow));
        when(appointmentMapper.updateAttendance(
                eq(20L), eq(AttendanceStatus.ATTENDED), any()
        )).thenReturn(1);
        when(appointmentMapper.updateAttendance(
                eq(21L), eq(AttendanceStatus.NO_SHOW), any()
        )).thenReturn(1);
        when(depositMapper.findByAppointmentMemberId(20L))
                .thenReturn(hostDeposit);
        when(depositMapper.findByAppointmentMemberId(21L))
                .thenReturn(guestDeposit);
        when(depositMapper.findByAppointmentMemberId(22L))
                .thenReturn(leftDeposit);
        when(appointmentMapper.updateAppointmentStatus(
                10L,
                AppointmentStatus.IN_PROGRESS,
                AppointmentStatus.COMPLETED
        )).thenReturn(1);
        when(depositPayoutBatchMapper.insert(any())).thenReturn(1);

        appointmentService.confirmAttendance(1L, 10L, attendanceRequest());

        ArgumentCaptor<DepositPayoutBatch> batchCaptor =
                ArgumentCaptor.forClass(DepositPayoutBatch.class);
        verify(depositPayoutBatchMapper).insert(batchCaptor.capture());
        assertEquals(0, BigDecimal.valueOf(30_000)
                .compareTo(batchCaptor.getValue().getTotalHeldAmount()));
    }

    @Test
    void confirmAttendance_notHost_rejects() {
        Appointment appointment = appointment(10L, AppointmentStatus.IN_PROGRESS);
        when(appointmentMapper.findAppointmentByIdForUpdate(10L))
                .thenReturn(appointment);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> appointmentService.confirmAttendance(
                        2L, 10L, attendanceRequest()
                )
        );

        assertEquals(AppointmentErrorCode.APPOINTMENT_FORBIDDEN,
                exception.getErrorCode());
        verify(appointmentMapper, never()).updateAttendance(any(), any(), any());
    }

    @Test
    void confirmAttendance_notInProgress_rejects() {
        Appointment appointment = appointment(10L, AppointmentStatus.FULL);
        when(appointmentMapper.findAppointmentByIdForUpdate(10L))
                .thenReturn(appointment);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> appointmentService.confirmAttendance(
                        1L, 10L, attendanceRequest()
                )
        );

        assertEquals(AppointmentErrorCode.INVALID_ATTENDANCE_CONFIRMATION,
                exception.getErrorCode());
    }

    @Test
    void confirmAttendance_missingActiveMember_rejects() {
        Appointment appointment = endedAppointment(
                10L, AppointmentStatus.IN_PROGRESS);
        AppointmentMember host = AppointmentMember.builder()
                .appointmentMemberId(20L)
                .appointmentId(10L)
                .memberId(1L)
                .build();
        AppointmentMember guest = AppointmentMember.builder()
                .appointmentMemberId(21L)
                .appointmentId(10L)
                .memberId(2L)
                .build();
        AppointmentMember thirdMember = AppointmentMember.builder()
                .appointmentMemberId(22L)
                .appointmentId(10L)
                .memberId(3L)
                .build();
        when(appointmentMapper.findAppointmentByIdForUpdate(10L))
                .thenReturn(appointment);
        when(appointmentMapper.findActiveMembersByAppointmentId(10L))
                .thenReturn(List.of(host, guest, thirdMember));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> appointmentService.confirmAttendance(
                        1L, 10L, attendanceRequest()
                )
        );

        assertEquals(AppointmentErrorCode.INVALID_ATTENDANCE_CONFIRMATION,
                exception.getErrorCode());
        verify(appointmentMapper, never()).updateAttendance(any(), any(), any());
    }

    @Test
    void confirmAttendance_activityNotEnded_rejects() {
        // IN_PROGRESS는 활동 시작 시각에 스케줄러가 바꾼다. 상태만 맞으면 통과하면
        // 활동 도중에 확정이 되고, 아직 오는 중인 참여자가 노쇼로 굳어 보증금을 잃는다.
        Appointment appointment = appointment(10L, AppointmentStatus.IN_PROGRESS);
        when(appointmentMapper.findAppointmentByIdForUpdate(10L))
                .thenReturn(appointment);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> appointmentService.confirmAttendance(
                        1L, 10L, attendanceRequest()
                )
        );

        assertEquals(AppointmentErrorCode.ATTENDANCE_NOT_ENDED,
                exception.getErrorCode());
        verify(appointmentMapper, never()).updateAttendance(any(), any(), any());
        verify(appointmentMapper, never()).findActiveMembersByAppointmentId(any());
    }

    @Test
    void confirmAttendance_activityEndAtMissing_rejects() {
        // 활동 종료 시각을 못 읽으면 끝났는지 확인할 방법이 없다. 되돌릴 수 없는
        // 처리라 모르는 채로 진행하지 않는다.
        Appointment appointment = endedAppointment(
                10L, AppointmentStatus.IN_PROGRESS);
        appointment.setActivityEndAt(null);
        when(appointmentMapper.findAppointmentByIdForUpdate(10L))
                .thenReturn(appointment);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> appointmentService.confirmAttendance(
                        1L, 10L, attendanceRequest()
                )
        );

        assertEquals(AppointmentErrorCode.ATTENDANCE_NOT_ENDED,
                exception.getErrorCode());
        verify(appointmentMapper, never()).updateAttendance(any(), any(), any());
    }

    @Test
    void confirmAttendance_noAttendedMember_rejects() {
        Appointment appointment = endedAppointment(
                10L, AppointmentStatus.IN_PROGRESS);
        when(appointmentMapper.findAppointmentByIdForUpdate(10L))
                .thenReturn(appointment);

        AppointmentAttendanceRequest.MemberAttendance host =
                new AppointmentAttendanceRequest.MemberAttendance();
        host.setMemberId(1L);
        host.setAttendanceStatus(AttendanceStatus.NO_SHOW);
        AppointmentAttendanceRequest.MemberAttendance guest =
                new AppointmentAttendanceRequest.MemberAttendance();
        guest.setMemberId(2L);
        guest.setAttendanceStatus(AttendanceStatus.NO_SHOW);
        AppointmentAttendanceRequest allNoShowRequest = new AppointmentAttendanceRequest();
        allNoShowRequest.setMembers(List.of(host, guest));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> appointmentService.confirmAttendance(1L, 10L, allNoShowRequest)
        );

        assertEquals(AppointmentErrorCode.INVALID_ATTENDANCE_CONFIRMATION,
                exception.getErrorCode());
        verify(appointmentMapper, never()).findActiveMembersByAppointmentId(any());
        verify(appointmentMapper, never()).updateAttendance(any(), any(), any());
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
        request.setTripId(1L);
        request.setVisitDate(VISIT_DATE);
        request.setActivityStartTime(LocalTime.of(18, 30));
        request.setActivityEndTime(LocalTime.of(22, 0));
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
                .activityStartAt(LocalDateTime.of(VISIT_DATE, LocalTime.of(18, 30)))
                .activityEndAt(LocalDateTime.of(VISIT_DATE, LocalTime.of(22, 0)))
                .build();
    }

    // 출석 확정은 활동이 끝난 뒤에만 열린다(APPOINTMENT-009). 기본 픽스처는 아직
    // 열리지 않은 약속이라 그 조건을 넘지 못하므로, 출석 테스트는 이것을 쓴다.
    private static Appointment endedAppointment(
            Long appointmentId,
            AppointmentStatus status) {
        Appointment appointment = appointment(appointmentId, status);
        appointment.setActivityStartAt(LocalDateTime.now().minusHours(4));
        appointment.setActivityEndAt(LocalDateTime.now().minusHours(1));
        return appointment;
    }

    private static AppointmentAttendanceRequest attendanceRequest() {
        AppointmentAttendanceRequest.MemberAttendance host =
                new AppointmentAttendanceRequest.MemberAttendance();
        host.setMemberId(1L);
        host.setAttendanceStatus(AttendanceStatus.ATTENDED);
        AppointmentAttendanceRequest.MemberAttendance guest =
                new AppointmentAttendanceRequest.MemberAttendance();
        guest.setMemberId(2L);
        guest.setAttendanceStatus(AttendanceStatus.NO_SHOW);
        AppointmentAttendanceRequest request =
                new AppointmentAttendanceRequest();
        request.setMembers(List.of(host, guest));
        return request;
    }
}
