package me.nawa.auth.jwt;

import lombok.Getter;

import java.time.Instant;

@Getter
public final class AccessToken {
    private final String value;
    private final Instant expiresAt;

    public AccessToken(String value, Instant expiresAt) {
        this.value = value;
        this.expiresAt = expiresAt;
    }
}
