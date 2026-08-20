package me.nawa.appointment.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.nawa.appointment.domain.AppointmentStatus;
import me.nawa.appointment.dto.request.AppointmentCreateRequest;
import me.nawa.appointment.dto.request.AppointmentSearchRequest;
import me.nawa.appointment.dto.response.AppointmentDetailResponse;
import me.nawa.appointment.dto.response.AppointmentListResponse;
import me.nawa.appointment.dto.response.AppointmentMemberResponse;
import me.nawa.appointment.dto.response.AppointmentSummaryResponse;
import me.nawa.appointment.dto.response.MyOngoingAppointmentResponse;
import me.nawa.appointment.exception.AppointmentErrorCode;
import me.nawa.appointment.service.AppointmentService;
import me.nawa.auth.security.AuthenticatedMember;
import me.nawa.common.exception.GlobalExceptionHandler;
import me.nawa.common.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AppointmentControllerTest {
    @Mock
    private AppointmentService appointmentService;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AppointmentController(appointmentService))
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
    void searchAppointments_returnsPagedResponse() throws Exception {
        AppointmentSummaryResponse summary = summaryResponse();
        when(appointmentService.searchAppointments(
                any(AppointmentSearchRequest.class)
        )).thenReturn(new AppointmentListResponse(
                List.of(summary), 0, 20, 1, 1, false
        ));

        JsonNode body = performGet("/api/v1/appointments?itemId=100");

        assertTrue(body.path("success").asBoolean());
        assertEquals(10L,
                body.path("data").path("content").get(0)
                        .path("appointmentId").asLong());
        assertEquals("2026-08-21T18:30:00",
                body.path("data").path("content").get(0)
                        .path("activityStartAt").asText());
    }

    @Test
    void getAppointment_returnsMembersWithIsHostProperty() throws Exception {
        when(appointmentService.getAppointment(1L, 10L))
                .thenReturn(detailResponse());

        JsonNode body = performGet("/api/v1/appointments/10");

        assertTrue(body.path("data").path("members").get(0)
                .path("isHost").asBoolean());
    }

    @Test
    void getMyOngoingAppointments_returnsResponseList() throws Exception {
        when(appointmentService.getMyOngoingAppointments(1L, "ONGOING"))
                .thenReturn(List.of(myOngoingAppointmentResponse()));

        JsonNode body = performGet("/api/v1/appointments/me");

        assertTrue(body.path("success").asBoolean());
        assertEquals(10L,
                body.path("data").get(0).path("appointmentId").asLong());
        assertEquals("Seoul Night Tour",
                body.path("data").get(0).path("appointmentName").asText());
        assertEquals(100L, body.path("data").get(0).path("itemId").asLong());
        assertEquals("EVENT", body.path("data").get(0).path("itemType").asText());
        assertEquals("IN_PROGRESS",
                body.path("data").get(0).path("appointmentStatus").asText());
    }

    @Test
    void getMyOngoingAppointments_passesAllScopeThrough() throws Exception {
        when(appointmentService.getMyOngoingAppointments(1L, "ALL"))
                .thenReturn(List.of());

        JsonNode body = performGet("/api/v1/appointments/me?scope=ALL");

        assertTrue(body.path("success").asBoolean());
        verify(appointmentService).getMyOngoingAppointments(1L, "ALL");
    }

    @Test
    void createAppointment_requiresPaymentIntegration() throws Exception {
        when(appointmentService.createAppointment(
                eq(1L),
                any(AppointmentCreateRequest.class)
        )).thenThrow(new BusinessException(
                AppointmentErrorCode.PAYMENT_INTEGRATION_REQUIRED
        ));

        String request = "{"
                + "\"itemId\":100,"
                + "\"itemType\":\"EVENT\","
                + "\"languageCode\":\"en\","
                + "\"appointmentName\":\"Seongsu Tour\","
                + "\"maxMembers\":5,"
                + "\"depositAmount\":10000,"
                + "\"meetingPlace\":\"Seongsu\","
                + "\"joinDeadline\":\"2026-08-20T18:00:00\","
                + "\"activityStartAt\":\"2026-08-21T18:30:00\","
                + "\"activityEndAt\":\"2026-08-21T22:00:00\"}"
                ;

        String responseBody = mockMvc.perform(post("/api/v1/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isConflict())
                .andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        assertEquals("APPOINTMENT-008", objectMapper.readTree(responseBody)
                .path("error").path("code").asText());
    }

    @Test
    void createAppointment_serviceValidationFailure_returnsBadRequest()
            throws Exception {
        when(appointmentService.createAppointment(
                eq(1L),
                any(AppointmentCreateRequest.class)
        )).thenThrow(new me.nawa.common.exception.BusinessException(
                me.nawa.common.exception.CommonErrorCode.INVALID_INPUT
        ));

        String request = "{"
                + "\"itemId\":100,"
                + "\"languageCode\":\"en\","
                + "\"appointmentName\":\"Seongsu Tour\","
                + "\"maxMembers\":5,"
                + "\"depositAmount\":10000,"
                + "\"meetingPlace\":\"Seongsu\","
                + "\"joinDeadline\":\"2026-08-20T18:00:00\","
                + "\"activityStartAt\":\"2026-08-21T18:30:00\","
                + "\"activityEndAt\":\"2026-08-21T22:00:00\"}"
                ;

        mockMvc.perform(post("/api/v1/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest());
    }

    private JsonNode performGet(String path) throws Exception {
        String responseBody = mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(responseBody);
    }

    private static MyOngoingAppointmentResponse myOngoingAppointmentResponse() {
        return new MyOngoingAppointmentResponse(
                10L,
                "Seoul Night Tour",
                5L,
                "Gwanghwamun Square",
                LocalDateTime.of(2026, 8, 21, 18, 30),
                LocalDateTime.of(2026, 8, 21, 22, 0),
                100L,
                "EVENT",
                "IN_PROGRESS"
        );
    }

    private static AppointmentSummaryResponse summaryResponse() {
        return AppointmentSummaryResponse.builder()
                .appointmentId(10L)
                .itemId(100L)
                .itemType("EVENT")
                .appointmentName("Seongsu Tour")
                .languageCode("en")
                .maxMembers(5)
                .currentMemberCount(3)
                .depositAmount(BigDecimal.valueOf(10_000))
                .appointmentStatus(AppointmentStatus.RECRUITING)
                .meetingPlace("Seongsu")
                .activityStartAt(LocalDateTime.of(2026, 8, 21, 18, 30))
                .activityEndAt(LocalDateTime.of(2026, 8, 21, 22, 0))
                .joinDeadline(LocalDateTime.of(2026, 8, 20, 18, 0))
                .hostDisplayName("Host")
                .build();
    }

    private static AppointmentDetailResponse detailResponse() {
        AppointmentMemberResponse host = AppointmentMemberResponse.builder()
                .appointmentMemberId(20L)
                .memberId(1L)
                .displayName("Host")
                .isHost(true)
                .build();
        AppointmentSummaryResponse summary = summaryResponse();
        return AppointmentDetailResponse.builder()
                .appointmentId(summary.getAppointmentId())
                .itemId(summary.getItemId())
                .itemType(summary.getItemType())
                .appointmentName(summary.getAppointmentName())
                .languageCode(summary.getLanguageCode())
                .maxMembers(summary.getMaxMembers())
                .currentMemberCount(summary.getCurrentMemberCount())
                .depositAmount(summary.getDepositAmount())
                .appointmentStatus(summary.getAppointmentStatus())
                .meetingPlace(summary.getMeetingPlace())
                .activityStartAt(summary.getActivityStartAt())
                .activityEndAt(summary.getActivityEndAt())
                .joinDeadline(summary.getJoinDeadline())
                .hostDisplayName(summary.getHostDisplayName())
                .members(List.of(host))
                .build();
    }
}
