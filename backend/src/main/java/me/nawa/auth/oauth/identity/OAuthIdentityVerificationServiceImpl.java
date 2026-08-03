package me.nawa.auth.oauth.identity;

import me.nawa.auth.exception.AuthErrorCode;
import me.nawa.auth.oauth.OAuthProvider;
import me.nawa.auth.oauth.state.OAuthStateSession;
import me.nawa.auth.oauth.token.OAuthProviderTokenSet;
import me.nawa.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestOperations;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

@Service
public class OAuthIdentityVerificationServiceImpl
        implements OAuthIdentityVerificationService {
    private static final URI LINE_ID_TOKEN_VERIFY_URI = URI.create(
            "https://api.line.me/oauth2/v2.1/verify"
    );

    private final JwtDecoder googleIdTokenDecoder;
    private final RestOperations restOperations;
    private final String googleClientId;
    private final String lineClientId;
    private final Clock clock;

    @Autowired
    public OAuthIdentityVerificationServiceImpl(
            @Qualifier("googleIdTokenDecoder")
            JwtDecoder googleIdTokenDecoder,
            @Qualifier("oauthRestOperations") RestOperations restOperations,
            @Value("${oauth.google.client-id}") String googleClientId,
            @Value("${oauth.line.client-id}") String lineClientId) {
        this(
                googleIdTokenDecoder,
                restOperations,
                googleClientId,
                lineClientId,
                Clock.systemUTC()
        );
    }

    OAuthIdentityVerificationServiceImpl(
            JwtDecoder googleIdTokenDecoder,
            RestOperations restOperations,
            String googleClientId,
            String lineClientId,
            Clock clock) {
        this.googleIdTokenDecoder = Objects.requireNonNull(
                googleIdTokenDecoder,
                "Google ID token decoder is required"
        );
        this.restOperations = Objects.requireNonNull(
                restOperations,
                "OAuth RestOperations is required"
        );
        this.googleClientId = optionalText(googleClientId);
        this.lineClientId = optionalText(lineClientId);
        this.clock = Objects.requireNonNull(clock, "Clock is required");
    }

    @Override
    public OAuthUserProfile verify(
            OAuthStateSession stateSession,
            OAuthProviderTokenSet tokenSet) {
        OAuthStateSession requiredStateSession = Objects.requireNonNull(
                stateSession,
                "OAuth state session is required"
        );
        OAuthProviderTokenSet requiredTokenSet = Objects.requireNonNull(
                tokenSet,
                "OAuth provider token set is required"
        );
        if (requiredStateSession.getProvider()
                != requiredTokenSet.getProvider()) {
            throw invalidIdToken();
        }

        return switch (requiredStateSession.getProvider()) {
            case GOOGLE -> verifyGoogle(
                    requiredStateSession,
                    requiredTokenSet
            );
            case LINE -> verifyLine(
                    requiredStateSession,
                    requiredTokenSet
            );
        };
    }

    private OAuthUserProfile verifyGoogle(
            OAuthStateSession stateSession,
            OAuthProviderTokenSet tokenSet) {
        if (googleClientId == null) {
            throw new BusinessException(
                    AuthErrorCode.OAUTH_PROVIDER_NOT_CONFIGURED
            );
        }

        Jwt jwt;
        try {
            jwt = googleIdTokenDecoder.decode(tokenSet.getIdToken());
        } catch (JwtException | IllegalArgumentException exception) {
            throw invalidIdToken();
        }
        if (!secureEquals(
                stateSession.getNonce(),
                jwt.getClaimAsString("nonce")
        )) {
            throw invalidIdToken();
        }

        try {
            return new OAuthUserProfile(
                    OAuthProvider.GOOGLE,
                    jwt.getSubject(),
                    jwt.getClaimAsString("email"),
                    jwt.getClaimAsString("name"),
                    jwt.getClaimAsString("picture")
            );
        } catch (IllegalArgumentException exception) {
            throw invalidIdToken();
        }
    }

    private OAuthUserProfile verifyLine(
            OAuthStateSession stateSession,
            OAuthProviderTokenSet tokenSet) {
        if (lineClientId == null) {
            throw new BusinessException(
                    AuthErrorCode.OAUTH_PROVIDER_NOT_CONFIGURED
            );
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("id_token", tokenSet.getIdToken());
        form.add("client_id", lineClientId);
        form.add("nonce", stateSession.getNonce());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        ResponseEntity<LineIdTokenVerificationResponse> response;
        try {
            response = restOperations.exchange(
                    LINE_ID_TOKEN_VERIFY_URI,
                    HttpMethod.POST,
                    new HttpEntity<>(form, headers),
                    LineIdTokenVerificationResponse.class
            );
        } catch (HttpClientErrorException exception) {
            throw invalidIdToken();
        } catch (HttpServerErrorException | ResourceAccessException exception) {
            throw new BusinessException(
                    AuthErrorCode.OAUTH_PROVIDER_UNAVAILABLE
            );
        } catch (RestClientException exception) {
            throw new BusinessException(
                    AuthErrorCode.INVALID_OAUTH_TOKEN_RESPONSE
            );
        }

        LineIdTokenVerificationResponse body = response.getBody();
        if (body == null) {
            throw new BusinessException(
                    AuthErrorCode.INVALID_OAUTH_TOKEN_RESPONSE
            );
        }
        try {
            return body.toUserProfile(
                    lineClientId,
                    stateSession.getNonce(),
                    Instant.now(clock)
            );
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(
                    AuthErrorCode.INVALID_OAUTH_TOKEN_RESPONSE
            );
        }
    }

    private static BusinessException invalidIdToken() {
        return new BusinessException(AuthErrorCode.INVALID_OAUTH_ID_TOKEN);
    }

    private static String optionalText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static boolean secureEquals(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8)
        );
    }
}
