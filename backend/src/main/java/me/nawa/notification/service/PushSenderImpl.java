package me.nawa.notification.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.security.GeneralSecurityException;
import java.security.Security;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import me.nawa.notification.domain.Notification;
import me.nawa.notification.domain.PushSubscription;
import me.nawa.notification.mapper.PushSubscriptionMapper;
import nl.martijndwars.webpush.AbstractPushService;
import nl.martijndwars.webpush.Encoding;
import nl.martijndwars.webpush.Utils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * 웹 푸시를 전용 스레드풀에서 보내고, 푸시 서비스가 "이 구독은 끝났다"고 답하면 구독을 지운다.
 *
 * 본문 암호화(RFC 8291)와 VAPID 서명(RFC 8292)은 web-push 라이브러리의 요청 조립 부분만
 * 쓴다. 라이브러리의 전송 메서드는 호출마다 HTTP 클라이언트를 새로 만들고 기다리는 시간이
 * 없으며 기본 인코딩도 구형(aesgcm)이라 쓰지 않는다. 전송은 시간제한을 둔 JDK HttpClient로
 * 하고, 인코딩은 표준인 aes128gcm으로 고정한다.
 *
 * 로그에는 구독 번호와 상태 코드, 예외 종류만 남긴다. endpoint 경로는 그 기기로 보내는 권한을
 * 가리키는 값이고, 예외 메시지에는 그 주소가 섞여 들어올 수 있기 때문이다.
 */
@Slf4j
@Service
public class PushSenderImpl implements PushSender {

    // 기기가 꺼져 있을 때 푸시 서비스가 들고 있어 줄 시간이다. 하루가 지난 정산 알림은 목록으로 본다.
    private static final int TTL_SECONDS = 24 * 60 * 60;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final PushSubscriptionMapper pushSubscriptionMapper;
    private final HttpClient httpClient;
    private final Executor executor;
    // 푸시가 꺼져 있으면 null이다.
    private final VapidRequestBuilder requestBuilder;

    public PushSenderImpl(
        PushProperties properties,
        PushSubscriptionMapper pushSubscriptionMapper,
        HttpClient pushHttpClient,
        @Qualifier("pushExecutor") Executor pushExecutor
    ) {
        this.pushSubscriptionMapper = pushSubscriptionMapper;
        this.httpClient = pushHttpClient;
        this.executor = pushExecutor;
        this.requestBuilder = properties.isEnabled() ? VapidRequestBuilder.create(properties) : null;
    }

    @Override
    public void send(List<Notification> notifications) {
        if (requestBuilder == null || notifications.isEmpty()) {
            return;
        }
        for (Notification notification : notifications) {
            try {
                executor.execute(() -> deliver(notification));
            } catch (RuntimeException e) {
                log.warn("푸시 발송을 대기열에 넣지 못했습니다. cause={}", e.getClass().getSimpleName());
            }
        }
    }

    private void deliver(Notification notification) {
        List<PushSubscription> subscriptions;
        try {
            subscriptions = pushSubscriptionMapper.findByMemberId(notification.getRecipientMemberId());
        } catch (RuntimeException e) {
            log.warn("푸시 구독을 읽지 못했습니다. cause={}", e.getClass().getSimpleName());
            return;
        }
        for (PushSubscription subscription : subscriptions) {
            deliverTo(subscription, notification);
        }
    }

    /** 기기 하나의 실패가 같은 회원의 다른 기기로 번지지 않도록 기기마다 따로 감싼다. */
    private void deliverTo(PushSubscription subscription, Notification notification) {
        try {
            int status = post(subscription, payload(notification, subscription.getLanguageCode()));
            if (status == 404 || status == 410) {
                // 푸시 서비스가 이 구독은 끝났다고 알린 것이다(앱 삭제·권한 회수·만료).
                // 계속 보내 봐야 실패만 쌓이므로 지운다. 다시 구독하면 새로 등록된다.
                pushSubscriptionMapper.deleteByEndpoint(subscription.getEndpoint());
            } else if (status < 200 || status >= 300) {
                log.warn("푸시 서비스가 발송을 거절했습니다. subscriptionId={}, status={}",
                    subscription.getPushSubscriptionId(), status);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.warn("푸시 발송에 실패했습니다. subscriptionId={}, cause={}",
                subscription.getPushSubscriptionId(), e.getClass().getSimpleName());
        }
    }

    private int post(PushSubscription subscription, byte[] payload) throws Exception {
        nl.martijndwars.webpush.HttpRequest prepared = requestBuilder.build(
            new nl.martijndwars.webpush.Notification(
                subscription.getEndpoint(),
                subscription.getP256dh(),
                subscription.getAuthSecret(),
                payload,
                TTL_SECONDS
            )
        );
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(prepared.getUrl()))
            .timeout(REQUEST_TIMEOUT)
            .POST(HttpRequest.BodyPublishers.ofByteArray(prepared.getBody()));
        prepared.getHeaders().forEach(request::header);
        return httpClient.send(request.build(), BodyHandlers.discarding()).statusCode();
    }

    /**
     * 서비스 워커가 문구를 만들 재료다. 백엔드에는 알림 문구를 만드는 i18n이 없어서 문장이 아니라
     * 값을 보내고, 어떤 언어로 만들지는 구독을 등록한 화면이 정한 language가 알려 준다.
     */
    static byte[] payload(Notification notification, String language) throws JsonProcessingException {
        return OBJECT_MAPPER.writeValueAsBytes(new Payload(
            notification.getNotificationType(),
            notification.getSettlementId(),
            notification.getActorName(),
            notification.getGatheringName(),
            notification.getAmount(),
            notification.getCurrencyCode(),
            language
        ));
    }

    record Payload(
        String type,
        Long settlementId,
        String actorName,
        String gatheringName,
        BigDecimal amount,
        String currencyCode,
        String language
    ) {
    }

    /** 라이브러리의 요청 조립(prepareRequest)이 protected라서 상속해서 연다. */
    private static final class VapidRequestBuilder extends AbstractPushService<VapidRequestBuilder> {

        private VapidRequestBuilder(String publicKey, String privateKey, String subject)
            throws GeneralSecurityException {
            super(publicKey, privateKey, subject);
        }

        /** 키가 잘못됐으면 발송할 때가 아니라 서버가 시작할 때 멈추게 한다. */
        static VapidRequestBuilder create(PushProperties properties) {
            // 라이브러리가 "BC"라는 이름으로 공급자를 찾는다. 목록 맨 뒤에 붙으므로 다른 곳의
            // 알고리즘 선택은 바뀌지 않는다.
            if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
                Security.addProvider(new BouncyCastleProvider());
            }
            try {
                VapidRequestBuilder builder = new VapidRequestBuilder(
                    properties.getPublicKey(), properties.getPrivateKey(), properties.getSubject());
                if (!Utils.verifyKeyPair(builder.getPrivateKey(), builder.getPublicKey())) {
                    throw new IllegalStateException(
                        "VAPID_PUBLIC_KEY와 VAPID_PRIVATE_KEY가 한 쌍이 아닙니다.");
                }
                return builder;
            } catch (GeneralSecurityException | IllegalArgumentException e) {
                throw new IllegalStateException(
                    "VAPID 키를 읽을 수 없습니다. VAPID_PUBLIC_KEY와 VAPID_PRIVATE_KEY를 확인하세요.", e);
            }
        }

        nl.martijndwars.webpush.HttpRequest build(nl.martijndwars.webpush.Notification notification)
            throws Exception {
            return prepareRequest(notification, Encoding.AES128GCM);
        }
    }
}
