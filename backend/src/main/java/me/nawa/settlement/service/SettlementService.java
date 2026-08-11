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

/**
 * 정산 서비스 계약
 *
 * 정산과 영수증 분석, 게임형 정산의 도메인 작업을 정의합니다.
 */
public interface SettlementService {

    /**
     * 정산 목록 조회
     *
     * 회원이 받은 정산 요청과 보낸 정산 요청을 분류해 반환합니다.
     */
    default SettlementListResponse getSettlements(Long memberId) {
        throw new UnsupportedOperationException();
    }

    /**
     * 정산 후보 조회
     *
     * 회원이 정산을 생성할 수 있는 원거래와 참여자 정보를 반환합니다.
     */
    default List<SettlementCandidateResponse> getCandidates(Long memberId) {
        throw new UnsupportedOperationException();
    }

    /**
     * 정산 생성
     *
     * 회원의 생성 요청을 검증하고 새 정산의 식별자를 반환합니다.
     */
    default SettlementCreateResponse createSettlement(
        Long memberId,
        CreateSettlementRequest request
    ) {
        throw new UnsupportedOperationException();
    }

    /**
     * 정산 상세 조회
     *
     * 회원의 정산 참여 권한을 확인한 뒤 정산 상세 정보를 반환합니다.
     */
    default SettlementDetailResponse getSettlement(Long memberId, Long settlementId) {
        throw new UnsupportedOperationException();
    }

    /**
     * 정산 결제
     *
     * 회원이 부담해야 할 정산 금액의 결제를 처리합니다.
     */
    default void paySettlement(Long memberId, Long settlementId) {
        throw new UnsupportedOperationException();
    }

    /**
     * 정산 취소
     *
     * 정산 생성자가 보낸 정산 요청을 취소합니다.
     */
    default void cancelSettlement(Long memberId, Long settlementId) {
        throw new UnsupportedOperationException();
    }

    /**
     * 영수증 분석
     *
     * 영수증 파일을 분석해 항목과 인식 합계가 담긴 분석 결과를 생성합니다.
     */
    default ReceiptAnalysisResponse analyzeReceipt(
        Long memberId,
        Long sourceTransferId,
        MultipartFile file
    ) {
        throw new UnsupportedOperationException();
    }

    /**
     * 영수증 항목 수정
     *
     * 저장된 영수증 분석의 인식 항목을 수정하고 갱신된 결과를 반환합니다.
     */
    default ReceiptAnalysisResponse updateReceiptItems(
        Long memberId,
        Long receiptAnalysisId,
        ReceiptItemUpdateRequest request
    ) {
        throw new UnsupportedOperationException();
    }

    /**
     * 영수증 항목 배분 확정
     *
     * 영수증 항목마다 참여자에게 배분한 수량을 저장합니다.
     */
    default void updateReceiptAllocations(
        Long memberId,
        Long receiptAnalysisId,
        ReceiptAllocationUpdateRequest request
    ) {
        throw new UnsupportedOperationException();
    }

    /**
     * 게임 동의 제출
     *
     * 회원의 게임형 정산 참여 동의 또는 거절 상태를 저장합니다.
     */
    default void submitGameConsent(
        Long memberId,
        Long settlementId,
        GameConsentRequest request
    ) {
        throw new UnsupportedOperationException();
    }

    /**
     * 게임 상태 조회
     *
     * 회원 관점의 게임형 정산 진행 상태와 참여자 정보를 반환합니다.
     */
    default SettlementGameResponse getGame(Long memberId, Long settlementId) {
        throw new UnsupportedOperationException();
    }

    /**
     * 게임 시작
     *
     * 정산 요청자가 게임형 정산의 진행을 시작합니다.
     */
    default void startGame(Long memberId, Long settlementId) {
        throw new UnsupportedOperationException();
    }

    /**
     * 게임 결과 조회
     *
     * 게임으로 확정된 정산 부담자와 부담 금액을 반환합니다.
     */
    default SettlementGameResultResponse getGameResult(
        Long memberId,
        Long settlementId
    ) {
        throw new UnsupportedOperationException();
    }
}
