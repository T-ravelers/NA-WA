package me.nawa.wallet.external.stripe;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Getter
@Component
public class StripeProperties {

    // false이면 충전이 꺼진 환경이다. 이때는 Stripe 키를 읽지도 쓰지도 않는다.
    private final boolean enabled;
    private final String secretKey;
    private final String webhookSecret;

    public StripeProperties(
        @Value("${payment.enabled}") boolean enabled,
        @Value("${stripe.secret-key}") String secretKey,
        @Value("${stripe.webhook-secret}") String webhookSecret
    ) {
        // 켜져 있는데 키가 비어 있으면 첫 충전 요청에서야 실패해 원인 찾기가 어렵다.
        // 서버가 시작할 때 멈춰서 설정 실수를 바로 드러낸다. 메시지에는 키 값을 넣지 않는다.
        if (enabled && (isBlank(secretKey) || isBlank(webhookSecret))) {
            throw new IllegalStateException(
                "payment.enabled=true이면 STRIPE_SECRET_KEY와 STRIPE_WEBHOOK_SECRET이 모두 필요합니다. "
                    + "충전을 끄려면 PAYMENT_ENABLED=false로 설정하세요."
            );
        }
        this.enabled = enabled;
        this.secretKey = secretKey;
        this.webhookSecret = webhookSecret;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
