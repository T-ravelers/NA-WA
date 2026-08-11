package me.nawa.settlement.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 정산으로 전환할 수 있는 완료 원거래와 약속 문맥이다. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SettlementSource {
    private Long transferId;
    private Long appointmentId;
    private Long payerMemberId;
    private BigDecimal amount;
    private String journeyName;
    private String gatheringName;
    private String merchantName;
    private LocalDateTime paidAt;
    private String payerName;
}
