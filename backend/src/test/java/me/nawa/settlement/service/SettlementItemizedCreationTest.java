package me.nawa.settlement.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import me.nawa.common.exception.BusinessException;
import me.nawa.settlement.domain.Settlement;
import me.nawa.settlement.domain.SettlementItem;
import me.nawa.settlement.domain.SettlementItemShare;
import me.nawa.settlement.domain.SettlementMember;
import me.nawa.settlement.domain.SettlementSource;
import me.nawa.settlement.dto.request.CreateSettlementRequest;
import me.nawa.settlement.dto.request.ItemizedSettlementItemAllocationRequest;
import me.nawa.settlement.dto.request.ItemizedSettlementItemRequest;
import me.nawa.settlement.dto.response.SettlementCreateResponse;
import me.nawa.settlement.mapper.SettlementMapper;
import me.nawa.settlement.service.creation.ItemizedSettlementCreator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SettlementItemizedCreationTest {

    @Mock
    private SettlementMapper settlementMapper;

    @Test
    void create_manualItems_insertsCalculatedItemAndShareSnapshots() {
        when(settlementMapper.findActiveMembers(7L)).thenReturn(activeMembers());
        assignGeneratedIds();

        SettlementCreateResponse response = new ItemizedSettlementCreator(settlementMapper)
            .create(1L, validRequest(), source("10.00", 2), "key", "fingerprint");

        assertEquals(91L, response.getId());
        ArgumentCaptor<Settlement> settlement = ArgumentCaptor.forClass(Settlement.class);
        verify(settlementMapper).insertSettlement(settlement.capture());
        assertEquals("REQUESTED", settlement.getValue().getSettlementStatus());
        assertEquals("ITEMIZED", settlement.getValue().getSplitMethod());
        assertEquals(new BigDecimal("2.00"), settlement.getValue().getPayerShareAmount());
        assertEquals(new BigDecimal("8.00"), settlement.getValue().getReceivableAmount());

        ArgumentCaptor<List<SettlementMember>> members = ArgumentCaptor.forClass(List.class);
        verify(settlementMapper).insertSettlementMembers(members.capture());
        assertEquals(List.of(new BigDecimal("2.00"), new BigDecimal("8.00")), members.getValue().stream()
            .map(SettlementMember::getShareAmount).toList());
        assertEquals(List.of("NOT_REQUESTED", "PENDING"), members.getValue().stream()
            .map(SettlementMember::getRequestStatus).toList());

        ArgumentCaptor<SettlementItem> items = ArgumentCaptor.forClass(SettlementItem.class);
        verify(settlementMapper, org.mockito.Mockito.times(2)).insertSettlementItem(items.capture());
        assertEquals(List.of(new BigDecimal("6.00"), new BigDecimal("4.00")), items.getAllValues().stream()
            .map(SettlementItem::getLineTotal).toList());
        assertEquals(List.of((short) 0, (short) 1), items.getAllValues().stream()
            .map(SettlementItem::getSourceOrder).toList());

        ArgumentCaptor<List<SettlementItemShare>> shares = ArgumentCaptor.forClass(List.class);
        verify(settlementMapper, org.mockito.Mockito.times(2)).insertSettlementItemShares(eq(91L), shares.capture());
        assertEquals(List.of(new BigDecimal("2.00"), new BigDecimal("4.00")), shares.getAllValues().get(0).stream()
            .map(SettlementItemShare::getAllocatedAmount).toList());
        assertEquals(List.of(new BigDecimal("4.00")), shares.getAllValues().get(1).stream()
            .map(SettlementItemShare::getAllocatedAmount).toList());
    }

    @Test
    void create_itemLineTotalDoesNotMatchSourceAmount_rejectsCreation() {
        when(settlementMapper.findActiveMembers(7L)).thenReturn(activeMembers());

        BusinessException exception = assertThrows(BusinessException.class, () ->
            new ItemizedSettlementCreator(settlementMapper)
                .create(1L, validRequest(), source("9.99", 2), "key", "fingerprint")
        );

        assertEquals("SETTLEMENT-005", exception.getErrorCode().getCode());
    }

    @Test
    void create_itemAllocationQuantitiesDoNotEqualItemQuantity_rejectsCreation() {
        when(settlementMapper.findActiveMembers(7L)).thenReturn(activeMembers());
        CreateSettlementRequest request = validRequest();
        request.getItems().get(0).getAllocations().get(0).setQuantity(new BigDecimal("1"));

        BusinessException exception = assertThrows(BusinessException.class, () ->
            new ItemizedSettlementCreator(settlementMapper)
                .create(1L, request, source("10.00", 2), "key", "fingerprint")
        );

        assertEquals("SETTLEMENT-005", exception.getErrorCode().getCode());
    }

    @Test
    void create_pendingParticipantWithZeroTotalShare_rejectsCreation() {
        when(settlementMapper.findActiveMembers(7L)).thenReturn(activeMembers());
        CreateSettlementRequest request = validRequest();
        request.getItems().forEach(item -> item.setAllocations(List.of(allocation(71L, item.getQuantity()))));

        BusinessException exception = assertThrows(BusinessException.class, () ->
            new ItemizedSettlementCreator(settlementMapper)
                .create(1L, request, source("10.00", 2), "key", "fingerprint")
        );

        assertEquals("SETTLEMENT-005", exception.getErrorCode().getCode());
    }

    @Test
    void create_amountOutsideCurrencyMinimumUnit_rejectsCreation() {
        when(settlementMapper.findActiveMembers(7L)).thenReturn(activeMembers());
        CreateSettlementRequest request = validRequest();
        request.getItems().get(1).setUnitPrice(new BigDecimal("4.005"));

        BusinessException exception = assertThrows(BusinessException.class, () ->
            new ItemizedSettlementCreator(settlementMapper)
                .create(1L, request, source("10.005", 2), "key", "fingerprint")
        );

        assertEquals("SETTLEMENT-005", exception.getErrorCode().getCode());
    }

    @Test
    void create_itemNameLongerThanSnapshotColumn_rejectsCreation() {
        when(settlementMapper.findActiveMembers(7L)).thenReturn(activeMembers());
        CreateSettlementRequest request = validRequest();
        request.getItems().get(0).setName("x".repeat(201));

        BusinessException exception = assertThrows(BusinessException.class, () ->
            new ItemizedSettlementCreator(settlementMapper)
                .create(1L, request, source("10.00", 2), "key", "fingerprint")
        );

        assertEquals("SETTLEMENT-005", exception.getErrorCode().getCode());
    }

    @Test
    void create_itemQuantityWithMoreThanThreeDecimalPlaces_rejectsCreation() {
        when(settlementMapper.findActiveMembers(7L)).thenReturn(activeMembers());
        CreateSettlementRequest request = validRequest();
        request.getItems().get(0).setQuantity(new BigDecimal("3.0001"));
        request.getItems().get(0).getAllocations().get(1).setQuantity(new BigDecimal("1.0001"));

        BusinessException exception = assertThrows(BusinessException.class, () ->
            new ItemizedSettlementCreator(settlementMapper)
                .create(1L, request, source("10.0002", 4), "key", "fingerprint")
        );

        assertEquals("SETTLEMENT-005", exception.getErrorCode().getCode());
    }

    @Test
    void create_allocationQuantityWithMoreThanThreeDecimalPlaces_rejectsCreation() {
        when(settlementMapper.findActiveMembers(7L)).thenReturn(activeMembers());
        CreateSettlementRequest request = validRequest();
        request.getItems().get(0).getAllocations().get(0).setQuantity(new BigDecimal("1.9999"));
        request.getItems().get(0).getAllocations().get(1).setQuantity(new BigDecimal("1.0001"));

        BusinessException exception = assertThrows(BusinessException.class, () ->
            new ItemizedSettlementCreator(settlementMapper)
                .create(1L, request, source("10.0000", 4), "key", "fingerprint")
        );

        assertEquals("SETTLEMENT-005", exception.getErrorCode().getCode());
    }

    @Test
    void create_quantityOutsideDecimalTwelveThreeIntegerRange_rejectsCreation() {
        when(settlementMapper.findActiveMembers(7L)).thenReturn(activeMembers());
        CreateSettlementRequest request = new CreateSettlementRequest();
        request.setSourceTransferId(50L);
        request.setType("ITEMIZED");
        request.setParticipantAppointmentMemberIds(List.of(71L, 72L));
        request.setItems(List.of(item(
            "tea",
            "0.0001",
            "1000000000",
            allocation(71L, "1"),
            allocation(72L, "999999999")
        )));

        BusinessException exception = assertThrows(BusinessException.class, () ->
            new ItemizedSettlementCreator(settlementMapper)
                .create(1L, request, source("100000.0000", 4), "key", "fingerprint")
        );

        assertEquals("SETTLEMENT-005", exception.getErrorCode().getCode());
    }

    private CreateSettlementRequest validRequest() {
        CreateSettlementRequest request = new CreateSettlementRequest();
        request.setSourceTransferId(50L);
        request.setType("ITEMIZED");
        request.setParticipantAppointmentMemberIds(List.of(71L, 72L));
        request.setItems(List.of(
            item("meal", "2.00", "3", allocation(72L, "2"), allocation(71L, "1")),
            item("drink", "4.00", "1", allocation(72L, "1"))
        ));
        return request;
    }

    private ItemizedSettlementItemRequest item(
        String name,
        String unitPrice,
        String quantity,
        ItemizedSettlementItemAllocationRequest... allocations
    ) {
        ItemizedSettlementItemRequest item = new ItemizedSettlementItemRequest();
        item.setName(name);
        item.setUnitPrice(new BigDecimal(unitPrice));
        item.setQuantity(new BigDecimal(quantity));
        item.setAllocations(List.of(allocations));
        return item;
    }

    private ItemizedSettlementItemAllocationRequest allocation(Long appointmentMemberId, String quantity) {
        ItemizedSettlementItemAllocationRequest allocation = new ItemizedSettlementItemAllocationRequest();
        allocation.setAppointmentMemberId(appointmentMemberId);
        allocation.setQuantity(new BigDecimal(quantity));
        return allocation;
    }

    private ItemizedSettlementItemAllocationRequest allocation(Long appointmentMemberId, BigDecimal quantity) {
        return allocation(appointmentMemberId, quantity.toPlainString());
    }

    private SettlementSource source(String amount, int decimalPlaces) {
        SettlementSource source = new SettlementSource();
        source.setTransferId(50L);
        source.setAppointmentId(7L);
        source.setPayerMemberId(1L);
        source.setAmount(new BigDecimal(amount));
        source.setCurrencyDecimalPlaces(decimalPlaces);
        return source;
    }

    private List<SettlementMember> activeMembers() {
        return List.of(
            new SettlementMember(null, null, 71L, 1L, null, null, null),
            new SettlementMember(null, null, 72L, 2L, null, null, null)
        );
    }

    private void assignGeneratedIds() {
        doAnswer(invocation -> {
            invocation.getArgument(0, Settlement.class).setSettlementId(91L);
            return null;
        }).when(settlementMapper).insertSettlement(any(Settlement.class));
        doAnswer(invocation -> {
            SettlementItem item = invocation.getArgument(0, SettlementItem.class);
            item.setSettlementItemId(100L + item.getSourceOrder());
            return null;
        }).when(settlementMapper).insertSettlementItem(any(SettlementItem.class));
    }
}
