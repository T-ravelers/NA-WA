package me.nawa.wallet.dto.response;

public record StripeWebhookResponse(
    boolean received,        // 요청을 정상적으로 받았는지 (서명 검증 통과)
    boolean alreadyProcessed // 이미 처리된 이벤트/결제라 이번엔 아무것도 안 바꿨는지
) {
}
