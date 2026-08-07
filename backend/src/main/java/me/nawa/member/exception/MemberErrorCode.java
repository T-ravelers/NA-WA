package me.nawa.member.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.nawa.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum MemberErrorCode implements ErrorCode {
    MEMBER_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "MEMBER-001",
            "회원 정보를 찾을 수 없습니다."
    ),

    UNSUPPORTED_LANGUAGE(
            HttpStatus.BAD_REQUEST,
            "MEMBER-002",
            "지원하지 않는 언어입니다."
    ),

    UNSUPPORTED_CURRENCY(
            HttpStatus.BAD_REQUEST,
            "MEMBER-003",
            "지원하지 않는 통화입니다."
    ),

    NO_UPDATABLE_FIELD(
            HttpStatus.BAD_REQUEST,
            "MEMBER-004",
            "변경할 항목이 없습니다."
    );

    private final HttpStatus status;
    private final String code;
    private final String message;
}
