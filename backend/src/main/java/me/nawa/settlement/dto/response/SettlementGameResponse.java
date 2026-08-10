package me.nawa.settlement.dto.response;

import java.math.BigDecimal;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SettlementGameResponse {

    private final Long id;
    private final String gameType;
    private final BigDecimal amount;
    private final Integer liableCount;
    private final List<SettlementParticipantResponse> participants;
    private final Integer agreementCount;
    private final String lifecycle;
    private final String viewerRole;
    private final String declinedBy;
    private final String journeyName;
    private final String merchantName;
    private final String originalPayer;
    private final List<SettlementParticipantResponse> liableParticipants;
    private final String transactionId;
    private final String currentParticipantName;
}
