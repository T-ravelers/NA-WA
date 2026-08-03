package me.nawa.auth.oauth.callback;

import me.nawa.common.exception.ErrorCode;

import java.net.URI;

public interface OAuthCallbackService {
    OAuthCallbackResult handle(
            String provider,
            String state,
            String authorizationCode,
            String authorizationError
    );

    URI createFailureRedirectUri(ErrorCode errorCode);
}
