package me.nawa.settlement.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import me.nawa.auth.security.AuthenticatedMember;
import me.nawa.common.exception.GlobalExceptionHandler;
import me.nawa.common.storage.StoredReceipt;
import me.nawa.settlement.dto.response.SettlementReceiptUploadResponse;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SettlementReceiptControllerTest {

    private static final byte[] PNG_BYTES = {
        (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x01, 0x02
    };

    @Mock
    private SettlementReceiptService settlementReceiptService;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(new SettlementReceiptController(settlementReceiptService))
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
}
