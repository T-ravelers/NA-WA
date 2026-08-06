package me.nawa.explore.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import me.nawa.common.exception.BusinessException;
import me.nawa.common.exception.GlobalExceptionHandler;
import me.nawa.explore.dto.request.EventSearchRequest;
import me.nawa.explore.dto.response.EventDetailResponse;
import me.nawa.explore.dto.response.EventListResponse;
import me.nawa.explore.dto.response.EventSummaryResponse;
import me.nawa.explore.domain.EventStatus;
import me.nawa.explore.exception.ExploreErrorCode;
import me.nawa.explore.service.EventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class EventControllerTest {

    @Mock
    private EventService eventService;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(new EventController(eventService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .build();
    }

    @Test
    void searchEvents_returnsSuccessResponse_whenItemTypeIsEvent() throws Exception {
        EventSummaryResponse event = EventSummaryResponse.builder()
            .itemId(990001L)
            .eventKind("FESTIVAL")
            .status(EventStatus.SCHEDULED)
            .title("서울 야시장 푸드 팝업(테스트)")
            .subtitle("목록 테스트")
            .region1("서울")
            .region2("중구")
            .region3("명동")
            .startDate(LocalDate.of(2026, 8, 5))
            .endDate(LocalDate.of(2026, 8, 31))
            .build();

        EventListResponse response = new EventListResponse(
            List.of(event), 0, 20, 1L, 1, false
        );

        when(eventService.searchEvents(
            any(EventSearchRequest.class),
            isNull(Long.class)
        ))
            .thenReturn(response);

        String responseBody = mockMvc.perform(get("/api/v1/explore/events"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);

        JsonNode body = objectMapper.readTree(responseBody);

        assertTrue(body.path("success").asBoolean());
        assertEquals(
            990001L,
            body.path("data").path("content").get(0).path("itemId").asLong()
        );
        assertEquals(
            "FESTIVAL",
            body.path("data").path("content").get(0).path("eventKind").asText()
        );
        assertEquals(
            "SCHEDULED",
            body.path("data").path("content").get(0).path("status").asText()
        );
        assertEquals(
            "2026-08-05",
            body.path("data").path("content").get(0).path("startDate").asText()
        );
        assertEquals(
            "2026-08-31",
            body.path("data").path("content").get(0).path("endDate").asText()
        );
        assertEquals(1L, body.path("data").path("totalElements").asLong());
    }

    @Test
    void searchEvents_bindsMultiValueFilters() throws Exception {
        when(eventService.searchEvents(
            any(EventSearchRequest.class),
            isNull(Long.class)
        )).thenReturn(new EventListResponse(
            List.of(), 0, 20, 0L, 0, false
        ));

        mockMvc.perform(
                get("/api/v1/explore/events")
                    .param("eventKinds", "POPUP", "CONCERT")
                    .param("region1", "서울", "경기")
                    .param("region2", "성수", "홍대")
                    .param("startDate", "2026-08-01")
                    .param("endDate", "2026-08-31")
                    .param("freeOnly", "true")
            )
            .andExpect(status().isOk());

        var requestCaptor = forClass(EventSearchRequest.class);
        verify(eventService).searchEvents(
            requestCaptor.capture(),
            isNull(Long.class)
        );

        EventSearchRequest request = requestCaptor.getValue();
        assertEquals(List.of("POPUP", "CONCERT"), request.getEventKinds());
        assertEquals(List.of("서울", "경기"), request.getRegion1());
        assertEquals(List.of("성수", "홍대"), request.getRegion2());
        assertEquals(
            LocalDate.of(2026, 8, 1),
            request.getStartDate()
        );
        assertEquals(
            LocalDate.of(2026, 8, 31),
            request.getEndDate()
        );
        assertEquals(true, request.getFreeOnly());
    }

    @Test
    void searchEvents_returnsBadRequest_whenDateFormatIsInvalid()
        throws Exception {
        String responseBody = mockMvc.perform(
                get("/api/v1/explore/events")
                    .param("startDate", "not-a-date")
            )
            .andExpect(status().isBadRequest())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);

        JsonNode body = objectMapper.readTree(responseBody);

        assertFalse(body.path("success").asBoolean());
        assertEquals(
            "COMMON-001",
            body.path("error").path("code").asText()
        );
    }

    @Test
    void getEventDetail_returnsSuccessResponse() throws Exception {
        EventDetailResponse response = EventDetailResponse.builder()
            .eventId(990001L)
            .eventKind("FESTIVAL")
            .title("서울 야시장 푸드 팝업")
            .imageUrls(objectMapper.readTree(
                "[\"https://example.com/event-990001.jpg\"]"
            ))
            .links(objectMapper.readTree(
                "{\"homepageUrl\":\"https://example.com\"}"
            ))
            .preReservation(objectMapper.readTree(
                "{\"has\":true,\"link\":\"https://example.com/reserve\","
                    + "\"startAt\":\"2026-08-01T09:00:00\"}"
            ))
            .operatingHours(objectMapper.readTree(
                "{\"raw\":\"10:00-20:00\"}"
            ))
            .openDays(objectMapper.readTree(
                "[\"mon\",\"tue\"]"
            ))
            .contact("02-1234-5678")
            .organizer("NA-WA 테스트 운영팀")
            .startDate(LocalDate.of(2026, 8, 5))
            .endDate(LocalDate.of(2026, 8, 31))
            .activities(List.of())
            .build();

        when(eventService.getEventDetail(990001L, "ko"))
            .thenReturn(response);

        String body = mockMvc.perform(
                get("/api/v1/explore/events/990001")
                    .param("language", "ko")
            )
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);

        JsonNode json = objectMapper.readTree(body);

        assertTrue(json.path("success").asBoolean());
        assertEquals(
            "FESTIVAL",
            json.path("data").path("eventKind").asText()
        );
        assertEquals(
            "https://example.com/event-990001.jpg",
            json.path("data").path("imageUrls").get(0).asText()
        );
        assertEquals(
            "https://example.com",
            json.path("data").path("links").path("homepageUrl").asText()
        );
        assertTrue(
            json.path("data").path("preReservation").path("has").asBoolean()
        );
        assertEquals(
            "2026-08-01T09:00:00",
            json.path("data")
                .path("preReservation")
                .path("startAt")
                .asText()
        );
        assertEquals(
            "10:00-20:00",
            json.path("data").path("operatingHours").path("raw").asText()
        );
        assertEquals(
            "mon",
            json.path("data").path("openDays").get(0).asText()
        );
        assertEquals(
            "2026-08-05",
            json.path("data").path("startDate").asText()
        );
        assertEquals(
            "2026-08-31",
            json.path("data").path("endDate").asText()
        );
    }

    @Test
    void getEventDetail_returns404WithErrorCode_whenEventNotFound()
        throws Exception {
        when(eventService.getEventDetail(990001L, "ko"))
            .thenThrow(new BusinessException(ExploreErrorCode.EVENT_NOT_FOUND));

        String responseBody = mockMvc.perform(
                get("/api/v1/explore/events/990001")
                    .param("language", "ko")
            )
            .andExpect(status().isNotFound())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);

        JsonNode body = objectMapper.readTree(responseBody);

        assertFalse(body.path("success").asBoolean());
        assertEquals(
            "EXPLORE-001",
            body.path("error").path("code").asText()
        );
    }
}
