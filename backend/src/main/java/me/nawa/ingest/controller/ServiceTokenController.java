package me.nawa.ingest.controller;

import me.nawa.auth.jwt.AccessToken;
import me.nawa.auth.jwt.JwtTokenProvider;
import me.nawa.common.response.ApiResponse;
import me.nawa.ingest.dto.request.ServiceTokenRequest;
import me.nawa.ingest.dto.response.ServiceTokenResponse;
import me.nawa.ingest.exception.IngestUnauthorizedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 크롤러 파이프라인이 접근 토큰을 받아 가는 곳입니다.
 *
 * <p>사람 로그인은 OAuth2 뿐이라 기계가 쓸 경로가 없었습니다. 공유 비밀을 확인하고
 * 기존 JWT 를 그대로 발급합니다. 검증은 기존 필터가 하므로 인증 체계가 갈라지지
 * 않습니다.
 *
 * <p>발급 대상은 V15 가 만든 SYSTEM 회원 하나로 고정합니다. 요청이 대상 회원을
 * 고르게 두면 비밀 하나로 아무 회원이나 사칭할 수 있습니다.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class ServiceTokenController {

    private final JwtTokenProvider jwtTokenProvider;
    private final byte[] expectedSecret;
    private final long pipelineMemberId;

    public ServiceTokenController(
            JwtTokenProvider jwtTokenProvider,
            @Value("${auth.service.pipeline-secret:}") String pipelineSecret,
            @Value("${auth.service.pipeline-member-id:1000000}") long pipelineMemberId) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.expectedSecret = pipelineSecret.getBytes(StandardCharsets.UTF_8);
        this.pipelineMemberId = pipelineMemberId;
    }

    @PostMapping("/service-token")
    public ApiResponse<ServiceTokenResponse> issue(@RequestBody ServiceTokenRequest request) {
        if (expectedSecret.length == 0) {
            // 비밀을 설정하지 않은 환경에서는 이 경로를 아예 닫는다.
            // 빈 문자열끼리 맞아떨어져 누구나 토큰을 받는 사고를 막는다.
            throw new IngestUnauthorizedException();
        }
        if (!matches(request.getSecret())) {
            throw new IngestUnauthorizedException();
        }

        AccessToken token = jwtTokenProvider.issueAccessToken(pipelineMemberId);
        return ApiResponse.success(
                new ServiceTokenResponse(token.getValue(), token.getExpiresAt()));
    }

    /**
     * 길이와 내용 모두 시간 차이 없이 비교합니다. equals 는 첫 불일치에서 멈춰
     * 비밀을 한 글자씩 알아낼 여지를 줍니다.
     */
    private boolean matches(String candidate) {
        byte[] given = candidate == null
                ? new byte[0]
                : candidate.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expectedSecret, given);
    }
}
