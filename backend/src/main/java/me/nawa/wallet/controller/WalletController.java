package me.nawa.wallet.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import me.nawa.common.response.ApiResponse;
import me.nawa.wallet.dto.response.WalletHomeResponse;
import me.nawa.wallet.service.WalletService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/wallet")
@RequiredArgsConstructor
@Log4j2
public class WalletController {

    private final WalletService walletService;

    // TODO: 인증 연동되면 X-Member_Id 헤더 대신 SecurityContext에서 memberId 추출하도록 교체
    @GetMapping
    public ApiResponse<WalletHomeResponse> getWalletHome(
        @RequestHeader("X-Member-Id") Long memberId
    ){
        return ApiResponse.success(walletService.getWalletHome(memberId));
    }
}

