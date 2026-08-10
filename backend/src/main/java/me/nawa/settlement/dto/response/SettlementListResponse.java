package me.nawa.settlement.dto.response;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SettlementListResponse {

    private final List<SettlementSummaryResponse> received;
    private final List<SettlementSummaryResponse> sent;
}
