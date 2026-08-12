package me.nawa.review.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.nawa.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ReviewErrorCode implements ErrorCode {
    REVIEW_NOT_ALLOWED(
            HttpStatus.FORBIDDEN,
            "REVIEW-001",
            "후기를 작성할 수 없습니다."
    ),
    REVIEW_DUPLICATE(
            HttpStatus.CONFLICT,
            "REVIEW-002",
            "이미 작성한 후기입니다."
    );

    private final HttpStatus status;
    private final String code;
    private final String message;
}
