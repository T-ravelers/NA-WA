package me.nawa.settlement.dto.response;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Getter;

/**
 * 영수증 분석 항목 응답
 *
 * 인식하거나 수정한 영수증 항목의 식별자, 이름, 수량 및 단가를 반환합니다.
 */
@Getter
@Builder
public class ReceiptAnalysisItemResponse {

    private final Long id;
    private final String name;
    private final BigDecimal quantity;
    private final BigDecimal unitPrice;
}
