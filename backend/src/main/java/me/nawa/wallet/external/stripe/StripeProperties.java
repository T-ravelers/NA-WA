package me.nawa.wallet.external.stripe;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Getter
@Component
public class StripeProperties {

    private final String secretKey;
    private final String webhookSecret;

    public StripeProperties(
        @Value("${stripe.secret-key}") String secretKey,
        @Value("${stripe.webhook-secret}") String webhookSecret
    ) {
        this.secretKey = secretKey;
        this.webhookSecret = webhookSecret;
    }
}
