package me.nawa.settlement.controller;

import lombok.RequiredArgsConstructor;
import me.nawa.auth.security.AuthenticatedMember;
import me.nawa.common.response.ApiResponse;
import me.nawa.settlement.dto.request.GameConsentRequest;
import me.nawa.settlement.dto.response.SettlementGameResponse;
import me.nawa.settlement.dto.response.SettlementGameResultResponse;
import me.nawa.settlement.service.SettlementService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/settlements/{settlementId}/game")
@RequiredArgsConstructor
public class SettlementGameController {

    private final SettlementService settlementService;

    @PostMapping("/consents")
    public ApiResponse<Void> submitGameConsent(
        @AuthenticationPrincipal AuthenticatedMember member,
        @PathVariable Long settlementId,
        @RequestBody GameConsentRequest request
    ) {
        settlementService.submitGameConsent(
            member.getMemberId(), settlementId, request
        );
        return ApiResponse.success();
    }

    @GetMapping
    public ApiResponse<SettlementGameResponse> getGame(
        @AuthenticationPrincipal AuthenticatedMember member,
        @PathVariable Long settlementId
    ) {
        return ApiResponse.success(settlementService.getGame(
            member.getMemberId(), settlementId
        ));
    }

    @PostMapping("/start")
    public ApiResponse<Void> startGame(
        @AuthenticationPrincipal AuthenticatedMember member,
        @PathVariable Long settlementId
    ) {
        settlementService.startGame(member.getMemberId(), settlementId);
        return ApiResponse.success();
    }

    @GetMapping("/result")
    public ApiResponse<SettlementGameResultResponse> getGameResult(
        @AuthenticationPrincipal AuthenticatedMember member,
        @PathVariable Long settlementId
    ) {
        return ApiResponse.success(settlementService.getGameResult(
            member.getMemberId(), settlementId
        ));
    }
}
