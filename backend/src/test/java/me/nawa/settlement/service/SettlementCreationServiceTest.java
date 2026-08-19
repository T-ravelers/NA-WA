package me.nawa.settlement.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import me.nawa.common.exception.BusinessException;
import me.nawa.settlement.domain.Settlement;
import me.nawa.settlement.dto.request.CreateSettlementRequest;
import me.nawa.settlement.dto.request.ItemizedSettlementItemAllocationRequest;
import me.nawa.settlement.dto.request.ItemizedSettlementItemRequest;
import me.nawa.settlement.dto.response.SettlementCreateResponse;
import me.nawa.settlement.mapper.SettlementMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

@ExtendWith(MockitoExtension.class)
class SettlementCreationServiceTest {

    @Mock
    private SettlementMapper settlementMapper;
    @Mock
    private SettlementCreationAttemptService creationAttemptService;

    @Test
    void createSettlement_uniqueConflictAfterAttemptTransaction_returnsWinner() {
        CreateSettlementRequest request = request();
        Settlement winner = Settlement.builder()
            .createdByMemberId(1L)
            .sourceTransferId(50L)
            .idempotencyKey("race-key")
            .settlementStatus("REQUESTED")
            .splitMethod("EQUAL")
            .totalAmount(new BigDecimal("100"))
            .build();
        winner.setSettlementId(91L);
        when(settlementMapper.findByCreatorAndIdempotencyKey(1L, "race-key"))
            .thenReturn(null, winner);
        when(creationAttemptService.create(
            eq(1L), eq(7L), eq("race-key"), any(), eq(request)
        )).thenAnswer(invocation -> {
            winner.setRequestFingerprint(invocation.getArgument(3, String.class));
            throw new DuplicateKeyException("simulated unique conflict");
        });

        SettlementCreateResponse response = service().createSettlement(
            1L, 7L, "race-key", request
        );

        assertEquals(91L, response.getId());
    }

    @Test
    void createSettlement_gameType_rejectsUnsupportedTypeBeforeCreatingAttempt() {
        CreateSettlementRequest request = request();
        request.setType("GAME");

        BusinessException exception = assertThrows(BusinessException.class, () ->
            service().createSettlement(1L, 7L, "game-key", request)
        );

        assertEquals("SETTLEMENT-005", exception.getErrorCode().getCode());
        verifyNoInteractions(creationAttemptService);
    }

    @Test
    void createSettlement_duplicateParticipant_rejectsBeforeCreatingAttempt() {
        CreateSettlementRequest request = request();
        request.setParticipantAppointmentMemberIds(List.of(71L, 71L));

        BusinessException exception = assertThrows(BusinessException.class, () ->
            service().createSettlement(1L, 7L, "duplicate-key", request)
        );

        assertEquals("SETTLEMENT-005", exception.getErrorCode().getCode());
        verifyNoInteractions(creationAttemptService);
    }

    @Test
    void createSettlement_differentItemizedInputWithSameKey_rejectsIdempotencyConflict() {
        CreateSettlementRequest original = itemizedRequest("2");
        CreateSettlementRequest changed = itemizedRequest("1");
        Settlement winner = Settlement.builder()
            .createdByMemberId(1L)
            .sourceTransferId(50L)
            .idempotencyKey("itemized-key")
            .settlementStatus("REQUESTED")
            .splitMethod("ITEMIZED")
            .totalAmount(new BigDecimal("10"))
            .build();
        winner.setSettlementId(91L);
        when(settlementMapper.findByCreatorAndIdempotencyKey(1L, "itemized-key"))
            .thenReturn(null, winner);
        when(creationAttemptService.create(eq(1L), eq(7L), eq("itemized-key"), any(), eq(original)))
            .thenAnswer(invocation -> {
                winner.setRequestFingerprint(invocation.getArgument(3, String.class));
                return SettlementCreateResponse.builder().id(91L).build();
            });

        SettlementCreateResponse first = service().createSettlement(1L, 7L, "itemized-key", original);
        BusinessException exception = assertThrows(BusinessException.class, () ->
            service().createSettlement(1L, 7L, "itemized-key", changed)
        );

        assertEquals(91L, first.getId());
        assertEquals("SETTLEMENT-009", exception.getErrorCode().getCode());
    }

