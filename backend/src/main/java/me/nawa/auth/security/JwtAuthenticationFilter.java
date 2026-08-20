package me.nawa.auth.security;

import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import me.nawa.auth.cookie.AuthCookieManager;
import me.nawa.auth.jwt.AccessTokenClaims;
import me.nawa.auth.jwt.JwtTokenProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthCookieManager authCookieManager;
    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            authCookieManager.findAccessToken(request.getCookies())
                    .or(() -> findBearerToken(request))
                    .ifPresent(token -> authenticate(request, token));
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Authorization 헤더의 Bearer 토큰을 읽습니다.
     *
     * <p>쿠키가 먼저입니다. 브라우저 클라이언트의 동작은 바뀌지 않습니다.
     *
     * <p>기계 클라이언트에는 쿠키를 쓸 자리가 없습니다. 크롤러 파이프라인은
     * service-token 으로 받은 JWT 를 이 헤더에 실어 보냅니다.
     */
    private Optional<String> findBearerToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return Optional.empty();
        }

        String token = header.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? Optional.empty() : Optional.of(token);
    }

    private void authenticate(HttpServletRequest request, String token) {
        try {
            AccessTokenClaims claims = jwtTokenProvider.parseAccessToken(token);
            AuthenticatedMember principal = new AuthenticatedMember(
                    claims.getMemberId()
            );
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            principal,
                            null,
                            Collections.emptyList()
                    );
            authentication.setDetails(
                    new WebAuthenticationDetailsSource().buildDetails(request)
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (JwtException | IllegalArgumentException exception) {
            SecurityContextHolder.clearContext();
        }
    }
}
