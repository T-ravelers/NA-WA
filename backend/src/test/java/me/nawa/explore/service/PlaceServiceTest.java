package me.nawa.explore.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import me.nawa.common.exception.BusinessException;
import me.nawa.explore.dto.request.PlaceSearchRequest;
import me.nawa.explore.dto.response.PlaceDetailResponse;
import me.nawa.explore.dto.response.PlaceListResponse;
import me.nawa.explore.dto.response.PlaceSummaryResponse;
import me.nawa.explore.exception.ExploreErrorCode;
import me.nawa.explore.mapper.PlaceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlaceServiceTest {

    @Mock
    private PlaceMapper placeMapper;
    @Mock
    private PlaceOpenStatusEvaluator openStatusEvaluator;
    private PlaceService placeService;

    @BeforeEach
    void setUp() {
        placeService = new PlaceService(placeMapper, openStatusEvaluator);
    }

    @Test
    void searchPlaces_returnsPagedResultAndNormalizesKind() {
        PlaceSearchRequest request = new PlaceSearchRequest();
        PlaceSummaryResponse place = PlaceSummaryResponse.builder()
            .itemId(1L).name("테스트 식당").placeKind("관광식당").build();
        when(placeMapper.searchPlaces(request, 0, 20, null))
            .thenReturn(List.of(place));
        when(placeMapper.countPlaces(request, null)).thenReturn(1L);

        PlaceListResponse result = placeService.searchPlaces(request, null);

        assertEquals("RESTAURANT", result.getContent().get(0).getPlaceKind());
        assertEquals(1L, result.getTotalElements());
        assertEquals(1, result.getTotalPages());
    }

    @Test
    void searchPlaces_normalizesFilters() {
        PlaceSearchRequest request = new PlaceSearchRequest();
        request.setPlaceKinds(List.of(" cafe ", "ETC", "cafe"));
        request.setRegion1(List.of(" 서울 ", "서울"));
        request.setSort("popular");
        when(placeMapper.searchPlaces(any(), eq(0), eq(20), isNull()))
            .thenReturn(List.of());
        when(placeMapper.countPlaces(any(), isNull())).thenReturn(0L);

        placeService.searchPlaces(request, null);

        assertEquals(List.of("CAFE", "ETC"), request.getPlaceKinds());
        assertEquals(List.of("서울"), request.getRegion1());
        assertEquals("POPULAR", request.getSort());
    }

    @Test
    void searchPlaces_rejectsUnsupportedKind() {
        PlaceSearchRequest request = new PlaceSearchRequest();
        request.setPlaceKinds(List.of("HOTEL"));
        assertThrows(
            BusinessException.class,
            () -> placeService.searchPlaces(request, null)
        );
        verifyNoInteractions(placeMapper);
    }

    @Test
    void searchPlaces_requiresMemberForSavedOnly() {
        PlaceSearchRequest request = new PlaceSearchRequest();
        request.setSavedOnly(true);
        assertThrows(
            BusinessException.class,
            () -> placeService.searchPlaces(request, null)
        );
        verifyNoInteractions(placeMapper);
    }

    @Test
    void searchPlaces_filtersOpenNowBeforePaging() {
        PlaceSearchRequest request = new PlaceSearchRequest();
        request.setOpenNow(true);
        request.setSize(1);
        PlaceSummaryResponse closed = PlaceSummaryResponse.builder()
            .itemId(1L).build();
        PlaceSummaryResponse open = PlaceSummaryResponse.builder()
            .itemId(2L).build();
        when(placeMapper.searchPlaces(request, 0, null, null))
            .thenReturn(List.of(closed, open));
        when(openStatusEvaluator.isOpen(any(), any(), any()))
            .thenReturn(false, true);

        PlaceListResponse result = placeService.searchPlaces(request, null);

        assertEquals(1, result.getContent().size());
        assertEquals(2L, result.getContent().get(0).getItemId());
        assertEquals(1L, result.getTotalElements());
        verify(placeMapper).searchPlaces(request, 0, null, null);
    }

    @Test
    void getPlaceDetail_returnsNormalizedPlace() {
        PlaceDetailResponse place = PlaceDetailResponse.builder()
            .placeId(1L).itemId(1L).name("테스트").placeKind("뷰티매장")
            .build();
        when(placeMapper.findPlaceDetail(1L)).thenReturn(place);
        when(placeMapper.findPlaceActivities(1L, "en")).thenReturn(List.of());

        PlaceDetailResponse result = placeService.getPlaceDetail(1L, "EN");

        assertEquals("BEAUTY", result.getPlaceKind());
        assertEquals(List.of(), result.getActivities());
    }

    @Test
    void getPlaceDetail_throwsPlaceNotFound() {
        when(placeMapper.findPlaceDetail(1L)).thenReturn(null);
        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> placeService.getPlaceDetail(1L, "en")
        );
        assertEquals(ExploreErrorCode.PLACE_NOT_FOUND, exception.getErrorCode());
    }
}
