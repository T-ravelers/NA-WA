package me.nawa.auth.oauth.token;

import me.nawa.auth.exception.AuthErrorCode;
import me.nawa.auth.oauth.OAuthProvider;
import me.nawa.auth.oauth.state.OAuthStateSession;
import me.nawa.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import java.util.Map;
import java.util.Objects;

@Service
public class OAuthTokenExchangeServiceImpl
        implements OAuthTokenExchangeService {
    private static final URI GOOGLE_TOKEN_URI = URI.create(
            "https://oauth2.googleapis.com/token"
    );
    private static final URI LINE_TOKEN_URI = URI.create(
            "https://api.line.me/oauth2/v2.1/token"
    );

    private final RestOperations restOperations;
    private final Map<OAuthProvider, OAuthClient> clients;

    @Autowired
    public OAuthTokenExchangeServiceImpl(
            @Qualifier("oauthRestOperations") RestOperations restOperations,
            @Value("${oauth.google.client-id}") String googleClientId,
            @Value("${oauth.google.client-secret}") String googleClientSecret,
            @Value("${oauth.google.redirect-uri}") String googleRedirectUri,
            @Value("${oauth.line.client-id}") String lineClientId,
            @Value("${oauth.line.client-secret}") String lineClientSecret,
            @Value("${oauth.line.redirect-uri}") String lineRedirectUri) {
        this.restOperations = Objects.requireNonNull(
                restOperations,
                "OAuth RestOperations is required"
        );
        this.clients = Map.of(
                OAuthProvider.GOOGLE,
                new OAuthClient(
                        GOOGLE_TOKEN_URI,
                        googleClientId,
                        googleClientSecret,
                        googleRedirectUri
                ),
                OAuthProvider.LINE,
                new OAuthClient(
                        LINE_TOKEN_URI,
                        lineClientId,
                        lineClientSecret,
                        lineRedirectUri
                )
        );
    }

    @Override
    public OAuthProviderTokenSet exchange(
            OAuthStateSession stateSession,
            String authorizationCode) {
        OAuthStateSession requiredStateSession = Objects.requireNonNull(
                stateSession,
                "OAuth state session is required"
        );
        if (!StringUtils.hasText(authorizationCode)) {
            throw new BusinessException(
                    AuthErrorCode.OAUTH_TOKEN_EXCHANGE_FAILED
            );
        }

        OAuthClient client = clients.get(requiredStateSession.getProvider());
        if (client == null) {
            throw new BusinessException(
                    AuthErrorCode.UNSUPPORTED_OAUTH_PROVIDER
            );
        }
        if (!client.isConfigured()) {
            throw new BusinessException(
                    AuthErrorCode.OAUTH_PROVIDER_NOT_CONFIGURED
            );
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", authorizationCode.trim());
        form.add("redirect_uri", client.redirectUri);
        form.add("client_id", client.clientId);
        form.add("client_secret", client.clientSecret);
        form.add("code_verifier", requiredStateSession.getCodeVerifier());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        ResponseEntity<OAuthTokenResponse> response;
        try {
            response = restOperations.exchange(
                    client.tokenUri,
                    HttpMethod.POST,
                    new HttpEntity<>(form, headers),
                    OAuthTokenResponse.class
            );
        } catch (HttpClientErrorException exception) {
            throw new BusinessException(
                    AuthErrorCode.OAUTH_TOKEN_EXCHANGE_FAILED
            );
        } catch (HttpServerErrorException | ResourceAccessException exception) {
            throw new BusinessException(
                    AuthErrorCode.OAUTH_PROVIDER_UNAVAILABLE
            );
        } catch (RestClientException exception) {
            throw new BusinessException(
                    AuthErrorCode.INVALID_OAUTH_TOKEN_RESPONSE
            );
        }

        OAuthTokenResponse body = response.getBody();
        if (body == null) {
            throw new BusinessException(
                    AuthErrorCode.INVALID_OAUTH_TOKEN_RESPONSE
            );
        }
        try {
            return body.toTokenSet(requiredStateSession.getProvider());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(
                    AuthErrorCode.INVALID_OAUTH_TOKEN_RESPONSE
            );
        }
    }

    private static final class OAuthClient {
        private final URI tokenUri;
        private final String clientId;
        private final String clientSecret;
        private final String redirectUri;

        private OAuthClient(
                URI tokenUri,
                String clientId,
                String clientSecret,
                String redirectUri) {
            this.tokenUri = Objects.requireNonNull(
                    tokenUri,
                    "OAuth token URI is required"
            );
            this.clientId = optionalText(clientId);
            this.clientSecret = optionalText(clientSecret);
            this.redirectUri = requireRedirectUri(redirectUri);
        }

        private boolean isConfigured() {
            return clientId != null && clientSecret != null;
        }

        private static String optionalText(String value) {
            return StringUtils.hasText(value) ? value.trim() : null;
        }

        private static String requireRedirectUri(String value) {
            if (!StringUtils.hasText(value)) {
                throw new IllegalArgumentException(
                        "OAuth redirect URI must not be blank"
                );
            }
            URI uri;
            try {
                uri = URI.create(value.trim());
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
            return uri.toString();
        }
    }
}
