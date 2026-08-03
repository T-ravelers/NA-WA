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
    ),

    UNSUPPORTED_OAUTH_PROVIDER(
            HttpStatus.BAD_REQUEST,
            "AUTH-007",
            "지원하지 않는 소셜 로그인 제공자입니다."
    ),

    INVALID_OAUTH_RETURN_PATH(
            HttpStatus.BAD_REQUEST,
            "AUTH-008",
            "허용되지 않은 로그인 복귀 경로입니다."
    ),

    OAUTH_PROVIDER_NOT_CONFIGURED(
            HttpStatus.SERVICE_UNAVAILABLE,
            "AUTH-009",
            "소셜 로그인 설정이 완료되지 않았습니다."
    ),

    OAUTH_TOKEN_EXCHANGE_FAILED(
            HttpStatus.UNAUTHORIZED,
            "AUTH-010",
            "소셜 로그인 인증에 실패했습니다. 다시 시도해주세요."
    ),

    OAUTH_PROVIDER_UNAVAILABLE(
            HttpStatus.SERVICE_UNAVAILABLE,
            "AUTH-011",
            "소셜 로그인 제공자와 통신할 수 없습니다. 잠시 후 다시 시도해주세요."
    ),

    INVALID_OAUTH_TOKEN_RESPONSE(
            HttpStatus.BAD_GATEWAY,
            "AUTH-012",
            "소셜 로그인 제공자의 응답을 확인할 수 없습니다."
    ),

    INVALID_OAUTH_ID_TOKEN(
            HttpStatus.UNAUTHORIZED,
            "AUTH-013",
            "소셜 로그인 사용자 정보를 확인할 수 없습니다. 다시 시도해주세요."
    );

    private final HttpStatus status;
    private final String code;
    private final String message;
}
