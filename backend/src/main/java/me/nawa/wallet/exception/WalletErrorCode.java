package me.nawa.wallet.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.nawa.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum WalletErrorCode implements ErrorCode {

    WALLET_NOT_FOUND(
      HttpStatus.NOT_FOUND,
      "WALLET-001",
        "지갑 정보를 찾을 수 없습니다."
    ),

    TOPUP_METHOD_NOT_SUPPORTED(
        HttpStatus.BAD_REQUEST,
          "WALLET-002",
              "지원하지 않는 충전 수단입니다."
    ),

    UNSUPPORTED_CURRENCY(
        HttpStatus.BAD_REQUEST,
          "WALLET-003",
              "지원하지 않는 통화입니다."
    ),

    INVALID_TOPUP_AMOUNT(
        HttpStatus.BAD_REQUEST,
        "WALLET-004",
            "충전 금액은 0보다 커야 합니다."
    ),

    TRANSACTION_NOT_FOUND(
        HttpStatus.NOT_FOUND,
    "WALLET-005",
        "거래 내역을 찾을 수 없습니다."
    ),

    TRANSACTION_FORBIDDEN(
        HttpStatus.FORBIDDEN,
    "WALLET-006",
        "본인의 거래만 조회할 수 있습니다."
    ),

    IDEMPOTENCY_KEY_REQUIRED(
        HttpStatus.BAD_REQUEST,
        "WALLET-007",
        "Idempotency-Key 헤더가 필요합니다."
    ),

    STRIPE_WALLET_NOT_ACTIVE(
        HttpStatus.FORBIDDEN,
        "WALLET-008",
        "지갑 상태에서는 충전할 수 없습니다."
    ),

    IDEMPOTENCY_KEY_CONFLICT(
        HttpStatus.CONFLICT,
        "WALLET-009",
        "동일한 Idempotency-Key로 다른 요청이 이미 처리되었습니다."
    ),

    STRIPE_UNAVAILABLE(
        HttpStatus.SERVICE_UNAVAILABLE,
        "WALLET-010",
        "결제 서비스에 일시적으로 연결할 수 없습니다."
    ),

    TOPUP_NOT_FOUND(
        HttpStatus.NOT_FOUND,
        "WALLET-011",
        "충전 내역을 찾을 수 없습니다."
    ),

    TOPUP_FORBIDDEN(
        HttpStatus.FORBIDDEN,
        "WALLET-012",
        "본인의 충전 내역만 조회할 수 있습니다."
    ),

    STRIPE_INVALID_SIGNATURE(
        HttpStatus.BAD_REQUEST,
        "WALLET-013",
        "Webhook 서명 검증에 실패했습니다."
    ),

    INVALID_SETTLEMENT_TRANSFER(
        HttpStatus.BAD_REQUEST,
        "WALLET-014",
        "정산 이체 금액 또는 대상이 올바르지 않습니다."
    ),

    INSUFFICIENT_BALANCE(
        HttpStatus.CONFLICT,
        "WALLET-015",
        "지갑 잔액이 부족합니다."
    ),

    SETTLEMENT_WALLET_NOT_ACTIVE(
        HttpStatus.FORBIDDEN,
        "WALLET-016",
        "현재 지갑 상태에서는 정산 이체를 할 수 없습니다."
    ),

    WALLET_NOT_ACTIVE(
        HttpStatus.FORBIDDEN,
        "WALLET-017",
        "지갑이 활성 상태가 아닙니다."
    ),

    QR_PAYMENT_NOT_FOUND(
        HttpStatus.NOT_FOUND,
    "WALLET-015",
        "QR 결제 정보를 찾을 수 없습니다."
    ),

    QR_PAYMENT_EXPIRED(
        HttpStatus.GONE,
    "WALLET-016",
        "만료된 QR 결제입니다."
    ),

    QR_PAYMENT_ALREADY_COMPLETED(
        HttpStatus.CONFLICT,
    "WALLET-017",
        "이미 결제가 완료된 QR입니다."
    ),

    QR_PAYMENT_NOT_ACTIVE(
        HttpStatus.CONFLICT,
    "WALLET-018",
        "현재 사용할 수 없는 QR 결제입니다."
    ),

    QR_SELF_PAYMENT_NOT_ALLOWED(
        HttpStatus.BAD_REQUEST,
    "WALLET-019",
        "본인에게 결제할 수 없습니다."
    ),

    QR_PAYEE_WALLET_NOT_ACTIVE(
        HttpStatus.CONFLICT,
    "WALLET-020",
        "수취인의 지갑을 현재 사용할 수 없습니다."
    );

    private final HttpStatus status;
    private final String code;
    private final String message;
}
