package me.nawa.auth.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.security.web.csrf.InvalidCsrfTokenException;
import org.springframework.security.web.csrf.MissingCsrfTokenException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AuthAccessDeniedHandlerTest {
    private AuthAccessDeniedHandler handler;

    @BeforeEach
    void setUp() {
        handler = new AuthAccessDeniedHandler(
                new SecurityErrorResponseWriter()
        );
    }

    @Test
    void handle_accessDeniedException_returnsAccessDeniedResponse()
            throws Exception {
        MockHttpServletResponse response = handle(
                new AccessDeniedException("denied")
        );

        assertError(response, "AUTH-004", "접근 권한이 없습니다.");
    }

    @Test
    void handle_missingCsrfToken_returnsCsrfFailureResponse()
            throws Exception {
        MockHttpServletResponse response = handle(
                new MissingCsrfTokenException(null)
        );

        assertError(
                response,
                "AUTH-005",
                "요청의 CSRF 토큰이 유효하지 않습니다."
        );
    }

    @Test
    void handle_invalidCsrfToken_returnsCsrfFailureResponse()
            throws Exception {
        MockHttpServletResponse response = handle(
                new InvalidCsrfTokenException(
                        new DefaultCsrfToken(
                                "X-XSRF-TOKEN",
                                "_csrf",
                                "expected"
                        ),
                        "actual"
                )
        );

        assertError(
                response,
                "AUTH-005",
                "요청의 CSRF 토큰이 유효하지 않습니다."
        );
    }

    private MockHttpServletResponse handle(
            AccessDeniedException exception) throws Exception {
        MockHttpServletResponse response =
                new MockHttpServletResponse();
        handler.handle(
                new MockHttpServletRequest(),
                response,
                exception
        );
        return response;
    }

    private void assertError(
            MockHttpServletResponse response,
            String code,
            String message) throws Exception {
        assertEquals(403, response.getStatus());
        JsonNode body = new ObjectMapper().readTree(
                response.getContentAsString()
        );
        assertFalse(body.path("success").asBoolean());
        assertEquals(code, body.path("error").path("code").asText());
        assertEquals(
                message,
                body.path("error").path("message").asText()
        );
    }
}
