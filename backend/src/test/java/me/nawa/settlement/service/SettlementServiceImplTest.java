package me.nawa.settlement.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import me.nawa.settlement.domain.Settlement;
import me.nawa.settlement.domain.SettlementMember;
import me.nawa.settlement.domain.SettlementSource;
import me.nawa.settlement.domain.SettlementSummary;
import me.nawa.settlement.domain.SettlementParticipant;
import me.nawa.settlement.domain.SettlementDetail;
import me.nawa.settlement.dto.request.CreateSettlementRequest;
import me.nawa.settlement.dto.response.SettlementCreateResponse;
import me.nawa.settlement.dto.response.SettlementListResponse;
import me.nawa.settlement.dto.response.SettlementCandidateResponse;
import me.nawa.settlement.dto.response.SettlementDetailResponse;
import me.nawa.settlement.dto.response.ReceiptAnalysisResponse;
import me.nawa.settlement.dto.request.ReceiptItemRequest;
import me.nawa.settlement.dto.request.ReceiptItemUpdateRequest;
import me.nawa.settlement.dto.request.ReceiptAllocationRequest;
import me.nawa.settlement.dto.request.ReceiptAllocationUpdateRequest;
import me.nawa.settlement.dto.request.GameConsentRequest;
import me.nawa.settlement.dto.request.GameCreateRequest;
import me.nawa.settlement.dto.response.SettlementGameResultResponse;
import me.nawa.settlement.dto.response.SettlementGameResponse;
import me.nawa.settlement.mapper.SettlementMapper;
import me.nawa.wallet.service.WalletTransferService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
class SettlementServiceImplTest {

    @Mock
    private SettlementMapper settlementMapper;

    @Mock
    private WalletTransferService walletTransferService;

    @Test
    void createEqualSettlement_preservesTotalAndRequestsOnlyOtherParticipants() {
        SettlementSource source = new SettlementSource();
        source.setTransferId(50L);
        source.setAppointmentId(7L);
        source.setPayerMemberId(1L);
        source.setAmount(new BigDecimal("100.00"));
        when(settlementMapper.findSourceForCreate(50L, 1L)).thenReturn(source);
        when(settlementMapper.findActiveMembers(7L)).thenReturn(List.of(
            new SettlementMember(null, null, 71L, 1L, null, null, null),
            new SettlementMember(null, null, 72L, 2L, null, null, null),
            new SettlementMember(null, null, 73L, 3L, null, null, null)
        ));
        doAnswer(invocation -> {
            invocation.getArgument(0, Settlement.class).setSettlementId(90L);
            return null;
        }).when(settlementMapper).insertSettlement(any(Settlement.class));
        CreateSettlementRequest request = new CreateSettlementRequest();
        request.setSourceTransferId(50L);
        request.setType("EQUAL");
        request.setParticipantIds(List.of(1L, 2L, 3L));

        SettlementCreateResponse response = new SettlementServiceImpl(
            settlementMapper, walletTransferService
        ).createSettlement(1L, request);

        assertEquals(90L, response.getId());
        ArgumentCaptor<Settlement> settlement = ArgumentCaptor.forClass(Settlement.class);
        verify(settlementMapper).insertSettlement(settlement.capture());
        assertEquals(new BigDecimal("33.34"), settlement.getValue().getPayerShareAmount());
        assertEquals(new BigDecimal("66.66"), settlement.getValue().getReceivableAmount());
        ArgumentCaptor<List<SettlementMember>> members = ArgumentCaptor.forClass(List.class);
        verify(settlementMapper).insertSettlementMembers(members.capture());
        assertEquals(List.of("NOT_REQUESTED", "PENDING", "PENDING"), members.getValue().stream()
            .map(SettlementMember::getRequestStatus).toList());
    }

