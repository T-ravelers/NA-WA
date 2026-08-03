package me.nawa.auth.oauth;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

@Getter
@RequiredArgsConstructor
public enum OAuthProvider {
    GOOGLE("google", false),
    LINE("line", true);

    private final String registrationId;
    private final boolean pkceRequired;

    public static OAuthProvider fromRegistrationId(String registrationId) {
        return Arrays.stream(values())
                .filter(provider -> provider.registrationId.equals(
                        registrationId
                ))
                .findFirst()
                .orElseThrow(
                        () -> new IllegalArgumentException(
                                "Unsupported OAuth provider"
                        )
                );
    }
}
