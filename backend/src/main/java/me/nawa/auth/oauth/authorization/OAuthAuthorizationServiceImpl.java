package me.nawa.auth.oauth.authorization;

import me.nawa.auth.exception.AuthErrorCode;
import me.nawa.auth.oauth.OAuthProvider;
import me.nawa.auth.oauth.state.OAuthAuthorizationRequestState;
import me.nawa.auth.oauth.state.OAuthStateService;
import me.nawa.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class OAuthAuthorizationServiceImpl
        implements OAuthAuthorizationService {
    private static final URI GOOGLE_AUTHORIZATION_URI = URI.create(
            "https://accounts.google.com/o/oauth2/v2/auth"
    );
    private static final URI LINE_AUTHORIZATION_URI = URI.create(
            "https://access.line.me/oauth2/v2.1/authorize"
    );

    private final OAuthStateService stateService;
    private final Map<OAuthProvider, OAuthClient> clients;

    @Autowired
    public OAuthAuthorizationServiceImpl(
            OAuthStateService stateService,
            @Value("${oauth.google.client-id}") String googleClientId,
            @Value("${oauth.google.redirect-uri}") String googleRedirectUri,
            @Value("${oauth.google.scopes}") String googleScopes,
            @Value("${oauth.line.client-id}") String lineClientId,
            @Value("${oauth.line.redirect-uri}") String lineRedirectUri,
            @Value("${oauth.line.scopes}") String lineScopes) {
        this.stateService = Objects.requireNonNull(
                stateService,
                "OAuthStateService is required"
        );
        this.clients = Map.of(
                OAuthProvider.GOOGLE,
                new OAuthClient(
                        GOOGLE_AUTHORIZATION_URI,
                        googleClientId,
                        googleRedirectUri,
                        googleScopes
                ),
                OAuthProvider.LINE,
                new OAuthClient(
                        LINE_AUTHORIZATION_URI,
                        lineClientId,
                        lineRedirectUri,
                        lineScopes
                )
        );
    }

    @Override
    public URI createAuthorizationUri(String provider, String returnPath) {
        OAuthProvider oauthProvider = requireSupportedProvider(provider);
        OAuthClient client = clients.get(oauthProvider);
        if (!client.isConfigured()) {
            throw new BusinessException(
                    AuthErrorCode.OAUTH_PROVIDER_NOT_CONFIGURED
            );
        }
        OAuthAuthorizationRequestState requestState;
        try {
            requestState = stateService.issue(oauthProvider, returnPath);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(
                    AuthErrorCode.INVALID_OAUTH_RETURN_PATH,
                    exception
            );
        }
        return UriComponentsBuilder.fromUri(client.authorizationUri)
                .queryParam("response_type", "code")
                .queryParam("client_id", client.clientId)
                .queryParam("redirect_uri", client.redirectUri)
                .queryParam("scope", String.join(" ", client.scopes))
                .queryParam("state", requestState.getState())
                .queryParam("nonce", requestState.getNonce())
                .queryParam(
                        "code_challenge",
                        requestState.getCodeChallenge()
                )
                .queryParam(
                        "code_challenge_method",
                        requestState.getCodeChallengeMethod()
                )
                .build()
                .encode(StandardCharsets.UTF_8)
                .toUri();
    }

    private OAuthProvider requireSupportedProvider(String provider) {
        try {
            return OAuthProvider.fromRegistrationId(provider);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(
                    AuthErrorCode.UNSUPPORTED_OAUTH_PROVIDER,
                    exception
            );
        }
    }

    private static final class OAuthClient {
        private final URI authorizationUri;
        private final String clientId;
        private final URI redirectUri;
        private final List<String> scopes;

        private OAuthClient(
                URI authorizationUri,
                String clientId,
                String redirectUri,
                String scopes) {
            this.authorizationUri = Objects.requireNonNull(
                    authorizationUri,
                    "OAuth authorization URI is required"
            );
            this.clientId = StringUtils.hasText(clientId)
                    ? clientId.trim()
                    : null;
            this.redirectUri = requireRedirectUri(redirectUri);
            this.scopes = parseScopes(scopes);
        }

        private boolean isConfigured() {
            return clientId != null;
        }

        private static String requireText(String value, String fieldName) {
            if (!StringUtils.hasText(value)) {
                throw new IllegalArgumentException(
                        fieldName + " must not be blank"
                );
            }
            return value.trim();
        }

        private static URI requireRedirectUri(String value) {
            URI uri;
            try {
                uri = URI.create(requireText(value, "OAuth redirect URI"));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException(
                        "OAuth redirect URI is invalid",
                        exception
                );
            }
            if (!uri.isAbsolute()
                    || uri.getHost() == null
                    || !("http".equalsIgnoreCase(uri.getScheme())
                    || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getRawFragment() != null) {
                throw new IllegalArgumentException(
                        "OAuth redirect URI must be an HTTP(S) URL without a fragment"
                );
            }
            return uri;
        }

        private static List<String> parseScopes(String value) {
            List<String> parsedScopes = Arrays.stream(
                            requireText(value, "OAuth scopes").split(",")
                    )
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .distinct()
                    .toList();
            if (parsedScopes.isEmpty() || !parsedScopes.contains("openid")) {
                throw new IllegalArgumentException(
                        "OAuth scopes must include openid"
                );
            }
            return parsedScopes;
        }
    }
}
