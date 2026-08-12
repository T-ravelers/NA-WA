package me.nawa.settlement.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import me.nawa.common.exception.BusinessException;
import me.nawa.settlement.domain.Settlement;
import me.nawa.settlement.domain.SettlementMember;
import me.nawa.settlement.dto.response.SettlementMutationResponse;
import me.nawa.settlement.mapper.SettlementMapper;
import me.nawa.wallet.service.WalletTransferService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SettlementPaymentServiceTest {

    @Mock
    private SettlementMapper settlementMapper;
    @Mock
    private WalletTransferService walletTransferService;

    @Test
    void paySettlement_nonPendingMember_throwsPaymentNotAllowed() {
        when(settlementMapper.findByIdForUpdate(90L)).thenReturn(settlement("REQUESTED"));
        when(settlementMapper.findMemberBySettlementAndMemberForUpdate(90L, 2L))
            .thenReturn(member("NOT_REQUESTED"));

        BusinessException exception = assertThrows(BusinessException.class, () ->
            service().paySettlement(2L, 90L, "pay-key")
        );

        assertEquals("SETTLEMENT-002", exception.getErrorCode().getCode());
        verify(walletTransferService, never()).transfer(
            anyLong(), anyLong(), anyLong(), any(), any()
        );
    }

    @Test
    void paySettlement_sameKeyForPaidMember_returnsStoredTransferWithoutWalletTransfer() {
        SettlementMember paid = member("PAID");
        paid.setPaidTransferId(700L);
        paid.setPaymentIdempotencyKey("pay-key");
        when(settlementMapper.findByIdForUpdate(90L)).thenReturn(settlement("REQUESTED"));
        when(settlementMapper.findMemberBySettlementAndMemberForUpdate(90L, 2L)).thenReturn(paid);

        SettlementMutationResponse response = service().paySettlement(2L, 90L, "pay-key");

        assertEquals(700L, response.getTransferId());
        verify(walletTransferService, never()).transfer(
            anyLong(), anyLong(), anyLong(), any(), any()
        );
    }

    @Test
    void paySettlement_differentKeyForPaidMember_throwsIdempotencyConflict() {
        SettlementMember paid = member("PAID");
        paid.setPaymentIdempotencyKey("first-key");
        when(settlementMapper.findByIdForUpdate(90L)).thenReturn(settlement("REQUESTED"));
        when(settlementMapper.findMemberBySettlementAndMemberForUpdate(90L, 2L)).thenReturn(paid);

        BusinessException exception = assertThrows(BusinessException.class, () ->
            service().paySettlement(2L, 90L, "second-key")
        );

        assertEquals("SETTLEMENT-014", exception.getErrorCode().getCode());
    }

    @Test
    void paySettlement_lastPendingPayment_completesSettlement() {
        Settlement settlement = settlement("REQUESTED");
        SettlementMember pending = member("PENDING");
        when(settlementMapper.findByIdForUpdate(90L)).thenReturn(settlement);
        when(settlementMapper.findMemberBySettlementAndMemberForUpdate(90L, 2L)).thenReturn(pending);
        when(walletTransferService.transfer(2L, 2L, 1L, new BigDecimal("20"), "Settlement #90"))
            .thenReturn(700L);
        when(settlementMapper.markSettlementMemberPaid(401L, 700L, "pay-key")).thenReturn(1);
        Settlement completed = settlement("COMPLETED");
        when(settlementMapper.findById(90L)).thenReturn(completed);

        SettlementMutationResponse response = service().paySettlement(2L, 90L, "pay-key");

        verify(settlementMapper).completeSettlementIfNoPendingPayments(90L);
        assertEquals("COMPLETED", response.getSettlementStatus());
    }

    private SettlementPaymentService service() {
        return new SettlementPaymentServiceImpl(
            settlementMapper, walletTransferService, new SettlementViewerPolicy()
        );
    }

    private Settlement settlement(String status) {
        Settlement settlement = Settlement.builder()
            .createdByMemberId(1L)
            .payerMemberId(1L)
            .settlementStatus(status)
            .splitMethod("EQUAL")
            .totalAmount(new BigDecimal("100"))
            .build();
        settlement.setSettlementId(90L);
        return settlement;
    }

    private SettlementMember member(String status) {
        return new SettlementMember(
            401L, 90L, 72L, 2L, new BigDecimal("20"), status, null
        );
    }
}
