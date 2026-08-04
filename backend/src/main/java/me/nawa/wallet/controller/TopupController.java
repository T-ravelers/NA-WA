package me.nawa.wallet.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import me.nawa.common.response.ApiResponse;
import me.nawa.wallet.dto.response.TopupMethodsResponse;
import me.nawa.wallet.service.TopupService;
import org.springframework.web.bind.annotation.GetMapping;
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
}
