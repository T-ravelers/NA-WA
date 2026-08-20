package me.nawa.auth.oauth.account;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class OAuthMemberInsertTest {

    private static String normalizedUrl(String profileImageUrl) {
        return new OAuthMemberInsert("여행자", profileImageUrl).getProfileImageUrl();
    }

    /** 수정 경로와 같은 strip()이라 전각 공백(U+3000) 같은 유니코드 공백도 지운다. */
    @Test
    void keepsHttpsUrl_afterStripping() {
        assertEquals(
                "https://cdn.example.com/me.png",
                normalizedUrl("  https://cdn.example.com/me.png  ")
        );
        assertEquals(
                "https://cdn.example.com/me.png",
                normalizedUrl("　https://cdn.example.com/me.png　")
        );
    }

    @Test
    void keepsHttpUrl_andSchemeCaseDoesNotMatter() {
        assertEquals(
                "http://cdn.example.com/me.png",
                normalizedUrl("http://cdn.example.com/me.png")
        );
        assertEquals(
                "HTTPS://cdn.example.com/me.png",
                normalizedUrl("HTTPS://cdn.example.com/me.png")
        );
    }

    /** 수정 경로(MemberProfileServiceImpl)와 같은 거부 목록. 가입 경로는 오류 대신 null로 떨군다. */
    @Test
    void dropsUrlToNull_whenSchemeNotHttp() {
        for (String url : new String[] {
                "javascript:alert(1)",
                "data:image/png;base64,AAAA",
                "ftp://cdn.example.com/me.png",
                "cdn.example.com/me.png",
        }) {
            assertNull(normalizedUrl(url));
        }
    }

    @Test
    void keepsUrl_atExactColumnLimit() {
        String base = "https://cdn.example.com/";
        String url = base + "a".repeat(500 - base.length());
        assertEquals(url, normalizedUrl(url));
    }

    @Test
    void dropsUrlToNull_whenLongerThanColumnLimit() {
        String url = "https://cdn.example.com/" + "a".repeat(500);
        assertNull(normalizedUrl(url));
    }

    @Test
    void dropsUrlToNull_whenBlank() {
        assertNull(normalizedUrl(null));
        assertNull(normalizedUrl("   "));
    }
}
