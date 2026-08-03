package me.nawa.auth.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import me.nawa.auth.cookie.AuthCookieManager;
import me.nawa.common.exception.BusinessException;
import me.nawa.common.exception.ErrorCode;
import me.nawa.common.response.ApiResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Log4j2
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = AuthController.class)
@RequiredArgsConstructor
public class AuthExceptionHandler {
    private final AuthCookieManager authCookieManager;

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(
            BusinessException exception) {
        ErrorCode errorCode = exception.getErrorCode();
        log.warn("[AuthException] code={}", errorCode.getCode());

        return ResponseEntity
                .status(errorCode.getStatus())
                .header(
                        HttpHeaders.SET_COOKIE,
                        authCookieManager.deleteAccessTokenCookie().toString(),
                        authCookieManager.deleteRefreshTokenCookie().toString()
                )
                .body(ApiResponse.failure(errorCode));
    }
}
