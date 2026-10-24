package me.nawa.notification.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import me.nawa.common.exception.BusinessException;
import me.nawa.common.exception.CommonErrorCode;
import me.nawa.common.i18n.SupportedLanguagePolicy;
import me.nawa.notification.dto.request.PushSubscribeRequest;
import me.nawa.notification.mapper.PushSubscriptionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 기기별 푸시 구독의 등록·해지와, 구독할 때 쓸 공개키 조회를 맡는다.
 *
 * endpoint는 서버가 나중에 직접 POST를 보내는 주소다. 사용자가 적어 보낸 값을 그대로 받으면
 * 서버가 임의의 주소로 요청을 보내게 되므로(SSRF), https이면서 알려진 푸시 서비스의 호스트일
 * 때만 저장한다. 목록이 낡으면 새 브라우저의 구독이 400으로 막히지만, 조용히 사라지지 않고
 * 구독 요청에서 바로 드러난다.
 */
@Service
public class PushSubscriptionServiceImpl implements PushSubscriptionService {

    private static final int ENDPOINT_MAX_LENGTH = 512;
    // 브라우저가 주는 키의 길이다. p256dh는 압축하지 않은 P-256 점(0x04 + x + y), auth는 16바이트.
    private static final int P256DH_LENGTH = 65;
    private static final int AUTH_LENGTH = 16;

    // 호스트가 이 도메인이거나 그 하위 도메인이면 통과한다.
    private static final List<String> PUSH_SERVICE_DOMAINS = List.of(
        "fcm.googleapis.com",         // Chrome 등 Chromium 계열
        "push.services.mozilla.com",  // Firefox
        "push.apple.com",             // Safari·iOS 홈 화면 앱
        "notify.windows.com"          // Edge의 Windows 푸시(WNS). 공식 문서로는 확인하지 못했다
    );

    private final PushSubscriptionMapper pushSubscriptionMapper;
    private final PushProperties pushProperties;

    public PushSubscriptionServiceImpl(
        PushSubscriptionMapper pushSubscriptionMapper,
        PushProperties pushProperties
    ) {
        this.pushSubscriptionMapper = pushSubscriptionMapper;
        this.pushProperties = pushProperties;
    }

    @Override
    public String getPublicKey() {
        return pushProperties.isEnabled() ? pushProperties.getPublicKey() : null;
    }

    @Override
    @Transactional
    public void subscribe(Long memberId, PushSubscribeRequest request) {
        if (request == null
            || !isPushServiceEndpoint(request.getEndpoint())
            || !hasDecodedLength(request.getP256dh(), P256DH_LENGTH, (byte) 0x04)
            || !hasDecodedLength(request.getAuth(), AUTH_LENGTH, null)) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        pushSubscriptionMapper.upsert(
            memberId,
            request.getEndpoint(),
            request.getP256dh(),
            request.getAuth(),
            SupportedLanguagePolicy.normalize(request.getLanguage())
        );
    }

    /** 내 구독이 아니거나 없는 endpoint여도 성공으로 끝낸다. 알림 지우기와 같은 이유다. */
    @Override
    @Transactional
    public void unsubscribe(Long memberId, String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        pushSubscriptionMapper.deleteByMemberAndEndpoint(memberId, endpoint);
    }

    private static boolean isPushServiceEndpoint(String endpoint) {
        if (endpoint == null || endpoint.length() > ENDPOINT_MAX_LENGTH) {
            return false;
        }
        try {
            URI uri = new URI(endpoint);
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null) {
                return false;
            }
            String lowerHost = host.toLowerCase(Locale.ROOT);
            return PUSH_SERVICE_DOMAINS.stream()
                .anyMatch(domain -> lowerHost.equals(domain) || lowerHost.endsWith("." + domain));
        } catch (URISyntaxException e) {
            return false;
        }
    }

    /** base64url로 풀었을 때 정해진 길이(와 첫 바이트)인지 본다. 형식이 다르면 풀리지도 않는다. */
    private static boolean hasDecodedLength(String key, int length, Byte firstByte) {
        if (key == null) {
            return false;
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(key);
            return decoded.length == length && (firstByte == null || decoded[0] == firstByte);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
