package me.nawa.common.ocr;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 영수증 글자 인식(네이버 CLOVA OCR) 접속 설정이다.
 *
 * 주소와 비밀키는 콘솔에서 도메인을 만들면 그 도메인 전용으로 함께 나온다. 그래서 둘은 항상
 * 한 쌍이며, 하나만 채워 두면 서버는 멀쩡히 뜬 것처럼 보이다가 사용자가 영수증을 찍는
 * 순간에야 실패한다. 그 실패는 원인을 찾기 어려우므로 시작할 때 바로 막는다.
 *
 * 반대로 둘 다 비어 있으면 글자 인식 기능만 꺼진 것으로 본다. 이 기능을 쓰지 않는 로컬
 * 개발에서 서버가 안 뜨면 곤란하기 때문이다.
 */
@Getter
@Component
public class ClovaOcrProperties {

    private final String invokeUrl;
    private final String secretKey;
    private final int connectTimeoutMillis;
    private final int readTimeoutMillis;

    public ClovaOcrProperties(
        @Value("${clova.ocr.invoke-url}") String invokeUrl,
        @Value("${clova.ocr.secret-key}") String secretKey,
        @Value("${clova.ocr.connect-timeout-millis}") int connectTimeoutMillis,
        @Value("${clova.ocr.read-timeout-millis}") int readTimeoutMillis
    ) {
        boolean hasInvokeUrl = invokeUrl != null && !invokeUrl.isBlank();
        boolean hasSecretKey = secretKey != null && !secretKey.isBlank();
        if (hasInvokeUrl != hasSecretKey) {
            throw new IllegalStateException(
                "CLOVA_OCR_INVOKE_URL과 CLOVA_OCR_SECRET_KEY는 함께 설정해야 합니다.");
        }
        if (connectTimeoutMillis <= 0 || readTimeoutMillis <= 0) {
            throw new IllegalStateException("CLOVA OCR 타임아웃은 0보다 커야 합니다.");
        }

        this.invokeUrl = hasInvokeUrl ? invokeUrl.trim() : "";
        this.secretKey = hasSecretKey ? secretKey.trim() : "";
        this.connectTimeoutMillis = connectTimeoutMillis;
        this.readTimeoutMillis = readTimeoutMillis;
    }

    /** 접속 정보가 갖춰져 있어야 글자 인식을 부를 수 있다. */
    public boolean isConfigured() {
        return !invokeUrl.isEmpty();
    }
}
