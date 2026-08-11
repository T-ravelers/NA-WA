package me.nawa.settlement.dto.response;

import java.math.BigDecimal;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

/**
 * 영수증 분석 응답
 *
 * 영수증 분석 식별자와 인식 합계, 항목 목록을 반환합니다.
 */
@Getter
@Builder
public class ReceiptAnalysisResponse {

    private final Long receiptAnalysisId;
    private final BigDecimal recognizedTotal;
    private final List<ReceiptAnalysisItemResponse> items;
}
