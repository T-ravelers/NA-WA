package me.nawa.wallet.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import me.nawa.auth.security.AuthenticatedMember;
import me.nawa.common.response.ApiResponse;
import me.nawa.wallet.dto.request.StripeIntentCreateRequest;
import me.nawa.wallet.dto.request.TopupPreviewRequest;
import me.nawa.wallet.dto.response.StripeIntentResponse;
import me.nawa.wallet.dto.response.StripeTopupStatusResponse;
import me.nawa.wallet.dto.response.TopupListResponse;
import me.nawa.wallet.dto.response.TopupMethodsResponse;
import me.nawa.wallet.dto.response.TopupPreviewResponse;
import me.nawa.wallet.service.TopupService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/topups")
@RequiredArgsConstructor
@Log4j2
public class TopupController {

    private final TopupService topupService;

    @GetMapping
    public ApiResponse<TopupListResponse> getTopups(
        @AuthenticationPrincipal AuthenticatedMember member,
        @RequestParam(required = false) Long cursor,
        @RequestParam(required = false) Integer size
    ){
        return ApiResponse.success(topupService.getTopups(member.getMemberId(), cursor, size));
    }

    @GetMapping("/methods")
    public ApiResponse<TopupMethodsResponse> getTopupMethods(){
        return ApiResponse.success(topupService.getAvailableTopupMethods());
    }

    @PostMapping("/preview")
    public ApiResponse<TopupPreviewResponse> previewTopup(
        @AuthenticationPrincipal AuthenticatedMember member,
        @RequestBody TopupPreviewRequest request
    ){
        return ApiResponse.success(topupService.previewTopup(member.getMemberId(), request));
    }

    @PostMapping("/stripe/intent")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<StripeIntentResponse> createStripeIntent(
        @AuthenticationPrincipal AuthenticatedMember member,
        @RequestHeader("Idempotency-Key") String idempotencyKdy,
        @RequestBody StripeIntentCreateRequest request
        ){
        return ApiResponse.success(topupService.createStripeIntent(member.getMemberId(), idempotencyKdy, request));
    }

    @GetMapping("/stripe/{topupId}")
    public ApiResponse<StripeTopupStatusResponse> getStripeTopupStatus(
        @AuthenticationPrincipal AuthenticatedMember member,
        @PathVariable Long topupId
    ){
        return ApiResponse.success(topupService.getStripeTopupStatus(member.getMemberId(), topupId));
    }

}
