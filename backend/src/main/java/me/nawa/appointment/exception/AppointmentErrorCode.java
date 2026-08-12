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
    );

    private final HttpStatus status;
    private final String code;
    private final String message;
}
