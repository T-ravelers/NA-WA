package me.nawa.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.nawa.auth.cookie.AuthCookieManager;
import me.nawa.auth.jwt.AccessToken;
import me.nawa.auth.jwt.JwtTokenProvider;
import me.nawa.auth.security.AuthAuthenticationEntryPoint;
import me.nawa.auth.security.AuthenticatedMember;
import me.nawa.auth.security.JwtAuthenticationFilter;
import me.nawa.auth.security.SecurityErrorResponseWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
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
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = {
        SecurityConfig.class,
        SecurityConfigTest.TestWebConfig.class
})
class SecurityConfigTest {
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
    void protectedPost_validAccessToken_remainsAccessibleWithoutCsrfForNow()
            throws Exception {
        AccessToken token = jwtTokenProvider.issueAccessToken(42L);

        MockHttpServletResponse response = mockMvc.perform(
                        post("/api/security-test")
                                .cookie(new Cookie(
                                        "access_token",
                                        token.getValue()
                                ))
                )
                .andReturn()
                .getResponse();

        assertEquals(200, response.getStatus());
    }

    @Test
    void authEndpoints_withoutAccessToken_remainPublic() throws Exception {
        MockHttpServletResponse refreshResponse = mockMvc.perform(
                        post("/api/auth/refresh")
                )
                .andReturn()
                .getResponse();
        MockHttpServletResponse logoutResponse = mockMvc.perform(
                        post("/api/auth/logout")
                )
                .andReturn()
                .getResponse();

        assertEquals(200, refreshResponse.getStatus());
        assertEquals("refresh", refreshResponse.getContentAsString());
        assertEquals(200, logoutResponse.getStatus());
        assertEquals("logout", logoutResponse.getContentAsString());
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

        @PostMapping("/api/auth/refresh")
        String refresh() {
            return "refresh";
        }

        @PostMapping("/api/auth/logout")
        String logout() {
            return "logout";
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
