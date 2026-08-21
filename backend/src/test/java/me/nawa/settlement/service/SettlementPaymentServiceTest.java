package me.nawa.settlement.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import me.nawa.common.exception.BusinessException;
import me.nawa.settlement.domain.Settlement;
import me.nawa.settlement.domain.SettlementMember;
import me.nawa.settlement.dto.response.SettlementMutationResponse;
import me.nawa.settlement.event.SettlementCompletedEvent;
import me.nawa.settlement.event.SettlementPaidEvent;
import me.nawa.settlement.mapper.SettlementMapper;
import me.nawa.wallet.service.WalletTransferService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.ApplicationEventPublisher;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SettlementPaymentServiceTest {

    @Mock
    private SettlementMapper settlementMapper;
    @Mock
    private WalletTransferService walletTransferService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

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
        // 완료 UPDATE가 실제로 한 줄을 바꿨을 때만 완료 알림이 나간다.
        when(settlementMapper.completeSettlementIfNoPendingPayments(
            eq(90L), any(LocalDateTime.class)
        )).thenReturn(1);
        Settlement completed = settlement("COMPLETED");
        when(settlementMapper.findById(90L)).thenReturn(completed);

        SettlementMutationResponse response = service().paySettlement(2L, 90L, "pay-key");

        verify(settlementMapper).completeSettlementIfNoPendingPayments(eq(90L), any(LocalDateTime.class));
        assertEquals("COMPLETED", response.getSettlementStatus());
        verify(eventPublisher).publishEvent(new SettlementPaidEvent(90L, 2L));
        verify(eventPublisher).publishEvent(new SettlementCompletedEvent(90L));
    }

    @Test
    void paySettlement_stillHasPendingMembers_publishesPaidButNotCompleted() {
        SettlementMember pending = member("PENDING");
        when(settlementMapper.findByIdForUpdate(90L)).thenReturn(settlement("REQUESTED"));
        when(settlementMapper.findMemberBySettlementAndMemberForUpdate(90L, 2L)).thenReturn(pending);
        when(walletTransferService.transfer(2L, 2L, 1L, new BigDecimal("20"), "Settlement #90"))
            .thenReturn(700L);
        when(settlementMapper.markSettlementMemberPaid(401L, 700L, "pay-key")).thenReturn(1);
        // 아직 낼 사람이 남아 완료 UPDATE가 아무 줄도 바꾸지 못한 상황이다.
        when(settlementMapper.completeSettlementIfNoPendingPayments(
            eq(90L), any(LocalDateTime.class)
        )).thenReturn(0);

        service().paySettlement(2L, 90L, "pay-key");

        verify(eventPublisher).publishEvent(new SettlementPaidEvent(90L, 2L));
        verify(eventPublisher, never()).publishEvent(new SettlementCompletedEvent(90L));
    }

    @Test
    void paySettlement_idempotentRetry_publishesNothing() {
        SettlementMember paid = member("PAID");
        paid.setPaidTransferId(700L);
        paid.setPaymentIdempotencyKey("pay-key");
        when(settlementMapper.findByIdForUpdate(90L)).thenReturn(settlement("REQUESTED"));
        when(settlementMapper.findMemberBySettlementAndMemberForUpdate(90L, 2L)).thenReturn(paid);

        service().paySettlement(2L, 90L, "pay-key");

        // 돈이 움직이지 않은 요청이라 알릴 일도 없다. 재시도마다 알림이 쌓이면 안 된다.
        verify(eventPublisher, never()).publishEvent(any());
    }

    private SettlementPaymentService service() {
        return new SettlementPaymentServiceImpl(
            settlementMapper, walletTransferService, new SettlementViewerPolicy(), eventPublisher
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
