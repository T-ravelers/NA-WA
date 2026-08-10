package me.nawa.settlement.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SettlementParticipantResponse {

    private final Long id;
    private final String name;
    private final String initials;
    private final String consentStatus;
}
