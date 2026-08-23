package me.nawa.explore.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
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
    private ExploreViewCountRecorder viewCountRecorder;
    private PlaceService placeService;

    @BeforeEach
    void setUp() {
        placeService = new PlaceService(placeMapper, viewCountRecorder);
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
        request.setRegion2Other(true);
        request.setSort("popular");
        when(placeMapper.searchPlaces(any(), eq(0), eq(20), isNull()))
            .thenReturn(List.of());
        when(placeMapper.countPlaces(any(), isNull())).thenReturn(0L);

        placeService.searchPlaces(request, null);

        assertEquals(List.of("CAFE", "ETC"), request.getPlaceKinds());
        assertEquals(List.of("서울"), request.getRegion1());
        assertTrue(request.getKnownRegion2Values().contains("성수"));
        assertEquals("POPULAR", request.getSort());
    }

    @Test
    void searchPlaces_defaultsToPopularSort() {
        PlaceSearchRequest request = new PlaceSearchRequest();
        request.setSort(" ");
        when(placeMapper.searchPlaces(any(), eq(0), eq(20), isNull()))
            .thenReturn(List.of());
        when(placeMapper.countPlaces(any(), isNull())).thenReturn(0L);

        placeService.searchPlaces(request, null);

        assertEquals("POPULAR", request.getSort());
    }

    @Test
    void searchPlaces_acceptsLegacyLatestSortAsNewest() {
        PlaceSearchRequest request = new PlaceSearchRequest();
        request.setSort("LATEST");
        when(placeMapper.searchPlaces(any(), eq(0), eq(20), isNull()))
            .thenReturn(List.of());
        when(placeMapper.countPlaces(any(), isNull())).thenReturn(0L);

        placeService.searchPlaces(request, null);

        assertEquals("NEWEST", request.getSort());
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
    void getPlaceDetail_returnsNormalizedPlace() {
        PlaceDetailResponse place = PlaceDetailResponse.builder()
            .placeId(1L).itemId(1L).name("테스트").placeKind("뷰티매장")
            .build();
        when(placeMapper.findPlaceDetail(1L, null)).thenReturn(place);
        when(placeMapper.findPlaceActivities(1L, "en")).thenReturn(List.of());

        PlaceDetailResponse result = placeService.getPlaceDetail(1L, "EN", null);

        assertEquals("BEAUTY", result.getPlaceKind());
        assertEquals(List.of(), result.getActivities());
    }

    @Test
    void getPlaceDetail_passesMemberIdToMapper() {
        PlaceDetailResponse place = PlaceDetailResponse.builder()
            .placeId(1L).itemId(1L).name("테스트").placeKind("CAFE")
            .build();
        when(placeMapper.findPlaceDetail(1L, 7L)).thenReturn(place);
        when(placeMapper.findPlaceActivities(1L, "en")).thenReturn(List.of());

        placeService.getPlaceDetail(1L, "en", 7L);

        verify(placeMapper).findPlaceDetail(1L, 7L);
    }

    /* EventService와 같다. 읽는 경로는 조회수를 세지 않는다. */
    @Test
    void getPlaceDetail_leavesTheViewCountAlone() {
        PlaceDetailResponse place = PlaceDetailResponse.builder()
            .placeId(1L).itemId(1L).name("테스트").placeKind("CAFE")
            .build();
        when(placeMapper.findPlaceDetail(1L, null)).thenReturn(place);
        when(placeMapper.findPlaceActivities(1L, "en")).thenReturn(List.of());

        placeService.getPlaceDetail(1L, "en", null);

        verifyNoInteractions(viewCountRecorder);
    }

    @Test
    void recordPlaceView_countsTheView() {
        placeService.recordPlaceView(1L);

        verify(viewCountRecorder).recordPlaceView(1L);
    }

    @Test
    void recordPlaceView_swallowsTheFailure() {
        doThrow(new IllegalStateException("boom"))
            .when(viewCountRecorder).recordPlaceView(1L);

        /* 조회수는 부가 정보다. 집계가 멈춰도 상세 화면은 열려야 한다. */
        placeService.recordPlaceView(1L);
    }

    @Test
    void getPlaceDetail_throwsPlaceNotFound() {
        when(placeMapper.findPlaceDetail(1L, null)).thenReturn(null);
        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> placeService.getPlaceDetail(1L, "en", null)
        );
        assertEquals(ExploreErrorCode.PLACE_NOT_FOUND, exception.getErrorCode());
    }
}
