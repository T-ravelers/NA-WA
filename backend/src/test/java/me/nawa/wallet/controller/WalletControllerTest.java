package me.nawa.wallet.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import me.nawa.common.exception.BusinessException;
import me.nawa.common.exception.GlobalExceptionHandler;
import me.nawa.wallet.dto.response.TransactionSummaryResponse;
import me.nawa.wallet.dto.response.WalletHomeResponse;
import me.nawa.wallet.exception.WalletErrorCode;
import me.nawa.wallet.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class WalletControllerTest {

    @Mock
    private WalletService walletService;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        WalletController walletController = new WalletController(walletService);
        mockMvc = MockMvcBuilders.standaloneSetup(walletController)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    void getWalletHome_returns200WithWalletData_whenHeaderPresent() throws Exception {
        WalletHomeResponse response = WalletHomeResponse.of(
            BigDecimal.valueOf(50000),
            "ACTIVE",
            List.of(new TransactionSummaryResponse(
                10L, "TOPUP", "CREDIT", BigDecimal.valueOf(10000), BigDecimal.valueOf(50000), null
            ))
        );
        when(walletService.getWalletHome(1L)).thenReturn(response);

        String responseBody = mockMvc.perform(get("/api/v1/wallet").header("X-Member-Id", 1L))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);

        JsonNode body = objectMapper.readTree(responseBody);

        assertTrue(body.path("success").asBoolean());
        assertEquals(0, BigDecimal.valueOf(50000).compareTo(body.path("data").path("balance").decimalValue()));
        assertEquals("ACTIVE", body.path("data").path("availabilityStatus").asText());
        assertEquals(1, body.path("data").path("recentTransactions").size());
        assertEquals(10, body.path("data").path("recentTransactions").get(0).path("transferId").asInt());
    }

    @Test
    void getWalletHome_returns404WithErrorBody_whenWalletNotFound() throws Exception {
        when(walletService.getWalletHome(1L))
            .thenThrow(new BusinessException(WalletErrorCode.WALLET_NOT_FOUND));

        String responseBody = mockMvc.perform(get("/api/v1/wallet").header("X-Member-Id", 1L))
            .andExpect(status().isNotFound())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);

        JsonNode body = objectMapper.readTree(responseBody);

        assertFalse(body.path("success").asBoolean());
        assertEquals("WALLET-001", body.path("error").path("code").asText());
        assertEquals("지갑 정보를 찾을 수 없습니다.", body.path("error").path("message").asText());
    }

    @Test
    void getWalletHome_returns500_whenMemberIdHeaderMissing() throws Exception {
        String responseBody = mockMvc.perform(get("/api/v1/wallet"))
            .andExpect(status().isInternalServerError())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);

        JsonNode body = objectMapper.readTree(responseBody);

        assertEquals("COMMON-999", body.path("error").path("code").asText());
    }
}
