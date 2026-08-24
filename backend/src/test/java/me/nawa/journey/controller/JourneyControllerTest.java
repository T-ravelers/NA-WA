package me.nawa.journey.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import me.nawa.appointment.exception.AppointmentErrorCode;
import me.nawa.auth.security.AuthenticatedMember;
import me.nawa.common.exception.BusinessException;
import me.nawa.common.exception.GlobalExceptionHandler;
import me.nawa.journey.dto.request.JourneyCreateRequest;
import me.nawa.journey.dto.request.JourneyItemCreateRequest;
import me.nawa.journey.dto.request.JourneyUpdateRequest;
import me.nawa.journey.dto.response.JourneyDetailResponse;
import me.nawa.journey.dto.response.JourneyItemExistsResponse;
import me.nawa.journey.dto.response.JourneyItemResponse;
import me.nawa.journey.dto.response.JourneyResponse;
import me.nawa.journey.dto.response.JourneyTimelineAppointmentResponse;
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
    void updateJourney_returns200WithUpdatedSettings() throws Exception {
        JourneyResponse response = JourneyResponse.builder()
            .tripId(20L)
            .title("Updated Seoul Journey")
            .startDate(LocalDate.of(2026, 4, 1))
            .endDate(LocalDate.of(2026, 4, 5))
            .regions(List.of())
            .build();
        when(journeyService.updateJourney(
            eq(1L),
            eq(20L),
            any(JourneyUpdateRequest.class)
        )).thenReturn(response);

        String responseBody = mockMvc.perform(
                put("/api/v1/journeys/20")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "title": "Updated Seoul Journey",
                          "startDate": "2026-04-01",
                          "endDate": "2026-04-05",
                          "budgetAmount": null,
                          "companionPreference": null,
                          "regions": []
                        }
                        """)
            )
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);

        JsonNode body = objectMapper.readTree(responseBody);
        assertTrue(body.path("success").asBoolean());
        assertEquals(20L, body.path("data").path("tripId").asLong());
        assertEquals(
            "Updated Seoul Journey",
            body.path("data").path("title").asText()
        );
        assertEquals(0, body.path("data").path("regions").size());
    }

    @Test
    void updateJourney_returns409WhenDateRangeConflicts() throws Exception {
        when(journeyService.updateJourney(
            eq(1L),
            eq(20L),
            any(JourneyUpdateRequest.class)
        )).thenThrow(new BusinessException(
            JourneyErrorCode.JOURNEY_DATE_RANGE_CONFLICT
        ));

        String responseBody = mockMvc.perform(
                put("/api/v1/journeys/20")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "title": "Short Journey",
                          "startDate": "2026-04-02",
                          "endDate": "2026-04-03",
                          "regions": []
                        }
                        """)
            )
            .andExpect(status().isConflict())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);

        JsonNode body = objectMapper.readTree(responseBody);
        assertFalse(body.path("success").asBoolean());
        assertEquals(
            "JOURNEY-009",
            body.path("error").path("code").asText()
        );
    }

    @Test
    void addJourneyItem_returns201WithApiResponse() throws Exception {
        JourneyItemResponse response = JourneyItemResponse.builder()
            .tripItemId(7L)
            .journeyId(12L)
            .itemId(990001L)
            .itemType("EVENT")
            .visitDate(LocalDate.of(2026, 8, 8))
            .tripItemStatus("ADDED")
            .displayOrder(0)
            .confirmedAt(null)
            .createdAt(LocalDateTime.of(2026, 8, 8, 9, 30))
            .build();
        when(journeyService.addJourneyItem(
            eq(1L),
            eq(12L),
            any(JourneyItemCreateRequest.class)
        )).thenReturn(response);

        String responseBody = mockMvc.perform(
                post("/api/v1/journeys/12/items")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"itemId\":990001,\"visitDate\":\"2026-08-08\"}")
            )
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);

        JsonNode body = objectMapper.readTree(responseBody);
        assertTrue(body.path("success").asBoolean());
        assertEquals(7L, body.path("data").path("tripItemId").asLong());
        assertEquals("EVENT", body.path("data").path("itemType").asText());
        assertTrue(body.path("data").path("appointmentId").isNull());
        assertEquals(
            "2026-08-08T09:30:00",
            body.path("data").path("createdAt").asText()
        );
        assertTrue(body.path("data").path("confirmedAt").isNull());
    }

    @Test
    void addJourneyItem_returns409ForDuplicate() throws Exception {
        when(journeyService.addJourneyItem(
            eq(1L),
            eq(12L),
            any(JourneyItemCreateRequest.class)
        )).thenThrow(new BusinessException(
            JourneyErrorCode.JOURNEY_ITEM_DUPLICATE
        ));

        String responseBody = mockMvc.perform(
                post("/api/v1/journeys/12/items")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"itemId\":990001,\"visitDate\":\"2026-08-08\"}")
            )
            .andExpect(status().isConflict())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);

        JsonNode body = objectMapper.readTree(responseBody);
        assertFalse(body.path("success").asBoolean());
        assertEquals(
            "JOURNEY-004",
            body.path("error").path("code").asText()
        );
    }

    @Test
    void existsJourneyItem_returns200WithExistsFlag() throws Exception {
        when(journeyService.existsJourneyItem(
            1L, 12L, 990001L, LocalDate.of(2026, 8, 8)
        )).thenReturn(JourneyItemExistsResponse.builder()
            .exists(true)
            .appointmentLinked(true)
            .build());

        String responseBody = mockMvc.perform(
                get("/api/v1/journeys/12/items/exists")
                    .param("itemId", "990001")
                    .param("visitDate", "2026-08-08")
            )
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);

        JsonNode body = objectMapper.readTree(responseBody);
        assertTrue(body.path("success").asBoolean());
        assertTrue(body.path("data").path("exists").asBoolean());
        assertTrue(body.path("data").path("appointmentLinked").asBoolean());
    }

    // 담아만 둔 자리다. 여정 담기는 막히지만 약속 생성은 열려 있어야 하므로 두
    // 값이 갈린다 — 한 값으로 합치면 담아 둔 장소로는 약속을 만들 수 없게 된다.
    @Test
    void existsJourneyItem_returns200WithAppointmentLinkedFalse_whenOnlyAdded()
        throws Exception {
        when(journeyService.existsJourneyItem(
            1L, 12L, 990001L, LocalDate.of(2026, 8, 8)
        )).thenReturn(JourneyItemExistsResponse.builder()
            .exists(true)
            .appointmentLinked(false)
            .build());

        String responseBody = mockMvc.perform(
                get("/api/v1/journeys/12/items/exists")
                    .param("itemId", "990001")
                    .param("visitDate", "2026-08-08")
            )
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);

        JsonNode body = objectMapper.readTree(responseBody);
        assertTrue(body.path("data").path("exists").asBoolean());
        assertFalse(body.path("data").path("appointmentLinked").asBoolean());
    }

    @Test
    void existsJourneyItem_returns400ForMalformedVisitDate() throws Exception {
        String responseBody = mockMvc.perform(
                get("/api/v1/journeys/12/items/exists")
                    .param("itemId", "990001")
                    .param("visitDate", "not-a-date")
            )
            .andExpect(status().isBadRequest())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);

        JsonNode body = objectMapper.readTree(responseBody);
        assertFalse(body.path("success").asBoolean());
        assertEquals("COMMON-001", body.path("error").path("code").asText());
    }

    @Test
    void existsJourneyItem_returns400ForMissingVisitDate() throws Exception {
        String responseBody = mockMvc.perform(
                get("/api/v1/journeys/12/items/exists")
                    .param("itemId", "990001")
            )
            .andExpect(status().isBadRequest())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);

        JsonNode body = objectMapper.readTree(responseBody);
        assertFalse(body.path("success").asBoolean());
        assertEquals("COMMON-001", body.path("error").path("code").asText());
    }

    @Test
    void existsJourneyItem_returns400ForMalformedItemId() throws Exception {
        String responseBody = mockMvc.perform(
                get("/api/v1/journeys/12/items/exists")
                    .param("itemId", "abc")
                    .param("visitDate", "2026-08-08")
            )
            .andExpect(status().isBadRequest())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);

        JsonNode body = objectMapper.readTree(responseBody);
        assertFalse(body.path("success").asBoolean());
        assertEquals("COMMON-001", body.path("error").path("code").asText());
    }

    @Test
    void getJourney_returns200WithJourneyData() throws Exception {
        JourneyDetailResponse response = JourneyDetailResponse.builder()
            .tripId(20L)
            .title("Seoul Foodie Week")
            .startDate(LocalDate.of(2026, 3, 28))
            .endDate(LocalDate.of(2026, 4, 1))
            .budgetAmount(new java.math.BigDecimal("1800000"))
            .spentAmount(new java.math.BigDecimal("1284500"))
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
        assertEquals(1284500, body.path("data").path("spentAmount").asInt());
    }

    @Test
    void getJourneys_returns200WithJourneySummaries() throws Exception {
        when(journeyService.getJourneys(1L)).thenReturn(List.of(
            me.nawa.journey.dto.response.JourneySummaryResponse.builder()
                .tripId(20L)
                .title("Seoul Foodie Week")
                .startDate(LocalDate.of(2026, 3, 28))
                .endDate(LocalDate.of(2026, 4, 1))
                .eventCount(3L)
                .placeCount(5L)
                .build()
        ));

        String responseBody = mockMvc.perform(get("/api/v1/journeys"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);

        JsonNode body = objectMapper.readTree(responseBody);
        assertTrue(body.path("success").asBoolean());
        assertEquals(
            20L,
            body.path("data").get(0).path("tripId").asLong()
        );
        assertEquals(
            "2026-03-28",
            body.path("data").get(0).path("startDate").asText()
        );
        assertEquals(
            3L,
            body.path("data").get(0).path("eventCount").asLong()
        );
        assertEquals(
            5L,
            body.path("data").get(0).path("placeCount").asLong()
        );
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
        when(journeyService.getTimeline(1L, 20L, "ja")).thenReturn(response);

        String responseBody = mockMvc.perform(
                get("/api/v1/journeys/20/timeline").param("language", "ja")
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

    @Test
    void getTimeline_returns200WithAppointmentId() throws Exception {
        JourneyTimelineItemResponse item = JourneyTimelineItemResponse.builder()
            .tripItemId(103L)
            .itemId(203L)
            .status("CONFIRMED")
            .displayOrder(0)
            .exploreItem(JourneyTimelineExploreItemResponse.builder()
                .itemType("PLACE")
                .title("Gwangjang Market")
                .imageUrls(List.of())
                .build())
            .appointment(JourneyTimelineAppointmentResponse.builder()
                .appointmentId(900L)
                .appointmentStatus("CONFIRMED")
                .build())
            .build();
        JourneyTimelineResponse response = JourneyTimelineResponse.builder()
            .tripId(20L)
            .timeline(List.of(JourneyTimelineDayResponse.builder()
                .visitDate(LocalDate.of(2026, 4, 2))
                .items(List.of(item))
                .build()))
            .build();
        when(journeyService.getTimeline(1L, 20L, "en")).thenReturn(response);

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
        assertEquals(900L, responseItem.path("appointment")
            .path("appointmentId").asLong());
    }

    @Test
    void deleteJourneyItem_returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/journeys/12/items/7"))
            .andExpect(status().isNoContent());

        verify(journeyService).deleteJourneyItem(1L, 12L, 7L);
    }

    @Test
    void deleteJourneyItem_returns404WhenScheduleDoesNotExist()
        throws Exception {
        org.mockito.Mockito.doThrow(new BusinessException(
            JourneyErrorCode.JOURNEY_SCHEDULE_NOT_FOUND
        )).when(journeyService).deleteJourneyItem(1L, 12L, 7L);

        String responseBody = mockMvc.perform(
                delete("/api/v1/journeys/12/items/7")
            )
            .andExpect(status().isNotFound())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);

        JsonNode body = objectMapper.readTree(responseBody);
        assertEquals(
            "JOURNEY-010",
            body.path("error").path("code").asText()
        );
    }

    @Test
    void deleteJourneyItem_returnsAppointmentCancellationError()
        throws Exception {
        org.mockito.Mockito.doThrow(new BusinessException(
            AppointmentErrorCode.CANCELLATION_NOT_AVAILABLE
        )).when(journeyService).deleteJourneyItem(1L, 12L, 7L);

        String responseBody = mockMvc.perform(
                delete("/api/v1/journeys/12/items/7")
            )
            .andExpect(status().isConflict())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);

        JsonNode body = objectMapper.readTree(responseBody);
        assertEquals(
            "APPOINTMENT-007",
            body.path("error").path("code").asText()
        );
    }

    @Test
    void deleteJourney_returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/journeys/12"))
            .andExpect(status().isNoContent());

        verify(journeyService).deleteJourney(1L, 12L);
    }

    @Test
    void deleteJourney_returns409ForHostedAppointment() throws Exception {
        org.mockito.Mockito.doThrow(new BusinessException(
            JourneyErrorCode.JOURNEY_APPOINTMENT_HOST_DELETE_CONFLICT
        )).when(journeyService).deleteJourney(1L, 12L);

        String responseBody = mockMvc.perform(
                delete("/api/v1/journeys/12")
            )
            .andExpect(status().isConflict())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);

        JsonNode body = objectMapper.readTree(responseBody);
        assertFalse(body.path("success").asBoolean());
        assertEquals(
            "JOURNEY-011",
            body.path("error").path("code").asText()
        );
    }

    @Test
    void deleteJourney_returnsAppointmentCancellationError()
        throws Exception {
        org.mockito.Mockito.doThrow(new BusinessException(
            AppointmentErrorCode.CANCELLATION_NOT_AVAILABLE
        )).when(journeyService).deleteJourney(1L, 12L);

        String responseBody = mockMvc.perform(
                delete("/api/v1/journeys/12")
            )
            .andExpect(status().isConflict())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);

        JsonNode body = objectMapper.readTree(responseBody);
        assertEquals(
            "APPOINTMENT-007",
            body.path("error").path("code").asText()
        );
    }
}
