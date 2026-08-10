package me.nawa.settlement.dto.response;

import java.math.BigDecimal;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SettlementGameResultResponse {

    private final Long settlementId;
    private final BigDecimal amount;
    private final List<SettlementParticipantResponse> liableParticipants;
}
