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
    );

    private final HttpStatus status;
    private final String code;
    private final String message;
}
