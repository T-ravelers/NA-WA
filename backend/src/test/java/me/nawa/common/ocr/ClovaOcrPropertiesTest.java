package me.nawa.common.ocr;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 주소와 비밀키는 콘솔에서 도메인 하나를 만들면 함께 나오는 한 쌍이다. 하나만 채운 채로
 * 서버가 뜨면 사용자가 영수증을 찍는 순간에야 실패해서 원인을 찾기 어렵다. README와
 * SETTLEMENT.md가 "하나만 채우면 서버가 시작할 때 멈춘다"고 약속하고 있으므로 그 약속을
 * 여기서 붙잡아 둔다.
 */
class ClovaOcrPropertiesTest {

    private static final String INVOKE_URL = "https://ocr.example.com/receipt";
    private static final String SECRET_KEY = "secret-key";

    @Test
    void constructor_onlyInvokeUrl_fails() {
        assertThrows(
            IllegalStateException.class, () -> properties(INVOKE_URL, "")
        );
    }

    @Test
    void constructor_onlySecretKey_fails() {
        assertThrows(
            IllegalStateException.class, () -> properties("", SECRET_KEY)
        );
    }

    /** 공백만 있는 값은 채운 것으로 보지 않는다. .env에 지운 자국이 남는 일이 흔하다. */
    @Test
    void constructor_blankInvokeUrlWithSecretKey_fails() {
        assertThrows(
            IllegalStateException.class, () -> properties("   ", SECRET_KEY)
        );
    }

    /** 둘 다 비면 인식 기능만 꺼진다. 이 기능을 쓰지 않는 로컬 실행이 막히면 안 된다. */
    @Test
    void constructor_bothEmpty_disablesRecognitionWithoutFailing() {
        assertFalse(properties("", "").isConfigured());
    }

    @Test
    void constructor_bothSet_trimsAndEnables() {
        ClovaOcrProperties properties = properties(" " + INVOKE_URL + " ", " " + SECRET_KEY + " ");

        assertTrue(properties.isConfigured());
        assertEquals(INVOKE_URL, properties.getInvokeUrl());
        assertEquals(SECRET_KEY, properties.getSecretKey());
    }

    /** 대기 시간이 0 이하면 요청이 끊기거나 영영 기다린다. 뜨기 전에 막는다. */
    @Test
    void constructor_nonPositiveTimeout_fails() {
        assertThrows(
            IllegalStateException.class,
            () -> new ClovaOcrProperties(INVOKE_URL, SECRET_KEY, 0, 10000)
        );
        assertThrows(
            IllegalStateException.class,
            () -> new ClovaOcrProperties(INVOKE_URL, SECRET_KEY, 3000, -1)
        );
    }

    private static ClovaOcrProperties properties(String invokeUrl, String secretKey) {
        return new ClovaOcrProperties(invokeUrl, secretKey, 3000, 10000);
    }
}
