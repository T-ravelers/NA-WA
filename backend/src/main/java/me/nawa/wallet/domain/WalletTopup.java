package me.nawa.wallet.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class WalletTopup {

    private BigDecimal sourceAmount;            // 충전 시 입력한 원래 통화 기준 금액
    private String sourceCurrencyCode;          // 원래 통화 코드 (예: USD)
    private BigDecimal exchangeRateKrwPerUnit;  // 원래 통화 1단위당 KRW 환율
    private LocalDateTime quotedAt;             // 환율 스냅샷을 뜬 시각
    private Long topupId;                       // 충전 PK — 커서 페이지네이션에 사용
    private String topupStatus;                 // 충전 상태 (QUOTED | COMPLETED | FAILED | CANCELLED)
    private BigDecimal krwAmount;                // 환전 후 KRW 충전 금액
    private LocalDateTime completedAt;          // 충전 완료 시각 (완료 전이면 null)
    private LocalDateTime createdAt;            // 충전 요청 생성 시각
}
