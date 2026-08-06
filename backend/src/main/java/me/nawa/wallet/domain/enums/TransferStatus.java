package me.nawa.wallet.domain.enums;

public enum TransferStatus {
    PENDING,   // 처리 중
    COMPLETED, // 완료
    FAILED,    // 실패
    CANCELLED, // 취소됨
    REVERSED   // 역거래로 되돌려짐
}
