package me.nawa.settlement.dto.request;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 영수증 항목 배분 정보
 *
 * 영수증 항목을 참여자에게 배분할 수량을 전달합니다.
 */
@Getter
@Setter
@NoArgsConstructor
public class ReceiptAllocationRequest {

    private Long itemId;
    private Long appointmentMemberId;
    private BigDecimal quantity;
}
