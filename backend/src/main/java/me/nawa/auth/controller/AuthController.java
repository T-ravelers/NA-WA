package me.nawa.auth.controller;

import lombok.RequiredArgsConstructor;
import me.nawa.auth.cookie.AuthCookieManager;
import me.nawa.auth.exception.AuthErrorCode;
import me.nawa.auth.token.AuthTokenService;
import me.nawa.auth.token.AuthTokens;
import me.nawa.common.exception.BusinessException;
import me.nawa.common.response.ApiResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthTokenService authTokenService;
    private final AuthCookieManager authCookieManager;

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
