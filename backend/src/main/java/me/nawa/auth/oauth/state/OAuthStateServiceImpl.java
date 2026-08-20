package me.nawa.auth.oauth.state;

import me.nawa.auth.oauth.OAuthProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;

@Service
public class OAuthStateServiceImpl implements OAuthStateService {
    private static final int RANDOM_VALUE_BYTES = 32;
    private static final int MAX_SAVE_ATTEMPTS = 3;
    private static final String PKCE_METHOD = "S256";

    private final OAuthStateStore stateStore;
    private final OAuthReturnPathPolicy returnPathPolicy;
    private final long ttlSeconds;
    private final Clock clock;
    private final SecureRandom secureRandom;

    @Autowired
    public OAuthStateServiceImpl(
            OAuthStateStore stateStore,
            OAuthReturnPathPolicy returnPathPolicy,
            @Value("${auth.oauth-state-ttl-seconds}") long ttlSeconds) {
        this(
                stateStore,
                returnPathPolicy,
                ttlSeconds,
                Clock.systemUTC(),
                new SecureRandom()
        );
    }

    OAuthStateServiceImpl(
            OAuthStateStore stateStore,
            OAuthReturnPathPolicy returnPathPolicy,
            long ttlSeconds,
            Clock clock,
            SecureRandom secureRandom) {
        if (ttlSeconds <= 0) {
            throw new IllegalArgumentException(
                    "OAuth state TTL must be positive"
            );
        }
        this.stateStore = Objects.requireNonNull(
                stateStore,
                "OAuthStateStore is required"
        );
        this.returnPathPolicy = Objects.requireNonNull(
                returnPathPolicy,
                "OAuthReturnPathPolicy is required"
        );
        this.ttlSeconds = ttlSeconds;
        this.clock = Objects.requireNonNull(clock, "Clock is required");
        this.secureRandom = Objects.requireNonNull(
                secureRandom,
                "SecureRandom is required"
        );
    }

    @Override
    public OAuthAuthorizationRequestState issue(
            OAuthProvider provider,
            String returnPath) {
        Objects.requireNonNull(provider, "OAuth provider is required");
        String allowedReturnPath = returnPathPolicy.requireAllowed(returnPath);

        for (int attempt = 0; attempt < MAX_SAVE_ATTEMPTS; attempt++) {
            Instant issuedAt = clock.instant();
            Instant expiresAt = issuedAt.plusSeconds(ttlSeconds);
            String state = generateRandomValue();
            String nonce = generateRandomValue();
            String browserBinding = generateRandomValue();
            String codeVerifier = provider.isPkceRequired()
                    ? generateRandomValue()
                    : null;
            OAuthStateSession session = new OAuthStateSession(
                    state,
                    provider,
                    nonce,
                    codeVerifier,
                    sha256(browserBinding),
                    allowedReturnPath,
                    issuedAt,
                    expiresAt
            );

            if (stateStore.saveIfAbsent(session)) {
                return new OAuthAuthorizationRequestState(
                        state,
                        nonce,
                        createCodeChallenge(codeVerifier),
                        codeVerifier == null ? null : PKCE_METHOD,
                        browserBinding,
                        expiresAt
                );
            }
        }

        throw new IllegalStateException(
                "Failed to allocate a unique OAuth state"
        );
    }

    @Override
    public Optional<OAuthStateSession> consume(
            String state,
            String browserBinding) {
        if (!isValidState(state)) {
            return Optional.empty();
        }
        return stateStore.consume(state)
                .filter(session -> matchesBrowserBinding(
                        session,
                        browserBinding
                ));
    }

    private boolean matchesBrowserBinding(
            OAuthStateSession session,
            String browserBinding) {
        if (!StringUtils.hasText(browserBinding)) {
            return false;
        }
        return MessageDigest.isEqual(
                session.getBrowserBindingHash()
                        .getBytes(StandardCharsets.US_ASCII),
                sha256(browserBinding).getBytes(StandardCharsets.US_ASCII)
        );
    }

    private String generateRandomValue() {
        byte[] randomBytes = new byte[RANDOM_VALUE_BYTES];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(randomBytes);
    }

    private String createCodeChallenge(String codeVerifier) {
        if (codeVerifier == null) {
            return null;
        }
        return sha256(codeVerifier);
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 is not available",
                    exception
            );
        }
    }

    private boolean isValidState(String state) {
        if (!StringUtils.hasText(state)
                || state.length() < 43
                || state.length() > 128) {
            return false;
        }
        for (int index = 0; index < state.length(); index++) {
            char character = state.charAt(index);
            if (!(character >= 'A' && character <= 'Z')
                    && !(character >= 'a' && character <= 'z')
                    && !(character >= '0' && character <= '9')
                    && character != '-'
                    && character != '_') {
                return false;
            }
        }
        return true;
    }
}
