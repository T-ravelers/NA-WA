package me.nawa.report.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import me.nawa.auth.security.AuthenticatedMember;
import me.nawa.common.exception.GlobalExceptionHandler;
import me.nawa.report.domain.ReportComparisonScope;
import me.nawa.report.dto.response.ReportCategoryBreakdownResponse;
import me.nawa.report.dto.response.ReportComparisonCohortResponse;
import me.nawa.report.dto.response.ReportComparisonMemberResponse;
import me.nawa.report.dto.response.ReportComparisonRankResponse;
import me.nawa.report.dto.response.ReportComparisonResponse;
import me.nawa.report.service.ReportService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class ReportComparisonControllerTest {

    @Mock
    private ReportService reportService;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(new ReportController(reportService))
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
    void getComparison_defaultsToGroupScopeAndReturnsNumbersOnly() throws Exception {
        when(reportService.getComparison(1L, 100L, ReportComparisonScope.GROUP))
            .thenReturn(groupResponse());

        String responseBody = mockMvc.perform(get("/api/v1/reports/100/comparison"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);

        JsonNode data = objectMapper.readTree(responseBody).path("data");
        assertEquals("GROUP", data.path("scope").asText());
        assertEquals("LIVE", data.path("basis").asText());
        assertEquals(40000.0, data.path("me").path("totalSpent").asDouble());
        assertEquals("Mina", data.path("peers").get(0).path("displayName").asText());
        assertEquals(1, data.path("cohort").path("size").asInt());
        assertEquals("FOOD", data.path("ranks").get(0).path("category").asText());
        assertEquals(2, data.path("ranks").get(0).path("of").asInt());
    }

    @Test
    void getComparison_passesSimilarScopeThrough() throws Exception {
        when(reportService.getComparison(1L, 100L, ReportComparisonScope.SIMILAR))
            .thenReturn(groupResponse());

        mockMvc.perform(get("/api/v1/reports/100/comparison").param("scope", "SIMILAR"))
            .andExpect(status().isOk());
    }

    @Test
    void getComparison_rejectsUnknownScopeBeforeReachingTheService() throws Exception {
        String responseBody = mockMvc.perform(
                get("/api/v1/reports/100/comparison").param("scope", "BOGUS")
            )
            .andExpect(status().isBadRequest())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);

        JsonNode body = objectMapper.readTree(responseBody);
        assertFalse(body.path("success").asBoolean());
        assertTrue(responseBody.contains("COMMON-001"));
        verifyNoInteractions(reportService);
    }

    private static ReportComparisonResponse groupResponse() {
        ReportCategoryBreakdownResponse food = ReportCategoryBreakdownResponse.builder()
            .category("FOOD").amount(new BigDecimal("40000.0000"))
            .percentage(new BigDecimal("100.00")).build();
        return ReportComparisonResponse.builder()
            .scope("GROUP")
            .basis("LIVE")
            .me(ReportComparisonMemberResponse.builder()
                .memberId(1L).displayName("Me")
                .totalSpent(new BigDecimal("40000.0000"))
                .dailyAverage(new BigDecimal("8000.00"))
                .categoryBreakdown(List.of(food)).build())
            .peers(List.of(ReportComparisonMemberResponse.builder()
                .memberId(2L).displayName("Mina")
                .totalSpent(new BigDecimal("50000.0000"))
                .dailyAverage(new BigDecimal("10000.00"))
                .categoryBreakdown(List.of()).build()))
            .cohort(ReportComparisonCohortResponse.builder()
                .size(1).avgTotalSpent(new BigDecimal("50000.00"))
                .avgDailyAverage(new BigDecimal("10000.00"))
                .categoryBreakdown(List.of()).build())
            .ranks(List.of(ReportComparisonRankResponse.builder()
                .category("FOOD").rank(2).of(2).build()))
            .build();
    }
}
