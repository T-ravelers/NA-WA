package me.nawa.settlement.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.nawa.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 정산 오류 코드
 *
 * 정산 처리 중 발생하는 오류의 HTTP 상태와 응답 코드를 정의합니다.
 */
@Getter
@RequiredArgsConstructor
public enum SettlementErrorCode implements ErrorCode {

    /**
     * 정산 정보 없음
     *
     * 요청한 정산이 존재하지 않거나 접근할 수 없을 때 사용합니다.
     */
    SETTLEMENT_NOT_FOUND(
        HttpStatus.NOT_FOUND,
        "SETTLEMENT-001",
        "정산 정보를 찾을 수 없습니다."
    );

    private final HttpStatus status;
    private final String code;
    private final String message;
}
