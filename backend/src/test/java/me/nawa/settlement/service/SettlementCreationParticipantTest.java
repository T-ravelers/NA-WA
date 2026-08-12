package me.nawa.settlement.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import me.nawa.common.exception.BusinessException;
import me.nawa.settlement.domain.Settlement;
import me.nawa.settlement.domain.SettlementMember;
import me.nawa.settlement.domain.SettlementSource;
import me.nawa.settlement.dto.request.CreateSettlementRequest;
import me.nawa.settlement.mapper.SettlementMapper;
import me.nawa.settlement.service.creation.EqualSettlementCreator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SettlementCreationParticipantTest {

    @Mock
    private SettlementMapper settlementMapper;

    @Test
    void createEqual_selectedActiveMembers_insertsRequestedPayerAndPendingParticipants() {
        when(settlementMapper.findActiveMembers(7L)).thenReturn(activeMembers());
        assignSettlementId();

        new EqualSettlementCreator(settlementMapper, new SettlementAmountAllocator())
            .create(1L, request(List.of(71L, 72L)), source(), "equal-key", "fingerprint");

        ArgumentCaptor<List<SettlementMember>> members = ArgumentCaptor.forClass(List.class);
        verify(settlementMapper).insertSettlementMembers(members.capture());
        assertEquals(List.of(71L, 72L), members.getValue().stream()
            .map(SettlementMember::getAppointmentMemberId).toList());
        assertEquals(List.of(new BigDecimal("50"), new BigDecimal("50")), members.getValue().stream()
            .map(SettlementMember::getShareAmount).toList());
        assertEquals(List.of("NOT_REQUESTED", "PENDING"), members.getValue().stream()
            .map(SettlementMember::getRequestStatus).toList());

        ArgumentCaptor<Settlement> settlement = ArgumentCaptor.forClass(Settlement.class);
        verify(settlementMapper).insertSettlement(settlement.capture());
        assertEquals("REQUESTED", settlement.getValue().getSettlementStatus());
        assertEquals(new BigDecimal("50"), settlement.getValue().getPayerShareAmount());
        assertEquals(new BigDecimal("50"), settlement.getValue().getReceivableAmount());
    }

    @Test
    void createEqual_missingPayerAppointmentMember_rejectsCreation() {
        when(settlementMapper.findActiveMembers(7L)).thenReturn(activeMembers());

        BusinessException exception = assertThrows(BusinessException.class, () ->
            new EqualSettlementCreator(settlementMapper, new SettlementAmountAllocator())
                .create(1L, request(List.of(72L, 73L)), source(), "equal-key", "fingerprint")
        );

        assertEquals("SETTLEMENT-005", exception.getErrorCode().getCode());
    }

    @Test
    void createEqual_inactiveParticipant_rejectsCreation() {
        when(settlementMapper.findActiveMembers(7L)).thenReturn(activeMembers());

        BusinessException exception = assertThrows(BusinessException.class, () ->
            new EqualSettlementCreator(settlementMapper, new SettlementAmountAllocator())
                .create(1L, request(List.of(71L, 74L)), source(), "equal-key", "fingerprint")
        );

        assertEquals("SETTLEMENT-005", exception.getErrorCode().getCode());
    }

    private SettlementSource source() {
        SettlementSource source = new SettlementSource();
        source.setTransferId(50L);
        source.setAppointmentId(7L);
        source.setPayerMemberId(1L);
        source.setAmount(new BigDecimal("100"));
        source.setCurrencyDecimalPlaces(0);
        return source;
    }

    private List<SettlementMember> activeMembers() {
        return List.of(
            new SettlementMember(null, null, 71L, 1L, null, null, null),
            new SettlementMember(null, null, 72L, 2L, null, null, null),
            new SettlementMember(null, null, 73L, 3L, null, null, null)
        );
    }

    private CreateSettlementRequest request(List<Long> participantIds) {
        CreateSettlementRequest request = new CreateSettlementRequest();
        request.setSourceTransferId(50L);
        request.setType("EQUAL");
        request.setParticipantAppointmentMemberIds(participantIds);
        return request;
    }

    private void assignSettlementId() {
        doAnswer(invocation -> {
            invocation.getArgument(0, Settlement.class).setSettlementId(90L);
            return null;
        }).when(settlementMapper).insertSettlement(any(Settlement.class));
    }
}
