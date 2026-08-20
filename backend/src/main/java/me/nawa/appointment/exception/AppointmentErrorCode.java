package me.nawa.appointment.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.nawa.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AppointmentErrorCode implements ErrorCode {
    APPOINTMENT_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "APPOINTMENT-001",
            "약속을 찾을 수 없습니다."
    ),
    JOIN_NOT_AVAILABLE(
            HttpStatus.CONFLICT,
            "APPOINTMENT-002",
            "현재 참여할 수 없는 약속입니다."
    ),
    ALREADY_JOINED(
            HttpStatus.CONFLICT,
            "APPOINTMENT-003",
            "이미 참여 이력이 있는 약속입니다."
    ),
    APPOINTMENT_FORBIDDEN(
            HttpStatus.FORBIDDEN,
            "APPOINTMENT-004",
            "약속을 처리할 권한이 없습니다."
    ),
    APPOINTMENT_MEMBER_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "APPOINTMENT-005",
            "약속 참여 정보를 찾을 수 없습니다."
    ),
    INVALID_ATTENDANCE_CONFIRMATION(
            HttpStatus.CONFLICT,
            "APPOINTMENT-006",
            "출석 확정 조건을 충족하지 않습니다."
    ),
    CANCELLATION_NOT_AVAILABLE(
            HttpStatus.CONFLICT,
            "APPOINTMENT-007",
            "현재 참여를 취소할 수 없습니다."
    ),
    PAYMENT_INTEGRATION_REQUIRED(
            HttpStatus.CONFLICT,
            "APPOINTMENT-008",
            "보증금 결제 연동 후 이용할 수 있습니다."
    ),
    ATTENDANCE_NOT_ENDED(
            HttpStatus.CONFLICT,
            "APPOINTMENT-009",
            "활동이 끝난 뒤에 출석을 확정할 수 있습니다."
    );

    private final HttpStatus status;
    private final String code;
    private final String message;
}
