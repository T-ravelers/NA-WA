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

    // Stripe 등 외부 서버가 서버-투-서버로 호출하는 webhook은 브라우저가 아니라 Origin 헤더가 없다.
    // 이 경로는 Stripe-Signature 검증이 인증을 대신하므로 Origin 체크 대상에서 제외한다.
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return "/api/v1/stripe/webhook".equals(request.getRequestURI());
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
