package me.nawa.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.nawa.auth.cookie.AuthCookieManager;
import me.nawa.auth.oauth.authorization.OAuthAuthorizationRedirect;
import me.nawa.auth.oauth.authorization.OAuthAuthorizationService;
import me.nawa.auth.oauth.callback.OAuthCallbackResult;
import me.nawa.auth.oauth.callback.OAuthCallbackService;
import me.nawa.auth.token.AuthTokenService;
import me.nawa.auth.token.AuthTokens;
import me.nawa.appointment.service.AppointmentService;
import me.nawa.common.exception.ErrorCode;
import me.nawa.member.dto.MemberProfileResponse;
import me.nawa.member.dto.MemberAppointmentProfileResponse;
import me.nawa.member.dto.MerchantRegisterRequest;
import me.nawa.member.dto.OnboardingProfileRequest;
import me.nawa.member.dto.UpdateMemberProfileRequest;
import me.nawa.member.mapper.MemberMapper;
import me.nawa.member.service.MemberProfileService;
import me.nawa.settlement.service.SettlementCreationService;
import me.nawa.settlement.service.SettlementPaymentService;
import me.nawa.settlement.service.SettlementQueryService;
import me.nawa.wallet.dto.request.StripeIntentCreateRequest;
import me.nawa.wallet.dto.request.TopupPreviewRequest;
import me.nawa.wallet.dto.request.TransactionSearchCondition;
import me.nawa.wallet.dto.response.StripeIntentResponse;
import me.nawa.wallet.dto.response.StripeTopupStatusResponse;
import me.nawa.wallet.dto.response.StripeWebhookResponse;
import me.nawa.wallet.dto.response.TopupListResponse;
import me.nawa.wallet.dto.response.TopupMethodsResponse;
import me.nawa.wallet.dto.response.TopupPreviewResponse;
import me.nawa.wallet.dto.response.TransactionDetailResponse;
import me.nawa.wallet.dto.response.TransactionListResponse;
import me.nawa.wallet.dto.request.QrPaymentCreateRequest;
import me.nawa.wallet.dto.request.QrPaymentExecuteRequest;
import me.nawa.wallet.dto.request.QrPaymentPreviewRequest;
import me.nawa.wallet.dto.request.QrPaymentResolveRequest;
import me.nawa.wallet.dto.response.QrPaymentCreateResponse;
import me.nawa.wallet.dto.response.QrPaymentExecuteResponse;
import me.nawa.wallet.dto.response.QrPaymentPreviewResponse;
import me.nawa.wallet.dto.response.QrPaymentResolveResponse;
import me.nawa.wallet.dto.response.QrPaymentStatusResponse;
import me.nawa.wallet.service.QrPaymentService;
import me.nawa.wallet.service.TopupService;
import me.nawa.wallet.service.TransactionService;
import me.nawa.wallet.service.WalletService;
import me.nawa.explore.service.EventService;
import me.nawa.explore.service.ExploreItemLikeService;
import me.nawa.explore.service.PlaceService;
import me.nawa.journey.service.JourneyService;
import me.nawa.report.controller.ReportController;
import me.nawa.ingest.service.IngestService;
import me.nawa.ingest.service.IngestServiceImpl;
import me.nawa.auth.jwt.JwtTokenProvider;
import me.nawa.report.service.ReportService;
import me.nawa.review.service.ReviewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import springfox.documentation.spring.web.plugins.Docket;