    @Test
    void cancelSettlement_cancelsRequestedSettlementCreatedByRequester() {
        Settlement settlement = Settlement.builder()
            .appointmentId(7L).createdByMemberId(1L).payerMemberId(1L).sourceTransferId(50L)
            .settlementStatus("REQUESTED").splitMethod("EQUAL")
            .totalAmount(new BigDecimal("100.00")).payerShareAmount(new BigDecimal("33.34"))
            .receivableAmount(new BigDecimal("66.66")).build();
        settlement.setSettlementId(90L);
        when(settlementMapper.findByIdForUpdate(90L)).thenReturn(settlement);
        when(settlementMapper.cancelSettlement(90L, 1L)).thenReturn(1);

        new SettlementServiceImpl(settlementMapper, walletTransferService).cancelSettlement(1L, 90L);

        verify(settlementMapper).cancelSettlement(90L, 1L);
    }

    @Test
    void paySettlement_completesSettlementAfterLastPendingPayment() {
        Settlement settlement = Settlement.builder().appointmentId(7L).createdByMemberId(1L).payerMemberId(1L)
            .settlementStatus("REQUESTED").totalAmount(new BigDecimal("20.00")).build();
        settlement.setSettlementId(90L);
        SettlementMember payment = new SettlementMember(401L, 90L, 72L, 2L,
            new BigDecimal("20.00"), "PENDING", null);
        when(settlementMapper.findByIdForUpdate(90L)).thenReturn(settlement);
        when(settlementMapper.findMembersBySettlementIdForUpdate(90L)).thenReturn(List.of(payment));
        when(walletTransferService.transfer(2L, 2L, 1L, new BigDecimal("20.00"), "Settlement #90")).thenReturn(700L);
        when(settlementMapper.markSettlementMemberPaid(401L, 700L)).thenReturn(1);

        new SettlementServiceImpl(settlementMapper, walletTransferService).paySettlement(2L, 90L);

        verify(settlementMapper).completeSettlementIfNoPendingPayments(90L);
    }

    @Test
    void getSettlements_separatesReceivedAndSentRequests() {
        when(settlementMapper.findReceivedSummaries(2L)).thenReturn(List.of(
            new SettlementSummary(10L, "Dinner", new BigDecimal("20.00"), "EQUAL", "REQUESTED")
        ));
        when(settlementMapper.findSentSummaries(2L)).thenReturn(List.of(
            new SettlementSummary(11L, "Museum", new BigDecimal("10.00"), "ITEMIZED", "COMPLETED")
        ));

        SettlementListResponse response = new SettlementServiceImpl(
            settlementMapper, walletTransferService
        ).getSettlements(2L);

        assertEquals(10L, response.getReceived().get(0).getId());
        assertEquals(11L, response.getSent().get(0).getId());
    }

    @Test
    void getCandidates_includesSourceAndActiveParticipants() {
        SettlementSource source = new SettlementSource();
        source.setTransferId(50L);
        source.setAppointmentId(7L);
        source.setPayerMemberId(1L);
        source.setAmount(new BigDecimal("100.00"));
        source.setJourneyName("Seoul trip");
        source.setGatheringName("Dinner");
        source.setMerchantName("Nawa restaurant");
        source.setPayerName("Alex");
        when(settlementMapper.findCandidateSources(1L)).thenReturn(List.of(source));
        when(settlementMapper.findParticipants(7L)).thenReturn(List.of(
            new SettlementParticipant(1L, "Alex"),
            new SettlementParticipant(2L, "Mina")
        ));

        List<SettlementCandidateResponse> response = new SettlementServiceImpl(
            settlementMapper, walletTransferService
        ).getCandidates(1L);

        assertEquals(50L, response.get(0).getTransferId());
        assertEquals("Mina", response.get(0).getParticipants().get(1).getName());
    }

    @Test
    void getSettlement_returnsDetailOnlyToParticipant() {
        when(settlementMapper.findDetail(90L, 2L)).thenReturn(new SettlementDetail(
            90L, "ITEMIZED", new BigDecimal("100.00"), "REQUESTED", "Alex",
            "Dinner", "Nawa restaurant", "TXN-123", "Alex"
        ));
        when(settlementMapper.findItemNames(90L)).thenReturn(List.of("Soup", "Tea"));

        SettlementDetailResponse response = new SettlementServiceImpl(
            settlementMapper, walletTransferService
        ).getSettlement(2L, 90L);

        assertEquals("ITEMIZED", response.getType());
        assertEquals(List.of("Soup", "Tea"), response.getItems());
    }

