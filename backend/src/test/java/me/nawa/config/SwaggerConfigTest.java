package me.nawa.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.nawa.auth.cookie.AuthCookieManager;
import me.nawa.auth.oauth.authorization.OAuthAuthorizationService;
import me.nawa.auth.oauth.callback.OAuthCallbackResult;
import me.nawa.auth.oauth.callback.OAuthCallbackService;
import me.nawa.auth.token.AuthTokenService;
import me.nawa.auth.token.AuthTokens;
import me.nawa.common.exception.ErrorCode;
import me.nawa.member.dto.MemberProfileResponse;
import me.nawa.member.dto.UpdateMemberProfileRequest;
import me.nawa.member.service.MemberProfileService;
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
import me.nawa.wallet.service.TopupService;
import me.nawa.wallet.service.TransactionService;
import me.nawa.wallet.service.WalletService;
import me.nawa.explore.service.EventService;
import me.nawa.journey.service.JourneyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
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
        SwaggerConfigTest.ExploreTestConfig.class,
        SwaggerConfigTest.JourneyTestConfig.class,
        SwaggerConfigTest.TopupTestConfig.class,
        SwaggerConfigTest.TransactionTestConfig.class,
        SwaggerConfigTest.MemberTestConfig.class,
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
                    false,
                    "Lax",
                    ""
            );
        }

        @Bean
        OAuthAuthorizationService oauthAuthorizationService() {
            return (provider, returnPath) -> URI.create(
                    "https://accounts.google.com/o/oauth2/v2/auth"
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
                        String authorizationError) {
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
    }

    @Configuration
    static class JourneyTestConfig {

        @Bean
        JourneyService journeyService() {
            return new JourneyService(null);
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
