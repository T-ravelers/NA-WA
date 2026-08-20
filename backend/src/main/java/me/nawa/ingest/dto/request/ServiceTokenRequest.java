package me.nawa.ingest.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 파이프라인이 접근 토큰을 받기 위해 제시하는 공유 비밀입니다.
 */
@Getter
@Setter
@NoArgsConstructor
public class ServiceTokenRequest {

    private String secret;
}
