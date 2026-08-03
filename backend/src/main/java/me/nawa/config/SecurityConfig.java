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
                        .csrfTokenRepository(csrfTokenRepository))
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
                                antMatcher("/api/auth/csrf"),
                                antMatcher("/api/auth/refresh"),
                                antMatcher("/api/auth/logout"),
                                antMatcher("/swagger-ui.html"),
                                antMatcher("/swagger-resources"),
                                antMatcher("/swagger-resources/**"),
                                antMatcher("/v2/api-docs"),
                                antMatcher("/v2/api-docs/**"),
                                antMatcher("/webjars/**")
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
