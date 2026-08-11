package me.nawa.settlement.controller;

import java.util.List;
import lombok.RequiredArgsConstructor;
import me.nawa.auth.security.AuthenticatedMember;
import me.nawa.common.response.ApiResponse;
import me.nawa.settlement.dto.request.CreateSettlementRequest;
import me.nawa.settlement.dto.response.SettlementCandidateResponse;
import me.nawa.settlement.dto.response.SettlementCreateResponse;
import me.nawa.settlement.dto.response.SettlementDetailResponse;
import me.nawa.settlement.dto.response.SettlementListResponse;
import me.nawa.settlement.service.SettlementCreationService;
import me.nawa.settlement.service.SettlementPaymentService;
import me.nawa.settlement.service.SettlementQueryService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 정산 목록·생성·상세 화면의 API다.
 *
 * 목록 화면 진입 시 목록을, “Create settlement” 클릭 시 후보를 조회한다. 생성 완료,
 * 상세 화면의 “Pay”와 “Cancel” 클릭은 각각 전용 정산 작업으로 위임한다.
 */
@RestController
@RequestMapping("/api/v1/settlements")
@RequiredArgsConstructor
public class SettlementController {

    private final SettlementQueryService settlementQueryService;
    private final SettlementCreationService settlementCreationService;
    private final SettlementPaymentService settlementPaymentService;

    /**
     * 정산 목록 조회
     *
     * 인증된 회원이 받은 정산 요청과 보낸 정산 요청을 함께 조회합니다.
     */
    @GetMapping
    public ApiResponse<SettlementListResponse> getSettlements(
        @AuthenticationPrincipal AuthenticatedMember member
    ) {
        return ApiResponse.success(
            settlementQueryService.getSettlements(member.getMemberId())
        );
    }

    /**
     * 정산 후보 조회
     *
     * 인증된 회원이 정산 생성에 사용할 수 있는 원거래와 참여자 정보를 조회합니다.
     */
    @GetMapping("/candidates")
    public ApiResponse<List<SettlementCandidateResponse>> getCandidates(
        @AuthenticationPrincipal AuthenticatedMember member
    ) {
        return ApiResponse.success(
            settlementQueryService.getCandidates(member.getMemberId())
        );
    }

    /**
     * 정산 생성
     *
     * 원거래와 참여자, 정산 유형을 바탕으로 새 정산을 생성합니다.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SettlementCreateResponse> createSettlement(
        @AuthenticationPrincipal AuthenticatedMember member,
        @RequestBody CreateSettlementRequest request
    ) {
        return ApiResponse.success(settlementCreationService.createSettlement(
            member.getMemberId(), request
        ));
    }

    /**
     * 정산 상세 조회
     *
     * 인증된 회원이 참여한 정산의 금액, 상태 및 항목 정보를 조회합니다.
     */
    @GetMapping("/{settlementId}")
    public ApiResponse<SettlementDetailResponse> getSettlement(
        @AuthenticationPrincipal AuthenticatedMember member,
        @PathVariable Long settlementId
    ) {
        return ApiResponse.success(settlementQueryService.getSettlement(
            member.getMemberId(), settlementId
        ));
    }

    /**
     * 정산 결제
     *
     * 인증된 회원의 정산 부담금 결제를 처리합니다.
     */
    @PostMapping("/{settlementId}/payments")
    public ApiResponse<Void> paySettlement(
        @AuthenticationPrincipal AuthenticatedMember member,
        @PathVariable Long settlementId
    ) {
        settlementPaymentService.paySettlement(member.getMemberId(), settlementId);
        return ApiResponse.success();
    }

    /**
     * 정산 취소
     *
     * 인증된 회원이 생성한 정산 요청을 취소합니다.
     */
    @PostMapping("/{settlementId}/cancel")
    public ApiResponse<Void> cancelSettlement(
        @AuthenticationPrincipal AuthenticatedMember member,
        @PathVariable Long settlementId
    ) {
        settlementPaymentService.cancelSettlement(member.getMemberId(), settlementId);
        return ApiResponse.success();
    }
}
