package me.nawa.wallet.external.stripe;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class StripePaymentIntent {

    private final String providerPaymentId; // Stripe PaymentIntent ID
    private final String clientSecret;      // Stripe가 PaymentIntent를 생성할 때 같이 발급해주는 일회성 토큰
                                            // 브라우저가 이걸 들고 직접 Stripe와 통신해서 카드 결제를 완료
    private final String status;            // Stripe 원문 상태 문자열
}