    @Test
    void createSettlement_sameItemizedAllocationsInDifferentOrder_returnsIdempotentSettlement() {
        CreateSettlementRequest original = itemizedRequest("2");
        CreateSettlementRequest reordered = itemizedRequest("2");
        ItemizedSettlementItemRequest item = reordered.getItems().get(0);
        item.setAllocations(List.of(item.getAllocations().get(1), item.getAllocations().get(0)));
        Settlement winner = Settlement.builder()
            .createdByMemberId(1L)
            .sourceTransferId(50L)
            .idempotencyKey("itemized-order-key")
            .settlementStatus("REQUESTED")
            .splitMethod("ITEMIZED")
            .totalAmount(new BigDecimal("10"))
            .build();
        winner.setSettlementId(92L);
        when(settlementMapper.findByCreatorAndIdempotencyKey(1L, "itemized-order-key"))
            .thenReturn(null, winner);
        when(creationAttemptService.create(eq(1L), eq(7L), eq("itemized-order-key"), any(), eq(original)))
            .thenAnswer(invocation -> {
                winner.setRequestFingerprint(invocation.getArgument(3, String.class));
                return SettlementCreateResponse.builder().id(92L).build();
            });

        SettlementCreateResponse first = service().createSettlement(
            1L, 7L, "itemized-order-key", original
        );
        SettlementCreateResponse retry = service().createSettlement(
            1L, 7L, "itemized-order-key", reordered
        );

        assertEquals(92L, first.getId());
        assertEquals(92L, retry.getId());
    }

    @Test
    void createSettlement_sameKeyButDifferentReceipt_rejectsIdempotencyConflict() {
        CreateSettlementRequest original = request();
        original.setReceiptId(11L);
        CreateSettlementRequest changed = request();
        changed.setReceiptId(22L);
        Settlement winner = Settlement.builder()
            .createdByMemberId(1L)
            .sourceTransferId(50L)
            .idempotencyKey("receipt-key")
            .settlementStatus("REQUESTED")
            .splitMethod("EQUAL")
            .totalAmount(new BigDecimal("10"))
            .build();
        winner.setSettlementId(93L);
        when(settlementMapper.findByCreatorAndIdempotencyKey(1L, "receipt-key"))
            .thenReturn(null, winner);
        when(creationAttemptService.create(eq(1L), eq(7L), eq("receipt-key"), any(), eq(original)))
            .thenAnswer(invocation -> {
                winner.setRequestFingerprint(invocation.getArgument(3, String.class));
                return SettlementCreateResponse.builder().id(93L).build();
            });

        SettlementCreateResponse first = service().createSettlement(1L, 7L, "receipt-key", original);
        BusinessException exception = assertThrows(BusinessException.class, () ->
            service().createSettlement(1L, 7L, "receipt-key", changed)
        );

        assertEquals(93L, first.getId());
        assertEquals("SETTLEMENT-009", exception.getErrorCode().getCode());
    }

    private CreateSettlementRequest request() {
        CreateSettlementRequest request = new CreateSettlementRequest();
        request.setSourceTransferId(50L);
        request.setType("EQUAL");
        request.setParticipantAppointmentMemberIds(List.of(71L, 72L));
        return request;
    }

    private SettlementCreationServiceImpl service() {
        return new SettlementCreationServiceImpl(
            settlementMapper, creationAttemptService
        );
    }

    private CreateSettlementRequest itemizedRequest(String firstAllocationQuantity) {
        ItemizedSettlementItemAllocationRequest first = new ItemizedSettlementItemAllocationRequest();
        first.setAppointmentMemberId(71L);
        first.setQuantity(new BigDecimal(firstAllocationQuantity));
        ItemizedSettlementItemAllocationRequest second = new ItemizedSettlementItemAllocationRequest();
        second.setAppointmentMemberId(72L);
        second.setQuantity(new BigDecimal("1"));
        ItemizedSettlementItemRequest item = new ItemizedSettlementItemRequest();
        item.setName("meal");
        item.setUnitPrice(new BigDecimal("5"));
        item.setQuantity(new BigDecimal("3"));
        item.setAllocations(List.of(first, second));
        CreateSettlementRequest request = new CreateSettlementRequest();
        request.setSourceTransferId(50L);
        request.setType("ITEMIZED");
        request.setParticipantAppointmentMemberIds(List.of(71L, 72L));
        request.setItems(List.of(item));
        return request;
    }
}
