package me.nawa.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.nawa.auth.cookie.AuthCookieManager;
import me.nawa.auth.jwt.AccessToken;
import me.nawa.auth.jwt.JwtTokenProvider;
import me.nawa.auth.security.AllowedOriginPolicy;
import me.nawa.auth.security.AuthAccessDeniedHandler;
import me.nawa.auth.security.AuthAuthenticationEntryPoint;
import me.nawa.auth.security.AuthenticatedMember;
import me.nawa.auth.security.JwtAuthenticationFilter;
import me.nawa.auth.security.OriginValidationFilter;
import me.nawa.auth.security.SecurityErrorResponseWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@TestPropertySource(properties = {
        "auth.cookie.secure=false",
        "auth.cookie.domain="
})
@ContextConfiguration(classes = {
        SecurityConfig.class,
        SecurityConfigTest.TestWebConfig.class
})
class SecurityConfigTest {
    private static final String ALLOWED_ORIGIN = "http://localhost:5173";
    private static final String SECRET = Base64.getEncoder().encodeToString(
            "test-signing-key-that-is-at-least-32-bytes"
                    .getBytes(StandardCharsets.UTF_8)
    );

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void request_validAccessToken_exposesAuthenticatedMember()
            throws Exception {
        AccessToken token = jwtTokenProvider.issueAccessToken(42L);

        MockHttpServletResponse response = mockMvc.perform(
                        get("/api/security-test")
                                .cookie(new Cookie(
                                        "access_token",
                                        token.getValue()
                                ))
                )
                .andReturn()
                .getResponse();

        assertEquals(200, response.getStatus());
        assertEquals("42", response.getContentAsString());
    }

    @Test
    void protectedApi_withoutAccessToken_returnsAuthenticationRequired()
            throws Exception {
        MockHttpServletResponse response = mockMvc.perform(
                        get("/api/security-test")
                )
                .andReturn()
                .getResponse();

        assertAuthenticationRequired(response);
    }

    @Test
    void protectedApi_malformedAccessToken_returnsAuthenticationRequired()
            throws Exception {
        MockHttpServletResponse response = mockMvc.perform(
                        get("/api/security-test")
                                .cookie(new Cookie(
                                        "access_token",
                                        "malformed"
                                ))
                )
                .andReturn()
                .getResponse();

        assertAuthenticationRequired(response);
    }

    @Test
    void protectedPost_validAccessTokenCsrfAndOrigin_returnsOk()
            throws Exception {
        AccessToken token = jwtTokenProvider.issueAccessToken(42L);
        CsrfCredentials csrfCredentials = issueCsrfToken();

        MockHttpServletResponse response = mockMvc.perform(
                        post("/api/security-test")
                                .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                                .header(
                                        "X-XSRF-TOKEN",
                                        csrfCredentials.token
                                )
                                .cookie(
                                        new Cookie(
                                                "access_token",
                                                token.getValue()
                                        ),
                                        csrfCredentials.cookie
                                )
                )
                .andReturn()
                .getResponse();

        assertEquals(200, response.getStatus());
    }

    @Test
    void protectedPost_missingOrigin_returnsOriginNotAllowed()
            throws Exception {
        AccessToken token = jwtTokenProvider.issueAccessToken(42L);
        CsrfCredentials csrfCredentials = issueCsrfToken();

        MockHttpServletResponse response = mockMvc.perform(
                        post("/api/security-test")
                                .header(
                                        "X-XSRF-TOKEN",
                                        csrfCredentials.token
                                )
                                .cookie(
                                        new Cookie(
                                                "access_token",
                                                token.getValue()
                                        ),
                                        csrfCredentials.cookie
                                )
                )
                .andReturn()
                .getResponse();

        assertForbidden(response, "AUTH-006");
    }

    @Test
    void protectedPost_disallowedOrigin_returnsOriginNotAllowed()
            throws Exception {
        AccessToken token = jwtTokenProvider.issueAccessToken(42L);
        CsrfCredentials csrfCredentials = issueCsrfToken();

        MockHttpServletResponse response = mockMvc.perform(
                        post("/api/security-test")
                                .header(
                                        HttpHeaders.ORIGIN,
                                        "http://localhost:5174"
                                )
                                .header(
                                        "X-XSRF-TOKEN",
                                        csrfCredentials.token
                                )
                                .cookie(
                                        new Cookie(
                                                "access_token",
                                                token.getValue()
                                        ),
                                        csrfCredentials.cookie
                                )
                )
                .andReturn()
                .getResponse();

        assertForbidden(response, "AUTH-006");
    }

