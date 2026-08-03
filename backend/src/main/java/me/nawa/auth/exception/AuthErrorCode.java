package me.nawa.auth.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.nawa.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements ErrorCode {
    INVALID_REFRESH_TOKEN(
            HttpStatus.UNAUTHORIZED,
            "AUTH-001",
            "인증 세션이 유효하지 않습니다. 다시 로그인해주세요."
    ),

    REFRESH_TOKEN_REUSE_DETECTED(
            HttpStatus.UNAUTHORIZED,
            "AUTH-002",
            "인증 세션이 만료되었습니다. 다시 로그인해주세요."
    ),

    AUTHENTICATION_REQUIRED(
            HttpStatus.UNAUTHORIZED,
            "AUTH-003",
            "로그인이 필요합니다."
    ),

    ACCESS_DENIED(
            HttpStatus.FORBIDDEN,
            "AUTH-004",
            "접근 권한이 없습니다."
    ),

    INVALID_CSRF_TOKEN(
            HttpStatus.FORBIDDEN,
            "AUTH-005",
            "요청의 CSRF 토큰이 유효하지 않습니다."
    ),

    ORIGIN_NOT_ALLOWED(
            HttpStatus.FORBIDDEN,
            "AUTH-006",
            "허용되지 않은 요청 출처입니다."
    );

    private final HttpStatus status;
    private final String code;
    private final String message;
}
