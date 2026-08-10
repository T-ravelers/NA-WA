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
import me.nawa.settlement.service.SettlementService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/settlements")
@RequiredArgsConstructor
public class SettlementController {

    private final SettlementService settlementService;

    @GetMapping
    public ApiResponse<SettlementListResponse> getSettlements(
        @AuthenticationPrincipal AuthenticatedMember member
    ) {
        return ApiResponse.success(
            settlementService.getSettlements(member.getMemberId())
        );
    }

    @GetMapping("/candidates")
    public ApiResponse<List<SettlementCandidateResponse>> getCandidates(
        @AuthenticationPrincipal AuthenticatedMember member
    ) {
        return ApiResponse.success(
            settlementService.getCandidates(member.getMemberId())
        );
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SettlementCreateResponse> createSettlement(
        @AuthenticationPrincipal AuthenticatedMember member,
        @RequestBody CreateSettlementRequest request
    ) {
        return ApiResponse.success(settlementService.createSettlement(
            member.getMemberId(), request
        ));
    }

    @GetMapping("/{settlementId}")
    public ApiResponse<SettlementDetailResponse> getSettlement(
        @AuthenticationPrincipal AuthenticatedMember member,
        @PathVariable Long settlementId
    ) {
        return ApiResponse.success(settlementService.getSettlement(
            member.getMemberId(), settlementId
        ));
    }

    @PostMapping("/{settlementId}/payments")
    public ApiResponse<Void> paySettlement(
        @AuthenticationPrincipal AuthenticatedMember member,
        @PathVariable Long settlementId
    ) {
        settlementService.paySettlement(member.getMemberId(), settlementId);
        return ApiResponse.success();
    }

    @PostMapping("/{settlementId}/cancel")
    public ApiResponse<Void> cancelSettlement(
        @AuthenticationPrincipal AuthenticatedMember member,
        @PathVariable Long settlementId
    ) {
        settlementService.cancelSettlement(member.getMemberId(), settlementId);
        return ApiResponse.success();
    }
}
