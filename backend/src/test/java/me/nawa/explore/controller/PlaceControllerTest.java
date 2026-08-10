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
import java.util.List;
import me.nawa.common.exception.BusinessException;
import me.nawa.common.exception.GlobalExceptionHandler;
import me.nawa.explore.dto.request.PlaceSearchRequest;
import me.nawa.explore.dto.response.PlaceDetailResponse;
import me.nawa.explore.dto.response.PlaceListResponse;
import me.nawa.explore.dto.response.PlaceSummaryResponse;
import me.nawa.explore.exception.ExploreErrorCode;
import me.nawa.explore.service.PlaceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class PlaceControllerTest {

    @Mock
    private PlaceService placeService;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(new PlaceController(placeService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .build();
    }

    @Test
    void searchPlaces_returnsSuccessResponse() throws Exception {
        PlaceSummaryResponse place = PlaceSummaryResponse.builder()
            .itemId(1L).name("테스트 Place").placeKind("CAFE").build();
        when(placeService.searchPlaces(any(), isNull()))
            .thenReturn(new PlaceListResponse(
                List.of(place), 0, 20, 1L, 1, false
            ));

        String response = mockMvc.perform(get("/api/v1/explore/places"))
            .andExpect(status().isOk())
            .andReturn().getResponse()
            .getContentAsString(StandardCharsets.UTF_8);
        JsonNode body = objectMapper.readTree(response);

        assertTrue(body.path("success").asBoolean());
        assertEquals(1L, body.path("data").path("content").get(0)
            .path("itemId").asLong());
        assertFalse(body.path("data").path("content").get(0)
            .has("openingHours"));
    }

    @Test
    void searchPlaces_bindsFilters() throws Exception {
        when(placeService.searchPlaces(any(), isNull()))
            .thenReturn(new PlaceListResponse(List.of(), 0, 20, 0L, 0, false));

        mockMvc.perform(get("/api/v1/explore/places")
                .param("placeKinds", "CAFE", "ETC")
                .param("region1", "서울")
                .param("sectorIds", "1", "2")
                .param("openNow", "true"))
            .andExpect(status().isOk());

        var captor = forClass(PlaceSearchRequest.class);
        verify(placeService).searchPlaces(captor.capture(), isNull());
        assertEquals(List.of("CAFE", "ETC"), captor.getValue().getPlaceKinds());
        assertEquals(List.of(1L, 2L), captor.getValue().getSectorIds());
        assertTrue(captor.getValue().getOpenNow());
    }

    @Test
    void getPlaceDetail_returnsDetail() throws Exception {
        when(placeService.getPlaceDetail(1L, "en"))
            .thenReturn(PlaceDetailResponse.builder()
                .placeId(1L).itemId(1L).name("테스트 Place")
                .activities(List.of()).build());
        String response = mockMvc.perform(get("/api/v1/explore/places/1"))
            .andExpect(status().isOk())
            .andReturn().getResponse()
            .getContentAsString(StandardCharsets.UTF_8);
        assertEquals(1L, objectMapper.readTree(response)
            .path("data").path("placeId").asLong());
    }

    @Test
    void getPlaceDetail_returnsExplore002_whenNotFound() throws Exception {
        when(placeService.getPlaceDetail(1L, "en"))
            .thenThrow(new BusinessException(ExploreErrorCode.PLACE_NOT_FOUND));
        String response = mockMvc.perform(get("/api/v1/explore/places/1"))
            .andExpect(status().isNotFound())
            .andReturn().getResponse()
            .getContentAsString(StandardCharsets.UTF_8);
        assertEquals("EXPLORE-002", objectMapper.readTree(response)
            .path("error").path("code").asText());
    }
}
