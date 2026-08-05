package me.nawa.wallet.domain.enums;

public enum TransferType {
    TOPUP,                         // 충전
    QR_PAYMENT,                    // QR 결제
    SETTLEMENT,                    // 정산
    DEPOSIT_HOLD,                  // 보증금 홀드
    DEPOSIT_REFUND,                // 보증금 환불
    DEPOSIT_FORFEIT_DISTRIBUTION,  // 보증금 몰수 분배
    REVERSAL                       // 거래 취소/역거래
}
