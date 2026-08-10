package me.nawa.settlement.dto.response;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SettlementSummaryResponse {

    private final Long id;
    private final String title;
    private final BigDecimal amount;
    private final String type;
    private final String status;
}
