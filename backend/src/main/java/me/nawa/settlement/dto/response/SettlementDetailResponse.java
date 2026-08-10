package me.nawa.settlement.dto.response;

import java.math.BigDecimal;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

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
