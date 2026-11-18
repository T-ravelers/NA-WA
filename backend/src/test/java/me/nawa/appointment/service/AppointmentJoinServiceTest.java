package me.nawa.appointment.service;

import me.nawa.appointment.dto.request.AppointmentJoinRequest;
import me.nawa.appointment.dto.response.AppointmentMemberResponse;
import me.nawa.appointment.exception.AppointmentErrorCode;
import me.nawa.common.exception.BusinessException;
import me.nawa.common.exception.CommonErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 입장권 결과에 따라 참여 트랜잭션을 부를지, 실패했을 때 자리를 돌려줄지를 본다.
 * 트랜잭션 안의 판단은 {@code AppointmentServiceTest}가 본다.
 */
@ExtendWith(MockitoExtension.class)
class AppointmentJoinServiceTest {

    private static final long MEMBER_ID = 7L;
    private static final long APPOINTMENT_ID = 101L;

    @Mock
    private AppointmentSeatGate seatGate;

    @Mock
    private AppointmentService appointmentService;

    @InjectMocks
    private AppointmentJoinService joinService;

    private final AppointmentJoinRequest request = joinRequest(42L);

    @Test
    void join_rejectsWithoutTouchingTheDatabase_whenFull() {
        when(seatGate.tryAcquire(APPOINTMENT_ID, MEMBER_ID)).thenReturn(SeatAcquireResult.FULL);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> joinService.join(MEMBER_ID, APPOINTMENT_ID, request));

        assertEquals(AppointmentErrorCode.JOIN_NOT_AVAILABLE, exception.getErrorCode());
        verifyNoInteractions(appointmentService);
        verify(seatGate, never()).release(any(), any());
    }

    @Test
    void join_keepsTheSeat_whenTheDatabaseSucceeds() {
        AppointmentMemberResponse joined = mock(AppointmentMemberResponse.class);
        when(seatGate.tryAcquire(APPOINTMENT_ID, MEMBER_ID)).thenReturn(SeatAcquireResult.ACQUIRED);
        when(appointmentService.joinAppointment(MEMBER_ID, APPOINTMENT_ID, request)).thenReturn(joined);

        assertSame(joined, joinService.join(MEMBER_ID, APPOINTMENT_ID, request));
        verify(seatGate, never()).release(any(), any());
    }

    /** 잔액 부족처럼 트랜잭션이 롤백되면 이번에 뺀 자리를 돌려주고 같은 오류를 그대로 던진다. */
    @Test
    void join_releasesTheAcquiredSeat_whenTheDatabaseFails() {
        BusinessException failure = new BusinessException(CommonErrorCode.INVALID_INPUT);
        when(seatGate.tryAcquire(APPOINTMENT_ID, MEMBER_ID)).thenReturn(SeatAcquireResult.ACQUIRED);
        when(appointmentService.joinAppointment(MEMBER_ID, APPOINTMENT_ID, request)).thenThrow(failure);

        assertSame(failure, assertThrows(BusinessException.class,
                () -> joinService.join(MEMBER_ID, APPOINTMENT_ID, request)));
        verify(seatGate).release(APPOINTMENT_ID, MEMBER_ID);
    }

    /** 이미 가진 자리나 입장권을 쓰지 못한 요청은 이번에 뺀 것이 없어 돌려주지 않는다. */
    @Test
    void join_doesNotRelease_whenNothingWasDeducted() {
        when(appointmentService.joinAppointment(MEMBER_ID, APPOINTMENT_ID, request))
                .thenThrow(new BusinessException(AppointmentErrorCode.ALREADY_JOINED));

        for (SeatAcquireResult result : new SeatAcquireResult[] {
                SeatAcquireResult.ALREADY_HOLDING, SeatAcquireResult.UNAVAILABLE}) {
            when(seatGate.tryAcquire(APPOINTMENT_ID, MEMBER_ID)).thenReturn(result);

            assertThrows(BusinessException.class,
                    () -> joinService.join(MEMBER_ID, APPOINTMENT_ID, request));
        }
        verify(seatGate, never()).release(any(), any());
    }

    /** Redis를 쓸 수 없으면 지금처럼 DB만으로 처리한다. */
    @Test
    void join_fallsBackToTheDatabase_whenRedisIsUnavailable() {
        AppointmentMemberResponse joined = mock(AppointmentMemberResponse.class);
        when(seatGate.tryAcquire(APPOINTMENT_ID, MEMBER_ID)).thenReturn(SeatAcquireResult.UNAVAILABLE);
        when(appointmentService.joinAppointment(MEMBER_ID, APPOINTMENT_ID, request)).thenReturn(joined);

        assertSame(joined, joinService.join(MEMBER_ID, APPOINTMENT_ID, request));
    }

    /** 입력 오류는 joinAppointment가 400으로 거절한다. 그 요청으로 자리를 건드리지 않는다. */
    @Test
    void join_skipsTheGate_forMalformedRequests() {
        joinService.join(MEMBER_ID, APPOINTMENT_ID, null);
        joinService.join(MEMBER_ID, APPOINTMENT_ID, joinRequest(null));
        joinService.join(null, APPOINTMENT_ID, request);
        joinService.join(MEMBER_ID, 0L, request);

        verifyNoInteractions(seatGate);
    }

    private static AppointmentJoinRequest joinRequest(Long tripId) {
        AppointmentJoinRequest request = new AppointmentJoinRequest();
        request.setTripId(tripId);
        return request;
    }
}
