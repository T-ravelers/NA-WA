package me.nawa.auth.oauth.identity;

import me.nawa.auth.exception.AuthErrorCode;
import me.nawa.auth.oauth.OAuthProvider;
import me.nawa.auth.oauth.state.OAuthStateSession;
import me.nawa.auth.oauth.token.OAuthProviderTokenSet;
import me.nawa.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OAuthIdentityVerificationServiceTest {
    private static final Instant NOW = Instant.parse(
            "2026-08-03T05:30:00Z"
    );
    private static final String GOOGLE_CLIENT_ID = "google-client-id";
    private static final String LINE_CLIENT_ID = "line-client-id";
    private static final String NONCE = "expected-nonce";

    private FakeJwtDecoder googleDecoder;
    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private OAuthIdentityVerificationService service;

    @BeforeEach
    void setUp() {
        googleDecoder = new FakeJwtDecoder();
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        service = createService(GOOGLE_CLIENT_ID, LINE_CLIENT_ID);
    }

    @Test
    void verify_google_returnsNormalizedProfileAfterNonceMatch() {
        googleDecoder.jwt = googleJwt(NONCE);

        OAuthUserProfile profile = service.verify(
                stateSession(OAuthProvider.GOOGLE),
                tokenSet(OAuthProvider.GOOGLE, "google-id-token")
        );

        assertEquals(OAuthProvider.GOOGLE, profile.getProvider());
        assertEquals("google-user-id", profile.getProviderUserId());
        assertEquals("traveler@example.com", profile.getEmail());
        assertEquals("Traveler", profile.getDisplayName());
        assertEquals(
                "https://images.example/google.png",
                profile.getProfileImageUrl()
        );
        assertEquals("google-id-token", googleDecoder.tokenValue);
    }

    @Test
    void verify_googleRejectsMismatchedNonce_withoutRawErrorCause() {
        googleDecoder.jwt = googleJwt("different-nonce");

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.verify(
                        stateSession(OAuthProvider.GOOGLE),
                        tokenSet(OAuthProvider.GOOGLE, "sensitive-id-token")
                )
        );

        assertEquals(
                AuthErrorCode.INVALID_OAUTH_ID_TOKEN,
                exception.getErrorCode()
        );
        assertNull(exception.getCause());
    }

    @Test
    void verify_googleDecoderRejectsToken_returnsStableAuthError() {
        googleDecoder.failure = new JwtException(
                "provider response containing sensitive token details"
        );

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.verify(
                        stateSession(OAuthProvider.GOOGLE),
                        tokenSet(OAuthProvider.GOOGLE, "sensitive-id-token")
                )
        );

        assertEquals(
                AuthErrorCode.INVALID_OAUTH_ID_TOKEN,
                exception.getErrorCode()
        );
        assertEquals(
                AuthErrorCode.INVALID_OAUTH_ID_TOKEN.getMessage(),
                exception.getMessage()
        );
        assertNull(exception.getCause());
    }

    @Test
    void verify_lineCallsOfficialEndpointWithClientIdAndNonce() {
        expectLineVerification(
                "line-id-token",
                """
                        {
                          "iss": "https://access.line.me",
                          "sub": "line-user-id",
                          "aud": "line-client-id",
                          "exp": %d,
                          "iat": %d,
                          "nonce": "expected-nonce",
                          "name": "LINE Traveler",
                          "picture": "https://images.example/line.png",
                          "email": "line@example.com",
                          "future_claim": "ignored"
                        }
                        """.formatted(
                        NOW.plusSeconds(300).getEpochSecond(),
                        NOW.minusSeconds(10).getEpochSecond()
                )
        );

        OAuthUserProfile profile = service.verify(
                stateSession(OAuthProvider.LINE),
                tokenSet(OAuthProvider.LINE, "line-id-token")
        );

        assertEquals(OAuthProvider.LINE, profile.getProvider());
        assertEquals("line-user-id", profile.getProviderUserId());
        assertEquals("line@example.com", profile.getEmail());
        assertEquals("LINE Traveler", profile.getDisplayName());
        assertEquals(
                "https://images.example/line.png",
                profile.getProfileImageUrl()
        );
        server.verify();
    }

    @Test
    void verify_lineProviderRejectsToken_returnsStableAuthError() {
        server.expect(once(), request -> assertLinePostRequest(request))
                .andRespond(withBadRequest().body(
                        "{\"error_description\":\"sensitive detail\"}"
                ));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.verify(
                        stateSession(OAuthProvider.LINE),
                        tokenSet(OAuthProvider.LINE, "sensitive-id-token")
                )
        );

        assertEquals(
                AuthErrorCode.INVALID_OAUTH_ID_TOKEN,
                exception.getErrorCode()
        );
        assertNull(exception.getCause());
        server.verify();
    }

    @Test
    void verify_lineProviderServerError_returnsUnavailableError() {
        server.expect(once(), request -> assertLinePostRequest(request))
                .andRespond(withServerError());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.verify(
                        stateSession(OAuthProvider.LINE),
                        tokenSet(OAuthProvider.LINE, "line-id-token")
                )
        );

        assertEquals(
                AuthErrorCode.OAUTH_PROVIDER_UNAVAILABLE,
                exception.getErrorCode()
        );
        server.verify();
    }

    @Test
    void verify_lineContradictorySuccessResponse_returnsInvalidResponse() {
        expectLineVerification(
                "line-id-token",
                """
                        {
                          "iss": "https://access.line.me",
                          "sub": "line-user-id",
                          "aud": "another-client",
                          "exp": %d,
                          "nonce": "different-nonce"
                        }
                        """.formatted(
                        NOW.plusSeconds(300).getEpochSecond()
                )
        );

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.verify(
                        stateSession(OAuthProvider.LINE),
                        tokenSet(OAuthProvider.LINE, "line-id-token")
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
    void verify_providerMismatch_rejectsBeforeProviderCall() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.verify(
                        stateSession(OAuthProvider.GOOGLE),
                        tokenSet(OAuthProvider.LINE, "line-id-token")
                )
        );

        assertEquals(
                AuthErrorCode.INVALID_OAUTH_ID_TOKEN,
                exception.getErrorCode()
        );
        assertFalse(googleDecoder.decodeCalled);
        server.verify();
    }

    @Test
    void verify_unconfiguredGoogle_rejectsBeforeDecode() {
        service = createService("", LINE_CLIENT_ID);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.verify(
                        stateSession(OAuthProvider.GOOGLE),
                        tokenSet(OAuthProvider.GOOGLE, "google-id-token")
                )
        );

        assertEquals(
                AuthErrorCode.OAUTH_PROVIDER_NOT_CONFIGURED,
                exception.getErrorCode()
        );
        assertFalse(googleDecoder.decodeCalled);
    }

    private OAuthIdentityVerificationService createService(
            String googleClientId,
            String lineClientId) {
        return new OAuthIdentityVerificationServiceImpl(
                googleDecoder,
                restTemplate,
                googleClientId,
                lineClientId,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private void expectLineVerification(
            String idToken,
            String responseBody) {
        MultiValueMap<String, String> expectedForm =
                new LinkedMultiValueMap<>();
        expectedForm.add("id_token", idToken);
        expectedForm.add("client_id", LINE_CLIENT_ID);
        expectedForm.add("nonce", NONCE);

        server.expect(once(), request -> {
                    assertLinePostRequest(request);
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

    private void assertLinePostRequest(
            org.springframework.http.client.ClientHttpRequest request) {
        assertEquals(
                URI.create("https://api.line.me/oauth2/v2.1/verify"),
                request.getURI()
        );
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

    private OAuthStateSession stateSession(OAuthProvider provider) {
        return new OAuthStateSession(
                "state-value",
                provider,
                NONCE,
                "code-verifier",
                "/journeys",
                NOW.minusSeconds(60),
                NOW.plusSeconds(540)
        );
    }

    private OAuthProviderTokenSet tokenSet(
            OAuthProvider provider,
            String idToken) {
        return new OAuthProviderTokenSet(
                provider,
                "access-token",
                idToken,
                3600,
                "Bearer",
                "openid profile"
        );
    }

    private Jwt googleJwt(String nonce) {
        return Jwt.withTokenValue("google-id-token")
                .header("alg", "RS256")
                .issuer("https://accounts.google.com")
                .audience(List.of(GOOGLE_CLIENT_ID))
                .subject("google-user-id")
                .issuedAt(NOW.minusSeconds(10))
                .expiresAt(NOW.plusSeconds(300))
                .claim("nonce", nonce)
                .claim("email", "traveler@example.com")
                .claim("name", "Traveler")
                .claim("picture", "https://images.example/google.png")
                .build();
    }

    private static final class FakeJwtDecoder implements JwtDecoder {
        private Jwt jwt;
        private JwtException failure;
        private boolean decodeCalled;
        private String tokenValue;

        @Override
        public Jwt decode(String token) throws JwtException {
            decodeCalled = true;
            tokenValue = token;
            if (failure != null) {
                throw failure;
            }
            return jwt;
        }
    }
}
