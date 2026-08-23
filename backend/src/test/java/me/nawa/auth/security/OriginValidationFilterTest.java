package me.nawa.auth.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 브라우저가 아닌 곳에서 오는 요청이 Origin 검사에 막히지 않는지 봅니다.
 *
 * <p>서버 간 호출은 Origin 헤더를 보내지 않습니다. 이 필터가 인증 필터보다
 * 먼저 돌기 때문에, 예외 목록에 없으면 토큰이 아무리 정확해도 403 입니다.
 */
class OriginValidationFilterTest {

    private static final String ALLOWED_ORIGIN = "https://na-wa.cloud";

    private OriginValidationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new OriginValidationFilter(
                new AllowedOriginPolicy(ALLOWED_ORIGIN),
                new SecurityErrorResponseWriter());
    }

    @Test
    void ingestPathsPassWithoutAnOriginHeader() throws Exception {
        // 파이프라인이 부르는 경로다. Origin 이 없어도 통과해야 인증 필터까지 간다.
        for (String path : new String[] {
                "/api/v1/auth/service-token",
                "/api/v1/internal/ingest/events",
                "/api/v1/internal/ingest/places",
                "/api/v1/internal/ingest/event-translations",
                "/api/v1/internal/ingest/place-translations"}) {
            assertTrue(passesWithoutOrigin(path), path + " 가 Origin 검사에 막힙니다");
        }
    }

    @Test
    void stripeWebhookStillPassesWithoutAnOriginHeader() throws Exception {
        assertTrue(passesWithoutOrigin("/api/v1/stripe/webhook"));
    }

    @Test
    void otherPathsStillRequireAnAllowedOrigin() throws Exception {
        // 예외를 넓히다 브라우저 경로까지 열어 버리면 CSRF 방어가 무너진다.
        assertFalse(passesWithoutOrigin("/api/v1/journeys"));
    }

    @Test
    void otherPathsPassWithAnAllowedOrigin() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/journeys");
        request.addHeader("Origin", ALLOWED_ORIGIN);

        assertTrue(runFilter(request));
    }

    private boolean passesWithoutOrigin(String path) throws Exception {
        return runFilter(new MockHttpServletRequest("POST", path));
    }

    /**
     * 다음 필터로 넘어갔는지 돌려줍니다. shouldNotFilter 로 건너뛴 경우도
     * 통과로 봅니다 — 요청이 인증 필터까지 간다는 점이 같습니다.
     */
    private boolean runFilter(MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean reached = new AtomicBoolean(false);

        filter.doFilter(request, response, (req, res) -> reached.set(true));

        if (!reached.get()) {
            assertEquals(403, response.getStatus());
        }
        return reached.get();
    }
}
