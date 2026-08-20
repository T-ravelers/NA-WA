package me.nawa.ingest.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.nawa.auth.cookie.AuthCookieManager;
import me.nawa.auth.jwt.AccessTokenClaims;
import me.nawa.auth.jwt.JwtTokenProvider;
import me.nawa.auth.security.AuthenticatedMember;
import me.nawa.auth.security.JwtAuthenticationFilter;
import me.nawa.common.exception.GlobalExceptionHandler;
import me.nawa.ingest.dto.response.IngestResultResponse;
import me.nawa.ingest.service.IngestService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 토큰을 받아서 실제로 적재를 호출하는 데까지 이어지는지 봅니다.
 *
 * <p>이 테스트가 없어서 놓칠 뻔한 것: 필터가 쿠키만 읽던 시절에는 발급받은
 * 토큰을 {@code Authorization: Bearer} 로 보내도 인증이 되지 않았습니다.
 * 두 조각을 따로 보면 각각 정상이라 간극이 드러나지 않습니다.
 */
class ServiceTokenToIngestFlowTest {

    private static final String SECRET = "pipeline-secret-for-test";
    private static final long PIPELINE_MEMBER_ID = 1000000L;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private JwtTokenProvider jwtTokenProvider;
    private MockMvc tokenMvc;
    private MockMvc ingestMvc;
    private RecordingIngestService ingestService;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(
                Base64.getEncoder().encodeToString(
                        "test-signing-key-with-at-least-32-bytes!!".getBytes(StandardCharsets.UTF_8)),
                "nawa",
                900);

        tokenMvc = MockMvcBuilders
                .standaloneSetup(new ServiceTokenController(
                        jwtTokenProvider, SECRET, PIPELINE_MEMBER_ID))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        ingestService = new RecordingIngestService();
        ingestMvc = MockMvcBuilders
                .standaloneSetup(new IngestController(ingestService, PIPELINE_MEMBER_ID))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                // 쿠키는 실제 구현을 그대로 쓴다. 요청에 쿠키를 싣지 않으므로
                // 필터가 Bearer 헤더로 넘어가는 경로만 타게 된다.
                .addFilters(new JwtAuthenticationFilter(
                        new AuthCookieManager("nawa_at", "nawa_rt", "nawa_st",
                                false, "Lax", ""),
                        jwtTokenProvider))
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void issuedTokenAuthenticatesIngestCallThroughBearerHeader() throws Exception {
        String token = issueToken();

        String body = ingestMvc.perform(post("/api/v1/internal/ingest/events")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventBatchJson()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        JsonNode json = objectMapper.readTree(body);
        assertTrue(json.get("success").asBoolean(), body);
        assertEquals(1, ingestService.eventCalls, "적재 서비스가 불리지 않았습니다");
    }

    @Test
    void ingestIsRejectedWithoutAToken() throws Exception {
        ingestMvc.perform(post("/api/v1/internal/ingest/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventBatchJson()))
                .andExpect(status().isForbidden());

        assertEquals(0, ingestService.eventCalls, "인증 없이 적재가 실행됐습니다");
    }

    @Test
    void ingestIsRejectedForAHumanAccountToken() throws Exception {
        // 사람 계정의 토큰은 형식이 같아도 적재에 들어올 수 없어야 한다.
        String humanToken = jwtTokenProvider.issueAccessToken(42L).getValue();

        ingestMvc.perform(post("/api/v1/internal/ingest/events")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + humanToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventBatchJson()))
                .andExpect(status().isForbidden());

        assertEquals(0, ingestService.eventCalls);
    }

    @Test
    void serviceTokenIsRejectedForAWrongSecret() throws Exception {
        tokenMvc.perform(post("/api/v1/auth/service-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"secret\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiresAtIsSerializedAsAnIsoString() throws Exception {
        String body = tokenMvc.perform(post("/api/v1/auth/service-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"secret\":\"" + SECRET + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        JsonNode expiresAt = objectMapper.readTree(body).get("data").get("expiresAt");
        // 숫자로 나가면 파이프라인이 파싱하는 형식이 조용히 바뀐다.
        assertTrue(expiresAt.isTextual(), "expiresAt 이 문자열이 아닙니다: " + expiresAt);
        Instant.parse(expiresAt.asText());
    }

    @Test
    void issuedTokenCarriesThePipelineMemberId() {
        AccessTokenClaims claims = jwtTokenProvider.parseAccessToken(
                jwtTokenProvider.issueAccessToken(PIPELINE_MEMBER_ID).getValue());

        assertEquals(PIPELINE_MEMBER_ID, claims.getMemberId());
    }

    private String issueToken() throws Exception {
        String body = tokenMvc.perform(post("/api/v1/auth/service-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"secret\":\"" + SECRET + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        return objectMapper.readTree(body).get("data").get("accessToken").asText();
    }

    private static String eventBatchJson() {
        return "[{\"pipelineId\":\"11111111-1111-1111-1111-111111111111\","
                + "\"title\":\"제목\",\"startDate\":\"2026-08-20\","
                + "\"endDate\":\"2026-08-21\"}]";
    }

    /** 호출 여부만 기록합니다. 이 저장소는 목 프레임워크 대신 이 방식을 씁니다. */
    private static final class RecordingIngestService implements IngestService {
        private int eventCalls;

        @Override
        public IngestResultResponse ingestEvents(List<me.nawa.ingest.dto.request.EventIngestItem> items) {
            eventCalls += 1;
            return new IngestResultResponse(items.size(), items.size(), 0, 0);
        }

        @Override
        public IngestResultResponse ingestPlaces(List<me.nawa.ingest.dto.request.PlaceIngestItem> items) {
            return new IngestResultResponse(0, 0, 0, 0);
        }

        @Override
        public IngestResultResponse ingestEventTranslations(
                List<me.nawa.ingest.dto.request.EventTranslationIngestItem> items) {
            return new IngestResultResponse(0, 0, 0, 0);
        }

        @Override
        public IngestResultResponse ingestPlaceTranslations(
                List<me.nawa.ingest.dto.request.PlaceTranslationIngestItem> items) {
            return new IngestResultResponse(0, 0, 0, 0);
        }
    }
}
