package me.nawa.ingest.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.nawa.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 크롤러 파이프라인 적재에서 쓰는 오류 코드입니다.
 */
@Getter
@RequiredArgsConstructor
public enum IngestErrorCode implements ErrorCode {

    UNAUTHORIZED(
        HttpStatus.UNAUTHORIZED,
        "INGEST-001",
        "적재 자격 증명이 올바르지 않습니다."
    ),

    FORBIDDEN(
        HttpStatus.FORBIDDEN,
        "INGEST-002",
        "적재 권한이 없는 계정입니다."
    ),

    INVALID_ITEM(
        HttpStatus.BAD_REQUEST,
        "INGEST-004",
        "적재할 수 없는 항목이 있습니다. 값을 고쳐서 다시 보내주세요."
    ),

    BATCH_TOO_LARGE(
        HttpStatus.PAYLOAD_TOO_LARGE,
        "INGEST-003",
        "한 번에 보낼 수 있는 건수를 초과했습니다."
    );

    private final HttpStatus status;
    private final String code;
    private final String message;
}
