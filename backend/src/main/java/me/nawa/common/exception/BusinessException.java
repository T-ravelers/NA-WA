package me.nawa.common.exception;

import lombok.Getter;

/**
 * Service에서 발생한 비즈니스 오류를
 * GlobalExceptionHandler까지 전달하는 예외입니다.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    /**
     * 지정한 ErrorCode로 비즈니스 예외를 생성합니다.
     */
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    /**
     * 원인이 되는 예외를 함께 보관해야 할 때 사용합니다.
     */
    public BusinessException(
        ErrorCode errorCode,
        Throwable cause
    ) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
    }
}