    @Test
    void analyzeReceipt_createsDraftWithoutOcrItems() {
        SettlementSource source = new SettlementSource();
        source.setTransferId(50L);
        source.setAppointmentId(7L);
        source.setPayerMemberId(1L);
        source.setAmount(new BigDecimal("100.00"));
        when(settlementMapper.findSourceForCreate(50L, 1L)).thenReturn(source);
        MultipartFile file = org.mockito.Mockito.mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn("receipt.jpg");
        doAnswer(invocation -> {
            invocation.getArgument(0, me.nawa.settlement.domain.ReceiptAnalysis.class)
                .setReceiptAnalysisId(15L);
            return null;
        }).when(settlementMapper).insertReceiptAnalysis(any(me.nawa.settlement.domain.ReceiptAnalysis.class));

        ReceiptAnalysisResponse response = new SettlementServiceImpl(
            settlementMapper, walletTransferService
        ).analyzeReceipt(1L, 50L, file);

        assertEquals(15L, response.getReceiptAnalysisId());
        assertEquals(List.of(), response.getItems());
    }

    @Test
    void updateReceiptItems_replacesDraftItemsAndRecalculatesTotal() {
        when(settlementMapper.findReceiptAnalysisForUpdate(15L)).thenReturn(
            new me.nawa.settlement.domain.ReceiptAnalysis(15L, 50L, 7L, 1L, "receipt.jpg", "DRAFT", BigDecimal.ZERO)
        );
        ReceiptItemRequest item = new ReceiptItemRequest();
        item.setName("Tea");
        item.setQuantity(new BigDecimal("2"));
        item.setUnitPrice(new BigDecimal("4.50"));
        ReceiptItemUpdateRequest request = new ReceiptItemUpdateRequest();
        request.setItems(List.of(item));

        ReceiptAnalysisResponse response = new SettlementServiceImpl(
            settlementMapper, walletTransferService
        ).updateReceiptItems(1L, 15L, request);

        assertEquals(new BigDecimal("9.00"), response.getRecognizedTotal());
        verify(settlementMapper).deleteReceiptItems(15L);
        verify(settlementMapper).insertReceiptItems(any());
    }

    @Test
    void updateReceiptAllocations_marksAnalysisAllocatedWhenQuantityMatches() {
        when(settlementMapper.findReceiptAnalysisForUpdate(15L)).thenReturn(
            new me.nawa.settlement.domain.ReceiptAnalysis(15L, 50L, 7L, 1L, "receipt.jpg", "DRAFT", new BigDecimal("9.00"))
        );
        when(settlementMapper.findReceiptItemsForUpdate(15L)).thenReturn(List.of(
            new me.nawa.settlement.domain.ReceiptAnalysisItem(31L, 15L, "Tea", new BigDecimal("4.50"), new BigDecimal("2"), new BigDecimal("9.00"), (short) 1)
        ));
        when(settlementMapper.findActiveMembers(7L)).thenReturn(List.of(
            new SettlementMember(null, null, 71L, 1L, null, null, null)
        ));
        ReceiptAllocationRequest allocation = new ReceiptAllocationRequest();
        allocation.setItemId(31L);
        allocation.setParticipantId(1L);
        allocation.setQuantity(new BigDecimal("2"));
        ReceiptAllocationUpdateRequest request = new ReceiptAllocationUpdateRequest();
        request.setAllocations(List.of(allocation));

        new SettlementServiceImpl(settlementMapper, walletTransferService)
            .updateReceiptAllocations(1L, 15L, request);

        verify(settlementMapper).insertReceiptAllocations(any());
        verify(settlementMapper).markReceiptAllocated(15L);
    }

