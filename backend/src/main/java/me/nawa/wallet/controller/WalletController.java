package me.nawa.wallet.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import me.nawa.auth.security.AuthenticatedMember;
import me.nawa.common.response.ApiResponse;
import me.nawa.wallet.dto.response.WalletHomeResponse;
import me.nawa.wallet.service.WalletService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

    @GetMapping
    public ApiResponse<WalletHomeResponse> getWalletHome(
       @AuthenticationPrincipal AuthenticatedMember member
    ){
        return ApiResponse.success(walletService.getWalletHome(member.getMemberId()));
    }
}

