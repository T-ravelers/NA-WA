package me.nawa.auth.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import me.nawa.auth.cookie.AuthCookieManager;
import me.nawa.auth.exception.AuthErrorCode;
import me.nawa.auth.oauth.authorization.OAuthAuthorizationService;
import me.nawa.auth.oauth.callback.OAuthCallbackResult;
import me.nawa.auth.oauth.callback.OAuthCallbackService;
import me.nawa.auth.token.AuthTokenService;
import me.nawa.auth.token.AuthTokens;
import me.nawa.common.exception.BusinessException;
import me.nawa.common.exception.CommonErrorCode;
import me.nawa.common.exception.ErrorCode;
import me.nawa.common.response.ApiResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;

import javax.servlet.http.HttpServletRequest;
import java.net.URI;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Log4j2
public class AuthController {
    private final AuthTokenService authTokenService;
    private final AuthCookieManager authCookieManager;
    private final OAuthAuthorizationService oauthAuthorizationService;
    private final OAuthCallbackService oauthCallbackService;

    @GetMapping("/oauth2/authorization/{provider}")
    public ResponseEntity<Void> authorize(
            @PathVariable("provider") String provider,
            @RequestParam(name = "returnPath", required = false)
            String returnPath) {
        URI authorizationUri = oauthAuthorizationService
                .createAuthorizationUri(provider, returnPath);

        return ResponseEntity
                .status(HttpStatus.FOUND)
                .location(authorizationUri)
                .build();
    }

    @GetMapping("/oauth2/callback/{provider}")
    public ResponseEntity<Void> callback(
            @PathVariable("provider") String provider,
            @RequestParam(name = "state", required = false) String state,
            @RequestParam(name = "code", required = false) String code,
            @RequestParam(name = "error", required = false) String error) {
        try {
            OAuthCallbackResult result = oauthCallbackService.handle(
                    provider,
                    state,
                    code,
                    error
            );
            AuthTokens tokens = result.getTokens();
            return ResponseEntity
                    .status(HttpStatus.FOUND)
                    .location(result.getRedirectUri())
                    .header(
                            HttpHeaders.SET_COOKIE,
                            authCookieManager
                                    .createAccessTokenCookie(
                                            tokens.getAccessToken()
                                    )
                                    .toString(),
                            authCookieManager
                                    .createRefreshTokenCookie(
                                            tokens.getRefreshToken()
                                    )
                                    .toString()
                    )
                    .build();
        } catch (BusinessException exception) {
            log.warn(
                    "[OAuthCallbackFailure] code={}",
                    exception.getErrorCode().getCode()
            );
            return createCallbackFailureResponse(
                    exception.getErrorCode()
            );
        } catch (RuntimeException exception) {
            log.error(
                    "[OAuthCallbackUnexpectedFailure] type={}",
                    exception.getClass().getName()
            );
            return createCallbackFailureResponse(
                    CommonErrorCode.INTERNAL_SERVER_ERROR
            );
        }
    }

    private ResponseEntity<Void> createCallbackFailureResponse(
            ErrorCode errorCode) {
        return ResponseEntity
                .status(HttpStatus.FOUND)
                .location(oauthCallbackService.createFailureRedirectUri(
                        errorCode
                ))
                .header(
                        HttpHeaders.SET_COOKIE,
                        authCookieManager
                                .deleteAccessTokenCookie()
                                .toString(),
                        authCookieManager
                                .deleteRefreshTokenCookie()
                                .toString()
                )
                .build();
    }

    @GetMapping("/csrf")
    public ApiResponse<CsrfTokenResponse> csrf(
            HttpServletRequest request) {
        CsrfToken csrfToken = (CsrfToken) request.getAttribute(
                CsrfToken.class.getName()
        );

        return ApiResponse.success(
                new CsrfTokenResponse(
                        csrfToken.getToken(),
                        csrfToken.getHeaderName()
                )
        );
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<Void>> refresh(
            HttpServletRequest request) {
        String currentRefreshToken = authCookieManager
                .findRefreshToken(request.getCookies())
                .orElseThrow(
                        () -> new BusinessException(
                                AuthErrorCode.INVALID_REFRESH_TOKEN
                        )
                );
        AuthTokens tokens = authTokenService.refreshTokens(
                currentRefreshToken
        );

        return ResponseEntity.ok()
                .header(
                        HttpHeaders.SET_COOKIE,
                        authCookieManager
                                .createAccessTokenCookie(tokens.getAccessToken())
                                .toString(),
                        authCookieManager
                                .createRefreshTokenCookie(tokens.getRefreshToken())
                                .toString()
                )
                .body(ApiResponse.success());
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            HttpServletRequest request) {
        authCookieManager.findRefreshToken(request.getCookies())
                .ifPresent(authTokenService::revokeRefreshToken);

        ResponseCookie deletedAccessToken =
                authCookieManager.deleteAccessTokenCookie();
        ResponseCookie deletedRefreshToken =
                authCookieManager.deleteRefreshTokenCookie();

        return ResponseEntity.ok()
                .header(
                        HttpHeaders.SET_COOKIE,
                        deletedAccessToken.toString(),
                        deletedRefreshToken.toString()
                )
                .body(ApiResponse.success());
    }
}
