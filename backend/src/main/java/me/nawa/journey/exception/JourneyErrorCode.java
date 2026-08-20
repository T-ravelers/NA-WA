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
    ),

    JOURNEY_ITEM_DUPLICATE(
        HttpStatus.CONFLICT,
        "JOURNEY-004",
        "같은 Journey에 동일한 항목과 방문 날짜가 이미 등록되어 있습니다."
    ),

    JOURNEY_ITEM_NOT_FOUND(
        HttpStatus.NOT_FOUND,
        "JOURNEY-005",
        "추가할 Explore 항목을 찾을 수 없습니다."
    ),

    JOURNEY_ITEM_TYPE_UNSUPPORTED(
        HttpStatus.BAD_REQUEST,
        "JOURNEY-006",
        "Journey에 추가할 수 없는 Explore 항목 유형입니다."
    ),

    JOURNEY_ITEM_DATE_OUT_OF_RANGE(
        HttpStatus.BAD_REQUEST,
        "JOURNEY-007",
        "방문 날짜가 Journey 기간을 벗어났습니다."
    ),

    JOURNEY_ITEM_DISPLAY_ORDER_INVALID(
        HttpStatus.BAD_REQUEST,
        "JOURNEY-008",
        "displayOrder는 0 이상이어야 합니다."
    ),

    JOURNEY_DATE_RANGE_CONFLICT(
        HttpStatus.CONFLICT,
        "JOURNEY-009",
        "변경할 Journey 기간을 벗어나는 일정이 있습니다."
    ),

    JOURNEY_SCHEDULE_NOT_FOUND(
        HttpStatus.NOT_FOUND,
        "JOURNEY-010",
        "Journey 일정을 찾을 수 없습니다."
    ),

    JOURNEY_APPOINTMENT_HOST_DELETE_CONFLICT(
        HttpStatus.CONFLICT,
        "JOURNEY-011",
        "방장으로 참여 중인 Appointment 일정은 삭제할 수 없습니다."
    );

    private final HttpStatus status;
    private final String code;
    private final String message;
}