    @Test
    void createItemizedSettlement_usesAllocatedAmountsForEachParticipant() {
        SettlementSource source = new SettlementSource();
        source.setTransferId(50L); source.setAppointmentId(7L); source.setPayerMemberId(1L);
        source.setAmount(new BigDecimal("9.00"));
        when(settlementMapper.findSourceForCreate(50L, 1L)).thenReturn(source);
        when(settlementMapper.findReceiptAnalysisForUpdate(15L)).thenReturn(
            new me.nawa.settlement.domain.ReceiptAnalysis(15L, 50L, 7L, 1L, "receipt.jpg", "ALLOCATED", new BigDecimal("9.00"))
        );
        when(settlementMapper.findReceiptAllocationViews(15L)).thenReturn(List.of(
            new me.nawa.settlement.domain.ReceiptAllocationView(1L, new BigDecimal("4.50")),
            new me.nawa.settlement.domain.ReceiptAllocationView(2L, new BigDecimal("4.50"))
        ));
        doAnswer(invocation -> { invocation.getArgument(0, Settlement.class).setSettlementId(91L); return null; })
            .when(settlementMapper).insertSettlement(any(Settlement.class));
        CreateSettlementRequest request = new CreateSettlementRequest();
        request.setSourceTransferId(50L); request.setType("ITEMIZED"); request.setReceiptAnalysisId(15L);
        request.setParticipantIds(List.of(1L, 2L));

        SettlementCreateResponse response = new SettlementServiceImpl(settlementMapper, walletTransferService)
            .createSettlement(1L, request);

        assertEquals(91L, response.getId());
        verify(settlementMapper).copyReceiptItemsToSettlement(15L, 91L);
        verify(settlementMapper).copyReceiptItemSharesToSettlement(15L, 91L);
        verify(settlementMapper).markReceiptUsed(15L);
    }

    @Test
    void submitGameConsent_updatesOnlyOwnPendingConsent() {
        when(settlementMapper.findSettlementGameForUpdate(70L)).thenReturn(
            new me.nawa.settlement.domain.SettlementGame(70L, "RANDOM", 1, "WAITING_CONSENT", null)
        );
        when(settlementMapper.updateGameConsent(70L, 2L, "AGREED")).thenReturn(1);
        GameConsentRequest request = new GameConsentRequest();
        request.setStatus("AGREED");

        new SettlementServiceImpl(settlementMapper, walletTransferService).submitGameConsent(2L, 70L, request);

        verify(settlementMapper).updateGameConsent(70L, 2L, "AGREED");
    }

    @Test
    void createGameSettlement_createsConsentWaitingGame() {
        SettlementSource source = new SettlementSource();
        source.setTransferId(50L); source.setAppointmentId(7L); source.setPayerMemberId(1L); source.setAmount(new BigDecimal("100.00"));
        when(settlementMapper.findSourceForCreate(50L, 1L)).thenReturn(source);
        when(settlementMapper.findActiveMembers(7L)).thenReturn(List.of(
            new SettlementMember(null, null, 71L, 1L, null, null, null),
            new SettlementMember(null, null, 72L, 2L, null, null, null)
        ));
        doAnswer(invocation -> { invocation.getArgument(0, Settlement.class).setSettlementId(71L); return null; })
            .when(settlementMapper).insertSettlement(any(Settlement.class));
        CreateSettlementRequest request = new CreateSettlementRequest();
        request.setSourceTransferId(50L); request.setType("GAME"); request.setParticipantIds(List.of(1L, 2L));
        GameCreateRequest game = new GameCreateRequest(); game.setType("RANDOM"); game.setLiableCount(1); request.setGame(game);

        SettlementCreateResponse response = new SettlementServiceImpl(settlementMapper, walletTransferService)
            .createSettlement(1L, request);

        assertEquals(71L, response.getId());
        verify(settlementMapper).insertSettlementGame(any(me.nawa.settlement.domain.SettlementGame.class));
        verify(settlementMapper).insertSettlementGameMembers(any());
    }

