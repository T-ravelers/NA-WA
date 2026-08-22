package me.nawa.config;

import lombok.RequiredArgsConstructor;
import me.nawa.auth.security.AllowedOriginPolicy;
import me.nawa.auth.security.AuthAccessDeniedHandler;
import me.nawa.auth.security.AuthAuthenticationEntryPoint;
import me.nawa.auth.security.JwtAuthenticationFilter;
import me.nawa.auth.security.OriginValidationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

import static org.springframework.security.web.util.matcher.AntPathRequestMatcher.antMatcher;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final AuthAuthenticationEntryPoint authenticationEntryPoint;
    private final AuthAccessDeniedHandler accessDeniedHandler;
    private final OriginValidationFilter originValidationFilter;

    @Bean
    public CsrfTokenRepository csrfTokenRepository(
            @Value("${auth.cookie.secure}") boolean secure,
            @Value("${auth.cookie.domain:}") String domain) {
        CookieCsrfTokenRepository repository =
                new CookieCsrfTokenRepository();
        repository.setCookiePath("/");
        repository.setCookieHttpOnly(true);
        repository.setSecure(secure);
        if (domain != null && !domain.isBlank()) {
            repository.setCookieDomain(domain);
        }
        return repository;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            AllowedOriginPolicy allowedOriginPolicy) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(
                allowedOriginPolicy.getAllowedOrigins()
        );
        configuration.setAllowedMethods(List.of(
                "GET",
                "POST",
                "PUT",
                "PATCH",
                "DELETE",
                "OPTIONS"
        ));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            CsrfTokenRepository csrfTokenRepository,
            CorsConfigurationSource corsConfigurationSource)
            throws Exception {
        http
                .csrf(csrf -> csrf
                    .csrfTokenRepository(csrfTokenRepository)
                    .ignoringRequestMatchers(
                            antMatcher("/api/v1/stripe/webhook"),
                            // 기계가 부르는 경로다. 브라우저 세션이 없어
                            // CSRF 토큰을 받아 올 방법도, 받을 이유도 없다.
                            antMatcher("/api/v1/auth/service-token"),
                            // 경로를 하나씩 적는다. /api/v1/internal/** 로 열면
                            // 앞으로 생길 internal 엔드포인트가 면제를 물려받는다.
                            antMatcher("/api/v1/internal/ingest/events"),
                            antMatcher("/api/v1/internal/ingest/places"),
                            antMatcher("/api/v1/internal/ingest/event-translations"),
                            antMatcher("/api/v1/internal/ingest/place-translations"),
                            antMatcher("/api/v1/internal/ingest/event-activities"),
                            antMatcher("/api/v1/internal/ingest/place-activities")))
                .cors(cors -> cors
                        .configurationSource(corsConfigurationSource))
                .formLogin(formLogin -> formLogin.disable())
                .httpBasic(httpBasic -> httpBasic.disable())
                .requestCache(requestCache -> requestCache.disable())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                antMatcher(HttpMethod.OPTIONS, "/**")
                        ).permitAll()
                        .requestMatchers(
                                antMatcher("/api/v1/auth/csrf"),
                                antMatcher("/api/v1/auth/refresh"),
                                antMatcher("/api/v1/auth/logout"),
                                antMatcher(
                                        "/api/v1/auth/oauth2/authorization/**"
                                ),
                                antMatcher(
                                        "/api/v1/auth/oauth2/callback/**"
                                ),
                                antMatcher("/swagger-ui.html"),
                                antMatcher("/swagger-resources"),
                                antMatcher("/swagger-resources/**"),
                                antMatcher("/v2/api-docs"),
                                antMatcher("/v2/api-docs/**"),
                                antMatcher("/webjars/**"),
                                antMatcher("/api/v1/stripe/webhook"),
                                // 공유 비밀을 본문으로 확인하고 토큰을 내준다.
                                // 인증 전에 닿아야 하므로 여기서 열어 둔다.
                                antMatcher("/api/v1/auth/service-token"),
                                // 지표 수집기가 인증 없이 읽어야 해서 여기서는 연다.
                                // 외부 노출은 nginx가 /internal/ 접두사를 404로 막고,
                                // 운영 compose가 backend 포트를 공개하지 않는 것으로 함께 막는다.
                                // 이 줄만 보고 "열려 있다"고 판단하지 말 것.
                                antMatcher("/internal/metrics")
                        ).permitAll()
                        .requestMatchers(antMatcher("/api/**")).authenticated()
                        .anyRequest().permitAll())
                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                )
                .addFilterBefore(
                        originValidationFilter,
                        CorsFilter.class
                );

        return http.build();
    }
}
