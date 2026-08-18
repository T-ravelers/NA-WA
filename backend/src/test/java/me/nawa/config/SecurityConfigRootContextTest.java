package me.nawa.config;

import me.nawa.auth.cookie.AuthCookieManager;
import me.nawa.auth.jwt.JwtTokenProvider;
import me.nawa.auth.security.AllowedOriginPolicy;
import me.nawa.auth.security.AuthAccessDeniedHandler;
import me.nawa.auth.security.AuthAuthenticationEntryPoint;
import me.nawa.auth.security.JwtAuthenticationFilter;
import me.nawa.auth.security.OriginValidationFilter;
import me.nawa.auth.security.SecurityErrorResponseWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.web.WebAppConfiguration;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@TestPropertySource(properties = {
        "auth.cookie.secure=false",
        "auth.cookie.domain="
})
@ContextConfiguration(classes = {
        SecurityConfig.class,
        SecurityConfigRootContextTest.TestSecurityBeans.class
})
class SecurityConfigRootContextTest {
    private static final String SECRET = Base64.getEncoder().encodeToString(
            "test-signing-key-that-is-at-least-32-bytes"
                    .getBytes(StandardCharsets.UTF_8)
    );

    @Autowired
    private ApplicationContext context;

    @Autowired
    private SecurityFilterChain securityFilterChain;

    @Test
    void securityFilterChain_withoutMvcContext_loadsWithAntMatchers() {
        assertFalse(context.containsBean("mvcHandlerMappingIntrospector"));
        assertNotNull(securityFilterChain);
    }

    @Configuration
    static class TestSecurityBeans {

        @Bean
        JwtTokenProvider jwtTokenProvider() {
            return new JwtTokenProvider(SECRET, "nawa", 900);
        }

        @Bean
        AuthCookieManager authCookieManager() {
            return new AuthCookieManager(
                    "access_token",
                    "refresh_token",
                    "oauth_state",
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
            return new AllowedOriginPolicy("http://localhost:5173");
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
    }
}