    @Test
    void startGame_selectsLiableMembersAndRequestsSettlement() {
        Settlement settlement = Settlement.builder().appointmentId(7L).createdByMemberId(1L).payerMemberId(1L)
            .sourceTransferId(50L).settlementStatus("DRAFT").splitMethod("GAME")
            .totalAmount(new BigDecimal("100.00")).payerShareAmount(new BigDecimal("100.00"))
            .receivableAmount(BigDecimal.ZERO).build();
        settlement.setSettlementId(71L);
        when(settlementMapper.findByIdForUpdate(71L)).thenReturn(settlement);
        when(settlementMapper.findSettlementGameForUpdate(71L)).thenReturn(
            new me.nawa.settlement.domain.SettlementGame(71L, "RANDOM", 1, "WAITING_CONSENT", null)
        );
        when(settlementMapper.findGameMembersForUpdate(71L)).thenReturn(List.of(
            new me.nawa.settlement.domain.SettlementGameMember(71L, 71L, 1L, "AGREED", false),
            new me.nawa.settlement.domain.SettlementGameMember(71L, 72L, 2L, "AGREED", false)
        ));

        new SettlementServiceImpl(settlementMapper, walletTransferService).startGame(1L, 71L);

        verify(settlementMapper).insertSettlementMembers(any());
        verify(settlementMapper).completeGame(eq(71L), any());
    }

    @Test
    void getGameResult_returnsOnlyServerSelectedLiableMembers() {
        Settlement settlement = Settlement.builder().appointmentId(7L).createdByMemberId(1L).payerMemberId(1L)
            .sourceTransferId(50L).settlementStatus("REQUESTED").splitMethod("GAME")
            .totalAmount(new BigDecimal("100.00")).payerShareAmount(BigDecimal.ZERO)
            .receivableAmount(new BigDecimal("100.00")).build();
        settlement.setSettlementId(71L);
        when(settlementMapper.findById(71L)).thenReturn(settlement);
        when(settlementMapper.findSettlementGame(71L)).thenReturn(
            new me.nawa.settlement.domain.SettlementGame(71L, "RANDOM", 1, "COMPLETED", "seed")
        );
        when(settlementMapper.findGameMembers(71L)).thenReturn(List.of(
            new me.nawa.settlement.domain.SettlementGameMember(71L, 71L, 1L, "AGREED", false),
            new me.nawa.settlement.domain.SettlementGameMember(71L, 72L, 2L, "AGREED", true)
        ));
        when(settlementMapper.findParticipants(7L)).thenReturn(List.of(
            new SettlementParticipant(1L, "Alex"), new SettlementParticipant(2L, "Mina")
        ));

        SettlementGameResultResponse response = new SettlementServiceImpl(settlementMapper, walletTransferService)
            .getGameResult(2L, 71L);

        assertEquals(1, response.getLiableParticipants().size());
        assertEquals("Mina", response.getLiableParticipants().get(0).getName());
    }

    @Test
    void getGame_returnsConsentProgressAndViewerRole() {
        Settlement settlement = Settlement.builder().appointmentId(7L).createdByMemberId(1L).payerMemberId(1L)
            .sourceTransferId(50L).settlementStatus("DRAFT").splitMethod("GAME")
            .totalAmount(new BigDecimal("100.00")).payerShareAmount(new BigDecimal("100.00"))
            .receivableAmount(BigDecimal.ZERO).build();
        settlement.setSettlementId(71L);
        when(settlementMapper.findById(71L)).thenReturn(settlement);
        when(settlementMapper.findSettlementGame(71L)).thenReturn(
            new me.nawa.settlement.domain.SettlementGame(71L, "RANDOM", 1, "WAITING_CONSENT", null)
        );
        when(settlementMapper.findGameMembers(71L)).thenReturn(List.of(
            new me.nawa.settlement.domain.SettlementGameMember(71L, 71L, 1L, "AGREED", false),
            new me.nawa.settlement.domain.SettlementGameMember(71L, 72L, 2L, "PENDING", false)
        ));
        when(settlementMapper.findParticipants(7L)).thenReturn(List.of(
            new SettlementParticipant(1L, "Alex"), new SettlementParticipant(2L, "Mina")
        ));

        SettlementGameResponse response = new SettlementServiceImpl(settlementMapper, walletTransferService)
            .getGame(2L, 71L);

        assertEquals(1, response.getAgreementCount());
        assertEquals("PARTICIPANT", response.getViewerRole());
    }
}
