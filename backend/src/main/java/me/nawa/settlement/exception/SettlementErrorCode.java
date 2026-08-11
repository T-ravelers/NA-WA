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
    ),

    SETTLEMENT_PAYMENT_NOT_ALLOWED(
        HttpStatus.CONFLICT,
        "SETTLEMENT-002",
        "현재 상태에서는 정산 결제를 진행할 수 없습니다."
    ),

    SETTLEMENT_PAYMENT_NOT_FOUND(
        HttpStatus.FORBIDDEN,
        "SETTLEMENT-003",
        "본인의 정산 부담금을 찾을 수 없습니다."
    ),

    SETTLEMENT_SOURCE_NOT_FOUND(
        HttpStatus.NOT_FOUND,
        "SETTLEMENT-004",
        "정산 가능한 원거래를 찾을 수 없습니다."
    ),

    SETTLEMENT_CREATE_INVALID(
        HttpStatus.BAD_REQUEST,
        "SETTLEMENT-005",
        "정산 생성 정보가 올바르지 않습니다."
    ),

    SETTLEMENT_CANCEL_NOT_ALLOWED(
        HttpStatus.CONFLICT,
        "SETTLEMENT-006",
        "현재 상태에서는 정산을 취소할 수 없습니다."
    ),

    SETTLEMENT_RECEIPT_INVALID(
        HttpStatus.BAD_REQUEST,
        "SETTLEMENT-007",
        "영수증 파일 정보가 올바르지 않습니다."
    ),

    SETTLEMENT_GAME_INVALID(
        HttpStatus.CONFLICT,
        "SETTLEMENT-008",
        "현재 상태에서는 게임 정산을 진행할 수 없습니다."
    );

    private final HttpStatus status;
    private final String code;
    private final String message;
}
