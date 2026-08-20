package me.nawa.ingest.dto.response;

import lombok.Getter;

import java.time.Instant;

/**
 * 발급한 접근 토큰과 만료 시각입니다.
 */
@Getter
public class ServiceTokenResponse {

    private final String accessToken;
    private final Instant expiresAt;

    public ServiceTokenResponse(String accessToken, Instant expiresAt) {
        this.accessToken = accessToken;
        this.expiresAt = expiresAt;
    }
}
