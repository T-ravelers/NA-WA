package me.nawa.ingest.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;

import java.time.Instant;

/**
 * 발급한 접근 토큰과 만료 시각입니다.
 *
 * <p>만료 시각은 ISO-8601 문자열로 고정합니다. 이 저장소의 Jackson 설정에서는
 * 날짜가 epoch 숫자로 나갈 수 있는데, 파이프라인이 파싱할 값이라 형식이
 * 흔들리면 안 됩니다.
 */
@Getter
public class ServiceTokenResponse {

    private final String accessToken;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private final Instant expiresAt;

    public ServiceTokenResponse(String accessToken, Instant expiresAt) {
        this.accessToken = accessToken;
        this.expiresAt = expiresAt;
    }
}
