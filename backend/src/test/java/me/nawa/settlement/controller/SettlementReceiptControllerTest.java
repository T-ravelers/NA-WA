package me.nawa.settlement.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import me.nawa.auth.security.AuthenticatedMember;
import me.nawa.common.exception.BusinessException;
import me.nawa.common.exception.GlobalExceptionHandler;
import me.nawa.common.storage.StoredReceipt;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import me.nawa.settlement.dto.response.SettlementReceiptOcrItemResponse;
import me.nawa.settlement.dto.response.SettlementReceiptOcrResponse;
import me.nawa.settlement.dto.response.SettlementReceiptUploadResponse;
import me.nawa.settlement.exception.SettlementErrorCode;
import me.nawa.settlement.service.SettlementReceiptOcrService;
import me.nawa.settlement.service.SettlementReceiptService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SettlementReceiptControllerTest {

    private static final byte[] PNG_BYTES = {
        (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x01, 0x02
    };

    @Mock
    private SettlementReceiptService settlementReceiptService;

    @Mock
    private SettlementReceiptOcrService settlementReceiptOcrService;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(new SettlementReceiptController(
                settlementReceiptService, settlementReceiptOcrService
            ))
            .setControllerAdvice(new GlobalExceptionHandler())
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .build();

        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(
                new AuthenticatedMember(1L), null, Collections.emptyList()
            )
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void uploadReceipt_multipartImage_returnsReceiptId() throws Exception {
        when(settlementReceiptService.upload(eq(1L), eq("image/png"), any()))
            .thenReturn(SettlementReceiptUploadResponse.builder().receiptId(12L).build());

        String responseBody = mockMvc.perform(multipart("/api/v1/settlement-receipts")
                .file(new MockMultipartFile("file", "receipt.png", "image/png", PNG_BYTES)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        JsonNode body = objectMapper.readTree(responseBody);
        assertEquals(12, body.path("data").path("receiptId").asInt());
    }

    @Test
    void uploadReceipt_emptyFile_returnsBadRequest() throws Exception {
        mockMvc.perform(multipart("/api/v1/settlement-receipts")
                .file(new MockMultipartFile("file", "receipt.png", "image/png", new byte[0])))
            .andExpect(status().isBadRequest());
    }

    @Test
    void recognizeReceipt_draftReceipt_returnsItemDraft() throws Exception {
        when(settlementReceiptOcrService.recognize(1L, 12L)).thenReturn(
            SettlementReceiptOcrResponse.builder()
                .items(List.of(SettlementReceiptOcrItemResponse.builder()
                    .name("아메리카노")
                    .unitPrice(new BigDecimal("4500"))
                    .quantity(new BigDecimal("2"))
                    .build()))
                .recognizedTotal(new BigDecimal("9000"))
                .build()
        );

        // 품목 이름이 한글이라 응답을 UTF-8로 읽는다. 그냥 읽으면 서블릿 기본 인코딩이
        // 적용돼 글자가 깨진다.
        String responseBody = mockMvc.perform(post("/api/v1/settlement-receipts/12/ocr"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);

        JsonNode item = objectMapper.readTree(responseBody).path("data").path("items").path(0);
        assertEquals("아메리카노", item.path("name").asText());
        assertEquals(4500, item.path("unitPrice").asInt());
        assertEquals(2, item.path("quantity").asInt());
    }

    /** 남의 초안이거나 이미 정산에 붙은 사진이면 존재 여부까지 감춰 404로 답한다. */
    @Test
    void recognizeReceipt_notOwnDraft_returnsNotFound() throws Exception {
        when(settlementReceiptOcrService.recognize(1L, 12L)).thenThrow(
            new BusinessException(SettlementErrorCode.SETTLEMENT_RECEIPT_NOT_FOUND)
        );

        String responseBody = mockMvc.perform(post("/api/v1/settlement-receipts/12/ocr"))
            .andExpect(status().isNotFound())
            .andReturn()
            .getResponse()
            .getContentAsString();

        JsonNode body = objectMapper.readTree(responseBody);
        assertEquals("SETTLEMENT-018", body.path("error").path("code").asText());
    }

    @Test
    void getReceipt_participant_returnsImageBytesWithoutEnvelope() throws Exception {
        when(settlementReceiptService.getReceipt(1L, 69L))
            .thenReturn(new StoredReceipt(PNG_BYTES, "image/png"));

        MockHttpServletResponse response = mockMvc.perform(
                get("/api/v1/settlements/69/receipt"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse();

        assertEquals("image/png", response.getHeader("Content-Type"));
        // 사용자가 올린 파일이라 브라우저가 형식을 재해석하지 못하게 막아야 한다.
        assertEquals("nosniff", response.getHeader("X-Content-Type-Options"));
        assertEquals("private, no-store", response.getHeader("Cache-Control"));
        assertEquals(PNG_BYTES.length, response.getContentAsByteArray().length);
    }

    /** 보관 기한이 지난 사진은 "없음"(404)이 아니라 "사라짐"(410)으로 구분해 내려간다. */
    @Test
    void getReceipt_expired_returnsGoneWithEnvelope() throws Exception {
        when(settlementReceiptService.getReceipt(1L, 69L)).thenThrow(
            new BusinessException(SettlementErrorCode.SETTLEMENT_RECEIPT_EXPIRED)
        );

        String responseBody = mockMvc.perform(get("/api/v1/settlements/69/receipt"))
            .andExpect(status().isGone())
            .andReturn()
            .getResponse()
            .getContentAsString();

        JsonNode body = objectMapper.readTree(responseBody);
        assertEquals("SETTLEMENT-020", body.path("error").path("code").asText());
    }
}
