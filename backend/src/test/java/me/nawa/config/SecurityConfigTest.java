package me.nawa.config;

import me.nawa.auth.cookie.AuthCookieManager;
import me.nawa.auth.jwt.AccessToken;
import me.nawa.auth.jwt.JwtTokenProvider;
import me.nawa.auth.security.AuthenticatedMember;
import me.nawa.auth.security.JwtAuthenticationFilter;
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
    void request_withoutAccessToken_keepsEndpointPublic() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(
                        get("/api/security-test")
                )
                .andReturn()
                .getResponse();

        assertEquals(200, response.getStatus());
        assertEquals("anonymous", response.getContentAsString());
    }

    @Test
    void post_withoutCsrfToken_remainsAccessibleForCurrentStep()
            throws Exception {
        MockHttpServletResponse response = mockMvc.perform(
                        post("/api/security-test")
                )
                .andReturn()
                .getResponse();

        assertEquals(200, response.getStatus());
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
    }
}
