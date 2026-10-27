package me.nawa.notification.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Base64;
import me.nawa.common.exception.BusinessException;
import me.nawa.notification.dto.request.PushSubscribeRequest;
import me.nawa.notification.mapper.PushSubscriptionMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PushSubscriptionServiceImplTest {

    private static final String FCM = "https://fcm.googleapis.com/fcm/send/abc";

    @Mock
    private PushSubscriptionMapper mapper;

    // 테스트는 인터페이스 타입으로 부른다. 구현체는 여기서 한 번만 만든다.
    private PushSubscriptionService service() {
        return new PushSubscriptionServiceImpl(mapper, new PushProperties("", "", ""));
    }

    @Test
    void subscribe_storesNormalizedLanguage() {
        service().subscribe(1L, request(FCM, "zh-tw"));

        verify(mapper).upsert(1L, FCM, p256dh(), auth(), "zh-TW");
    }

    @Test
    void subscribe_acceptsTheEndpointHostsOfTheMajorBrowsers() {
        String[] endpoints = {
            FCM,
            "https://updates.push.services.mozilla.com/wpush/v2/abc",
            "https://web.push.apple.com/abc",
            "https://wns2-par02p.notify.windows.com/w/?token=abc"
        };
        for (String endpoint : endpoints) {
            service().subscribe(1L, request(endpoint, "en"));
            verify(mapper).upsert(1L, endpoint, p256dh(), auth(), "en");
        }
    }

    /**
     * 서버가 나중에 직접 POST하는 주소라 https이면서 푸시 서비스 호스트일 때만 받는다.
     * 내부 주소와, 푸시 서비스처럼 보이게 꾸민 주소가 모두 걸러져야 한다.
     */
    @Test
    void subscribe_rejectsEndpointsTheServerShouldNotCall() {
        String[] bad = {
            "http://fcm.googleapis.com/fcm/send/abc",
            "file:///etc/passwd",
            "https:///nohost",
            "not a url",
            "",
            null,
            "https://push.example.test/x",
            "https://localhost/x",
            "https://169.254.169.254/latest/meta-data",
            "https://fcm.googleapis.com.evil.test/x",
            "https://evilfcm.googleapis.com/x",
            "https://fcm.googleapis.com@evil.test/x",
            FCM + "a".repeat(512)
        };
        for (String endpoint : bad) {
            assertThrows(
                BusinessException.class,
                () -> service().subscribe(1L, request(endpoint, "en")),
                String.valueOf(endpoint)
            );
        }
        verify(mapper, never()).upsert(any(), any(), any(), any(), any());
    }

    @Test
    void subscribe_rejectsKeysThatTheBrowserCouldNotHaveProduced() {
        String[] badP256dh = {
            // 길이가 다르다.
            encode(new byte[64]),
            // 압축하지 않은 점은 0x04로 시작한다.
            encode(point(0x02)),
            // base64url이 아니다(표준 base64 문자, 공백).
            "+/" + p256dh(),
            "has space",
            null
        };
        for (String key : badP256dh) {
            PushSubscribeRequest request = request(FCM, "en");
            request.setP256dh(key);
            assertThrows(BusinessException.class, () -> service().subscribe(1L, request), String.valueOf(key));
        }

        PushSubscribeRequest shortAuth = request(FCM, "en");
        shortAuth.setAuth(encode(new byte[15]));
        assertThrows(BusinessException.class, () -> service().subscribe(1L, shortAuth));

        assertThrows(BusinessException.class, () -> service().subscribe(1L, request(FCM, "ko")));
        verify(mapper, never()).upsert(any(), any(), any(), any(), any());
    }

    /**
     * 허용 도메인 안에서 경로만 바꾸면 endpoint가 계속 달라진다. 상한이 없으면 한 회원이 구독을 끝없이
     * 늘려 발송 스레드풀을 붙잡을 수 있다.
     */
    @Test
    void subscribe_rejectsANewDeviceOnceTheMemberHasReachedTheLimit() {
        when(mapper.countByMemberExcludingEndpoint(1L, FCM)).thenReturn(20);

        assertThrows(BusinessException.class, () -> service().subscribe(1L, request(FCM, "en")));

        verify(mapper, never()).upsert(any(), any(), any(), any(), any());
    }

    @Test
    void subscribe_acceptsTheLastDeviceBelowTheLimit() {
        when(mapper.countByMemberExcludingEndpoint(1L, FCM)).thenReturn(19);

        service().subscribe(1L, request(FCM, "en"));

        verify(mapper).upsert(1L, FCM, p256dh(), auth(), "en");
    }

    /**
     * 이미 등록한 기기를 다시 등록하는 경우(언어 변경, 재로그인)는 새 기기가 아니다. 그래서 개수 조회에
     * 이 endpoint를 넘겨 제외하고, 상한에 닿은 회원도 기존 기기를 계속 갱신할 수 있다.
     */
    @Test
    void subscribe_countsTheOtherDevicesOnly() {
        service().subscribe(1L, request(FCM, "en"));

        verify(mapper).countByMemberExcludingEndpoint(1L, FCM);
    }

    @Test
    void getPublicKey_isNullWhenPushIsOff() {
        assertNull(service().getPublicKey());

        PushSubscriptionService on = new PushSubscriptionServiceImpl(
            mapper, new PushProperties(" abc ", "def", "")
        );
        assertEquals("abc", on.getPublicKey());
    }

    private static PushSubscribeRequest request(String endpoint, String language) {
        PushSubscribeRequest request = new PushSubscribeRequest();
        request.setEndpoint(endpoint);
        request.setP256dh(p256dh());
        request.setAuth(auth());
        request.setLanguage(language);
        return request;
    }

    private static String p256dh() {
        return encode(point(0x04));
    }

    private static String auth() {
        return encode(new byte[16]);
    }

    private static byte[] point(int firstByte) {
        byte[] point = new byte[65];
        point[0] = (byte) firstByte;
        return point;
    }

    private static String encode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
