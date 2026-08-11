package me.nawa.report.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import me.nawa.auth.security.AuthenticatedMember;
import me.nawa.common.exception.BusinessException;
import me.nawa.common.exception.GlobalExceptionHandler;
import me.nawa.report.dto.request.ReportCreateRequest;
import me.nawa.report.dto.response.ReportContentDayResponse;
import me.nawa.report.dto.response.ReportContentItemResponse;
import me.nawa.report.dto.response.ReportContentJourneyResponse;
import me.nawa.report.dto.response.ReportContentResponse;
import me.nawa.report.dto.response.ReportDetailResponse;
import me.nawa.report.dto.response.ReportExpenseCandidateResponse;
import me.nawa.report.dto.response.ReportSummaryResponse;
import me.nawa.report.exception.ReportErrorCode;
import me.nawa.report.service.ReportService;
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

@ExtendWith(MockitoExtension.class)
class ReportControllerTest {

    @Mock
    private ReportService reportService;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper()
        .findAndRegisterModules();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(new ReportController(reportService))
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
    void createReport_returns201WithTypedContent() throws Exception {
        when(reportService.createReport(
            eq(1L),
            eq(10L),
            any(ReportCreateRequest.class)
        )).thenReturn(detailResponse());

        String responseBody = mockMvc.perform(
                post("/api/v1/journeys/10/reports")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(
                        new ReportCreateRequest("ja")
                    ))
            )
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);

        JsonNode body = objectMapper.readTree(responseBody);
        assertTrue(body.path("success").asBoolean());
        assertEquals(100L, body.path("data").path("reportId").asLong());
        assertExactDetailDates(body.path("data"));
        assertEquals(
            "Example event",
            body.path("data").path("reportContent").path("days")
                .get(0).path("items").get(0).path("title").asText()
        );
    }

    @Test
    void getReports_returnsEmptyArray() throws Exception {
        when(reportService.getReports(1L)).thenReturn(List.of());

        String responseBody = mockMvc.perform(get("/api/v1/reports"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);

        JsonNode body = objectMapper.readTree(responseBody);
        assertTrue(body.path("success").asBoolean());
        assertTrue(body.path("data").isArray());
        assertEquals(0, body.path("data").size());
    }

    @Test
    void getExpenseCandidates_returnsOwnedCandidates() throws Exception {
        when(reportService.getExpenseCandidates(1L, 10L)).thenReturn(List.of(
            ReportExpenseCandidateResponse.builder()
                .transferId(99L)
                .amount(new java.math.BigDecimal("12000"))
                .occurredOn(LocalDate.of(2026, 8, 2))
                .category("FOOD")
                .memo("Lunch")
                .selected(false)
                .build()
        ));

        String responseBody = mockMvc.perform(
                get("/api/v1/journeys/10/report-expense-candidates")
            )
            .andExpect(status().isOk())
            .andReturn().getResponse()
            .getContentAsString(StandardCharsets.UTF_8);

        JsonNode candidate = objectMapper.readTree(responseBody).path("data").get(0);
        assertEquals(99L, candidate.path("transferId").asLong());
        assertEquals("FOOD", candidate.path("category").asText());
    }

    @Test
    void getReports_returnsSummaryWithoutContent() throws Exception {
        when(reportService.getReports(1L)).thenReturn(List.of(
            ReportSummaryResponse.builder()
                .reportId(100L)
                .tripId(10L)
                .title("Seoul Foodie Week")
                .startDate(LocalDate.of(2026, 8, 1))
                .endDate(LocalDate.of(2026, 8, 5))
                .generationStatus("COMPLETED")
                .locale("en")
                .generatedAt(LocalDateTime.of(2026, 8, 9, 12, 0))
                .createdAt(LocalDateTime.of(2026, 8, 9, 12, 0))
                .build()
        ));

        String responseBody = mockMvc.perform(get("/api/v1/reports"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);

        JsonNode report = objectMapper.readTree(responseBody)
            .path("data").get(0);
        assertEquals("COMPLETED", report.path("generationStatus").asText());
        assertEquals("2026-08-01", report.path("startDate").asText());
        assertEquals("2026-08-05", report.path("endDate").asText());
        assertEquals(
            "2026-08-09T12:00:00",
            report.path("generatedAt").asText()
        );
        assertEquals(
            "2026-08-09T12:00:00",
            report.path("createdAt").asText()
        );
        assertFalse(report.has("reportContent"));
    }

    @Test
    void getReport_returnsExactIsoDateStrings() throws Exception {
        when(reportService.getReport(1L, 100L)).thenReturn(detailResponse());

        String responseBody = mockMvc.perform(get("/api/v1/reports/100"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);

        JsonNode data = objectMapper.readTree(responseBody).path("data");
        assertExactDetailDates(data);
    }

    @Test
    void getReport_returns403ForAnotherMembersReport() throws Exception {
        when(reportService.getReport(1L, 100L)).thenThrow(
            new BusinessException(ReportErrorCode.REPORT_JOURNEY_FORBIDDEN)
        );

        String responseBody = mockMvc.perform(get("/api/v1/reports/100"))
            .andExpect(status().isForbidden())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);

        JsonNode body = objectMapper.readTree(responseBody);
        assertFalse(body.path("success").asBoolean());
        assertEquals(
            "REPORT-002",
            body.path("error").path("code").asText()
        );
    }

    @Test
    void getReport_returns404ForMissingReport() throws Exception {
        when(reportService.getReport(1L, 404L)).thenThrow(
            new BusinessException(ReportErrorCode.REPORT_NOT_FOUND)
        );

        String responseBody = mockMvc.perform(get("/api/v1/reports/404"))
            .andExpect(status().isNotFound())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);

        JsonNode body = objectMapper.readTree(responseBody);
        assertEquals(
            "REPORT-001",
            body.path("error").path("code").asText()
        );
    }

    private ReportDetailResponse detailResponse() {
        return ReportDetailResponse.builder()
            .reportId(100L)
            .tripId(10L)
            .title("Seoul Foodie Week")
            .startDate(LocalDate.of(2026, 8, 1))
            .endDate(LocalDate.of(2026, 8, 5))
            .generationStatus("COMPLETED")
            .locale("ja")
            .generatedAt(LocalDateTime.of(2026, 8, 9, 12, 0))
            .createdAt(LocalDateTime.of(2026, 8, 9, 12, 0))
            .reportContent(ReportContentResponse.builder()
                .journey(ReportContentJourneyResponse.builder()
                    .tripId(10L)
                    .title("Seoul Foodie Week")
                    .startDate(LocalDate.of(2026, 8, 1))
                    .endDate(LocalDate.of(2026, 8, 5))
                    .build())
                .days(List.of(ReportContentDayResponse.builder()
                    .visitDate(LocalDate.of(2026, 8, 2))
                    .items(List.of(ReportContentItemResponse.builder()
                        .tripItemId(10L)
                        .itemId(990001L)
                        .itemType("EVENT")
                        .title("Example event")
                        .status("ADDED")
                        .build()))
                    .build()))
                .build())
            .build();
    }

    private void assertExactDetailDates(JsonNode data) {
        assertEquals("2026-08-01", data.path("startDate").asText());
        assertEquals("2026-08-05", data.path("endDate").asText());
        assertEquals(
            "2026-08-09T12:00:00",
            data.path("generatedAt").asText()
        );
        assertEquals(
            "2026-08-09T12:00:00",
            data.path("createdAt").asText()
        );
        JsonNode content = data.path("reportContent");
        assertEquals(
            "2026-08-01",
            content.path("journey").path("startDate").asText()
        );
        assertEquals(
            "2026-08-05",
            content.path("journey").path("endDate").asText()
        );
        assertEquals(
            "2026-08-02",
            content.path("days").get(0).path("visitDate").asText()
        );
    }
}