    @Test
    void protectedPost_missingCsrfToken_returnsCsrfFailure()
            throws Exception {
        AccessToken token = jwtTokenProvider.issueAccessToken(42L);

        MockHttpServletResponse response = mockMvc.perform(
                        post("/api/security-test")
                                .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                                .cookie(new Cookie(
                                        "access_token",
                                        token.getValue()
                                ))
                )
                .andReturn()
                .getResponse();

        assertForbidden(response, "AUTH-005");
    }

    @Test
    void protectedPost_invalidCsrfToken_returnsCsrfFailure()
            throws Exception {
        AccessToken token = jwtTokenProvider.issueAccessToken(42L);
        CsrfCredentials csrfCredentials = issueCsrfToken();

        MockHttpServletResponse response = mockMvc.perform(
                        post("/api/security-test")
                                .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                                .header("X-XSRF-TOKEN", "invalid-token")
                                .cookie(
                                        new Cookie(
                                                "access_token",
                                                token.getValue()
                                        ),
                                        csrfCredentials.cookie
                                )
                )
                .andReturn()
                .getResponse();

        assertForbidden(response, "AUTH-005");
    }

    @Test
    void csrfEndpoint_withoutAccessToken_returnsTokenAndHttpOnlyCookie()
            throws Exception {
        MockHttpServletResponse response = mockMvc.perform(
                        get("/api/v1/auth/csrf")
                )
                .andReturn()
                .getResponse();

        assertEquals(200, response.getStatus());
        Cookie csrfCookie = response.getCookie("XSRF-TOKEN");
        assertNotNull(csrfCookie);
        assertEquals("/", csrfCookie.getPath());
        assertTrue(csrfCookie.isHttpOnly());
        assertFalse(csrfCookie.getSecure());

        assertEquals(
                csrfCookie.getValue(),
                response.getContentAsString()
        );
    }

    @Test
    void authEndpoints_withoutAccessToken_remainPublic() throws Exception {
        CsrfCredentials csrfCredentials = issueCsrfToken();

        MockHttpServletResponse refreshResponse = mockMvc.perform(
                        post("/api/v1/auth/refresh")
                                .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                                .header(
                                        "X-XSRF-TOKEN",
                                        csrfCredentials.token
                                )
                                .cookie(csrfCredentials.cookie)
                )
                .andReturn()
                .getResponse();
        MockHttpServletResponse logoutResponse = mockMvc.perform(
                        post("/api/v1/auth/logout")
                                .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                                .header(
                                        "X-XSRF-TOKEN",
                                        csrfCredentials.token
                                )
                                .cookie(csrfCredentials.cookie)
                )
                .andReturn()
                .getResponse();

        assertEquals(200, refreshResponse.getStatus());
        assertEquals("refresh", refreshResponse.getContentAsString());
        assertEquals(200, logoutResponse.getStatus());
        assertEquals("logout", logoutResponse.getContentAsString());
    }

    @Test
    void corsPreflight_allowedOrigin_returnsCorsHeaders() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(
                        options("/api/v1/auth/refresh")
                                .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                                .header(
                                        HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD,
                                        "POST"
                                )
                                .header(
                                        HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                                        "X-XSRF-TOKEN"
                                )
                )
                .andReturn()
                .getResponse();

