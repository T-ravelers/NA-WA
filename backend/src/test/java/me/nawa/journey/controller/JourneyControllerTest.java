package me.nawa.journey.controller;

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
import java.util.Collections;
import java.util.List;
import me.nawa.auth.security.AuthenticatedMember;
import me.nawa.common.exception.BusinessException;
import me.nawa.common.exception.GlobalExceptionHandler;
import me.nawa.journey.dto.request.JourneyCreateRequest;
import me.nawa.journey.dto.response.JourneyResponse;
import me.nawa.journey.dto.response.JourneyTimelineDayResponse;
import me.nawa.journey.dto.response.JourneyTimelineEventDetailResponse;
import me.nawa.journey.dto.response.JourneyTimelineExploreItemResponse;
import me.nawa.journey.dto.response.JourneyTimelineItemResponse;
import me.nawa.journey.dto.response.JourneyTimelineResponse;
import me.nawa.journey.exception.JourneyErrorCode;
import me.nawa.journey.service.JourneyService;
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
class JourneyControllerTest {

    @Mock
    private JourneyService journeyService;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper()
        .findAndRegisterModules();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(new JourneyController(journeyService))
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
    void createJourney_returns201WithEmptyRegions() throws Exception {
        JourneyCreateRequest request = new JourneyCreateRequest();
        request.setTitle("Seoul Foodie Week");
        request.setStartDate(LocalDate.of(2026, 3, 28));
        request.setEndDate(LocalDate.of(2026, 4, 1));

        JourneyResponse response = JourneyResponse.builder()
            .tripId(10L)
            .title("Seoul Foodie Week")
            .startDate(request.getStartDate())
            .endDate(request.getEndDate())
            .regions(List.of())
            .build();
        when(journeyService.createJourney(
            eq(1L),
            any(JourneyCreateRequest.class)
        )).thenReturn(response);

        String responseBody = mockMvc.perform(
                post("/api/v1/journeys")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);

        JsonNode body = objectMapper.readTree(responseBody);
        assertTrue(body.path("success").asBoolean());
        assertEquals(10L, body.path("data").path("tripId").asLong());
        assertTrue(body.path("data").path("regions").isArray());
        assertEquals(0, body.path("data").path("regions").size());
    }

    @Test
    void getJourney_returns200WithJourneyData() throws Exception {
        JourneyResponse response = JourneyResponse.builder()
            .tripId(20L)
            .title("Seoul Foodie Week")
            .startDate(LocalDate.of(2026, 3, 28))
            .endDate(LocalDate.of(2026, 4, 1))
            .regions(List.of())
            .build();
        when(journeyService.getJourney(1L, 20L)).thenReturn(response);

        String responseBody = mockMvc.perform(get("/api/v1/journeys/20"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);

        JsonNode body = objectMapper.readTree(responseBody);
        assertTrue(body.path("success").asBoolean());
        assertEquals("2026-03-28", body.path("data").path("startDate").asText());
    }

    @Test
    void getJourney_returns403WhenJourneyHasDifferentOwner() throws Exception {
        when(journeyService.getJourney(1L, 20L)).thenThrow(
            new BusinessException(JourneyErrorCode.JOURNEY_FORBIDDEN)
        );

        String responseBody = mockMvc.perform(get("/api/v1/journeys/20"))
            .andExpect(status().isForbidden())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);

        JsonNode body = objectMapper.readTree(responseBody);
        assertFalse(body.path("success").asBoolean());
        assertEquals(
            "JOURNEY-002",
            body.path("error").path("code").asText()
        );
    }

    @Test
    void getTimeline_returns200WithGroupedEventItem() throws Exception {
        JourneyTimelineItemResponse item = JourneyTimelineItemResponse.builder()
            .tripItemId(100L)
            .itemId(200L)
            .status("ADDED")
            .displayOrder(0)
            .exploreItem(JourneyTimelineExploreItemResponse.builder()
                .itemType("EVENT")
                .title("Spring Festival")
                .imageUrls(List.of())
                .build())
            .eventDetail(JourneyTimelineEventDetailResponse.builder()
                .eventKind("FESTIVAL")
                .startDate(LocalDate.of(2026, 4, 1))
                .build())
            .build();
        JourneyTimelineResponse response = JourneyTimelineResponse.builder()
            .tripId(20L)
            .timeline(List.of(JourneyTimelineDayResponse.builder()
                .visitDate(LocalDate.of(2026, 4, 1))
                .items(List.of(item))
                .build()))
            .build();
        when(journeyService.getTimeline(1L, 20L)).thenReturn(response);

        String responseBody = mockMvc.perform(
                get("/api/v1/journeys/20/timeline")
            )
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);

        JsonNode body = objectMapper.readTree(responseBody);
        JsonNode responseItem = body.path("data").path("timeline")
            .get(0).path("items").get(0);
        assertTrue(body.path("success").asBoolean());
        assertEquals("2026-04-01", body.path("data").path("timeline")
            .get(0).path("visitDate").asText());
        assertTrue(responseItem.path("exploreItem").path("imageUrls").isArray());
        assertEquals("FESTIVAL", responseItem.path("eventDetail")
            .path("eventKind").asText());
        assertTrue(responseItem.has("note"));
        assertTrue(responseItem.path("note").isNull());
        assertFalse(responseItem.has("placeDetail"));
        assertFalse(responseItem.has("appointment"));
    }
}
