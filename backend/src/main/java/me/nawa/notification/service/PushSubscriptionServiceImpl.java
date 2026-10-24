package me.nawa.notification.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Pattern;
import me.nawa.common.exception.BusinessException;
import me.nawa.common.exception.CommonErrorCode;
import me.nawa.common.i18n.SupportedLanguagePolicy;
import me.nawa.notification.dto.request.PushSubscribeRequest;
import me.nawa.notification.mapper.PushSubscriptionMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 기기별 푸시 구독의 등록·해지와, 구독할 때 쓸 공개키 조회를 맡는다.
 *
 * endpoint는 나중에 서버가 직접 POST를 보내는 주소다. 사용자가 적어 보낸 값이므로 https가
 * 아니거나 호스트가 없는 주소는 저장하지 않는다. 푸시 서비스 목록으로 더 좁히지는 않는다 —
 * 브라우저마다 서비스가 달라 목록이 낡으면 정상 기기가 조용히 빠진다.
 */
@Service
public class PushSubscriptionServiceImpl implements PushSubscriptionService {

    private static final int ENDPOINT_MAX_LENGTH = 512;
    private static final int P256DH_MAX_LENGTH = 128;
    private static final int AUTH_MAX_LENGTH = 64;
    // 브라우저가 주는 키는 base64url이다.
    private static final Pattern BASE64URL = Pattern.compile("[A-Za-z0-9_-]+");

    private final PushSubscriptionMapper pushSubscriptionMapper;
    private final String vapidPublicKey;

    public PushSubscriptionServiceImpl(
        PushSubscriptionMapper pushSubscriptionMapper,
        @Value("${push.vapid.public-key}") String vapidPublicKey
    ) {
        this.pushSubscriptionMapper = pushSubscriptionMapper;
        this.vapidPublicKey = vapidPublicKey == null ? "" : vapidPublicKey.trim();
    }

    @Override
    public String getPublicKey() {
        return vapidPublicKey.isEmpty() ? null : vapidPublicKey;
    }

    @Override
    @Transactional
    public void subscribe(Long memberId, PushSubscribeRequest request) {
        if (request == null
            || !isValidEndpoint(request.getEndpoint())
            || !isValidKey(request.getP256dh(), P256DH_MAX_LENGTH)
            || !isValidKey(request.getAuth(), AUTH_MAX_LENGTH)) {
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

    private static boolean isValidEndpoint(String endpoint) {
        if (endpoint == null || endpoint.length() > ENDPOINT_MAX_LENGTH) {
            return false;
        }
        try {
            URI uri = new URI(endpoint);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null;
        } catch (URISyntaxException e) {
            return false;
        }
    }

    private static boolean isValidKey(String key, int maxLength) {
        return key != null && key.length() <= maxLength && BASE64URL.matcher(key).matches();
    }
}
