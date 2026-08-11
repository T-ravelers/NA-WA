package me.nawa.report.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.nawa.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ReportErrorCode implements ErrorCode {

    REPORT_NOT_FOUND(
        HttpStatus.NOT_FOUND,
        "REPORT-001",
        "Report was not found."
    ),

    REPORT_JOURNEY_FORBIDDEN(
        HttpStatus.FORBIDDEN,
        "REPORT-002",
        "You do not have access to this Journey."
    ),

    INVALID_REPORT_INPUT(
        HttpStatus.BAD_REQUEST,
        "REPORT-003",
        "Report input is invalid."
    ),

    JOURNEY_NOT_COMPLETED(
        HttpStatus.BAD_REQUEST,
        "REPORT-004",
        "Only completed Journeys can generate a Report."
    ),

    REPORT_ALREADY_EXISTS(
        HttpStatus.CONFLICT,
        "REPORT-005",
        "An active Report already exists for this Journey."
    ),

    REPORT_JOURNEY_NOT_FOUND(
        HttpStatus.NOT_FOUND,
        "REPORT-006",
        "Journey was not found."
    ),

    INVALID_REPORT_EXPENSE(
        HttpStatus.BAD_REQUEST,
        "REPORT-007",
        "Selected expense is invalid."
    ),

    REPORT_EXPENSE_ALREADY_LINKED(
        HttpStatus.CONFLICT,
        "REPORT-008",
        "Selected expense is already linked to a Journey."
    );

    private final HttpStatus status;
    private final String code;
    private final String message;
}
