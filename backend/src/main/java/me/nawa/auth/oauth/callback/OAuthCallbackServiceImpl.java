package me.nawa.auth.oauth.callback;

import me.nawa.auth.exception.AuthErrorCode;
import me.nawa.auth.oauth.OAuthProvider;
import me.nawa.auth.oauth.account.OAuthMemberService;
import me.nawa.auth.oauth.identity.OAuthIdentityVerificationService;
import me.nawa.auth.oauth.identity.OAuthUserProfile;
import me.nawa.auth.oauth.state.OAuthStateService;
import me.nawa.auth.oauth.state.OAuthStateSession;
import me.nawa.auth.oauth.token.OAuthProviderTokenSet;
import me.nawa.auth.oauth.token.OAuthTokenExchangeService;
import me.nawa.auth.token.AuthTokenService;
import me.nawa.auth.token.AuthTokens;
import me.nawa.common.exception.BusinessException;
import me.nawa.common.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

@Service
public class OAuthCallbackServiceImpl implements OAuthCallbackService {
    private final OAuthStateService stateService;
    private final OAuthTokenExchangeService tokenExchangeService;
    private final OAuthIdentityVerificationService identityVerificationService;
    private final OAuthMemberService memberService;
    private final AuthTokenService authTokenService;
    private final URI successUri;
    private final URI failureUri;

    public OAuthCallbackServiceImpl(
            OAuthStateService stateService,
            OAuthTokenExchangeService tokenExchangeService,
            OAuthIdentityVerificationService identityVerificationService,
            OAuthMemberService memberService,
            AuthTokenService authTokenService,
            @Value("${auth.frontend.success-url}") String successUrl,
            @Value("${auth.frontend.failure-url}") String failureUrl) {
        this.stateService = Objects.requireNonNull(
                stateService,
                "OAuth state service is required"
        );
        this.tokenExchangeService = Objects.requireNonNull(
                tokenExchangeService,
                "OAuth token exchange service is required"
        );
        this.identityVerificationService = Objects.requireNonNull(
                identityVerificationService,
                "OAuth identity verification service is required"
        );
        this.memberService = Objects.requireNonNull(
                memberService,
                "OAuth member service is required"
        );
        this.authTokenService = Objects.requireNonNull(
                authTokenService,
                "Auth token service is required"
        );
        this.successUri = requireFrontendUri(successUrl, "success");
        this.failureUri = requireFrontendUri(failureUrl, "failure");
    }

    @Override
    public OAuthCallbackResult handle(
            String provider,
            String state,
            String authorizationCode,
            String authorizationError) {
        OAuthStateSession stateSession = stateService.consume(state)
                .orElseThrow(
                        () -> new BusinessException(
                                AuthErrorCode.INVALID_OAUTH_CALLBACK_STATE
                        )
                );
        OAuthProvider callbackProvider = requireMatchingProvider(
                provider,
                stateSession
        );
        if (StringUtils.hasText(authorizationError)
                || !StringUtils.hasText(authorizationCode)) {
            throw new BusinessException(
                    AuthErrorCode.OAUTH_AUTHORIZATION_FAILED
            );
        }

        OAuthProviderTokenSet providerTokens = tokenExchangeService.exchange(
                stateSession,
                authorizationCode
        );
        if (providerTokens.getProvider() != callbackProvider) {
            throw new BusinessException(
                    AuthErrorCode.INVALID_OAUTH_ID_TOKEN
            );
        }
        OAuthUserProfile profile = identityVerificationService.verify(
                stateSession,
                providerTokens
        );
        long memberId = memberService.resolveMemberId(profile);
        AuthTokens authTokens = authTokenService.issueTokens(memberId);

        return new OAuthCallbackResult(
                authTokens,
                createSuccessRedirectUri(stateSession.getReturnPath())
        );
    }

    @Override
    public URI createFailureRedirectUri(ErrorCode errorCode) {
        ErrorCode requiredErrorCode = Objects.requireNonNull(
                errorCode,
                "OAuth callback error code is required"
        );
        return UriComponentsBuilder.fromUri(failureUri)
                .replaceQueryParam("error", requiredErrorCode.getCode())
                .build()
                .encode(StandardCharsets.UTF_8)
                .toUri();
    }

    private OAuthProvider requireMatchingProvider(
            String provider,
            OAuthStateSession stateSession) {
        OAuthProvider callbackProvider;
        try {
            callbackProvider = OAuthProvider.fromRegistrationId(provider);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(
                    AuthErrorCode.INVALID_OAUTH_CALLBACK_STATE
            );
        }
        if (callbackProvider != stateSession.getProvider()) {
            throw new BusinessException(
                    AuthErrorCode.INVALID_OAUTH_CALLBACK_STATE
            );
        }
        return callbackProvider;
    }

    private URI createSuccessRedirectUri(String returnPath) {
        return UriComponentsBuilder.fromUri(successUri)
                .replaceQueryParam("returnPath", returnPath)
                .build()
                .encode(StandardCharsets.UTF_8)
                .toUri();
    }

    private static URI requireFrontendUri(String value, String name) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(
                    "OAuth frontend " + name + " URL must not be blank"
            );
        }
        URI uri;
        try {
            uri = URI.create(value.trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "OAuth frontend " + name + " URL is invalid",
                    exception
            );
        }
        if (!uri.isAbsolute()
                || uri.getHost() == null
                || !("http".equalsIgnoreCase(uri.getScheme())
                || "https".equalsIgnoreCase(uri.getScheme()))
                || uri.getRawUserInfo() != null
                || uri.getRawFragment() != null) {
            throw new IllegalArgumentException(
                    "OAuth frontend " + name
                            + " URL must be an HTTP(S) URL without credentials or fragment"
            );
        }
        return uri;
    }
}
