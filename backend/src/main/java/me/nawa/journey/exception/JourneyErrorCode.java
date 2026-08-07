package me.nawa.journey.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.nawa.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum JourneyErrorCode implements ErrorCode {

    JOURNEY_NOT_FOUND(
        HttpStatus.NOT_FOUND,
        "JOURNEY-001",
        "Journey를 찾을 수 없습니다."
    ),

    JOURNEY_FORBIDDEN(
        HttpStatus.FORBIDDEN,
        "JOURNEY-002",
        "Journey에 접근할 권한이 없습니다."
    ),

    INVALID_JOURNEY_INPUT(
        HttpStatus.BAD_REQUEST,
        "JOURNEY-003",
        "Journey 입력값이 올바르지 않습니다."
    );

    private final HttpStatus status;
    private final String code;
    private final String message;
}
