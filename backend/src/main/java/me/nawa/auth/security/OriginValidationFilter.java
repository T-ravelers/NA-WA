package me.nawa.auth.security;

import lombok.RequiredArgsConstructor;
import me.nawa.auth.exception.AuthErrorCode;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class OriginValidationFilter extends OncePerRequestFilter {
    private static final Set<String> SAFE_METHODS = Set.of(
            "GET",
            "HEAD",
            "TRACE",
            "OPTIONS"
    );

    private final AllowedOriginPolicy allowedOriginPolicy;
    private final SecurityErrorResponseWriter errorResponseWriter;

    // 브라우저가 아닌 곳에서 오는 요청은 Origin 헤더가 없다.
    //   Stripe webhook  — Stripe-Signature 검증이 인증을 대신한다
    //   적재 경로       — 공유 비밀과 JWT 가 인증을 대신한다
    // 이 경로들을 Origin 체크 대상에서 제외한다. 넣지 않으면 서버 간 호출이
    // 인증 필터에 닿기도 전에 403 으로 막힌다.
    private static final Set<String> ORIGINLESS_PATHS = Set.of(
            "/api/v1/stripe/webhook",
            "/api/v1/auth/service-token",
            "/api/v1/internal/ingest/events",
            "/api/v1/internal/ingest/places",
            "/api/v1/internal/ingest/event-translations",
            "/api/v1/internal/ingest/place-translations"
    );

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return ORIGINLESS_PATHS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {
        if (SAFE_METHODS.contains(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String origin = request.getHeader(HttpHeaders.ORIGIN);
        if (!allowedOriginPolicy.allows(origin)) {
            errorResponseWriter.write(
                    response,
                    AuthErrorCode.ORIGIN_NOT_ALLOWED
            );
            return;
        }

        filterChain.doFilter(request, response);
    }
}
