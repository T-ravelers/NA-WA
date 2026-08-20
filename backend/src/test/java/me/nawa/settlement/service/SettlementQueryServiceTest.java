package me.nawa.settlement.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import me.nawa.settlement.domain.SettlementAllowedAction;
import me.nawa.settlement.domain.SettlementCollectionMember;
import me.nawa.settlement.domain.SettlementDetail;
import me.nawa.settlement.domain.SettlementParticipant;
import me.nawa.settlement.domain.SettlementSource;
import me.nawa.settlement.domain.SettlementSummary;
import me.nawa.settlement.dto.response.SettlementCandidateResponse;
import me.nawa.settlement.domain.SettlementViewerItem;
import me.nawa.settlement.dto.response.SettlementDetailResponse;
import me.nawa.settlement.dto.response.SettlementListResponse;
import me.nawa.settlement.dto.response.SettlementParticipantResponse;
import me.nawa.settlement.mapper.SettlementMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SettlementQueryServiceTest {

    @Mock
    private SettlementMapper settlementMapper;

    @Test
    void getCandidates_preservesAppointmentAndPayerAppointmentMemberContext() {
        SettlementSource source = new SettlementSource();
        source.setTransferId(20L);
        source.setAppointmentId(7L);
        source.setPayerMemberId(1L);
        source.setPayerAppointmentMemberId(71L);
        when(settlementMapper.findCandidateSources(1L)).thenReturn(List.of(source));
        when(settlementMapper.findParticipants(7L)).thenReturn(List.of(
            new SettlementParticipant(71L, 1L, "Payer"),
            new SettlementParticipant(72L, 2L, "Participant")
        ));

        SettlementCandidateResponse response = service().getCandidates(1L).get(0);

        assertEquals(20L, response.getTransferId());
        assertEquals(7L, response.getAppointmentId());
        assertEquals(71L, response.getPayerAppointmentMemberId());
        assertEquals(List.of(71L, 72L), response.getParticipants().stream()
            .map(participant -> participant.getId()).toList());
    }

    @Test
    void getSettlements_pendingParticipant_returnsViewerPaymentContract() {
        SettlementSummary received = new SettlementSummary();
        received.setSettlementId(10L);
        received.setTitle("Dinner");
        received.setTotalAmount(new BigDecimal("100"));
        received.setReceivableAmount(new BigDecimal("66"));
        received.setSplitMethod("EQUAL");
        received.setSettlementStatus("REQUESTED");
        received.setCreatedByMemberId(1L);
        received.setViewerShareAmount(new BigDecimal("34"));
        received.setViewerRequestStatus("PENDING");
        when(settlementMapper.findReceivedSummaries(2L)).thenReturn(List.of(received));
        when(settlementMapper.findSentSummaries(2L)).thenReturn(List.of());

        SettlementListResponse response = service().getSettlements(2L);

        assertEquals(new BigDecimal("100"), response.getReceived().get(0).getTotalAmount());
        assertEquals("PARTICIPANT", response.getReceived().get(0).getViewer().getRole());
        assertEquals(new BigDecimal("34"), response.getReceived().get(0).getViewer().getPayableAmount());
        assertEquals(
            List.of(SettlementAllowedAction.PAY),
            response.getReceived().get(0).getViewer().getAllowedActions()
        );
    }

    @Test
    void getSettlements_returnsCreatedAndCompletedTimes() {
        SettlementSummary done = new SettlementSummary();
        done.setSettlementId(11L);
        done.setTitle("Lunch");
        done.setTotalAmount(new BigDecimal("100"));
        done.setReceivableAmount(new BigDecimal("50"));
        done.setSplitMethod("EQUAL");
        done.setSettlementStatus("COMPLETED");
        done.setCreatedByMemberId(2L);
        done.setViewerShareAmount(new BigDecimal("50"));
        done.setViewerRequestStatus("NOT_REQUESTED");
        done.setCreatedAt(LocalDateTime.of(2026, 8, 18, 12, 30, 0));
        done.setCompletedAt(LocalDateTime.of(2026, 8, 19, 9, 0, 0));

        // 이 필드를 남기기 전에 끝난 정산은 완료 시각이 비어 있다. 화면이 생성 시각으로
        // 대신 보여줄 수 있도록 두 값을 모두 내려준다.
        SettlementSummary legacy = new SettlementSummary();
        legacy.setSettlementId(12L);
        legacy.setTitle("Coffee");
        legacy.setTotalAmount(new BigDecimal("30"));
        legacy.setReceivableAmount(new BigDecimal("15"));
        legacy.setSplitMethod("EQUAL");
        legacy.setSettlementStatus("COMPLETED");
        legacy.setCreatedByMemberId(2L);
        legacy.setViewerShareAmount(new BigDecimal("15"));
        legacy.setViewerRequestStatus("NOT_REQUESTED");
        legacy.setCreatedAt(LocalDateTime.of(2026, 7, 1, 8, 0, 0));

        when(settlementMapper.findReceivedSummaries(2L)).thenReturn(List.of());
        when(settlementMapper.findSentSummaries(2L)).thenReturn(List.of(done, legacy));

        SettlementListResponse response = service().getSettlements(2L);

        assertEquals(LocalDateTime.of(2026, 8, 18, 12, 30, 0), response.getSent().get(0).getCreatedAt());
        assertEquals(LocalDateTime.of(2026, 8, 19, 9, 0, 0), response.getSent().get(0).getCompletedAt());
        assertEquals(LocalDateTime.of(2026, 7, 1, 8, 0, 0), response.getSent().get(1).getCreatedAt());
        assertNull(response.getSent().get(1).getCompletedAt());
    }

    @Test
    void getSettlement_itemizedParticipant_returnsOnlyViewerItemsAndActions() {
        SettlementDetail detail = new SettlementDetail();
        detail.setSettlementId(90L);
        detail.setSplitMethod("ITEMIZED");
        detail.setTotalAmount(new BigDecimal("100"));
        detail.setSettlementStatus("REQUESTED");
        detail.setCreatedByMemberId(1L);
        detail.setViewerShareAmount(new BigDecimal("18"));
        detail.setViewerRequestStatus("PENDING");
        detail.setRequestedBy("Alex");
        detail.setGatheringName("Dinner");
        detail.setMerchantName("Nawa restaurant");
        when(settlementMapper.findDetail(90L, 2L)).thenReturn(detail);
        when(settlementMapper.findViewerItems(90L, 2L)).thenReturn(List.of(
            new SettlementViewerItem(
                31L, "Tea", BigDecimal.ONE, new BigDecimal("6")
            ),
            new SettlementViewerItem(
                32L, "Pasta", BigDecimal.ONE, new BigDecimal("12")
            )
        ));

        SettlementDetailResponse response = service().getSettlement(2L, 90L);

        assertEquals(new BigDecimal("100"), response.getTotalAmount());
        assertEquals(new BigDecimal("18"), response.getViewer().getShareAmount());
        assertEquals(new BigDecimal("18"), response.getViewer().getPayableAmount());
        assertEquals(List.of("Tea", "Pasta"), response.getViewerItems().stream()
            .map(item -> item.getName()).toList());
        assertEquals(List.of(new BigDecimal("6"), new BigDecimal("12")), response.getViewerItems().stream()
            .map(item -> item.getAllocatedAmount()).toList());
    }

    @Test
    void getSettlement_creator_returnsCollectionWithPaidCount() {
        SettlementDetail detail = new SettlementDetail();
        detail.setSettlementId(90L);
        detail.setSplitMethod("EQUAL");
        detail.setTotalAmount(new BigDecimal("100"));
        detail.setSettlementStatus("REQUESTED");
        detail.setCreatedByMemberId(1L);
        detail.setViewerShareAmount(new BigDecimal("25"));
        detail.setViewerRequestStatus("NOT_REQUESTED");
        when(settlementMapper.findDetail(90L, 1L)).thenReturn(detail);
        // 조회는 청구한 상대만 돌려준다. 원결제자 본인 행은 쿼리에서 이미 빠져 있다.
        when(settlementMapper.findCollectionMembers(90L)).thenReturn(List.of(
            new SettlementCollectionMember(72L, "Bora", new BigDecimal("25"), "PAID"),
            new SettlementCollectionMember(73L, "Chan", new BigDecimal("25"), "PENDING"),
            new SettlementCollectionMember(74L, "Dain", new BigDecimal("25"), "PENDING")
        ));

        SettlementDetailResponse response = service().getSettlement(1L, 90L);

        assertEquals(3, response.getCollection().getTotalCount());
        assertEquals(1, response.getCollection().getPaidCount());
        assertEquals(List.of(72L, 73L, 74L), response.getCollection().getParticipants().stream()
            .map(participant -> participant.getId()).toList());
        assertEquals(List.of("PAID", "PENDING", "PENDING"), response.getCollection().getParticipants().stream()
            .map(participant -> participant.getRequestStatus()).toList());
        assertEquals("B", response.getCollection().getParticipants().get(0).getInitials());
        assertEquals(new BigDecimal("25"), response.getCollection().getParticipants().get(0).getShareAmount());
    }

    @Test
    void getSettlement_participant_omitsCollection() {
        SettlementDetail detail = new SettlementDetail();
        detail.setSettlementId(90L);
        detail.setSplitMethod("EQUAL");
        detail.setTotalAmount(new BigDecimal("100"));
        detail.setSettlementStatus("REQUESTED");
        detail.setCreatedByMemberId(1L);
        detail.setViewerShareAmount(new BigDecimal("25"));
        detail.setViewerRequestStatus("PENDING");
        when(settlementMapper.findDetail(90L, 2L)).thenReturn(detail);

        SettlementDetailResponse response = service().getSettlement(2L, 90L);

        // 낼 사람에게는 다른 사람이 냈는지가 보이지 않는다. 빈 목록조차 만들지 않는다.
        assertNull(response.getCollection());
        verify(settlementMapper, never()).findCollectionMembers(90L);
    }

    /**
     * 사진 대신 세우는 이름 첫 글자를 만든다.
     *
     * 앞의 공백을 그대로 잘라 오면 빈 동그라미가 되고, 이모지처럼 두 자리를 차지하는 글자를
     * 한 자리만 잘라 오면 반쪽만 남아 깨진다.
     */
    @Test
    void getCandidates_awkwardDisplayNames_stillProduceOneReadableInitial() {
        SettlementSource source = new SettlementSource();
        source.setTransferId(20L);
        source.setAppointmentId(7L);
        when(settlementMapper.findCandidateSources(1L)).thenReturn(List.of(source));
        when(settlementMapper.findParticipants(7L)).thenReturn(List.of(
            new SettlementParticipant(71L, 1L, " alex"),
            new SettlementParticipant(72L, 2L, "\uD83D\uDE42Bora"),
            new SettlementParticipant(73L, 3L, "   ")
        ));

        List<SettlementParticipantResponse> participants =
            service().getCandidates(1L).get(0).getParticipants();

        assertEquals("A", participants.get(0).getInitials());
        assertEquals("\uD83D\uDE42", participants.get(1).getInitials());
        assertEquals("?", participants.get(2).getInitials());
    }

    private SettlementQueryService service() {
        return new SettlementQueryServiceImpl(settlementMapper, new SettlementViewerPolicy());
    }
}
