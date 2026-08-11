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

/**
 * 정산 게임 API
 *
 * 게임형 정산의 동의, 진행 상태, 시작 및 결과 조회 요청을 처리합니다.
 */
@RestController
@RequestMapping("/api/v1/settlements/{settlementId}/game")
@RequiredArgsConstructor
public class SettlementGameController {

    private final SettlementService settlementService;

    /**
     * 게임 동의 제출
     *
     * 인증된 회원의 게임 정산 참여 동의 또는 거절 의사를 저장합니다.
     */
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

    /**
     * 게임 상태 조회
     *
     * 인증된 회원 관점의 게임 정산 진행 상태와 참여자 정보를 조회합니다.
     */
    @GetMapping
    public ApiResponse<SettlementGameResponse> getGame(
        @AuthenticationPrincipal AuthenticatedMember member,
        @PathVariable Long settlementId
    ) {
        return ApiResponse.success(settlementService.getGame(
            member.getMemberId(), settlementId
        ));
    }

    /**
     * 게임 시작
     *
     * 정산 요청자가 동의가 완료된 게임형 정산을 시작합니다.
     */
    @PostMapping("/start")
    public ApiResponse<Void> startGame(
        @AuthenticationPrincipal AuthenticatedMember member,
        @PathVariable Long settlementId
    ) {
        settlementService.startGame(member.getMemberId(), settlementId);
        return ApiResponse.success();
    }

    /**
     * 게임 결과 조회
     *
     * 게임으로 확정된 부담자와 정산 금액을 조회합니다.
     */
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
