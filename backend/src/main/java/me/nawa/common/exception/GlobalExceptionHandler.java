package me.nawa.common.exception;

import lombok.extern.log4j.Log4j2;
import me.nawa.common.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Controller 밖으로 전달된 예외를
 * 공통 JSON 응답으로 변환합니다.
 */
@Log4j2
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Service에서 발생한 BusinessException을 처리합니다.
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(
        BusinessException exception
    ) {
        ErrorCode errorCode = exception.getErrorCode();

        if (exception.getCause() == null) {
            log.warn(
                "[BusinessException] code={}, message={}",
                errorCode.getCode(),
                errorCode.getMessage()
            );
        } else {
            log.error(
                "[BusinessException] code={}, message={}",
                errorCode.getCode(),
                errorCode.getMessage(),
                exception
            );
        }

        return createErrorResponse(errorCode);
    }

    /**
     * JSON 문법이 잘못되었거나 요청 본문을 읽을 수 없을 때 처리합니다.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleMalformedJson() {
        return createErrorResponse(CommonErrorCode.MALFORMED_JSON);
    }

    /**
     * 지원하지 않는 HTTP Method로 요청했을 때 처리합니다.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotAllowed() {
        return createErrorResponse(CommonErrorCode.METHOD_NOT_ALLOWED);
    }

    /**
     * 별도로 처리하지 않은 예상 밖 예외를 처리합니다.
     *
     * 실제 예외는 서버 로그에 기록하고,
     * 클라이언트에는 COMMON-999만 반환합니다.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(
        Exception exception
    ) {
        log.error("[UnexpectedException]", exception);

        return createErrorResponse(
            CommonErrorCode.INTERNAL_SERVER_ERROR
        );
    }

    /**
     * ErrorCode를 HTTP 상태와 공통 실패 응답으로 변환합니다.
     *
     * 각 Handler의 ResponseEntity 생성 중복을 줄이기 위한 내부 메서드입니다.
     */
    private ResponseEntity<ApiResponse<Void>> createErrorResponse(
        ErrorCode errorCode
    ) {
        return ResponseEntity
            .status(errorCode.getStatus())
            .body(ApiResponse.failure(errorCode));
    }
}
