package me.nawa.wallet.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WalletTransfer {

    private Long transferId;             // 거래 PK
    private String transferNumber;       // 사용자 조회·CS·감사용 거래 번호
    private String transferType;         // 거래 종류 (TOPUP | QR_PAYMENT | SETTLEMENT | ...)
    private String transferStatus;       // 거래 상태 (PENDING | COMPLETED | FAILED | CANCELLED | REVERSED)
    private BigDecimal amount;           // 거래 금액
    private String memo;                 // 거래 메모
    private String spendingCategory;     // 지출 카테고리
    private String spendingType;         // 소비 범위 (PERSONAL | SHARED)
    private LocalDateTime completedAt;   // 거래 완료 시각 (완료 전이면 null)
    private LocalDateTime createdAt;     // 거래 생성 시각
    private Long initiatorMemberId;      // 거래를 시작한 회원 (시스템 자동 이체는 null)
    private String idempotencyKey;       // 중복 처리 방지 키
}
