package me.nawa.auth.oauth.identity;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestOperations;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Configuration
public class GoogleIdTokenConfig {
    private static final Set<String> GOOGLE_ISSUERS = Set.of(
            "https://accounts.google.com",
            "accounts.google.com"
    );
    private static final Duration CLOCK_SKEW = Duration.ofSeconds(60);

    @Bean("googleIdTokenDecoder")
    public JwtDecoder googleIdTokenDecoder(
            @Qualifier("oauthRestOperations") RestOperations restOperations,
            @Value("${oauth.google.client-id}") String clientId,
            @Value("${oauth.google.jwk-set-uri}") String jwkSetUri) {
        if (!StringUtils.hasText(jwkSetUri)) {
            throw new IllegalArgumentException(
                    "Google JWK Set URI must not be blank"
            );
        }

        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withJwkSetUri(jwkSetUri.trim())
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .restOperations(Objects.requireNonNull(
                        restOperations,
                        "OAuth RestOperations is required"
                ))
                .build();
        decoder.setJwtValidator(createGoogleValidator(
                normalizeClientId(clientId),
                Clock.systemUTC()
        ));
        return decoder;
    }

    static OAuth2TokenValidator<Jwt> createGoogleValidator(
            String clientId,
            Clock clock) {
        String expectedAudience = normalizeClientId(clientId);
        JwtTimestampValidator timestampValidator =
                new JwtTimestampValidator(CLOCK_SKEW);
        timestampValidator.setClock(Objects.requireNonNull(
                clock,
                "Clock is required"
        ));

        OAuth2TokenValidator<Jwt> issuerValidator =
                new JwtClaimValidator<>(
                        "iss",
                        issuer -> GOOGLE_ISSUERS.contains(issuer)
                );
        OAuth2TokenValidator<Jwt> audienceValidator =
                new JwtClaimValidator<List<String>>(
                        "aud",
                        audience -> audience != null
                                && expectedAudience != null
                                && audience.contains(expectedAudience)
                );
        OAuth2TokenValidator<Jwt> expirationRequiredValidator =
                new JwtClaimValidator<>("exp", Objects::nonNull);

        return new DelegatingOAuth2TokenValidator<>(
                timestampValidator,
                issuerValidator,
                audienceValidator,
                expirationRequiredValidator
        );
    }

    private static String normalizeClientId(String clientId) {
        return StringUtils.hasText(clientId) ? clientId.trim() : null;
    }
}
