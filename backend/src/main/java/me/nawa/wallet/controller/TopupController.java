package me.nawa.wallet.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import me.nawa.auth.security.AuthenticatedMember;
import me.nawa.common.response.ApiResponse;
import me.nawa.wallet.dto.request.TopupPreviewRequest;
import me.nawa.wallet.dto.response.TopupMethodsResponse;
import me.nawa.wallet.dto.response.TopupPreviewResponse;
import me.nawa.wallet.service.TopupService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/topups")
@RequiredArgsConstructor
@Log4j2
public class TopupController {

    private final TopupService topupService;

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
}
