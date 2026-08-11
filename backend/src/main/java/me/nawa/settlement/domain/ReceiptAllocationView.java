package me.nawa.settlement.domain;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** ITEMIZED 정산 생성에 필요한 영수증 배분 조회 모델이다. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReceiptAllocationView {
    private Long appointmentMemberId;
    private Long memberId;
    private BigDecimal allocatedAmount;
}
