package me.nawa.explore.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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
import me.nawa.explore.dto.response.EventDetailResponse;
import me.nawa.explore.dto.request.EventSearchRequest;
import me.nawa.explore.dto.response.EventListResponse;
import me.nawa.explore.dto.response.EventSummaryResponse;
import me.nawa.explore.exception.ExploreErrorCode;
import me.nawa.explore.service.EventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
            .build();
    }

    @Test
    void searchEvents_returnsSuccessResponse_whenItemTypeIsEvent() throws Exception {
        EventSummaryResponse event = new EventSummaryResponse(
            990001L,
            "서울 야시장 푸드 팝업(테스트)",
            "목록 테스트",
            null,
            "서울",
            "중구",
            "명동",
            null,
            null,
            LocalDate.of(2026, 8, 5),
            LocalDate.of(2026, 8, 31)
        );

        EventListResponse response = new EventListResponse(
            List.of(event), 0, 20, 1L, 1, false
        );

        when(eventService.searchEvents(any(EventSearchRequest.class)))
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
    void getEventDetail_returnsSuccessResponse() throws Exception {
        EventDetailResponse response = EventDetailResponse.builder()
            .eventId(990001L)
            .title("서울 야시장 푸드 팝업")
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
