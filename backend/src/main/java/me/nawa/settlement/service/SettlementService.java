package me.nawa.settlement.service;

import java.util.List;
import me.nawa.settlement.dto.request.CreateSettlementRequest;
import me.nawa.settlement.dto.request.GameConsentRequest;
import me.nawa.settlement.dto.request.ReceiptAllocationUpdateRequest;
import me.nawa.settlement.dto.request.ReceiptItemUpdateRequest;
import me.nawa.settlement.dto.response.ReceiptAnalysisResponse;
import me.nawa.settlement.dto.response.SettlementCandidateResponse;
import me.nawa.settlement.dto.response.SettlementCreateResponse;
import me.nawa.settlement.dto.response.SettlementDetailResponse;
import me.nawa.settlement.dto.response.SettlementGameResponse;
import me.nawa.settlement.dto.response.SettlementGameResultResponse;
import me.nawa.settlement.dto.response.SettlementListResponse;
import org.springframework.web.multipart.MultipartFile;

public interface SettlementService {

    default SettlementListResponse getSettlements(Long memberId) {
        throw new UnsupportedOperationException();
    }

    default List<SettlementCandidateResponse> getCandidates(Long memberId) {
        throw new UnsupportedOperationException();
    }

    default SettlementCreateResponse createSettlement(
        Long memberId,
        CreateSettlementRequest request
    ) {
        throw new UnsupportedOperationException();
    }

    default SettlementDetailResponse getSettlement(Long memberId, Long settlementId) {
        throw new UnsupportedOperationException();
    }

    default void paySettlement(Long memberId, Long settlementId) {
        throw new UnsupportedOperationException();
    }

    default void cancelSettlement(Long memberId, Long settlementId) {
        throw new UnsupportedOperationException();
    }

    default ReceiptAnalysisResponse analyzeReceipt(
        Long memberId,
        Long sourceTransferId,
        MultipartFile file
    ) {
        throw new UnsupportedOperationException();
    }

    default ReceiptAnalysisResponse updateReceiptItems(
        Long memberId,
        Long receiptAnalysisId,
        ReceiptItemUpdateRequest request
    ) {
        throw new UnsupportedOperationException();
    }

    default void updateReceiptAllocations(
        Long memberId,
        Long receiptAnalysisId,
        ReceiptAllocationUpdateRequest request
    ) {
        throw new UnsupportedOperationException();
    }

    default void submitGameConsent(
        Long memberId,
        Long settlementId,
        GameConsentRequest request
    ) {
        throw new UnsupportedOperationException();
    }

    default SettlementGameResponse getGame(Long memberId, Long settlementId) {
        throw new UnsupportedOperationException();
    }

    default void startGame(Long memberId, Long settlementId) {
        throw new UnsupportedOperationException();
    }

    default SettlementGameResultResponse getGameResult(
        Long memberId,
        Long settlementId
    ) {
        throw new UnsupportedOperationException();
    }
}
