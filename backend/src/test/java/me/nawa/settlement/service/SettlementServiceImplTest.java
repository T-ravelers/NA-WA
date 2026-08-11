package me.nawa.settlement.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
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
import me.nawa.common.exception.BusinessException;
import me.nawa.settlement.mapper.SettlementMapper;
import me.nawa.settlement.service.creation.EqualSettlementCreator;
import me.nawa.settlement.service.creation.GameSettlementCreator;
import me.nawa.settlement.service.creation.ItemizedSettlementCreator;
import me.nawa.wallet.service.WalletTransferService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.dao.DuplicateKeyException;

@ExtendWith(MockitoExtension.class)
class SettlementServiceBoundaryTest {

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
        source.setCurrencyDecimalPlaces(0);
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
        request.setParticipantAppointmentMemberIds(List.of(71L, 72L, 73L));

        SettlementCreateResponse response = creationService()
            .createSettlement(1L, 7L, "equal-1", request);

        assertEquals(90L, response.getId());
        ArgumentCaptor<Settlement> settlement = ArgumentCaptor.forClass(Settlement.class);
        verify(settlementMapper).insertSettlement(settlement.capture());
        assertEquals("DRAFT", settlement.getValue().getSettlementStatus());
        assertEquals(new BigDecimal("34"), settlement.getValue().getPayerShareAmount());
        assertEquals(new BigDecimal("66.00"), settlement.getValue().getReceivableAmount());
        ArgumentCaptor<List<SettlementMember>> members = ArgumentCaptor.forClass(List.class);
        verify(settlementMapper).insertSettlementMembers(members.capture());
        assertEquals(List.of(new BigDecimal("34"), new BigDecimal("33"), new BigDecimal("33")),
            members.getValue().stream().map(SettlementMember::getShareAmount).toList());
        assertEquals(List.of("NOT_REQUESTED", "NOT_REQUESTED", "NOT_REQUESTED"), members.getValue().stream()
            .map(SettlementMember::getRequestStatus).toList());
    }

    @Test
    void createEqualSettlement_remainderFollowsAppointmentMemberOrderNotPayer() {
        SettlementSource source = new SettlementSource();
        source.setTransferId(50L);
        source.setAppointmentId(7L);
        source.setPayerMemberId(2L);
        source.setAmount(new BigDecimal("100"));
        source.setCurrencyDecimalPlaces(0);
        when(settlementMapper.findSourceForCreate(50L, 2L)).thenReturn(source);
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
        request.setParticipantAppointmentMemberIds(List.of(71L, 72L, 73L));

        creationService().createSettlement(2L, 7L, "equal-order", request);

        ArgumentCaptor<List<SettlementMember>> members = ArgumentCaptor.forClass(List.class);
        verify(settlementMapper).insertSettlementMembers(members.capture());
        assertEquals(List.of(new BigDecimal("34"), new BigDecimal("33"), new BigDecimal("33")),
            members.getValue().stream().map(SettlementMember::getShareAmount).toList());
        ArgumentCaptor<Settlement> settlement = ArgumentCaptor.forClass(Settlement.class);
        verify(settlementMapper).insertSettlement(settlement.capture());
        assertEquals(new BigDecimal("33"), settlement.getValue().getPayerShareAmount());
        assertEquals(new BigDecimal("67"), settlement.getValue().getReceivableAmount());
    }

    @Test
    void createSettlement_sameKeyAndRequest_returnsExistingSettlementWithoutSecondInsert() {
        SettlementSource source = new SettlementSource();
        source.setTransferId(50L);
        source.setAppointmentId(7L);
        source.setPayerMemberId(1L);
        source.setAmount(new BigDecimal("100.00"));
        source.setCurrencyDecimalPlaces(2);
        when(settlementMapper.findSourceForCreate(50L, 1L)).thenReturn(source);
        when(settlementMapper.findActiveMembers(7L)).thenReturn(List.of(
            new SettlementMember(null, null, 71L, 1L, null, null, null),
            new SettlementMember(null, null, 72L, 2L, null, null, null)
        ));
        AtomicReference<Settlement> stored = new AtomicReference<>();
        when(settlementMapper.findByCreatorAndIdempotencyKey(1L, "same-key"))
            .thenAnswer(invocation -> stored.get());
        doAnswer(invocation -> {
            Settlement settlement = invocation.getArgument(0, Settlement.class);
            settlement.setSettlementId(90L);
            stored.set(settlement);
            return null;
        }).when(settlementMapper).insertSettlement(any(Settlement.class));
        CreateSettlementRequest request = new CreateSettlementRequest();
        request.setSourceTransferId(50L);
        request.setType("EQUAL");
        request.setParticipantAppointmentMemberIds(List.of(71L, 72L));

        SettlementCreateResponse first = creationService()
            .createSettlement(1L, 7L, "same-key", request);
        SettlementCreateResponse retried = creationService()
            .createSettlement(1L, 7L, "same-key", request);

        assertEquals(90L, first.getId());
        assertEquals(90L, retried.getId());
        verify(settlementMapper, org.mockito.Mockito.times(1)).insertSettlement(any(Settlement.class));
    }

    @Test
    void createSettlement_sameKeyWithDifferentRequest_throwsIdempotencyConflict() {
        SettlementSource source = new SettlementSource();
        source.setTransferId(50L);
        source.setAppointmentId(7L);
        source.setPayerMemberId(1L);
        source.setAmount(new BigDecimal("100.00"));
        source.setCurrencyDecimalPlaces(2);
        when(settlementMapper.findSourceForCreate(50L, 1L)).thenReturn(source);
        when(settlementMapper.findActiveMembers(7L)).thenReturn(List.of(
            new SettlementMember(null, null, 71L, 1L, null, null, null),
            new SettlementMember(null, null, 72L, 2L, null, null, null)
        ));
        AtomicReference<Settlement> stored = new AtomicReference<>();
        when(settlementMapper.findByCreatorAndIdempotencyKey(1L, "same-key"))
            .thenAnswer(invocation -> stored.get());
        doAnswer(invocation -> {
            Settlement settlement = invocation.getArgument(0, Settlement.class);
            settlement.setSettlementId(90L);
            stored.set(settlement);
            return null;
        }).when(settlementMapper).insertSettlement(any(Settlement.class));
        CreateSettlementRequest firstRequest = new CreateSettlementRequest();
        firstRequest.setSourceTransferId(50L);
        firstRequest.setType("EQUAL");
        firstRequest.setParticipantAppointmentMemberIds(List.of(71L, 72L));
        creationService().createSettlement(1L, 7L, "same-key", firstRequest);
        CreateSettlementRequest changedRequest = new CreateSettlementRequest();
        changedRequest.setSourceTransferId(50L);
        changedRequest.setType("EQUAL");
        changedRequest.setParticipantAppointmentMemberIds(List.of(71L));

        BusinessException exception = assertThrows(BusinessException.class, () ->
            creationService().createSettlement(1L, 7L, "same-key", changedRequest)
        );

        assertEquals("SETTLEMENT-009", exception.getErrorCode().getCode());
    }

    @Test
    void createSettlement_differentKeyForUsedSource_throwsSourceConflict() {
        Settlement existing = Settlement.builder()
            .appointmentId(7L).createdByMemberId(1L).payerMemberId(1L).sourceTransferId(50L)
            .idempotencyKey("first-key").requestFingerprint("stored")
            .settlementStatus("DRAFT").splitMethod("EQUAL")
            .totalAmount(new BigDecimal("100.00")).payerShareAmount(new BigDecimal("50.00"))
            .receivableAmount(new BigDecimal("50.00")).build();
        existing.setSettlementId(90L);
        when(settlementMapper.findBySourceTransferId(50L)).thenReturn(existing);
        CreateSettlementRequest request = new CreateSettlementRequest();
        request.setSourceTransferId(50L);
        request.setType("EQUAL");
        request.setParticipantAppointmentMemberIds(List.of(71L, 72L));

        BusinessException exception = assertThrows(BusinessException.class, () ->
            creationService().createSettlement(1L, 7L, "second-key", request)
        );

        assertEquals("SETTLEMENT-010", exception.getErrorCode().getCode());
    }

    @Test
    void createSettlement_concurrentSameRequest_returnsWinnerAfterUniqueConflict() {
        SettlementSource source = new SettlementSource();
        source.setTransferId(50L);
        source.setAppointmentId(7L);
        source.setPayerMemberId(1L);
        source.setAmount(new BigDecimal("100.00"));
        source.setCurrencyDecimalPlaces(2);
        when(settlementMapper.findSourceForCreate(50L, 1L)).thenReturn(source);
        when(settlementMapper.findActiveMembers(7L)).thenReturn(List.of(
            new SettlementMember(null, null, 71L, 1L, null, null, null),
            new SettlementMember(null, null, 72L, 2L, null, null, null)
        ));
        AtomicReference<Settlement> winner = new AtomicReference<>();
        when(settlementMapper.findByCreatorAndIdempotencyKey(1L, "race-key"))
            .thenAnswer(invocation -> winner.get());
        doAnswer(invocation -> {
            Settlement attempted = invocation.getArgument(0, Settlement.class);
            Settlement existing = Settlement.builder()
                .appointmentId(attempted.getAppointmentId())
                .createdByMemberId(attempted.getCreatedByMemberId())
                .payerMemberId(attempted.getPayerMemberId())
                .sourceTransferId(attempted.getSourceTransferId())
                .idempotencyKey(attempted.getIdempotencyKey())
                .requestFingerprint(attempted.getRequestFingerprint())
                .settlementStatus("DRAFT").splitMethod("EQUAL")
                .totalAmount(new BigDecimal("100.00")).payerShareAmount(new BigDecimal("50.00"))
                .receivableAmount(new BigDecimal("50.00")).build();
            existing.setSettlementId(91L);
            winner.set(existing);
            throw new DuplicateKeyException("simulated unique conflict");
        }).when(settlementMapper).insertSettlement(any(Settlement.class));
        CreateSettlementRequest request = new CreateSettlementRequest();
        request.setSourceTransferId(50L);
        request.setType("EQUAL");
        request.setParticipantAppointmentMemberIds(List.of(71L, 72L));

        SettlementCreateResponse response = creationService()
            .createSettlement(1L, 7L, "race-key", request);

        assertEquals(91L, response.getId());
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

        new SettlementPaymentServiceImpl(settlementMapper, walletTransferService).cancelSettlement(1L, 90L);

        verify(settlementMapper).cancelSettlement(90L, 1L);
    }

    @Test
    void requestSettlement_draftCreatedByRequester_marksSettlementAndMembersRequested() {
        Settlement settlement = Settlement.builder()
            .appointmentId(7L).createdByMemberId(1L).payerMemberId(1L).sourceTransferId(50L)
            .idempotencyKey("equal-1").requestFingerprint("fingerprint")
            .settlementStatus("DRAFT").splitMethod("EQUAL")
            .totalAmount(new BigDecimal("100.00")).payerShareAmount(new BigDecimal("50.00"))
            .receivableAmount(new BigDecimal("50.00")).build();
        settlement.setSettlementId(90L);
        when(settlementMapper.findByIdForUpdate(90L)).thenReturn(settlement);
        when(settlementMapper.markSettlementRequested(90L, 1L)).thenReturn(1);

        creationService().requestSettlement(1L, 90L);

        verify(settlementMapper).markSettlementRequested(90L, 1L);
        verify(settlementMapper).markSettlementMembersRequested(90L, 1L);
    }

    @Test
    void requestSettlement_alreadyRequested_throwsConflict() {
        Settlement settlement = Settlement.builder()
            .appointmentId(7L).createdByMemberId(1L).payerMemberId(1L).sourceTransferId(50L)
            .idempotencyKey("equal-1").requestFingerprint("fingerprint")
            .settlementStatus("REQUESTED").splitMethod("EQUAL")
            .totalAmount(new BigDecimal("100.00")).payerShareAmount(new BigDecimal("50.00"))
            .receivableAmount(new BigDecimal("50.00")).build();
        settlement.setSettlementId(90L);
        when(settlementMapper.findByIdForUpdate(90L)).thenReturn(settlement);

        BusinessException exception = assertThrows(BusinessException.class, () ->
            creationService().requestSettlement(1L, 90L)
        );

        assertEquals("SETTLEMENT-011", exception.getErrorCode().getCode());
    }

    @Test
    void cancelSettlement_cancelsDraftSettlementCreatedByRequester() {
        Settlement settlement = Settlement.builder()
            .appointmentId(7L).createdByMemberId(1L).payerMemberId(1L).sourceTransferId(50L)
            .settlementStatus("DRAFT").splitMethod("EQUAL")
            .totalAmount(new BigDecimal("100.00")).payerShareAmount(new BigDecimal("33.34"))
            .receivableAmount(new BigDecimal("66.66")).build();
        settlement.setSettlementId(90L);
        when(settlementMapper.findByIdForUpdate(90L)).thenReturn(settlement);
        when(settlementMapper.cancelSettlement(90L, 1L)).thenReturn(1);

        new SettlementPaymentServiceImpl(settlementMapper, walletTransferService).cancelSettlement(1L, 90L);

        verify(settlementMapper).cancelSettlement(90L, 1L);
    }

    @Test
    void cancelSettlement_draftGame_cancelsLinkedGameInSameOperation() {
        Settlement settlement = Settlement.builder()
            .appointmentId(7L).createdByMemberId(1L).payerMemberId(1L).sourceTransferId(50L)
            .settlementStatus("DRAFT").splitMethod("GAME")
            .totalAmount(new BigDecimal("100.00")).payerShareAmount(new BigDecimal("100.00"))
            .receivableAmount(BigDecimal.ZERO).build();
        settlement.setSettlementId(90L);
        when(settlementMapper.findByIdForUpdate(90L)).thenReturn(settlement);
        when(settlementMapper.cancelSettlement(90L, 1L)).thenReturn(1);
        when(settlementMapper.cancelGame(90L)).thenReturn(1);

        new SettlementPaymentServiceImpl(settlementMapper, walletTransferService)
            .cancelSettlement(1L, 90L);

        verify(settlementMapper).cancelGame(90L);
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

        new SettlementPaymentServiceImpl(settlementMapper, walletTransferService).paySettlement(2L, 90L);

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

        SettlementListResponse response = new SettlementQueryServiceImpl(settlementMapper).getSettlements(2L);

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
            new SettlementParticipant(71L, 1L, "Alex"),
            new SettlementParticipant(72L, 2L, "Mina")
        ));

        List<SettlementCandidateResponse> response = new SettlementQueryServiceImpl(settlementMapper).getCandidates(1L);

        assertEquals(50L, response.get(0).getTransferId());
        assertEquals(72L, response.get(0).getParticipants().get(1).getId());
        assertEquals("Mina", response.get(0).getParticipants().get(1).getName());
    }

    @Test
    void getSettlement_returnsDetailOnlyToParticipant() {
        when(settlementMapper.findDetail(90L, 2L)).thenReturn(new SettlementDetail(
            90L, "ITEMIZED", new BigDecimal("100.00"), "REQUESTED", "Alex",
            "Dinner", "Nawa restaurant", "TXN-123", "Alex"
        ));
        when(settlementMapper.findItemNames(90L)).thenReturn(List.of("Soup", "Tea"));

        SettlementDetailResponse response = new SettlementQueryServiceImpl(settlementMapper).getSettlement(2L, 90L);

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
        MultipartFile file = new org.springframework.mock.web.MockMultipartFile(
            "file", "receipt.jpg", "image/jpeg", new byte[] {1, 2, 3}
        );
        doAnswer(invocation -> {
            invocation.getArgument(0, me.nawa.settlement.domain.ReceiptAnalysis.class)
                .setReceiptAnalysisId(15L);
            return null;
        }).when(settlementMapper).insertReceiptAnalysis(any(me.nawa.settlement.domain.ReceiptAnalysis.class));

        ReceiptAnalysisResponse response = new ReceiptAnalysisServiceImpl(settlementMapper).analyzeReceipt(1L, 50L, file);

        assertEquals(15L, response.getReceiptAnalysisId());
        assertEquals(List.of(), response.getItems());
    }

    @Test
    void analyzeReceipt_disallowedMimeType_throwsInvalidBeforeDatabaseAccess() {
        MultipartFile file = new org.springframework.mock.web.MockMultipartFile(
            "file", "receipt.jpg", "text/plain", new byte[] {1, 2, 3}
        );

        BusinessException exception = assertThrows(BusinessException.class, () ->
            new ReceiptAnalysisServiceImpl(settlementMapper).analyzeReceipt(1L, 50L, file)
        );

        assertEquals("SETTLEMENT-007", exception.getErrorCode().getCode());
        verify(settlementMapper, never()).findSourceForCreate(any(), any());
    }

    @Test
    void analyzeReceipt_existingDraft_reusesAnalysisAndClearsPreviousData() {
        SettlementSource source = new SettlementSource();
        source.setTransferId(50L);
        source.setAppointmentId(7L);
        source.setPayerMemberId(1L);
        source.setAmount(new BigDecimal("100.00"));
        when(settlementMapper.findSourceForCreate(50L, 1L)).thenReturn(source);
        when(settlementMapper.findReceiptAnalysisBySourceAndCreatorForUpdate(50L, 1L)).thenReturn(
            new me.nawa.settlement.domain.ReceiptAnalysis(
                15L, 50L, 7L, 1L, "old.jpg", "DRAFT", new BigDecimal("9.00")
            )
        );
        MultipartFile file = new org.springframework.mock.web.MockMultipartFile(
            "file", "new.jpg", "image/jpeg", new byte[] {1, 2, 3}
        );

        ReceiptAnalysisResponse response = new ReceiptAnalysisServiceImpl(settlementMapper)
            .analyzeReceipt(1L, 50L, file);

        assertEquals(15L, response.getReceiptAnalysisId());
        InOrder order = inOrder(settlementMapper);
        order.verify(settlementMapper).deleteReceiptAllocations(15L);
        order.verify(settlementMapper).deleteReceiptItems(15L);
        order.verify(settlementMapper).resetDraftReceiptAnalysis(15L, "new.jpg");
        verify(settlementMapper, never()).insertReceiptAnalysis(any());
    }

    @Test
    void analyzeReceipt_existingAllocated_throwsConflict() {
        SettlementSource source = new SettlementSource();
        source.setTransferId(50L);
        source.setAppointmentId(7L);
        source.setPayerMemberId(1L);
        source.setAmount(new BigDecimal("100.00"));
        when(settlementMapper.findSourceForCreate(50L, 1L)).thenReturn(source);
        when(settlementMapper.findReceiptAnalysisBySourceAndCreatorForUpdate(50L, 1L)).thenReturn(
            new me.nawa.settlement.domain.ReceiptAnalysis(
                15L, 50L, 7L, 1L, "old.jpg", "ALLOCATED", new BigDecimal("9.00")
            )
        );
        MultipartFile file = new org.springframework.mock.web.MockMultipartFile(
            "file", "new.jpg", "image/jpeg", new byte[] {1, 2, 3}
        );

        BusinessException exception = assertThrows(BusinessException.class, () ->
            new ReceiptAnalysisServiceImpl(settlementMapper).analyzeReceipt(1L, 50L, file)
        );

        assertEquals("SETTLEMENT-012", exception.getErrorCode().getCode());
    }

    @Test
    void analyzeReceipt_concurrentDraftInsert_reusesWinnerAfterUniqueConflict() {
        SettlementSource source = new SettlementSource();
        source.setTransferId(50L);
        source.setAppointmentId(7L);
        source.setPayerMemberId(1L);
        source.setAmount(new BigDecimal("100.00"));
        when(settlementMapper.findSourceForCreate(50L, 1L)).thenReturn(source);
        me.nawa.settlement.domain.ReceiptAnalysis winner = new me.nawa.settlement.domain.ReceiptAnalysis(
            15L, 50L, 7L, 1L, "winner.jpg", "DRAFT", BigDecimal.ZERO
        );
        when(settlementMapper.findReceiptAnalysisBySourceAndCreatorForUpdate(50L, 1L))
            .thenReturn(null, winner);
        doThrow(new DuplicateKeyException("simulated receipt unique conflict"))
            .when(settlementMapper).insertReceiptAnalysis(any());
        MultipartFile file = new org.springframework.mock.web.MockMultipartFile(
            "file", "new.jpg", "image/jpeg", new byte[] {1, 2, 3}
        );

        ReceiptAnalysisResponse response = new ReceiptAnalysisServiceImpl(settlementMapper)
            .analyzeReceipt(1L, 50L, file);

        assertEquals(15L, response.getReceiptAnalysisId());
        verify(settlementMapper).resetDraftReceiptAnalysis(15L, "new.jpg");
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

        ReceiptAnalysisResponse response = new ReceiptAnalysisServiceImpl(settlementMapper)
            .updateReceiptItems(1L, 15L, request);

        assertEquals(new BigDecimal("9.00"), response.getRecognizedTotal());
        InOrder order = inOrder(settlementMapper);
        order.verify(settlementMapper).deleteReceiptAllocations(15L);
        order.verify(settlementMapper).deleteReceiptItems(15L);
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
        allocation.setAppointmentMemberId(71L);
        allocation.setQuantity(new BigDecimal("2"));
        ReceiptAllocationUpdateRequest request = new ReceiptAllocationUpdateRequest();
        request.setAllocations(List.of(allocation));

        new ReceiptAnalysisServiceImpl(settlementMapper)
            .updateReceiptAllocations(1L, 15L, request);

        verify(settlementMapper).insertReceiptAllocations(any());
        verify(settlementMapper).markReceiptAllocated(15L);
    }

    @Test
    void updateReceiptAllocations_duplicateItemAndAppointmentMember_throwsInvalid() {
        when(settlementMapper.findReceiptAnalysisForUpdate(15L)).thenReturn(
            new me.nawa.settlement.domain.ReceiptAnalysis(
                15L, 50L, 7L, 1L, "receipt.jpg", "DRAFT", new BigDecimal("9.00")
            )
        );
        when(settlementMapper.findReceiptItemsForUpdate(15L)).thenReturn(List.of(
            new me.nawa.settlement.domain.ReceiptAnalysisItem(
                31L, 15L, "Tea", new BigDecimal("4.50"), new BigDecimal("2"),
                new BigDecimal("9.00"), (short) 1
            )
        ));
        when(settlementMapper.findActiveMembers(7L)).thenReturn(List.of(
            new SettlementMember(null, null, 71L, 1L, null, null, null)
        ));
        ReceiptAllocationRequest first = new ReceiptAllocationRequest();
        first.setItemId(31L); first.setAppointmentMemberId(71L); first.setQuantity(BigDecimal.ONE);
        ReceiptAllocationRequest duplicate = new ReceiptAllocationRequest();
        duplicate.setItemId(31L); duplicate.setAppointmentMemberId(71L); duplicate.setQuantity(BigDecimal.ONE);
        ReceiptAllocationUpdateRequest request = new ReceiptAllocationUpdateRequest();
        request.setAllocations(List.of(first, duplicate));

        BusinessException exception = assertThrows(BusinessException.class, () ->
            new ReceiptAnalysisServiceImpl(settlementMapper)
                .updateReceiptAllocations(1L, 15L, request)
        );

        assertEquals("SETTLEMENT-007", exception.getErrorCode().getCode());
        verify(settlementMapper, never()).insertReceiptAllocations(any());
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
            new me.nawa.settlement.domain.ReceiptAllocationView(71L, 1L, new BigDecimal("4.50")),
            new me.nawa.settlement.domain.ReceiptAllocationView(72L, 2L, new BigDecimal("4.50"))
        ));
        when(settlementMapper.sumReceiptItemLineTotals(15L)).thenReturn(new BigDecimal("9.00"));
        when(settlementMapper.findActiveMembers(7L)).thenReturn(List.of(
            new SettlementMember(null, null, 71L, 1L, null, null, null),
            new SettlementMember(null, null, 72L, 2L, null, null, null)
        ));
        doAnswer(invocation -> { invocation.getArgument(0, Settlement.class).setSettlementId(91L); return null; })
            .when(settlementMapper).insertSettlement(any(Settlement.class));
        CreateSettlementRequest request = new CreateSettlementRequest();
        request.setSourceTransferId(50L); request.setType("ITEMIZED"); request.setReceiptAnalysisId(15L);
        request.setParticipantAppointmentMemberIds(List.of(71L, 72L));

        SettlementCreateResponse response = creationService()
            .createSettlement(1L, 7L, "itemized-1", request);

        assertEquals(91L, response.getId());
        ArgumentCaptor<Settlement> settlement = ArgumentCaptor.forClass(Settlement.class);
        verify(settlementMapper).insertSettlement(settlement.capture());
        assertEquals("DRAFT", settlement.getValue().getSettlementStatus());
        ArgumentCaptor<List<SettlementMember>> members = ArgumentCaptor.forClass(List.class);
        verify(settlementMapper).insertSettlementMembers(members.capture());
        assertEquals(List.of("NOT_REQUESTED", "NOT_REQUESTED"), members.getValue().stream()
            .map(SettlementMember::getRequestStatus).toList());
        verify(settlementMapper).copyReceiptItemsToSettlement(15L, 91L);
        verify(settlementMapper).copyReceiptItemSharesToSettlement(15L, 91L);
        verify(settlementMapper).markReceiptUsed(15L);
    }

    @Test
    void createItemizedSettlement_sourceAmountDiffersFromReceipt_throwsInvalid() {
        SettlementSource source = new SettlementSource();
        source.setTransferId(50L); source.setAppointmentId(7L); source.setPayerMemberId(1L);
        source.setAmount(new BigDecimal("10.00"));
        when(settlementMapper.findSourceForCreate(50L, 1L)).thenReturn(source);
        when(settlementMapper.findReceiptAnalysisForUpdate(15L)).thenReturn(
            new me.nawa.settlement.domain.ReceiptAnalysis(
                15L, 50L, 7L, 1L, "receipt.jpg", "ALLOCATED", new BigDecimal("9.00")
            )
        );
        when(settlementMapper.sumReceiptItemLineTotals(15L)).thenReturn(new BigDecimal("9.00"));
        when(settlementMapper.findReceiptAllocationViews(15L)).thenReturn(List.of(
            new me.nawa.settlement.domain.ReceiptAllocationView(71L, 1L, new BigDecimal("9.00"))
        ));
        CreateSettlementRequest request = new CreateSettlementRequest();
        request.setSourceTransferId(50L); request.setType("ITEMIZED"); request.setReceiptAnalysisId(15L);
        request.setParticipantAppointmentMemberIds(List.of(71L));

        BusinessException exception = assertThrows(BusinessException.class, () ->
            creationService().createSettlement(1L, 7L, "itemized-mismatch", request)
        );

        assertEquals("SETTLEMENT-005", exception.getErrorCode().getCode());
    }

    @Test
    void createItemizedSettlement_lineTotalsDifferFromRecognizedTotal_throwsInvalid() {
        SettlementSource source = new SettlementSource();
        source.setTransferId(50L); source.setAppointmentId(7L); source.setPayerMemberId(1L);
        source.setAmount(new BigDecimal("9.00"));
        when(settlementMapper.findSourceForCreate(50L, 1L)).thenReturn(source);
        when(settlementMapper.findReceiptAnalysisForUpdate(15L)).thenReturn(
            new me.nawa.settlement.domain.ReceiptAnalysis(
                15L, 50L, 7L, 1L, "receipt.jpg", "ALLOCATED", new BigDecimal("9.00")
            )
        );
        when(settlementMapper.sumReceiptItemLineTotals(15L)).thenReturn(new BigDecimal("8.00"));
        when(settlementMapper.findReceiptAllocationViews(15L)).thenReturn(List.of(
            new me.nawa.settlement.domain.ReceiptAllocationView(71L, 1L, new BigDecimal("9.00"))
        ));
        CreateSettlementRequest request = new CreateSettlementRequest();
        request.setSourceTransferId(50L); request.setType("ITEMIZED"); request.setReceiptAnalysisId(15L);
        request.setParticipantAppointmentMemberIds(List.of(71L));

        BusinessException exception = assertThrows(BusinessException.class, () ->
            creationService().createSettlement(1L, 7L, "itemized-lines", request)
        );

        assertEquals("SETTLEMENT-005", exception.getErrorCode().getCode());
    }

    @Test
    void submitGameConsent_updatesOnlyOwnPendingConsent() {
        when(settlementMapper.findSettlementGameForUpdate(70L)).thenReturn(
            new me.nawa.settlement.domain.SettlementGame(70L, "RANDOM", 1, "WAITING_CONSENT", null)
        );
        when(settlementMapper.updateGameConsent(70L, 2L, "AGREED")).thenReturn(1);
        GameConsentRequest request = new GameConsentRequest();
        request.setStatus("AGREED");

        new SettlementGameServiceImpl(settlementMapper).submitGameConsent(2L, 70L, request);

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
        request.setSourceTransferId(50L); request.setType("GAME");
        request.setParticipantAppointmentMemberIds(List.of(71L, 72L));
        GameCreateRequest game = new GameCreateRequest(); game.setType("RANDOM"); game.setLiableCount(1); request.setGame(game);

        SettlementCreateResponse response = creationService()
            .createSettlement(1L, 7L, "game-1", request);

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

        new SettlementGameServiceImpl(settlementMapper).startGame(1L, 71L);

        verify(settlementMapper).insertSettlementMembers(any());
        verify(settlementMapper).completeGame(eq(71L), any());
    }

    @Test
    void submitGameConsent_declined_cancelsGameAndDraftSettlement() {
        when(settlementMapper.findSettlementGameForUpdate(71L)).thenReturn(
            new me.nawa.settlement.domain.SettlementGame(71L, "RANDOM", 1, "WAITING_CONSENT", null)
        );
        when(settlementMapper.updateGameConsent(71L, 2L, "DECLINED")).thenReturn(1);
        when(settlementMapper.cancelGameAndSettlement(71L)).thenReturn(1);
        GameConsentRequest request = new GameConsentRequest();
        request.setStatus("DECLINED");

        new SettlementGameServiceImpl(settlementMapper).submitGameConsent(2L, 71L, request);

        verify(settlementMapper).cancelGameAndSettlement(71L);
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
            new SettlementParticipant(71L, 1L, "Alex"),
            new SettlementParticipant(72L, 2L, "Mina")
        ));

        SettlementGameResultResponse response = new SettlementGameServiceImpl(settlementMapper)
            .getGameResult(2L, 71L);

        assertEquals(1, response.getLiableParticipants().size());
        assertEquals(72L, response.getLiableParticipants().get(0).getId());
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
            new SettlementParticipant(71L, 1L, "Alex"),
            new SettlementParticipant(72L, 2L, "Mina")
        ));

        SettlementGameResponse response = new SettlementGameServiceImpl(settlementMapper)
            .getGame(2L, 71L);

        assertEquals(1, response.getAgreementCount());
        assertEquals("PARTICIPANT", response.getViewerRole());
    }

    private SettlementCreationService creationService() {
        return new SettlementCreationServiceImpl(
            settlementMapper,
            List.of(
                new EqualSettlementCreator(settlementMapper),
                new ItemizedSettlementCreator(settlementMapper),
                new GameSettlementCreator(settlementMapper)
            )
        );
    }
}
