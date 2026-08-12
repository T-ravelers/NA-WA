package me.nawa.settlement.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import me.nawa.settlement.domain.SettlementAllowedAction;
import me.nawa.settlement.domain.SettlementViewerContext;
import me.nawa.settlement.dto.response.SettlementViewerResponse;
import org.junit.jupiter.api.Test;

class SettlementViewerPolicyTest {

    private final SettlementViewerPolicy policy = new SettlementViewerPolicy();

    @Test
    void resolve_pendingParticipant_exposesOnlyPayAndExactPayableAmount() {
        SettlementViewerResponse viewer = policy.resolve(SettlementViewerContext.builder()
            .role("PARTICIPANT")
            .shareAmount(new BigDecimal("18000"))
            .requestStatus("PENDING")
            .settlementStatus("REQUESTED")
            .build());

        assertEquals(new BigDecimal("18000"), viewer.getShareAmount());
        assertEquals(new BigDecimal("18000"), viewer.getPayableAmount());
        assertEquals(List.of(SettlementAllowedAction.PAY), viewer.getAllowedActions());
    }

    @Test
    void resolve_paidParticipant_exposesNoMutationAndZeroPayableAmount() {
        SettlementViewerResponse viewer = policy.resolve(SettlementViewerContext.builder()
            .role("PARTICIPANT")
            .shareAmount(new BigDecimal("18000"))
            .requestStatus("PAID")
            .settlementStatus("REQUESTED")
            .build());

        assertEquals(new BigDecimal("18000"), viewer.getShareAmount());
        assertEquals(BigDecimal.ZERO, viewer.getPayableAmount());
        assertEquals(List.of(), viewer.getAllowedActions());
    }

    @Test
    void resolve_creator_exposesNoMutation() {
        SettlementViewerResponse viewer = policy.resolve(SettlementViewerContext.builder()
            .role("CREATOR")
            .shareAmount(new BigDecimal("4000"))
            .requestStatus("NOT_REQUESTED")
            .settlementStatus("REQUESTED")
            .build());

        assertEquals(
            List.of(),
            viewer.getAllowedActions()
        );
        assertEquals(BigDecimal.ZERO, viewer.getPayableAmount());
    }

    @Test
    void allowedActions_definesOnlyPay() {
        assertEquals(List.of(SettlementAllowedAction.PAY), Arrays.asList(SettlementAllowedAction.values()));
    }
}
