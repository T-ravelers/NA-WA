package me.nawa.wallet.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import me.nawa.common.response.ApiResponse;
import me.nawa.wallet.dto.response.StripeWebhookResponse;
import me.nawa.wallet.service.TopupService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Stripe가 직접 호출하는 엔드포인트라 인증(JWT)이 없다 — Stripe-Signature 검증이 그 역할을 대신한다.
// SecurityConfig에 이 경로가 permitAll + CSRF 예외로 등록되어 있어야 한다.
@RestController
@RequestMapping("/api/v1/stripe")
@RequiredArgsConstructor
@Log4j2
public class StripeWebhookController {

    private final TopupService topupService;

    @PostMapping("/webhook")
    public ApiResponse<StripeWebhookResponse> handleWebhook(
        @RequestHeader("Stripe-Signature") String signature,
        // 서명 검증에 원문 바이트가 그대로 필요해서 DTO로 먼저 파싱하지 않고 String으로 받는다.
        @RequestBody String payload
    ) {
        return ApiResponse.success(topupService.applyStripeWebhookEvent(payload, signature));
    }
}
