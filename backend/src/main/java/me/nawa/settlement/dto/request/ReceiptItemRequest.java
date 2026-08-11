package me.nawa.settlement.dto.request;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 영수증 항목 정보
 *
 * 영수증 분석 결과에서 수정하거나 확정할 항목의 이름, 수량 및 단가를 전달합니다.
 */
@Getter
@Setter
@NoArgsConstructor
public class ReceiptItemRequest {

    private Long id;
    private String name;
    private BigDecimal quantity;
    private BigDecimal unitPrice;
}