        assertEquals(200, response.getStatus());
        assertEquals(
                ALLOWED_ORIGIN,
                response.getHeader(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN
                )
        );
        assertEquals(
                "true",
                response.getHeader(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS
                )
        );
    }

    @Test
    void corsPreflight_disallowedOrigin_returnsForbidden() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(
                        options("/api/v1/auth/refresh")
                                .header(
                                        HttpHeaders.ORIGIN,
                                        "http://localhost:5174"
                                )
                                .header(
                                        HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD,
                                        "POST"
                                )
                )
                .andReturn()
                .getResponse();

        assertEquals(403, response.getStatus());
    }

    @Test
    void swaggerEndpoints_withoutAccessToken_remainPublic() throws Exception {
        assertEquals(
                200,
                mockMvc.perform(get("/swagger-ui.html"))
                        .andReturn()
                        .getResponse()
                        .getStatus()
        );
        assertEquals(
                200,
                mockMvc.perform(get("/swagger-resources"))
                        .andReturn()
                        .getResponse()
                        .getStatus()
        );
        assertEquals(
                200,
                mockMvc.perform(get("/v2/api-docs"))
                        .andReturn()
                        .getResponse()
                        .getStatus()
        );
        assertEquals(
                200,
                mockMvc.perform(get("/webjars/test.js"))
                        .andReturn()
                        .getResponse()
                        .getStatus()
        );
    }

    @Test
    void nonApiRequest_withoutAccessToken_remainsPublic() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(
                        get("/public-test")
                )
                .andReturn()
                .getResponse();

        assertEquals(200, response.getStatus());
        assertEquals("public", response.getContentAsString());
    }

    private void assertAuthenticationRequired(
            MockHttpServletResponse response) throws Exception {
        assertEquals(401, response.getStatus());
        assertEquals("application/json;charset=UTF-8", response.getContentType());
        assertNull(response.getHeader("WWW-Authenticate"));

        JsonNode body = new ObjectMapper().readTree(
                response.getContentAsString()
        );
        assertFalse(body.path("success").asBoolean());
        assertEquals("AUTH-003", body.path("error").path("code").asText());
        assertEquals(
                "로그인이 필요합니다.",
                body.path("error").path("message").asText()
        );
    }

    private CsrfCredentials issueCsrfToken() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(
                        get("/api/v1/auth/csrf")
                )
                .andReturn()
                .getResponse();
        Cookie cookie = response.getCookie("XSRF-TOKEN");
        assertNotNull(cookie);
        return new CsrfCredentials(
                cookie,
                response.getContentAsString()
        );
    }

    private void assertForbidden(
            MockHttpServletResponse response,
            String code) throws Exception {
        assertEquals(403, response.getStatus());
        assertEquals("application/json;charset=UTF-8", response.getContentType());

        JsonNode body = new ObjectMapper().readTree(
                response.getContentAsString()
        );
        assertFalse(body.path("success").asBoolean());
        assertEquals(code, body.path("error").path("code").asText());
    }

    private static final class CsrfCredentials {
        private final Cookie cookie;
        private final String token;

        private CsrfCredentials(Cookie cookie, String token) {
            this.cookie = cookie;
            this.token = token;
        }
    }

    @Configuration
    @EnableWebMvc
    static class TestWebConfig {

        @Bean
        JwtTokenProvider jwtTokenProvider() {
            return new JwtTokenProvider(SECRET, "nawa", 900);
        }

        @Bean
        AuthCookieManager authCookieManager() {
            return new AuthCookieManager(
                    "access_token",
                    "refresh_token",
                    false,
                    "Lax",
                    ""
            );
        }

        @Bean
        JwtAuthenticationFilter jwtAuthenticationFilter(
                AuthCookieManager authCookieManager,
                JwtTokenProvider jwtTokenProvider) {
            return new JwtAuthenticationFilter(
                    authCookieManager,
                    jwtTokenProvider
            );
        }

        @Bean
        SecurityErrorResponseWriter securityErrorResponseWriter() {
            return new SecurityErrorResponseWriter();
        }

        @Bean
        AllowedOriginPolicy allowedOriginPolicy() {
            return new AllowedOriginPolicy(ALLOWED_ORIGIN);
        }

        @Bean
        OriginValidationFilter originValidationFilter(
                AllowedOriginPolicy allowedOriginPolicy,
                SecurityErrorResponseWriter errorResponseWriter) {
            return new OriginValidationFilter(
                    allowedOriginPolicy,
                    errorResponseWriter
            );
        }

        @Bean
        AuthAccessDeniedHandler authAccessDeniedHandler(
                SecurityErrorResponseWriter errorResponseWriter) {
            return new AuthAccessDeniedHandler(errorResponseWriter);
        }

        @Bean
        AuthAuthenticationEntryPoint authAuthenticationEntryPoint(
                SecurityErrorResponseWriter errorResponseWriter) {
            return new AuthAuthenticationEntryPoint(errorResponseWriter);
        }

        @Bean
        SecurityTestController securityTestController() {
            return new SecurityTestController();
        }
    }

    @RestController
    static class SecurityTestController {

        @GetMapping("/api/security-test")
        String getAuthentication() {
            Authentication authentication = SecurityContextHolder.getContext()
                    .getAuthentication();
            if (authentication == null
                    || !(authentication.getPrincipal()
                    instanceof AuthenticatedMember member)) {
                return "anonymous";
            }
            return member.getName();
        }

        @PostMapping("/api/security-test")
        void postWithoutCsrf() {
        }

        @PostMapping("/api/v1/auth/refresh")
        String refresh() {
            return "refresh";
        }

        @PostMapping("/api/v1/auth/logout")
        String logout() {
            return "logout";
        }

        @GetMapping("/api/v1/auth/csrf")
        String csrf(HttpServletRequest request) {
            CsrfToken csrfToken = (CsrfToken) request.getAttribute(
                    CsrfToken.class.getName()
            );
            return csrfToken.getToken();
        }

        @GetMapping({
                "/swagger-ui.html",
                "/swagger-resources",
                "/v2/api-docs",
                "/webjars/test.js"
        })
        String swagger() {
            return "swagger";
        }

        @GetMapping("/public-test")
        String publicRequest() {
            return "public";
        }
    }
}
