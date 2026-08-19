package me.nawa.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 모든 API에서 공통으로 사용할 수 있는 오류 코드입니다.
 */
@Getter
@RequiredArgsConstructor
public enum CommonErrorCode implements ErrorCode {

    INVALID_INPUT(
        HttpStatus.BAD_REQUEST,
        "COMMON-001",
        "입력값이 올바르지 않습니다."
    ),

    MALFORMED_JSON(
        HttpStatus.BAD_REQUEST,
        "COMMON-002",
        "요청 본문을 확인해주세요."
    ),

    METHOD_NOT_ALLOWED(
        HttpStatus.METHOD_NOT_ALLOWED,
        "COMMON-003",
        "지원하지 않는 HTTP 메서드입니다."
    ),

    PAYLOAD_TOO_LARGE(
        HttpStatus.PAYLOAD_TOO_LARGE,
        "COMMON-004",
        "요청 크기가 허용 범위를 초과했습니다."
    ),

    INTERNAL_SERVER_ERROR(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "COMMON-999",
        "시스템 내부 오류가 발생했습니다."
    );

    private final HttpStatus status;
    private final String code;
    private final String message;
}
