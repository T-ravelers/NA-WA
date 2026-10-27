package me.nawa.notification.service;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 웹 푸시의 VAPID 서버 키 설정이다.
 *
 * 공개키와 비밀키는 한 쌍으로 만들어지므로 항상 함께 있어야 한다. 하나만 채워 두면 서버는
 * 멀쩡히 뜬 것처럼 보이다가 알림이 생기는 순간에야 발송이 실패하고, 그 실패는 원인을 찾기
 * 어려우므로 시작할 때 바로 막는다.
 *
 * 반대로 둘 다 비어 있으면 푸시만 꺼진 것으로 본다. 키를 만들지 않은 로컬 개발에서 서버가
 * 안 뜨면 곤란하기 때문이다. 이때 알림 목록과 폴링은 그대로 동작한다.
 *
 * subject는 푸시 서비스가 문제가 생기면 연락할 곳이다. VAPID 규격이 mailto: 또는 https:
 * 주소로 정해 두었고, 형식이 틀리면 푸시 서비스가 서명을 거절할 수 있어 함께 확인한다.
 * 비워 두면 서비스 주소를 쓴다.
 */
@Getter
@Component
public class PushProperties {

    private static final String DEFAULT_SUBJECT = "https://na-wa.cloud";

    private final String publicKey;
    private final String privateKey;
    private final String subject;

    public PushProperties(
        @Value("${push.vapid.public-key}") String publicKey,
        @Value("${push.vapid.private-key}") String privateKey,
        @Value("${push.vapid.subject}") String subject
    ) {
        boolean hasPublicKey = publicKey != null && !publicKey.isBlank();
        boolean hasPrivateKey = privateKey != null && !privateKey.isBlank();
        if (hasPublicKey != hasPrivateKey) {
            throw new IllegalStateException(
                "VAPID_PUBLIC_KEY와 VAPID_PRIVATE_KEY는 함께 설정해야 합니다.");
        }

        this.publicKey = hasPublicKey ? publicKey.trim() : "";
        this.privateKey = hasPrivateKey ? privateKey.trim() : "";
        this.subject = subject == null || subject.isBlank() ? DEFAULT_SUBJECT : subject.trim();

        if (isEnabled()
            && !this.subject.startsWith("mailto:")
            && !this.subject.startsWith("https://")) {
            throw new IllegalStateException("VAPID_SUBJECT는 mailto: 또는 https:// 로 시작해야 합니다.");
        }
    }

    /** 키 한 쌍이 갖춰져 있어야 푸시를 보낼 수 있다. */
    public boolean isEnabled() {
        return !publicKey.isEmpty();
    }
}
