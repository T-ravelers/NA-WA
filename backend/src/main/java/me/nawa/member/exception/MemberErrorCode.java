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
    ),

    UNSUPPORTED_NATIONALITY(
            HttpStatus.BAD_REQUEST,
            "MEMBER-005",
            "지원하지 않는 국가 코드입니다."
    ),

    INVALID_DISPLAY_NAME(
            HttpStatus.BAD_REQUEST,
            "MEMBER-006",
            "표시 이름이 올바르지 않습니다."
    ),

    INVALID_PROFILE_IMAGE_URL(
            HttpStatus.BAD_REQUEST,
            "MEMBER-007",
            "프로필 이미지 주소가 올바르지 않습니다."
    ),

    ONBOARDING_FIELD_MISSING(
            HttpStatus.BAD_REQUEST,
            "MEMBER-008",
            "온보딩에 필요한 항목이 비어 있습니다."
    ),

    ALREADY_MERCHANT(
            HttpStatus.CONFLICT,
            "MEMBER-009",
            "이미 가맹점으로 등록된 계정입니다."
    );

    private final HttpStatus status;
    private final String code;
    private final String message;
}
