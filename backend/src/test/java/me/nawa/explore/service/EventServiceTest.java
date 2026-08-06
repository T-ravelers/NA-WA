package me.nawa.explore.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.List;
import me.nawa.common.exception.BusinessException;
import me.nawa.explore.dto.request.EventSearchRequest;
import me.nawa.explore.dto.response.EventDetailResponse;
import me.nawa.explore.dto.response.EventListResponse;
import me.nawa.explore.dto.response.EventSummaryResponse;
import me.nawa.explore.domain.EventStatus;
import me.nawa.explore.exception.ExploreErrorCode;
import me.nawa.explore.mapper.EventMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock
    private EventMapper eventMapper;

    @InjectMocks
    private EventService eventService;

    @Test
    void searchEvents_returnsPagedResult() {
        EventSearchRequest request = new EventSearchRequest();
        request.setPage(1);
        request.setSize(2);
        request.setSort("POPULAR");

        EventSummaryResponse event = EventSummaryResponse.builder()
            .itemId(990001L)
            .eventKind("FESTIVAL")
            .status(EventStatus.SCHEDULED)
            .title("서울 야시장 푸드 팝업(테스트)")
            .subtitle("목록 테스트")
            .region1("서울")
            .region2("중구")
            .region3("명동")
            .build();

        when(eventMapper.searchEvents(
            any(EventSearchRequest.class),
            eq(2),
            isNull(Long.class)
        ))
            .thenReturn(List.of(event));
        when(eventMapper.countEvents(
            any(EventSearchRequest.class),
            isNull(Long.class)
        ))
            .thenReturn(3L);

        EventListResponse result = eventService.searchEvents(request);

        assertEquals(1, result.getContent().size());
        assertEquals(1, result.getPage());
        assertEquals(2, result.getSize());
        assertEquals(3L, result.getTotalElements());
        assertEquals(2, result.getTotalPages());
        assertEquals(false, result.isHasNext());
        assertEquals(990001L, result.getContent().get(0).getItemId());
        assertEquals(
            "FESTIVAL",
            result.getContent().get(0).getEventKind()
        );
        assertEquals(
            EventStatus.SCHEDULED,
            result.getContent().get(0).getStatus()
        );
    }

    @Test
    void searchEvents_usesDefaultPageAndSize() {
        EventSearchRequest request = new EventSearchRequest();

        when(eventMapper.searchEvents(
            any(EventSearchRequest.class),
            eq(0),
            isNull(Long.class)
        ))
            .thenReturn(List.of());
        when(eventMapper.countEvents(
            any(EventSearchRequest.class),
            isNull(Long.class)
        ))
            .thenReturn(0L);

        EventListResponse result = eventService.searchEvents(request);

        assertEquals(0, result.getPage());
        assertEquals(20, result.getSize());
        assertEquals(0L, result.getTotalElements());
        assertEquals(0, result.getTotalPages());
        assertEquals(false, result.isHasNext());
        verify(eventMapper).searchEvents(request, 0, null);
    }

    @Test
    void searchEvents_throwsInvalidInput_whenSortIsUnsupported() {
        EventSearchRequest request = new EventSearchRequest();
        request.setSort("NEAREST");

        assertThrows(
            BusinessException.class,
            () -> eventService.searchEvents(request)
        );
        verifyNoInteractions(eventMapper);
    }

    @Test
    void searchEvents_throwsInvalidInput_whenSizeExceedsMaximum() {
        EventSearchRequest request = new EventSearchRequest();
        request.setSize(101);

        assertThrows(
            BusinessException.class,
            () -> eventService.searchEvents(request)
        );
        verifyNoInteractions(eventMapper);
    }

    @Test
    void searchEvents_normalizesMultiValueFilters_beforeMapperCall() {
        EventSearchRequest request = new EventSearchRequest();
        request.setEventKinds(List.of(" popup ", "CONCERT", "popup"));
        request.setRegion1(List.of("서울", " 서울 ", "경기"));
        request.setRegion2(List.of("성수", " 홍대 "));
        request.setDatePreset("opening_soon");
        request.setSort("ending_soon");
        request.setFreeOnly(true);
        request.setOpenWeekendOnly(true);
        request.setOpensLateOnly(true);
        request.setPreReservationOnly(true);
        request.setExperienceOnly(true);
        request.setPhotoZoneOnly(true);

        when(eventMapper.searchEvents(
            any(EventSearchRequest.class),
            eq(0),
            isNull(Long.class)
        )).thenReturn(List.of());
        when(eventMapper.countEvents(
            any(EventSearchRequest.class),
            isNull(Long.class)
        )).thenReturn(0L);

        eventService.searchEvents(request);

        var requestCaptor = forClass(EventSearchRequest.class);
        verify(eventMapper).searchEvents(
            requestCaptor.capture(),
            eq(0),
            isNull(Long.class)
        );

        EventSearchRequest normalized = requestCaptor.getValue();
        assertEquals(List.of("POPUP", "CONCERT"), normalized.getEventKinds());
        assertEquals(List.of("서울", "경기"), normalized.getRegion1());
        assertEquals(List.of("성수", "홍대"), normalized.getRegion2());
        assertEquals("OPENING_SOON", normalized.getDatePreset());
        assertEquals("ENDING_SOON", normalized.getSort());
        assertEquals(true, normalized.getFreeOnly());
        assertEquals(true, normalized.getOpenWeekendOnly());
        assertEquals(true, normalized.getOpensLateOnly());
        assertEquals(true, normalized.getPreReservationOnly());
        assertEquals(true, normalized.getExperienceOnly());
        assertEquals(true, normalized.getPhotoZoneOnly());
    }

    @Test
    void searchEvents_throwsInvalidInput_whenDatePresetAndDateRangeAreCombined() {
        EventSearchRequest request = new EventSearchRequest();
        request.setDatePreset("ONGOING");
        request.setStartDate(LocalDate.of(2026, 8, 1));

        assertThrows(
            BusinessException.class,
            () -> eventService.searchEvents(request)
        );
        verifyNoInteractions(eventMapper);
    }

    @Test
    void searchEvents_throwsInvalidInput_whenDateRangeIsReversed() {
        EventSearchRequest request = new EventSearchRequest();
        request.setStartDate(LocalDate.of(2026, 8, 31));
        request.setEndDate(LocalDate.of(2026, 8, 1));

        assertThrows(
            BusinessException.class,
            () -> eventService.searchEvents(request)
        );
        verifyNoInteractions(eventMapper);
    }

    @Test
    void searchEvents_throwsInvalidInput_whenEventKindIsUnsupported() {
        EventSearchRequest request = new EventSearchRequest();
        request.setEventKinds(List.of("STORE"));

        assertThrows(
            BusinessException.class,
            () -> eventService.searchEvents(request)
        );
        verifyNoInteractions(eventMapper);
    }

    @Test
    void searchEvents_throwsAuthenticationRequired_whenSavedOnlyWithoutMember() {
        EventSearchRequest request = new EventSearchRequest();
        request.setSavedOnly(true);

        assertThrows(
            BusinessException.class,
            () -> eventService.searchEvents(request, null)
        );
        verifyNoInteractions(eventMapper);
    }

    @Test
    void searchEvents_passesMemberId_whenSavedOnlyIsRequested() {
        EventSearchRequest request = new EventSearchRequest();
        request.setSavedOnly(true);

        when(eventMapper.searchEvents(request, 0, 7L))
            .thenReturn(List.of());
        when(eventMapper.countEvents(request, 7L)).thenReturn(0L);

        EventListResponse result = eventService.searchEvents(request, 7L);

        assertEquals(0, result.getContent().size());
        verify(eventMapper).searchEvents(request, 0, 7L);
        verify(eventMapper).countEvents(request, 7L);
    }

    @Test
    void getEventDetail_returnsEventWithActivities() {
        EventDetailResponse event = EventDetailResponse.builder()
            .eventId(990001L)
            .eventKind("FESTIVAL")
            .title("서울 야시장 푸드 팝업(테스트)")
            .build();

        when(eventMapper.findEventDetail(990001L, "ko"))
            .thenReturn(event);
        when(eventMapper.findEventActivities(990001L, "ko"))
            .thenReturn(List.of());

        EventDetailResponse result = eventService.getEventDetail(
            990001L,
            "ko"
        );

        assertEquals(990001L, result.getEventId());
        assertEquals("FESTIVAL", result.getEventKind());
        assertEquals("서울 야시장 푸드 팝업(테스트)", result.getTitle());
        assertEquals(0, result.getActivities().size());
        verify(eventMapper).findEventDetail(990001L, "ko");
        verify(eventMapper).findEventActivities(990001L, "ko");
    }

    @Test
    void getEventDetail_throwsInvalidInput_whenEventIdIsInvalid() {
        assertThrows(
            BusinessException.class,
            () -> eventService.getEventDetail(0L, "ko")
        );
        verifyNoInteractions(eventMapper);
    }

    @Test
    void getEventDetail_throwsEventNotFound_whenMapperReturnsNull() {
        when(eventMapper.findEventDetail(990001L, "ko"))
            .thenReturn(null);

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> eventService.getEventDetail(990001L, "ko")
        );

        assertEquals(
            ExploreErrorCode.EVENT_NOT_FOUND,
            exception.getErrorCode()
        );
        verify(eventMapper).findEventDetail(990001L, "ko");
    }

    @Test
    void getEventDetail_normalizesNullActivitiesToEmptyList() {
        EventDetailResponse event = EventDetailResponse.builder()
            .eventId(990001L)
            .title("서울 야시장 푸드 팝업(테스트)")
            .build();

        when(eventMapper.findEventDetail(990001L, "ko"))
            .thenReturn(event);
        when(eventMapper.findEventActivities(990001L, "ko"))
            .thenReturn(null);

        EventDetailResponse result = eventService.getEventDetail(
            990001L,
            "ko"
        );

        assertEquals(List.of(), result.getActivities());
        verify(eventMapper).findEventActivities(990001L, "ko");
    }

    @Test
    void getEventDetail_normalizesJsonKeysToCamelCase() throws Exception {
        EventDetailResponse event = EventDetailResponse.builder()
            .eventId(990001L)
            .links(new ObjectMapper().readTree(
                "{\"homepage_url\":\"https://example.com\","
                    + "\"reservation_url\":\"https://example.com/book\"}"
            ))
            .preReservation(new ObjectMapper().readTree(
                "{\"has\":true,\"start_at\":\"2026-08-01T09:00:00\","
                    + "\"end_at\":\"2026-08-01T18:00:00\"}"
            ))
            .operatingHours(new ObjectMapper().readTree(
                "\"10:00-20:00\""
            ))
            .build();

        when(eventMapper.findEventDetail(990001L, "en"))
            .thenReturn(event);
        when(eventMapper.findEventActivities(990001L, "en"))
            .thenReturn(List.of());

        EventDetailResponse result = eventService.getEventDetail(
            990001L,
            "en"
        );

        assertEquals(
            "https://example.com",
            result.getLinks().path("homepageUrl").asText()
        );
        assertEquals(
            "2026-08-01T09:00:00",
            result.getPreReservation().path("startAt").asText()
        );
        assertEquals(
            "2026-08-01T18:00:00",
            result.getPreReservation().path("endAt").asText()
        );
        assertEquals(
            "10:00-20:00",
            result.getOperatingHours().path("raw").asText()
        );
        assertEquals(
            "https://example.com/book",
            result.getReservationUrl()
        );
    }

    @Test
    void getEventDetail_prefersPreReservationLink() throws Exception {
        EventDetailResponse event = EventDetailResponse.builder()
            .eventId(990001L)
            .reservationUrl("https://example.com/reservation")
            .preReservation(new ObjectMapper().readTree(
                "{\"has\":true,\"link\":\"https://example.com/pre-reservation\"}"
            ))
            .build();

        when(eventMapper.findEventDetail(990001L, "ko"))
            .thenReturn(event);
        when(eventMapper.findEventActivities(990001L, "ko"))
            .thenReturn(List.of());

        EventDetailResponse result = eventService.getEventDetail(
            990001L,
            "ko"
        );

        assertEquals(
            "https://example.com/pre-reservation",
            result.getReservationUrl()
        );
    }

    @Test
    void getEventDetail_fallsBackToReservationColumn() throws Exception {
        EventDetailResponse event = EventDetailResponse.builder()
            .eventId(990001L)
            .reservationUrl("https://example.com/reservation")
            .preReservation(new ObjectMapper().readTree(
                "{\"has\":false,\"link\":null}"
            ))
            .build();

        when(eventMapper.findEventDetail(990001L, "ko"))
            .thenReturn(event);
        when(eventMapper.findEventActivities(990001L, "ko"))
            .thenReturn(List.of());

        EventDetailResponse result = eventService.getEventDetail(
            990001L,
            "ko"
        );

        assertEquals(
            "https://example.com/reservation",
            result.getReservationUrl()
        );
    }

    @Test
    void getEventDetail_fallsBackToLinksReservationUrl() throws Exception {
        EventDetailResponse event = EventDetailResponse.builder()
            .eventId(990001L)
            .links(new ObjectMapper().readTree(
                "{\"reservation_url\":\"https://example.com/links-reservation\"}"
            ))
            .build();

        when(eventMapper.findEventDetail(990001L, "ko"))
            .thenReturn(event);
        when(eventMapper.findEventActivities(990001L, "ko"))
            .thenReturn(List.of());

        EventDetailResponse result = eventService.getEventDetail(
            990001L,
            "ko"
        );

        assertEquals(
            "https://example.com/links-reservation",
            result.getReservationUrl()
        );
    }

    @Test
    void getEventDetail_usesEnglishAsDefaultLanguage() {
        EventDetailResponse event = EventDetailResponse.builder()
            .eventId(990001L)
            .build();

        when(eventMapper.findEventDetail(990001L, "en"))
            .thenReturn(event);
        when(eventMapper.findEventActivities(990001L, "en"))
            .thenReturn(List.of());

        eventService.getEventDetail(990001L, null);

        verify(eventMapper).findEventDetail(990001L, "en");
        verify(eventMapper).findEventActivities(990001L, "en");
    }
}
