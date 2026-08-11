package me.nawa.settlement.dto.response;

import java.math.BigDecimal;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

/**
 * 정산 상세 응답
 *
 * 정산의 금액, 진행 상태, 원거래와 항목 및 결제자 정보를 반환합니다.
 */
@Getter
@Builder
public class SettlementDetailResponse {

    private final Long id;
    private final String type;
    private final BigDecimal amount;
    private final String status;
    private final String requestedBy;
    private final String gatheringName;
    private final String merchantName;
    private final List<String> items;
    private final String transactionId;
    private final String paidBy;
}
