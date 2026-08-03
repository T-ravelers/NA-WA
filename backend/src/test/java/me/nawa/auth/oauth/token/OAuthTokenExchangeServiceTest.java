package me.nawa.auth.oauth.token;

import me.nawa.auth.exception.AuthErrorCode;
import me.nawa.auth.oauth.OAuthProvider;
import me.nawa.auth.oauth.state.OAuthStateSession;
import me.nawa.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OAuthTokenExchangeServiceTest {
    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private OAuthTokenExchangeService service;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        service = createService(
                "google-client-id",
                "google-client-secret",
                "line-client-id",
                "line-client-secret"
        );
    }

    @Test
    void exchange_google_postsOfficialTokenRequestWithPkce() {
        expectTokenRequest(
                "https://oauth2.googleapis.com/token",
                "authorization-code",
                "http://localhost:8080/api/v1/auth/oauth2/callback/google",
                "google-client-id",
                "google-client-secret",
                "google-code-verifier",
                """
                        {
                          "access_token": "google-access-token",
                          "expires_in": 3599,
                          "scope": "openid profile email",
                          "token_type": "Bearer",
                          "id_token": "google-id-token",
                          "refresh_token": "must-not-be-retained"
                        }
                        """
        );

        OAuthProviderTokenSet tokenSet = service.exchange(
                stateSession(
                        OAuthProvider.GOOGLE,
                        "google-code-verifier"
                ),
                "authorization-code"
        );

        assertEquals(OAuthProvider.GOOGLE, tokenSet.getProvider());
        assertEquals("google-access-token", tokenSet.getAccessToken());
        assertEquals("google-id-token", tokenSet.getIdToken());
        assertEquals(3599, tokenSet.getExpiresInSeconds());
        assertEquals("Bearer", tokenSet.getTokenType());
        assertEquals("openid profile email", tokenSet.getScope());
        server.verify();
    }

    @Test
    void exchange_line_postsV21TokenRequestWithPkce() {
        expectTokenRequest(
                "https://api.line.me/oauth2/v2.1/token",
                "line-code",
                "http://localhost:8080/api/v1/auth/oauth2/callback/line",
                "line-client-id",
                "line-client-secret",
                "line-code-verifier",
                """
                        {
                          "access_token": "line-access-token",
                          "expires_in": 2592000,
                          "token_type": "bearer",
                          "id_token": "line-id-token",
                          "refresh_token": "must-not-be-retained"
                        }
                        """
        );

        OAuthProviderTokenSet tokenSet = service.exchange(
                stateSession(OAuthProvider.LINE, "line-code-verifier"),
                "line-code"
        );

        assertEquals(OAuthProvider.LINE, tokenSet.getProvider());
        assertEquals("line-access-token", tokenSet.getAccessToken());
        assertEquals("line-id-token", tokenSet.getIdToken());
        assertEquals(2592000, tokenSet.getExpiresInSeconds());
        assertEquals("Bearer", tokenSet.getTokenType());
        assertNull(tokenSet.getScope());
        server.verify();
    }

    @Test
    void exchange_providerRejectsCode_returnsStableAuthError() {
        server.expect(once(), request -> assertPostRequest(
                        request,
                        "https://oauth2.googleapis.com/token"
                ))
                .andRespond(withBadRequest().body(
                        "{\"error\":\"invalid_grant\","
                                + "\"error_description\":\"secret detail\"}"
                ));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.exchange(
                        stateSession(
                                OAuthProvider.GOOGLE,
                                "google-code-verifier"
                        ),
                        "expired-code"
                )
        );

        assertEquals(
                AuthErrorCode.OAUTH_TOKEN_EXCHANGE_FAILED,
                exception.getErrorCode()
        );
        assertEquals(
                AuthErrorCode.OAUTH_TOKEN_EXCHANGE_FAILED.getMessage(),
                exception.getMessage()
        );
        assertNull(exception.getCause());
        server.verify();
    }

    @Test
    void exchange_providerServerError_returnsUnavailableError() {
        server.expect(once(), request -> assertPostRequest(
                        request,
                        "https://oauth2.googleapis.com/token"
                ))
                .andRespond(withServerError());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.exchange(
                        stateSession(
                                OAuthProvider.GOOGLE,
                                "google-code-verifier"
                        ),
                        "authorization-code"
                )
        );

        assertEquals(
                AuthErrorCode.OAUTH_PROVIDER_UNAVAILABLE,
                exception.getErrorCode()
        );
        server.verify();
    }

    @Test
    void exchange_malformedSuccessResponse_returnsInvalidResponseError() {
        server.expect(once(), request -> assertPostRequest(
                        request,
                        "https://oauth2.googleapis.com/token"
                ))
                .andRespond(withSuccess(
                        "{\"access_token\":\"only-access-token\"}",
                        MediaType.APPLICATION_JSON
                ));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.exchange(
                        stateSession(
                                OAuthProvider.GOOGLE,
                                "google-code-verifier"
                        ),
                        "authorization-code"
                )
        );

        assertEquals(
                AuthErrorCode.INVALID_OAUTH_TOKEN_RESPONSE,
                exception.getErrorCode()
        );
        assertNull(exception.getCause());
        server.verify();
    }

    @Test
    void exchange_unconfiguredProvider_rejectsBeforeHttpRequest() {
        service = createService(
                "google-client-id",
                "",
                "line-client-id",
                "line-client-secret"
        );

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.exchange(
                        stateSession(
                                OAuthProvider.GOOGLE,
                                "google-code-verifier"
                        ),
                        "authorization-code"
                )
        );

        assertEquals(
                AuthErrorCode.OAUTH_PROVIDER_NOT_CONFIGURED,
                exception.getErrorCode()
        );
        server.verify();
    }

    private OAuthTokenExchangeService createService(
            String googleClientId,
            String googleClientSecret,
            String lineClientId,
            String lineClientSecret) {
        return new OAuthTokenExchangeServiceImpl(
                restTemplate,
                googleClientId,
                googleClientSecret,
                "http://localhost:8080/api/v1/auth/oauth2/callback/google",
                lineClientId,
                lineClientSecret,
                "http://localhost:8080/api/v1/auth/oauth2/callback/line"
        );
    }

    private void expectTokenRequest(
            String tokenUri,
            String code,
            String redirectUri,
            String clientId,
            String clientSecret,
            String codeVerifier,
            String responseBody) {
        MultiValueMap<String, String> expectedForm =
                new LinkedMultiValueMap<>();
        expectedForm.add("grant_type", "authorization_code");
        expectedForm.add("code", code);
        expectedForm.add("redirect_uri", redirectUri);
        expectedForm.add("client_id", clientId);
        expectedForm.add("client_secret", clientSecret);
        expectedForm.add("code_verifier", codeVerifier);

        server.expect(once(), request -> {
                    assertPostRequest(request, tokenUri);
                    assertTrue(
                            MediaType.APPLICATION_FORM_URLENCODED
                                    .isCompatibleWith(
                                            request.getHeaders()
                                                    .getContentType()
                                    )
                    );
                    MockClientHttpRequest mockRequest =
                            (MockClientHttpRequest) request;
                    assertEquals(
                            expectedForm,
                            parseForm(mockRequest.getBodyAsString())
                    );
                })
                .andRespond(withSuccess(
                        responseBody,
                        MediaType.APPLICATION_JSON
                ));
    }

    private void assertPostRequest(
            org.springframework.http.client.ClientHttpRequest request,
            String expectedUri) {
        assertEquals(URI.create(expectedUri), request.getURI());
        assertEquals(HttpMethod.POST, request.getMethod());
    }

    private MultiValueMap<String, String> parseForm(String body) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        for (String pair : body.split("&")) {
            String[] parts = pair.split("=", 2);
            form.add(decode(parts[0]), decode(parts[1]));
        }
        return form;
    }

    private String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private OAuthStateSession stateSession(
            OAuthProvider provider,
            String codeVerifier) {
        Instant issuedAt = Instant.parse("2026-08-03T01:00:00Z");
        return new OAuthStateSession(
                "state-value",
                provider,
                "nonce-value",
                codeVerifier,
                "/journeys",
                issuedAt,
                issuedAt.plusSeconds(600)
        );
    }
}
