package me.nawa.loadtest.controller;

import lombok.extern.log4j.Log4j2;
import me.nawa.auth.cookie.AuthCookieManager;
import me.nawa.auth.token.AuthTokenService;
import me.nawa.auth.token.AuthTokens;
import me.nawa.common.exception.BusinessException;
import me.nawa.common.response.ApiResponse;
import me.nawa.auth.exception.AuthErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 부하 테스트가 로그인 단계를 통과하기 위한 경로입니다.
 *
 * <p>소셜 로그인(Google·LINE)은 브라우저 동의 화면을 사람이 거쳐야 완료됩니다. k6 같은
 * 부하 도구는 그 화면을 통과할 수 없어 첫 단계에서 막힙니다. 공유 비밀을 확인하고
 * 정상 로그인과 똑같은 쿠키를 발급해 그 구간만 건너뜁니다. 토큰 발급과 검증은 기존
 * 경로를 그대로 쓰므로 인증 체계가 갈라지지 않습니다.
 *
 * <p><b>이 클래스는 운영 산출물에 들어가지 않습니다.</b> {@code src/loadtest/java}에 있고,
 * {@code build.gradle}이 {@code -Ploadtest}를 준 빌드에서만 컴파일 대상에 넣습니다.
 * 배포 워크플로는 그 플래그를 넘기지 않습니다. 아래 공유 비밀 확인은 그 위의 두 번째
 * 방어선입니다 — 어느 한쪽에 기대지 마세요.
 *
 * <p>대상 회원을 요청이 고르게 두는 것은 의도한 것입니다. 부하 테스트는 수천 명이
 * 동시에 다른 계정으로 접속하는 상황을 만들어야 합니다. 이 경로가 운영에 존재하지
 * 않는다는 전제 위에서만 성립하는 설계이므로, 위 두 방어선을 절대 걷어내지 마세요.
 */
@Log4j2
@RestController
@RequestMapping("/internal/loadtest")
public class LoadTestLoginController {

    private final AuthTokenService authTokenService;
    private final AuthCookieManager authCookieManager;
    private final byte[] expectedSecret;

    public LoadTestLoginController(
            AuthTokenService authTokenService,
            AuthCookieManager authCookieManager,
            @Value("${loadtest.login-secret:}") String loginSecret) {
        this.authTokenService = authTokenService;
        this.authCookieManager = authCookieManager;
        this.expectedSecret = loginSecret.getBytes(StandardCharsets.UTF_8);
    }

    @PostConstruct
    void warnThatThisBuildIsNotForProduction() {
        log.warn("부하 테스트용 로그인 경로가 이 빌드에 포함되어 있습니다. "
            + "운영 배포용 산출물이 아닙니다.");
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<Void>> login(
            @RequestBody LoadTestLoginRequest request) {
        if (expectedSecret.length == 0) {
            // 비밀을 설정하지 않은 환경에서는 경로를 아예 닫는다.
            // 빈 문자열끼리 맞아떨어져 누구나 로그인하는 사고를 막는다.
            throw new BusinessException(AuthErrorCode.AUTHENTICATION_REQUIRED);
        }
        if (!matches(request.getSecret())) {
            throw new BusinessException(AuthErrorCode.AUTHENTICATION_REQUIRED);
        }
        if (request.getMemberId() == null || request.getMemberId() <= 0) {
            throw new BusinessException(AuthErrorCode.AUTHENTICATION_REQUIRED);
        }

        AuthTokens tokens = authTokenService.issueTokens(request.getMemberId());

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
