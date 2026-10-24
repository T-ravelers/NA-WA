package me.nawa.notification.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import me.nawa.common.exception.BusinessException;
import me.nawa.notification.dto.request.PushSubscribeRequest;
import me.nawa.notification.mapper.PushSubscriptionMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PushSubscriptionServiceImplTest {

    @Mock
    private PushSubscriptionMapper mapper;

    // 테스트는 인터페이스 타입으로 부른다. 구현체는 여기서 한 번만 만든다.
    private PushSubscriptionService service(String publicKey) {
        return new PushSubscriptionServiceImpl(mapper, publicKey);
    }

    @Test
    void subscribe_storesNormalizedLanguage() {
        service("key").subscribe(1L, request("https://fcm.googleapis.com/fcm/send/abc", "zh-tw"));

        verify(mapper).upsert(1L, "https://fcm.googleapis.com/fcm/send/abc", "BP-k_1", "au-_1", "zh-TW");
    }

    @Test
    void subscribe_rejectsEndpointsTheServerShouldNotCall() {
        // 서버가 나중에 직접 POST하는 주소라 https가 아니거나 호스트가 없으면 거른다.
        for (String bad : new String[] {
            "http://push.example.test/x", "file:///etc/passwd", "https:///nohost", "not a url", "", null
        }) {
            assertThrows(
                BusinessException.class,
                () -> service("key").subscribe(1L, request(bad, "en")),
                String.valueOf(bad)
            );
        }
        verify(mapper, never()).upsert(any(), any(), any(), any(), any());
    }

    @Test
    void subscribe_rejectsMalformedKeysAndUnsupportedLanguage() {
        PushSubscribeRequest badKey = request("https://push.example.test/x", "en");
        badKey.setAuth("has space");
        assertThrows(BusinessException.class, () -> service("key").subscribe(1L, badKey));

        assertThrows(
            BusinessException.class,
            () -> service("key").subscribe(1L, request("https://push.example.test/x", "ko"))
        );
        verify(mapper, never()).upsert(any(), any(), any(), any(), any());
    }

    @Test
    void getPublicKey_isNullWhenPushIsOff() {
        assertNull(service("  ").getPublicKey());
        assertEquals("abc", service(" abc ").getPublicKey());
    }

    private static PushSubscribeRequest request(String endpoint, String language) {
        PushSubscribeRequest request = new PushSubscribeRequest();
        request.setEndpoint(endpoint);
        request.setP256dh("BP-k_1");
        request.setAuth("au-_1");
        request.setLanguage(language);
        return request;
    }
}
