package me.nawa.settlement.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import me.nawa.auth.security.AuthenticatedMember;
import me.nawa.common.exception.GlobalExceptionHandler;
import me.nawa.settlement.dto.request.CreateSettlementRequest;
import me.nawa.settlement.dto.response.SettlementCreateResponse;
import me.nawa.settlement.dto.response.SettlementCandidateResponse;
import me.nawa.settlement.dto.response.SettlementCollectionParticipantResponse;
import me.nawa.settlement.dto.response.SettlementCollectionResponse;
import me.nawa.settlement.dto.response.SettlementDetailResponse;
import me.nawa.settlement.dto.response.SettlementParticipantResponse;
import me.nawa.settlement.dto.response.SettlementViewerResponse;
import me.nawa.settlement.domain.SettlementAllowedAction;
import me.nawa.settlement.service.SettlementCreationService;
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
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper()
        .findAndRegisterModules();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                new SettlementController(settlementQueryService, settlementCreationService, settlementPaymentService)
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
    void createSettlement_createsDraftAtAppointmentEndpointWithIdempotencyKey() throws Exception {
        when(settlementCreationService.createSettlement(
            eq(1L), eq(7L), eq("settlement-create-1"), any(CreateSettlementRequest.class)
        )).thenReturn(SettlementCreateResponse.builder().id(69L).build());

        String responseBody = mockMvc.perform(post("/api/v1/appointments/7/settlements")
                .header("Idempotency-Key", "settlement-create-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sourceTransferId\":20,\"type\":\"EQUAL\",\"participantAppointmentMemberIds\":[71,72]}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);

        JsonNode body = objectMapper.readTree(responseBody);
        assertTrue(body.path("success").asBoolean());
        assertEquals(69L, body.path("data").path("id").asLong());
        verify(settlementCreationService).createSettlement(
            eq(1L), eq(7L), eq("settlement-create-1"), any(CreateSettlementRequest.class)
        );
    }

    @Test
    void createSettlement_withoutIdempotencyKey_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/appointments/7/settlements")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sourceTransferId\":20,\"type\":\"EQUAL\",\"participantAppointmentMemberIds\":[71,72]}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void getCandidates_serializesCreationContextWithPayerAmongParticipants() throws Exception {
        when(settlementQueryService.getCandidates(1L)).thenReturn(List.of(
            SettlementCandidateResponse.builder()
                .transferId(20L)
                .appointmentId(7L)
                .payerAppointmentMemberId(71L)
                .participants(List.of(
                    SettlementParticipantResponse.builder().id(71L).name("Payer").initials("P").build(),
                    SettlementParticipantResponse.builder().id(72L).name("Participant").initials("P").build()
                ))
                .build()
        ));

        String responseBody = mockMvc.perform(get("/api/v1/settlements/candidates"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);

        JsonNode candidate = objectMapper.readTree(responseBody).path("data").get(0);
        assertEquals(20L, candidate.path("transferId").asLong());
        assertEquals(7L, candidate.path("appointmentId").asLong());
        assertEquals(71L, candidate.path("payerAppointmentMemberId").asLong());
        assertTrue(candidate.path("participants").findValuesAsText("id")
            .contains(candidate.path("payerAppointmentMemberId").asText()));
    }

    @Test
    void getSettlement_serializesViewerAllowedActionsAsEnumNames() throws Exception {
        when(settlementQueryService.getSettlement(1L, 69L)).thenReturn(
            SettlementDetailResponse.builder()
                .id(69L)
                .type("EQUAL")
                .totalAmount(new BigDecimal("100"))
                .status("REQUESTED")
                .viewerItems(List.of())
                .viewer(SettlementViewerResponse.builder()
                    .role("PARTICIPANT")
                    .shareAmount(new BigDecimal("50"))
                    .payableAmount(new BigDecimal("50"))
                    .requestStatus("PENDING")
                    .allowedActions(List.of(SettlementAllowedAction.PAY))
                    .build())
                .build()
        );

        String responseBody = mockMvc.perform(get("/api/v1/settlements/69"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);

        JsonNode data = objectMapper.readTree(responseBody).path("data");
        JsonNode viewer = data.path("viewer");
        assertEquals("PARTICIPANT", viewer.path("role").asText());
        assertEquals(50, viewer.path("payableAmount").asInt());
        assertEquals("PAY", viewer.path("allowedActions").path(0).asText());
        // 낼 사람에게는 다른 사람이 냈는지가 아예 오지 않는다.
        assertTrue(data.path("collection").isNull());
    }

    /**
     * 돈을 받을 사람만 "누가 냈나"를 본다.
     *
     * 인원수는 청구한 상대만 센다. 원결제자 본인은 자기에게 돈을 보내지 않으므로 서비스가
     * 넘겨준 목록에도 없고, 그래서 둘 다 냈을 때 2명 중 2명이 된다.
     */
    @Test
    void getSettlement_creator_serializesCollectionWithPaidCount() throws Exception {
        when(settlementQueryService.getSettlement(1L, 69L)).thenReturn(
            SettlementDetailResponse.builder()
                .id(69L)
                .type("EQUAL")
                .totalAmount(new BigDecimal("150"))
                .status("REQUESTED")
                .viewerItems(List.of())
                .viewer(SettlementViewerResponse.builder()
                    .role("CREATOR")
                    .shareAmount(new BigDecimal("50"))
                    .payableAmount(BigDecimal.ZERO)
                    .requestStatus("NOT_REQUESTED")
                    .allowedActions(List.of())
                    .build())
                .collection(SettlementCollectionResponse.builder()
                    .totalCount(2)
                    .paidCount(1)
                    // 안 낸 사람이 먼저다. 조회가 정한 순서가 응답에 그대로 실린다.
                    .participants(List.of(
                        SettlementCollectionParticipantResponse.builder()
                            .id(73L).name("Chan").initials("C")
                            .shareAmount(new BigDecimal("50"))
                            .requestStatus("PENDING").build(),
                        SettlementCollectionParticipantResponse.builder()
                            .id(72L).name("Bora").initials("B")
                            .shareAmount(new BigDecimal("50"))
                            .requestStatus("PAID").build()
                    ))
                    .build())
                .build()
        );

        String responseBody = mockMvc.perform(get("/api/v1/settlements/69"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);

        JsonNode collection = objectMapper.readTree(responseBody).path("data").path("collection");
        assertEquals(2, collection.path("totalCount").asInt());
        assertEquals(1, collection.path("paidCount").asInt());
        assertEquals(73, collection.path("participants").path(0).path("id").asInt());
        assertEquals("Chan", collection.path("participants").path(0).path("name").asText());
        assertEquals(50, collection.path("participants").path(0).path("shareAmount").asInt());
        assertEquals("PENDING", collection.path("participants").path(0).path("requestStatus").asText());
        assertEquals("PAID", collection.path("participants").path(1).path("requestStatus").asText());
    }

    /**
     * 이 컨트롤러가 다루는 경로는 다섯 개뿐이다. 영수증 업로드·조회는
     * SettlementReceiptController가 따로 맡으므로 여기에 늘어나지 않는다.
     */
    @Test
    void settlementController_exposesOnlyFiveSupportedPaths() throws Exception {
        mockMvc.perform(get("/api/v1/settlements"))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/settlements/candidates"))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/settlements/69"))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/settlements/69/members/me/pay")
                .header("Idempotency-Key", "settlement-pay-1"))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/settlements/69/request")
                .header("Idempotency-Key", "settlement-request-1"))
            .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/settlements/69/cancel"))
            .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/settlements/receipt-analyses"))
            .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(post("/api/v1/settlements/69/game/start"))
            .andExpect(status().isNotFound());
    }

    @Test
    void controller_declaresNoRemovedLifecycleMethods() {
        List<String> methodNames = Arrays.stream(SettlementController.class.getDeclaredMethods())
            .map(method -> method.getName())
            .toList();

        assertFalse(methodNames.contains("requestSettlement"));
        assertFalse(methodNames.contains("cancelSettlement"));
    }

    @Test
    void lifecycleMutation_withoutIdempotencyKey_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/settlements/69/members/me/pay"))
            .andExpect(status().isBadRequest());
    }
}
