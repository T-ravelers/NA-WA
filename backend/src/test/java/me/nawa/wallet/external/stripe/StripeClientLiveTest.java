package me.nawa.wallet.external.stripe;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Mock이 아니라 실제 Stripe test 모드 API를 호출해서 StripeClient 연동을 눈으로 검증하는 테스트.
// STRIPE_SECRET_KEY가 sk_test_로 시작하는 값으로 없으면 자동 skip되므로
// 평소 ./gradlew test(CI 포함)에는 영향이 없고, live 키(sk_live_)로는 애초에 켜지지 않는다.
//
// 실행:
//   STRIPE_SECRET_KEY=sk_test_... ./gradlew test --tests "*StripeClientLiveTest"
// 실행 후 Stripe 대시보드 (Test mode -> Payments)에서 같은 PaymentIntent id가 보이는지 확인.
@EnabledIfEnvironmentVariable(named = "STRIPE_SECRET_KEY", matches = "sk_test_.+")
class StripeClientLiveTest {

    @Test
    void createPaymentIntent가_실제_Stripe_test_모드에_생성된다() throws Exception {
        StripeProperties properties = new StripeProperties(
            System.getenv("STRIPE_SECRET_KEY"),
            ""
        );
        StripeClient stripeClient = new StripeClient(properties);
        stripeClient.init();

        StripePaymentIntent intent = stripeClient.createPaymentIntent(
            BigDecimal.valueOf(1000),
            UUID.randomUUID().toString()
        );

        assertNotNull(intent.getProviderPaymentId());
        assertTrue(intent.getProviderPaymentId().startsWith("pi_"));
        assertNotNull(intent.getClientSecret());

        System.out.println(
            "[StripeClientLiveTest] created id=" + intent.getProviderPaymentId()
                + " status=" + intent.getStatus()
        );
    }
}
