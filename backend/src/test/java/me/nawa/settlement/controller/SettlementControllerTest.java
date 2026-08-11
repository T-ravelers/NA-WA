package me.nawa.settlement.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import me.nawa.auth.security.AuthenticatedMember;
import me.nawa.common.exception.GlobalExceptionHandler;
import me.nawa.settlement.dto.request.CreateSettlementRequest;
import me.nawa.settlement.dto.request.GameConsentRequest;
import me.nawa.settlement.dto.request.ReceiptAllocationUpdateRequest;
import me.nawa.settlement.dto.request.ReceiptItemUpdateRequest;
import me.nawa.settlement.dto.response.SettlementCreateResponse;
import me.nawa.settlement.service.ReceiptAnalysisService;
import me.nawa.settlement.service.SettlementCreationService;
import me.nawa.settlement.service.SettlementGameService;
import me.nawa.settlement.service.SettlementPaymentService;
import me.nawa.settlement.service.SettlementQueryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class SettlementControllerTest {

    @Mock
    private SettlementQueryService settlementQueryService;
    @Mock
    private SettlementCreationService settlementCreationService;
    @Mock
    private SettlementPaymentService settlementPaymentService;
    @Mock
    private ReceiptAnalysisService receiptAnalysisService;
    @Mock
    private SettlementGameService settlementGameService;

    @Mock
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper()
        .findAndRegisterModules();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                new SettlementController(settlementQueryService, settlementCreationService, settlementPaymentService),
                new SettlementReceiptAnalysisController(receiptAnalysisService),
                new SettlementGameController(settlementGameService)
            )
            .setControllerAdvice(new GlobalExceptionHandler())
            .setCustomArgumentResolvers(
                new AuthenticationPrincipalArgumentResolver()
            )
            .build();

        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(
                new AuthenticatedMember(1L),
                null,
                Collections.emptyList()
            )
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createSettlement_returns201WithServerGeneratedId() throws Exception {
        when(settlementCreationService.createSettlement(
            eq(1L), any(CreateSettlementRequest.class)
        )).thenReturn(SettlementCreateResponse.builder().id(69L).build());

        String responseBody = mockMvc.perform(post("/api/v1/settlements")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sourceTransferId\":20,\"type\":\"EQUAL\",\"participantIds\":[2,3]}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);

        JsonNode body = objectMapper.readTree(responseBody);
        assertTrue(body.path("success").asBoolean());
        assertEquals(69L, body.path("data").path("id").asLong());
    }

    @Test
    void settlementEndpoints_acceptFrontendContractPaths() throws Exception {
        MockMultipartFile receipt = new MockMultipartFile(
            "file", "receipt.jpg", MediaType.IMAGE_JPEG_VALUE, "receipt".getBytes()
        );

        mockMvc.perform(get("/api/v1/settlements"))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/settlements/candidates"))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/settlements/69"))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/settlements/69/payments"))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/settlements/69/cancel"))
            .andExpect(status().isOk());
        mockMvc.perform(multipart("/api/v1/settlements/receipt-analyses")
                .file(receipt)
                .param("sourceTransferId", "20"))
            .andExpect(status().isCreated());
        mockMvc.perform(put("/api/v1/settlements/receipt-analyses/10/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ReceiptItemUpdateRequest())))
            .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/settlements/receipt-analyses/10/allocations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ReceiptAllocationUpdateRequest())))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/settlements/69/game/consents")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new GameConsentRequest())))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/settlements/69/game"))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/settlements/69/game/start"))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/settlements/69/game/result"))
            .andExpect(status().isOk());
    }
}