import java.net.URI;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.mockito.Mockito.mock;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = {
        ServletConfig.class,
        SwaggerConfig.class,
        SwaggerConfigTest.AuthTestConfig.class,
        SwaggerConfigTest.WalletTestConfig.class,
        SwaggerConfigTest.QrPaymentTestConfig.class,
        SwaggerConfigTest.ExploreTestConfig.class,
        SwaggerConfigTest.JourneyTestConfig.class,
        SwaggerConfigTest.ReportTestConfig.class,
        SwaggerConfigTest.IngestTestConfig.class,
        SwaggerConfigTest.SettlementTestConfig.class,
        SwaggerConfigTest.TopupTestConfig.class,
        SwaggerConfigTest.TransactionTestConfig.class,
        SwaggerConfigTest.MemberTestConfig.class,
        SwaggerConfigTest.AppointmentTestConfig.class,
        SwaggerConfigTest.ReviewTestConfig.class,
        SwaggerConfigTest.MetricsTestConfig.class,
        SwaggerConfigTest.LoadTestConfig.class,
        SwaggerConfigTest.TestController.class
})
class SwaggerConfigTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void swaggerConfig_registersDocket() {
        assertNotNull(context.getBean(Docket.class));
    }

    @Test
    void servletConfig_registersReportController() {
        assertNotNull(context.getBean(ReportController.class));
    }

    @Test
    void webConfig_registersSwaggerConfig() {
        assertArrayEquals(
                new Class<?>[]{ServletConfig.class, SwaggerConfig.class},
                new WebConfig().getServletConfigClasses());
    }

    @Test
    void apiDocs_exposesOnlyApiPaths() throws Exception {
        String responseBody = mockMvc.perform(get("/v2/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode apiDocs = new ObjectMapper().readTree(responseBody);

        assertEquals("2.0", apiDocs.path("swagger").asText());
        assertTrue(apiDocs.path("paths").has("/api/swagger-test"));
        assertFalse(apiDocs.path("paths").has("/internal/swagger-test"));
    }

    @Test
    void swaggerResources_returnsResourceList() throws Exception {
        mockMvc.perform(get("/swagger-resources"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    void swaggerUi_returnsHtml() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
    }

    @Test
    void swaggerUiWebJar_returnsJavascript() throws Exception {
        byte[] responseBody = mockMvc.perform(
                        get("/webjars/springfox-swagger-ui/swagger-ui-bundle.js"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        assertTrue(responseBody.length > 0);
    }

    @Configuration
    static class AuthTestConfig {

        @Bean
        AuthTokenService authTokenService() {
            return new NoOpAuthTokenService();
        }

        @Bean
        AuthCookieManager authCookieManager() {
            return new AuthCookieManager(
                    "access_token",
                    "refresh_token",
                    "oauth_state",
                    false,
                    "Lax",
                    ""
            );
        }

        @Bean
        OAuthAuthorizationService oauthAuthorizationService() {
            return (provider, returnPath) -> new OAuthAuthorizationRedirect(
                    URI.create(
                            "https://accounts.google.com/o/oauth2/v2/auth"
                    ),
                    "browser-binding-value",
                    Instant.parse("2026-08-03T00:10:00Z")
            );
        }

        @Bean
        OAuthCallbackService oauthCallbackService() {
            return new OAuthCallbackService() {
                @Override
                public OAuthCallbackResult handle(
                        String provider,
                        String state,
                        String authorizationCode,
                        String authorizationError,
                        String browserBinding) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public URI createFailureRedirectUri(ErrorCode errorCode) {
                    return URI.create(
                            "http://localhost:5173/auth/callback"
                    );
                }
            };
        }
    }

    @Configuration
    static class WalletTestConfig {

        @Bean
        WalletService walletService() {
            return memberId -> {
                throw new UnsupportedOperationException();
            };
        }
    }

    @Configuration
    static class QrPaymentTestConfig {

        @Bean
        QrPaymentService qrPaymentService() {
            return new QrPaymentService() {
                @Override
                public QrPaymentCreateResponse createPaymentQr(
                    Long memberId, QrPaymentCreateRequest request
                ) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public java.util.List<QrPaymentCreateResponse> listActivePaymentQrs(
                    Long memberId
                ) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public QrPaymentResolveResponse resolvePaymentQr(
                    Long memberId, QrPaymentResolveRequest request
                ) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public QrPaymentPreviewResponse previewPayment(
                    Long memberId, QrPaymentPreviewRequest request
                ) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public QrPaymentExecuteResponse executePayment(
                    Long memberId, String idempotencyKey, QrPaymentExecuteRequest request
                ) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public QrPaymentStatusResponse getPaymentStatus(
                    Long memberId, Long transferId
                ) {
                    throw new UnsupportedOperationException();
                }
            };
        }
    }

    @Configuration
    static class TopupTestConfig {

        @Bean
        TopupService topupService() {
            return new TopupService() {
                @Override
                public TopupMethodsResponse getAvailableTopupMethods() {
                    throw new UnsupportedOperationException();
                }

                @Override
                public TopupPreviewResponse previewTopup(Long memberId, TopupPreviewRequest request) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public TopupListResponse getTopups(Long memberId, Long cursor, Integer size) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public StripeIntentResponse createStripeIntent(
                    long memberId, String idempotencyKey, StripeIntentCreateRequest request
                ) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public StripeTopupStatusResponse getStripeTopupStatus(long memberId, Long topupId) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public StripeWebhookResponse applyStripeWebhookEvent(String payload, String signatureHeader) {
                    throw new UnsupportedOperationException();
                }
            };
        }
    }

    @Configuration
    static class TransactionTestConfig {

        @Bean
        TransactionService transactionService() {
            return new TransactionService() {
                @Override
                public TransactionListResponse getTransactions(Long memberId, TransactionSearchCondition condition) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public TransactionDetailResponse getTransactionDetail(Long memberId, Long transferId) {
                    throw new UnsupportedOperationException();
                }
            };
        }
    }

    @Configuration
    static class MemberTestConfig {

        @Bean
        MemberProfileService memberProfileService() {
            return new MemberProfileService() {
                @Override
                public MemberProfileResponse getProfile(long memberId) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public MemberProfileResponse updateProfile(
                        long memberId, UpdateMemberProfileRequest request) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public MemberProfileResponse completeOnboarding(
                        long memberId, OnboardingProfileRequest request) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public MemberProfileResponse registerAsMerchant(
                        long memberId, MerchantRegisterRequest request) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public MemberAppointmentProfileResponse getAppointmentProfile(long memberId) {
                    throw new UnsupportedOperationException();
                }
            };
        }
    }

    private static final class NoOpAuthTokenService
            implements AuthTokenService {

        @Override
        public AuthTokens issueTokens(long memberId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AuthTokens refreshTokens(String currentRefreshToken) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void revokeRefreshToken(String refreshToken) {
        }
    }

    @Configuration
    static class ExploreTestConfig {

        @Bean
        EventService eventService() {
            return new EventService(null);
        }

        @Bean
        ExploreItemLikeService exploreItemLikeService() {
            return new ExploreItemLikeService(null);
        }

        @Bean
        PlaceService placeService() {
            return new PlaceService(null);
        }
    }

    @Configuration
    static class JourneyTestConfig {

        @Bean
        JourneyService journeyService() {
            return new JourneyService(null, null);
        }
    }

    @Configuration
    static class AppointmentTestConfig {

        @Bean
        AppointmentService appointmentService() {
            return new AppointmentService(null, null, null, null, null);
        }
    }

    @Configuration
    static class ReviewTestConfig {

        @Bean
        ReviewService reviewService() {
            return new ReviewService(null, null);
        }
    }

    @Configuration
    static class SettlementTestConfig {

        @Bean
        SettlementQueryService settlementQueryService() {
            return new SettlementQueryService() {
                @Override public me.nawa.settlement.dto.response.SettlementListResponse getSettlements(Long memberId) { throw new UnsupportedOperationException(); }
                @Override public java.util.List<me.nawa.settlement.dto.response.SettlementCandidateResponse> getCandidates(Long memberId) { throw new UnsupportedOperationException(); }
                @Override public me.nawa.settlement.dto.response.SettlementDetailResponse getSettlement(Long memberId, Long settlementId) { throw new UnsupportedOperationException(); }
            };
        }

        @Bean
        me.nawa.settlement.service.SettlementReceiptService settlementReceiptService() {
            return new me.nawa.settlement.service.SettlementReceiptService() {
                @Override public me.nawa.settlement.dto.response.SettlementReceiptUploadResponse upload(
                    Long memberId, String declaredContentType, byte[] content
                ) { throw new UnsupportedOperationException(); }
                @Override public void linkToSettlement(
                    Long memberId, Long settlementId, Long receiptId
                ) { throw new UnsupportedOperationException(); }
                @Override public me.nawa.common.storage.StoredReceipt getReceipt(
                    Long memberId, Long settlementId
                ) { throw new UnsupportedOperationException(); }
            };
        }

        @Bean
        me.nawa.settlement.service.SettlementReceiptOcrService settlementReceiptOcrService() {
            return new me.nawa.settlement.service.SettlementReceiptOcrService() {
                @Override public me.nawa.settlement.dto.response.SettlementReceiptOcrResponse recognize(
                    Long memberId, Long receiptId
                ) { throw new UnsupportedOperationException(); }
            };
        }

        @Bean
        SettlementCreationService settlementCreationService() {
            return new SettlementCreationService() {
                @Override public me.nawa.settlement.dto.response.SettlementCreateResponse createSettlement(
                    Long memberId, Long appointmentId, String idempotencyKey,
                    me.nawa.settlement.dto.request.CreateSettlementRequest request
                ) { throw new UnsupportedOperationException(); }
            };
        }

        @Bean
        SettlementPaymentService settlementPaymentService() {
            return new SettlementPaymentService() {
                @Override public me.nawa.settlement.dto.response.SettlementMutationResponse paySettlement(
                    Long memberId, Long settlementId, String idempotencyKey
                ) { throw new UnsupportedOperationException(); }
            };
        }
    }

    @Configuration
    static class ReportTestConfig {

        @Bean
        ReportService reportService() {
            return new ReportService(null);
        }
    }

    /**
     * 적재 컨트롤러가 서블릿 컨텍스트에 등록되므로 의존 빈이 있어야 합니다.
     * 이 테스트는 ServletConfig 만 올리기 때문에 RootConfig 의 서비스가 없습니다.
     */
    @Configuration
    static class IngestTestConfig {

        @Bean
        IngestService ingestService() {
            return new IngestServiceImpl(null, 1000000L);
        }

        @Bean
        JwtTokenProvider jwtTokenProvider() {
            return new JwtTokenProvider(
                    Base64.getEncoder().encodeToString(
                            "swagger-test-signing-key-at-least-32-bytes"
                                    .getBytes(StandardCharsets.UTF_8)),
                    "nawa",
                    900);
        }
    }

    /**
     * 지표 컨트롤러가 서블릿 컨텍스트에 등록되므로 레지스트리가 있어야 합니다.
     * 실제 레지스트리는 루트 컨텍스트의 MetricsConfig가 만들지만, 이 테스트는
     * ServletConfig 만 올립니다.
     */
    @Configuration
    static class MetricsTestConfig {

        @Bean
        PrometheusMeterRegistry meterRegistry() {
            return new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        }
    }

    /**
     * `-Ploadtest` 빌드에서는 부하 테스트 컨트롤러도 서블릿 컨텍스트에 스캔되므로
     * 그 의존성이 있어야 합니다. 플래그 없는 평소 빌드에서는 스캔될 클래스가 없어
     * 이 빈들이 쓰이지 않습니다.
     */
    @Configuration
    static class LoadTestConfig {

        @Bean
        MemberMapper memberMapper() {
            return mock(MemberMapper.class);
        }
    }

    @RestController
    static class TestController {

        @GetMapping("/api/swagger-test")
        String apiPath() {
            return "api";
        }

        @GetMapping("/internal/swagger-test")
        String internalPath() {
            return "internal";
        }
    }
}
