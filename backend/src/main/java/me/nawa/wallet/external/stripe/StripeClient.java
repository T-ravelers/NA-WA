package me.nawa.wallet.external.stripe;

import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import com.stripe.param.PaymentIntentCreateParams;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;

@Component
public class StripeClient {

    private final StripeProperties properties;

    public StripeClient(StripeProperties properties) {
        this.properties = properties;
    }

    // Stripe SDK는 API 키를 매 호출 인자로 안 받고 static 필드(Stripe.apiKey)로 참조하므로,
    // 빈이 만들어진 직후 한 번만 세팅해준다.
    @PostConstruct
    void init() {
        Stripe.apiKey = properties.getSecretKey();
    }

    // 새 충전 건에 대해 Stripe에 PaymentIntent 생성을 요청한다 (우리 서버 -> Stripe).
    // idempotencyKey를 같이 보내면, 네트워크 재시도 등으로 같은 요청이 중복 전송돼도
    // Stripe가 새로 만들지 않고 최초 응답을 그대로 돌려준다 (wallet_topups.idempotency_key와 짝).
    public StripePaymentIntent createPaymentIntent(BigDecimal amountKrw, String idempotencyKey) throws StripeException {
        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
            .setAmount(amountKrw.longValueExact()) // KRW는 zero-decimal이라 그대로 정수 전달
            .setCurrency("krw")
            .build();

        RequestOptions options = RequestOptions.builder()
            .setIdempotencyKey(idempotencyKey)
            .build();

        PaymentIntent intent = PaymentIntent.create(params, options);
        return new StripePaymentIntent(intent.getId(), intent.getClientSecret(), intent.getStatus());
    }

    // wallet_topups.provider_payment_id로 저장해둔 ID로 Stripe 쪽 최신 상태를 다시 조회한다.
    // 동일한 요청 재요청 시,
    public StripePaymentIntent retrievePaymentIntent(String providerPaymentId) throws StripeException {
        PaymentIntent intent = PaymentIntent.retrieve(providerPaymentId);
        return new StripePaymentIntent(intent.getId(), intent.getClientSecret(), intent.getStatus());
    }

    // Stripe 웹훅 요청의 진위를 검증한다 (Stripe -> 우리 서버).
    // Stripe-Signature 헤더를 webhookSecret으로 검증해서, 통과하면 파싱된 Event를 돌려주고
    // 서명이 안 맞으면 SignatureVerificationException을 던진다.
    // SecurityConfig에서 이 웹훅 경로를 인증/CSRF 없이 permitAll 해도 안전한 이유가 이 검증.
    public Event constructWebhookEvent(String payload, String signatureHeader) throws SignatureVerificationException {
        return Webhook.constructEvent(payload, signatureHeader, properties.getWebhookSecret());
    }
}
