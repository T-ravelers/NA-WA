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
        // 결제수단 타입을 지정하지 않으면 Stripe 계정 설정에 따라 Link가 함께 활성화되고,
        // Stripe.js가 결제 후에도 지워지지 않는 Link "정보 저장" 위젯을 페이지에 붙인다.
        // 한국 로컬 결제수단(카드/카카오페이/네이버페이/페이코/삼성페이)은 그대로 두고
        // Link만 명시적으로 배제한다.
        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
            .setAmount(amountKrw.longValueExact()) // KRW는 zero-decimal이라 그대로 정수 전달
            .setCurrency("krw")
            .addPaymentMethodType("card")
            .addPaymentMethodType("kakao_pay")
            .addPaymentMethodType("naver_pay")
            .addPaymentMethodType("payco")
            .addPaymentMethodType("samsung_pay")
            .build();

        RequestOptions options = RequestOptions.builder()
            .setIdempotencyKey(idempotencyKey)
            .build();

        PaymentIntent intent = PaymentIntent.create(params, options);
        return new StripePaymentIntent(intent.getId(), intent.getClientSecret(), intent.getStatus());
    }

    // wallet_topups.provider_payment_id로 저장해둔 ID로 Stripe 쪽 최신 상태를 다시 조회한다.
    // 동일한 요청 재요청 시(client secret 가져오기 위함), 충전 상태를 조회할 때 사용(최신 충전 상태 반영)
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
