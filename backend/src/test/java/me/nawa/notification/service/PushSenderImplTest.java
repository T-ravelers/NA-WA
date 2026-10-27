package me.nawa.notification.service;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECPoint;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import me.nawa.notification.domain.Notification;
import me.nawa.notification.domain.PushSubscription;
import me.nawa.notification.mapper.PushSubscriptionMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 암호화와 VAPID 서명은 진짜 키로 돌리고, 푸시 서비스로 나가는 HTTP 호출만 목으로 막는다.
 * 키 순서를 바꿔 넘기거나 서명이 틀리면 여기서 예외가 나거나 검증이 실패한다.
 */
@ExtendWith(MockitoExtension.class)
class PushSenderImplTest {

    private static final long RECIPIENT = 7L;
    private static final String MOZILLA = "https://updates.push.services.mozilla.com/wpush/v2/";
    private static final String SUBJECT = "mailto:ops@example.test";

    @Mock
    private PushSubscriptionMapper mapper;

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpResponse<Void> response;

    private final KeyPair vapidKeys = newKeyPair();

    // 테스트는 인터페이스 타입으로 부른다. 구현체는 여기서 한 번만 만든다.
    private PushSender sender(Executor executor) {
        return new PushSenderImpl(
            new PushProperties(encodePublic(vapidKeys), encodePrivate(vapidKeys), SUBJECT),
            mapper, httpClient, executor
        );
    }

    @Test
    void send_postsAnEncryptedVapidSignedRequestToEveryDeviceOfTheRecipient() throws Exception {
        when(mapper.findByMemberId(RECIPIENT))
            .thenReturn(List.of(subscription(1, "a", "ja"), subscription(2, "b", "vi")));
        respondWith(201);

        sender(Runnable::run).send(List.of(notification()));

        ArgumentCaptor<HttpRequest> requests = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient, times(2)).send(requests.capture(), any());
        HttpRequest first = requests.getAllValues().get(0);
        assertEquals("POST", first.method());
        assertEquals(URI.create(MOZILLA + "a"), first.uri());
        assertEquals("aes128gcm", header(first, "Content-Encoding"));
        assertEquals("86400", header(first, "TTL"));
        // aes128gcm 머리(86바이트)에 암호문이 붙는다. 평문이 그대로 나가면 이보다 짧다.
        assertTrue(first.bodyPublisher().orElseThrow().contentLength() > 86);
        assertEquals(URI.create(MOZILLA + "b"), requests.getAllValues().get(1).uri());
        verify(mapper, never()).deleteByEndpoint(any());

