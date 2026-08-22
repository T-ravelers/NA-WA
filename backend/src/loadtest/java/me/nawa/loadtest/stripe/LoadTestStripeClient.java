package me.nawa.loadtest.stripe;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import me.nawa.wallet.external.stripe.StripeClient;
import me.nawa.wallet.external.stripe.StripePaymentIntent;
import me.nawa.wallet.external.stripe.StripeProperties;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 부하 테스트에서 Stripe 네트워크 호출을 제거하는 전용 클라이언트입니다.
 *
 * <p>실제 Stripe를 호출하면 외부 지연과 rate limit을 NA-WA 백엔드 성능으로 잘못
 * 측정하고, 키 설정 실수 시 실제 결제까지 시도할 수 있습니다. 생성 응답은 결제 대기,
 * 다음 상태 조회는 성공을 반환해 운영 서비스의 DB 기록과 지갑 적립 경로는 그대로
 * 실행하되 외부 통신만 대체합니다.
 *
 * <p>이 클래스는 {@code src/loadtest/java}에 있으므로 {@code -Ploadtest} 빌드에만
 * 들어갑니다. 운영 빌드에 포함되면 안 됩니다.
 */
@Primary
@Component
public class LoadTestStripeClient extends StripeClient {

    public LoadTestStripeClient(StripeProperties properties) {
        super(properties);
    }

    @Override
    public StripePaymentIntent createPaymentIntent(
        BigDecimal amountKrw,
        String idempotencyKey
    ) {
        String providerId = "pi_loadtest_" + digest(idempotencyKey);
        return new StripePaymentIntent(
            providerId,
            providerId + "_secret_loadtest",
            "requires_payment_method"
        );
    }

    @Override
    public StripePaymentIntent retrievePaymentIntent(String providerPaymentId) {
        return new StripePaymentIntent(
            providerPaymentId,
            providerPaymentId + "_secret_loadtest",
            "succeeded"
        );
    }

    private String digest(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes, 0, 12);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
