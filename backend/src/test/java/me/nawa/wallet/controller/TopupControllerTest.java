package me.nawa.wallet.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import me.nawa.auth.security.AuthenticatedMember;
import me.nawa.common.exception.BusinessException;
import me.nawa.common.exception.GlobalExceptionHandler;
import me.nawa.wallet.dto.request.TopupPreviewRequest;
import me.nawa.wallet.dto.response.TopupMethodResponse;
import me.nawa.wallet.dto.response.TopupMethodsResponse;
import me.nawa.wallet.dto.response.TopupPreviewResponse;
import me.nawa.wallet.exception.WalletErrorCode;
import me.nawa.wallet.service.TopupService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class TopupControllerTest {

    private static final Authentication AUTHENTICATION = new UsernamePasswordAuthenticationToken(
        new AuthenticatedMember(1L), null, Collections.emptyList()
    );

    @Mock
    private TopupService topupService;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        TopupController topupController = new TopupController(topupService);
        mockMvc = MockMvcBuilders.standaloneSetup(topupController)
            .setControllerAdvice(new GlobalExceptionHandler())
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .build();

        SecurityContextHolder.getContext().setAuthentication(AUTHENTICATION);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getTopupMethods_returns200WithMethodsData() throws Exception {
        TopupMethodsResponse response = new TopupMethodsResponse(
            List.of(new TopupMethodResponse("STRIPE_CARD", "해외 카드 충전", true, true)),
            "테스트 환경에서는 실제 결제가 발생하지 않습니다."
        );
        when(topupService.getAvailableTopupMethods()).thenReturn(response);

        String responseBody = mockMvc.perform(get("/api/v1/topups/methods"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);

        JsonNode body = objectMapper.readTree(responseBody);

        assertTrue(body.path("success").asBoolean());
        assertEquals(1, body.path("data").path("methods").size());
        assertEquals("STRIPE_CARD", body.path("data").path("methods").get(0).path("type").asText());
    }

    @Test
    void previewTopup_returns200WithPreviewData_whenAuthenticated() throws Exception {
        TopupPreviewRequest request = new TopupPreviewRequest(BigDecimal.valueOf(10000), "STRIPE_CARD", "KRW");
        TopupPreviewResponse response = new TopupPreviewResponse(
            BigDecimal.valueOf(10000), BigDecimal.ZERO, "KRW",
            BigDecimal.valueOf(50000), BigDecimal.valueOf(60000), null
        );
        when(topupService.previewTopup(eq(1L), any(TopupPreviewRequest.class))).thenReturn(response);

        String responseBody = mockMvc.perform(post("/api/v1/topups/preview")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);

        JsonNode body = objectMapper.readTree(responseBody);

        assertTrue(body.path("success").asBoolean());
        assertEquals(0, BigDecimal.valueOf(10000).compareTo(body.path("data").path("amount").decimalValue()));
        assertEquals("KRW", body.path("data").path("currency").asText());
        assertEquals(
            0, BigDecimal.valueOf(60000).compareTo(body.path("data").path("expectedSandboxBalance").decimalValue())
        );
    }

    @Test
    void previewTopup_returns404WithErrorBody_whenWalletNotFound() throws Exception {
        TopupPreviewRequest request = new TopupPreviewRequest(BigDecimal.valueOf(10000), "STRIPE_CARD", "KRW");
        when(topupService.previewTopup(eq(1L), any(TopupPreviewRequest.class)))
            .thenThrow(new BusinessException(WalletErrorCode.WALLET_NOT_FOUND));

        mockMvc.perform(post("/api/v1/topups/preview")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isNotFound());
    }

    @Test
    void previewTopup_returns400WithErrorBody_whenMethodNotSupported() throws Exception {
        TopupPreviewRequest request = new TopupPreviewRequest(BigDecimal.valueOf(10000), "UNKNOWN", "KRW");
        when(topupService.previewTopup(eq(1L), any(TopupPreviewRequest.class)))
            .thenThrow(new BusinessException(WalletErrorCode.TOPUP_METHOD_NOT_SUPPORTED));

        String responseBody = mockMvc.perform(post("/api/v1/topups/preview")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);

        JsonNode body = objectMapper.readTree(responseBody);

        assertEquals("WALLET-002", body.path("error").path("code").asText());
    }
}