        assertVapidHeader(first);
    }

    @Test
    void payload_carriesOnlyTheValuesTheServiceWorkerNeedsToWriteTheText() throws Exception {
        JsonNode json = new ObjectMapper().readTree(PushSenderImpl.payload(notification(), "ja"));

        assertEquals("SETTLEMENT_REQUESTED", json.get("type").asText());
        assertEquals(55L, json.get("settlementId").asLong());
        assertEquals("민수", json.get("actorName").asText());
        assertEquals("부산 여행", json.get("gatheringName").asText());
        assertEquals(0, new BigDecimal("12000").compareTo(json.get("amount").decimalValue()));
        assertEquals("KRW", json.get("currencyCode").asText());
        assertEquals("ja", json.get("language").asText());
        // 수신자 번호처럼 화면에 필요 없는 값이 섞여 나가지 않는다.
        assertEquals(7, json.size());
    }

    @Test
    void send_deletesTheSubscriptionWhenThePushServiceSaysItIsGone() throws Exception {
        when(mapper.findByMemberId(RECIPIENT)).thenReturn(List.of(
            subscription(1, "gone410", "en"), subscription(2, "gone404", "en"), subscription(3, "alive", "en")
        ));
        doReturn(response).when(httpClient).send(any(HttpRequest.class), any());
        when(response.statusCode()).thenReturn(410, 404, 201);

        sender(Runnable::run).send(List.of(notification()));

        verify(mapper).deleteByEndpoint(MOZILLA + "gone410");
        verify(mapper).deleteByEndpoint(MOZILLA + "gone404");
        verify(mapper, never()).deleteByEndpoint(MOZILLA + "alive");
    }

    /** 일시적일 수 있는 실패로 구독을 지우면 멀쩡한 기기가 알림을 영영 못 받는다. */
    @Test
    void send_keepsTheSubscriptionWhenTheFailureMayBeTemporary() throws Exception {
        when(mapper.findByMemberId(RECIPIENT))
            .thenReturn(List.of(subscription(1, "a", "en"), subscription(2, "b", "en")));
        doReturn(response).when(httpClient).send(any(HttpRequest.class), any());
        when(response.statusCode()).thenReturn(429, 503);

        sender(Runnable::run).send(List.of(notification()));

        verify(httpClient, times(2)).send(any(HttpRequest.class), any());
        verify(mapper, never()).deleteByEndpoint(any());
    }

    @Test
    void send_doesNotLetOneFailingDeviceStopTheNextOne() throws Exception {
        when(mapper.findByMemberId(RECIPIENT))
            .thenReturn(List.of(subscription(1, "a", "en"), subscription(2, "b", "en")));
        doThrow(new IOException("connection reset")).doReturn(response)
            .when(httpClient).send(any(HttpRequest.class), any());
        when(response.statusCode()).thenReturn(201);

        assertDoesNotThrow(() -> sender(Runnable::run).send(List.of(notification())));

        verify(httpClient, times(2)).send(any(HttpRequest.class), any());
    }

    @Test
    void send_doesNotThrowWhenTheSubscriptionsCannotBeRead() {
        when(mapper.findByMemberId(RECIPIENT)).thenThrow(new IllegalStateException("db down"));

        assertDoesNotThrow(() -> sender(Runnable::run).send(List.of(notification())));

        verifyNoInteractions(httpClient);
    }

    @Test
    void send_doesNotThrowWhenCleaningUpAGoneSubscriptionFails() throws Exception {
        when(mapper.findByMemberId(RECIPIENT)).thenReturn(List.of(subscription(1, "a", "en")));
        doReturn(response).when(httpClient).send(any(HttpRequest.class), any());
        when(response.statusCode()).thenReturn(410);
        doThrow(new IllegalStateException("db down")).when(mapper).deleteByEndpoint(any());

        assertDoesNotThrow(() -> sender(Runnable::run).send(List.of(notification())));
    }

    @Test
    void send_doesNotThrowWhenTheExecutorRejectsTheTask() {
        Executor rejecting = task -> {
            throw new RejectedExecutionException("full");
        };

        assertDoesNotThrow(() -> sender(rejecting).send(List.of(notification(), notification())));

        verifyNoInteractions(mapper, httpClient);
    }

    @Test
    void send_restoresTheInterruptFlagWhenInterrupted() throws Exception {
        when(mapper.findByMemberId(RECIPIENT)).thenReturn(List.of(subscription(1, "a", "en")));
        doThrow(new InterruptedException()).when(httpClient).send(any(HttpRequest.class), any());

        sender(Runnable::run).send(List.of(notification()));

        // 확인하면서 플래그도 지워, 다음 테스트로 새지 않게 한다.
        assertTrue(Thread.interrupted());
    }

    /** 호출한 스레드(정산 처리)는 구독을 읽지도 보내지도 않고, 작업을 맡기기만 한다. */
    @Test
    void send_handsTheWorkToTheExecutorInsteadOfRunningOnTheCallingThread() {
        List<Runnable> queued = new ArrayList<>();
        when(mapper.findByMemberId(RECIPIENT)).thenReturn(List.of());

        sender(queued::add).send(List.of(notification()));

        verifyNoInteractions(mapper, httpClient);
        assertEquals(1, queued.size());

        queued.get(0).run();
        verify(mapper).findByMemberId(RECIPIENT);
    }

    @Test
    void send_doesNothingWhenPushIsOff() {
        PushSender off = new PushSenderImpl(new PushProperties("", "", ""), mapper, httpClient, Runnable::run);

        off.send(List.of(notification()));

        verifyNoInteractions(mapper, httpClient);
    }

    /** 키가 잘못됐으면 알림이 생기는 순간이 아니라 서버가 시작할 때 드러나야 한다. */
    @Test
    void constructor_failsAtStartupWhenTheVapidKeysAreBroken() {
        assertThrows(IllegalStateException.class, () -> new PushSenderImpl(
            new PushProperties("AAAA", "AAAA", ""), mapper, httpClient, Runnable::run
        ));

        KeyPair other = newKeyPair();
        assertThrows(IllegalStateException.class, () -> new PushSenderImpl(
            new PushProperties(encodePublic(vapidKeys), encodePrivate(other), ""),
            mapper, httpClient, Runnable::run
        ));
    }

    private void respondWith(int status) throws Exception {
        doReturn(response).when(httpClient).send(any(HttpRequest.class), any());
        when(response.statusCode()).thenReturn(status);
    }

    /** 서명을 공개키로 검증한다. 대상(aud)·연락처(sub)·만료(exp)도 규격대로여야 푸시 서비스가 받아 준다. */
    private void assertVapidHeader(HttpRequest request) throws Exception {
        String authorization = header(request, "Authorization");
        assertTrue(authorization.startsWith("vapid t="), authorization);
        int split = authorization.indexOf(", k=");
        String jwt = authorization.substring("vapid t=".length(), split);
        assertEquals(encodePublic(vapidKeys), authorization.substring(split + ", k=".length()));

        String[] parts = jwt.split("\\.");
        Signature verifier = Signature.getInstance("SHA256withECDSAinP1363Format");
        verifier.initVerify(vapidKeys.getPublic());
        verifier.update((parts[0] + "." + parts[1]).getBytes(US_ASCII));
        assertTrue(verifier.verify(Base64.getUrlDecoder().decode(parts[2])), "VAPID 서명이 공개키로 검증되지 않는다");

        JsonNode claims = new ObjectMapper().readTree(Base64.getUrlDecoder().decode(parts[1]));
        assertEquals("https://updates.push.services.mozilla.com", claims.get("aud").asText());
        assertEquals(SUBJECT, claims.get("sub").asText());
        long remaining = claims.get("exp").asLong() - Instant.now().getEpochSecond();
        assertTrue(remaining > 0 && remaining <= 24 * 60 * 60, "exp는 24시간 안이어야 한다: " + remaining);
    }

    private static String header(HttpRequest request, String name) {
        return request.headers().firstValue(name).orElseThrow();
    }

    private static Notification notification() {
        return Notification.builder()
            .recipientMemberId(RECIPIENT)
            .notificationType("SETTLEMENT_REQUESTED")
            .settlementId(55L)
            .actorName("민수")
            .gatheringName("부산 여행")
            .amount(new BigDecimal("12000.0000"))
            .currencyCode("KRW")
            .build();
    }

    /** 기기마다 자기 키 쌍을 가진다. 발송은 기기의 공개키로 본문을 암호화한다. */
    private static PushSubscription subscription(long id, String path, String language) {
        byte[] auth = new byte[16];
        new SecureRandom().nextBytes(auth);

        PushSubscription subscription = new PushSubscription();
        subscription.setPushSubscriptionId(id);
        subscription.setMemberId(RECIPIENT);
        subscription.setEndpoint(MOZILLA + path);
        subscription.setP256dh(encodePublic(newKeyPair()));
        subscription.setAuthSecret(encode(auth));
        subscription.setLanguageCode(language);
        return subscription;
    }

    private static KeyPair newKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"));
            return generator.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 브라우저와 VAPID가 쓰는 표기다. 0x04 + x + y, 65바이트를 base64url로 쓴다. */
    private static String encodePublic(KeyPair keyPair) {
        ECPoint point = ((ECPublicKey) keyPair.getPublic()).getW();
        byte[] bytes = new byte[65];
        bytes[0] = 0x04;
        System.arraycopy(fixed(point.getAffineX()), 0, bytes, 1, 32);
        System.arraycopy(fixed(point.getAffineY()), 0, bytes, 33, 32);
        return encode(bytes);
    }

    private static String encodePrivate(KeyPair keyPair) {
        return encode(fixed(((ECPrivateKey) keyPair.getPrivate()).getS()));
    }

    /** BigInteger를 앞을 0으로 채워 32바이트로 맞춘다(부호용 0바이트가 붙은 33바이트도 처리). */
    private static byte[] fixed(BigInteger value) {
        byte[] raw = value.toByteArray();
        byte[] fixed = new byte[32];
        int length = Math.min(raw.length, 32);
        System.arraycopy(raw, raw.length - length, fixed, 32 - length, length);
        return fixed;
    }

    private static String encode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
