package me.nawa.auth.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtTokenProvider {
    private static final String TOKEN_TYPE_CLAIM = "token_type";
    private static final String ACCESS_TOKEN_TYPE = "access";

    private final SecretKey signingKey;
    private final String issuer;
    private final Duration accessTokenTtl;
    private final Clock clock;
    private final JwtParser jwtParser;

    @Autowired
    public JwtTokenProvider(
            @Value("${auth.jwt.secret}") String base64Secret,
            @Value("${auth.jwt.issuer}") String issuer,
            @Value("${auth.jwt.access-token-ttl-seconds}") long accessTokenTtlSeconds) {
        this(base64Secret, issuer, accessTokenTtlSeconds, Clock.systemUTC());
    }

    JwtTokenProvider(
            String base64Secret,
            String issuer,
            long accessTokenTtlSeconds,
            Clock clock) {
        if (!StringUtils.hasText(issuer)) {
            throw new IllegalArgumentException("JWT issuer must not be blank");
        }
        if (accessTokenTtlSeconds <= 0) {
            throw new IllegalArgumentException("Access token TTL must be positive");
        }

        this.signingKey = createSigningKey(base64Secret);
        this.issuer = issuer;
        this.accessTokenTtl = Duration.ofSeconds(accessTokenTtlSeconds);
        this.clock = clock;
        this.jwtParser = Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(issuer)
                .require(TOKEN_TYPE_CLAIM, ACCESS_TOKEN_TYPE)
                .sig()
                .clear()
                .add(Jwts.SIG.HS256)
                .and()
                .clock(() -> Date.from(clock.instant()))
                .build();
    }

    public AccessToken issueAccessToken(long memberId) {
        if (memberId <= 0) {
            throw new IllegalArgumentException("Member ID must be positive");
        }

        Instant issuedAt = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = issuedAt.plus(accessTokenTtl);
        String tokenId = UUID.randomUUID().toString();

        String value = Jwts.builder()
                .issuer(issuer)
                .subject(Long.toString(memberId))
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .id(tokenId)
                .claim(TOKEN_TYPE_CLAIM, ACCESS_TOKEN_TYPE)
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();

        return new AccessToken(value, expiresAt);
    }

    public AccessTokenClaims parseAccessToken(String token) {
        if (!StringUtils.hasText(token)) {
            throw new IllegalArgumentException("Access token must not be blank");
        }

        Claims claims = jwtParser.parseSignedClaims(token).getPayload();
        validateRequiredClaims(claims);

        return new AccessTokenClaims(
                parseMemberId(claims.getSubject()),
                claims.getId(),
                claims.getIssuedAt().toInstant(),
                claims.getExpiration().toInstant()
        );
    }

    private SecretKey createSigningKey(String base64Secret) {
        if (!StringUtils.hasText(base64Secret)) {
            throw new IllegalArgumentException("JWT Secret must not be blank");
        }

        try {
            return Keys.hmacShaKeyFor(Decoders.BASE64.decode(base64Secret));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(
                    "JWT Secret must be a Base64-encoded key of at least 32 bytes",
                    exception
            );
        }
    }

    private long parseMemberId(String subject) {
        try {
            long memberId = Long.parseLong(subject);
            if (memberId <= 0) {
                throw new NumberFormatException("Member ID must be positive");
            }
            return memberId;
        } catch (NumberFormatException exception) {
            throw new MalformedJwtException(
                    "JWT subject must be a numeric member ID",
                    exception
            );
        }
    }

    private void validateRequiredClaims(Claims claims) {
        if (!StringUtils.hasText(claims.getId())
                || claims.getIssuedAt() == null
                || claims.getExpiration() == null) {
            throw new MalformedJwtException(
                    "Access token is missing required claims"
            );
        }
    }
}
