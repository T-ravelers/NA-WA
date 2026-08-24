package me.nawa.explore.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.List;
import me.nawa.common.exception.BusinessException;
import me.nawa.common.exception.CommonErrorCode;
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

    @Mock
    private ExploreViewCountRecorder viewCountRecorder;

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
            isNull(Long.class),
            any(LocalDate.class)
        ))
            .thenReturn(List.of(event));
        when(eventMapper.countEvents(
            any(EventSearchRequest.class),
            isNull(Long.class),
            any(LocalDate.class)
        ))
            .thenReturn(3L);

        EventListResponse result = eventService.searchEvents(request, null);

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
            isNull(Long.class),
            any(LocalDate.class)
        ))
            .thenReturn(List.of());
        when(eventMapper.countEvents(
            any(EventSearchRequest.class),
            isNull(Long.class),
            any(LocalDate.class)
        ))
            .thenReturn(0L);

        EventListResponse result = eventService.searchEvents(request, null);

        assertEquals(0, result.getPage());
        assertEquals(20, result.getSize());
        assertEquals(0L, result.getTotalElements());
        assertEquals(0, result.getTotalPages());
        assertEquals(false, result.isHasNext());
        verify(eventMapper).searchEvents(
            eq(request), eq(0), isNull(Long.class), any(LocalDate.class));
    }

    @Test
    void searchEvents_throwsInvalidInput_whenSortIsUnsupported() {
        EventSearchRequest request = new EventSearchRequest();
        request.setSort("NEAREST");

        assertThrows(
            BusinessException.class,
            () -> eventService.searchEvents(request, null)
        );
        verifyNoInteractions(eventMapper);
    }

    @Test
    void searchEvents_throwsInvalidInput_whenSizeExceedsMaximum() {
        EventSearchRequest request = new EventSearchRequest();
        request.setSize(101);

        assertThrows(
            BusinessException.class,
            () -> eventService.searchEvents(request, null)
        );
        verifyNoInteractions(eventMapper);
    }

    @Test
    void searchEvents_normalizesMultiValueFilters_beforeMapperCall() {
        EventSearchRequest request = new EventSearchRequest();
        request.setEventKinds(List.of(" popup ", "CONCERT", "popup"));
        request.setRegion1(List.of("서울", " 서울 ", "경기"));
        request.setRegion2(List.of("성수", " 홍대 "));
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
            isNull(Long.class),
            any(LocalDate.class)
        )).thenReturn(List.of());
        when(eventMapper.countEvents(
            any(EventSearchRequest.class),
            isNull(Long.class),
            any(LocalDate.class)
        )).thenReturn(0L);

        eventService.searchEvents(request, null);

        var requestCaptor = forClass(EventSearchRequest.class);
        verify(eventMapper).searchEvents(
            requestCaptor.capture(),
            eq(0),
            isNull(Long.class),
            any(LocalDate.class)
        );

        EventSearchRequest normalized = requestCaptor.getValue();
        assertEquals(List.of("POPUP", "CONCERT"), normalized.getEventKinds());
        assertEquals(List.of("서울", "경기"), normalized.getRegion1());
        assertEquals(List.of("성수", "홍대"), normalized.getRegion2());
        assertEquals("ENDING_SOON", normalized.getSort());
        assertEquals(true, normalized.getFreeOnly());
        assertEquals(true, normalized.getOpenWeekendOnly());
        assertEquals(true, normalized.getOpensLateOnly());
        assertEquals(true, normalized.getPreReservationOnly());
        assertEquals(true, normalized.getExperienceOnly());
        assertEquals(true, normalized.getPhotoZoneOnly());
    }

    @Test
    void searchEvents_acceptsLegacyLatestSortAsNewest() {
        EventSearchRequest request = new EventSearchRequest();
        request.setSort("LATEST");
        when(eventMapper.searchEvents(
            any(EventSearchRequest.class),
            eq(0),
            isNull(Long.class),
            any(LocalDate.class)
        )).thenReturn(List.of());
        when(eventMapper.countEvents(
            any(EventSearchRequest.class),
            isNull(Long.class),
            any(LocalDate.class)
        )).thenReturn(0L);

        eventService.searchEvents(request, null);

        var requestCaptor = forClass(EventSearchRequest.class);
        verify(eventMapper).searchEvents(
            requestCaptor.capture(),
            eq(0),
            isNull(Long.class),
            any(LocalDate.class)
        );
        assertEquals("NEWEST", requestCaptor.getValue().getSort());
    }

    @Test
    void searchEvents_throwsInvalidInput_whenDateRangeIsReversed() {
        EventSearchRequest request = new EventSearchRequest();
        request.setStartDate(LocalDate.of(2026, 8, 31));
        request.setEndDate(LocalDate.of(2026, 8, 1));

        assertThrows(
            BusinessException.class,
            () -> eventService.searchEvents(request, null)
        );
        verifyNoInteractions(eventMapper);
    }

    @Test
    void searchEvents_throwsInvalidInput_whenEventKindIsUnsupported() {
        EventSearchRequest request = new EventSearchRequest();
        request.setEventKinds(List.of("STORE"));

        assertThrows(
            BusinessException.class,
            () -> eventService.searchEvents(request, null)
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

        when(eventMapper.searchEvents(
            eq(request), eq(0), eq(7L), any(LocalDate.class)))
            .thenReturn(List.of());
        when(eventMapper.countEvents(
            eq(request), eq(7L), any(LocalDate.class))).thenReturn(0L);

        EventListResponse result = eventService.searchEvents(request, 7L);

        assertEquals(0, result.getContent().size());
        verify(eventMapper).searchEvents(
            eq(request), eq(0), eq(7L), any(LocalDate.class));
        verify(eventMapper).countEvents(
            eq(request), eq(7L), any(LocalDate.class));
    }

    @Test
    void getEventDetail_returnsEventWithActivities() {
        EventDetailResponse event = EventDetailResponse.builder()
            .eventId(990001L)
            .eventKind("FESTIVAL")
            .title("서울 야시장 푸드 팝업(테스트)")
            .build();

        when(eventMapper.findEventDetail(
            eq(990001L), eq("ja"), isNull(Long.class), any(LocalDate.class)))
            .thenReturn(event);
        when(eventMapper.findEventActivities(990001L, "ja"))
            .thenReturn(List.of());

        EventDetailResponse result = eventService.getEventDetail(
            990001L,
            "ja",
            null);

        assertEquals(990001L, result.getEventId());
        assertEquals("FESTIVAL", result.getEventKind());
        assertEquals("서울 야시장 푸드 팝업(테스트)", result.getTitle());
        assertEquals(0, result.getActivities().size());
        verify(eventMapper).findEventDetail(
            eq(990001L), eq("ja"), isNull(Long.class), any(LocalDate.class));
        verify(eventMapper).findEventActivities(990001L, "ja");
    }

    /*
     * 상세를 읽는 경로는 조회수를 세지 않는다. 이 메서드는 읽기 트랜잭션 안에서 돌고,
     * 그 안에서 집계하면 REQUIRES_NEW가 커넥션을 하나 더 잡는다. 세는 것은 트랜잭션이
     * 끝난 뒤 호출부(컨트롤러)의 몫이다.
     */
    @Test
    void getEventDetail_leavesTheViewCountAlone() {
        EventDetailResponse event = EventDetailResponse.builder()
            .eventId(990001L)
            .build();
        when(eventMapper.findEventDetail(
            eq(990001L), eq("ja"), isNull(Long.class), any(LocalDate.class))).thenReturn(event);
        when(eventMapper.findEventActivities(990001L, "ja")).thenReturn(List.of());

        eventService.getEventDetail(990001L, "ja", null);

        verifyNoInteractions(viewCountRecorder);
    }

    @Test
    void recordEventView_countsTheView() {
        eventService.recordEventView(990001L);

        verify(viewCountRecorder).recordEventView(990001L);
    }

    @Test
    void recordEventView_swallowsTheFailure() {
        doThrow(new IllegalStateException("boom"))
            .when(viewCountRecorder).recordEventView(990001L);

        /* 조회수는 부가 정보다. 집계가 멈춰도 상세 화면은 열려야 한다. */
        eventService.recordEventView(990001L);
    }

    @Test
    void getEventDetail_throwsInvalidInput_whenEventIdIsInvalid() {
        assertThrows(
            BusinessException.class,
            () -> eventService.getEventDetail(0L, "ja", null)
        );
        verifyNoInteractions(eventMapper);
    }

    @Test
    void getEventDetail_throwsEventNotFound_whenMapperReturnsNull() {
        when(eventMapper.findEventDetail(
            eq(990001L), eq("ja"), isNull(Long.class), any(LocalDate.class)))
            .thenReturn(null);

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> eventService.getEventDetail(990001L, "ja", null)
        );

        assertEquals(
            ExploreErrorCode.EVENT_NOT_FOUND,
            exception.getErrorCode()
        );
        verify(eventMapper).findEventDetail(
            eq(990001L), eq("ja"), isNull(Long.class), any(LocalDate.class));
    }

    @Test
    void getEventDetail_normalizesNullActivitiesToEmptyList() {
        EventDetailResponse event = EventDetailResponse.builder()
            .eventId(990001L)
            .title("서울 야시장 푸드 팝업(테스트)")
            .build();

        when(eventMapper.findEventDetail(
            eq(990001L), eq("ja"), isNull(Long.class), any(LocalDate.class)))
            .thenReturn(event);
        when(eventMapper.findEventActivities(990001L, "ja"))
            .thenReturn(null);

        EventDetailResponse result = eventService.getEventDetail(
            990001L,
            "ja",
            null);

        assertEquals(List.of(), result.getActivities());
        verify(eventMapper).findEventActivities(990001L, "ja");
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

        when(eventMapper.findEventDetail(
            eq(990001L), eq("en"), isNull(Long.class), any(LocalDate.class)))
            .thenReturn(event);
        when(eventMapper.findEventActivities(990001L, "en"))
            .thenReturn(List.of());

        EventDetailResponse result = eventService.getEventDetail(
            990001L,
            "en",
            null);

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

        when(eventMapper.findEventDetail(
            eq(990001L), eq("ja"), isNull(Long.class), any(LocalDate.class)))
            .thenReturn(event);
        when(eventMapper.findEventActivities(990001L, "ja"))
            .thenReturn(List.of());

        EventDetailResponse result = eventService.getEventDetail(
            990001L,
            "ja",
            null);

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

        when(eventMapper.findEventDetail(
            eq(990001L), eq("ja"), isNull(Long.class), any(LocalDate.class)))
            .thenReturn(event);
        when(eventMapper.findEventActivities(990001L, "ja"))
            .thenReturn(List.of());

        EventDetailResponse result = eventService.getEventDetail(
            990001L,
            "ja",
            null);

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

        when(eventMapper.findEventDetail(
            eq(990001L), eq("ja"), isNull(Long.class), any(LocalDate.class)))
            .thenReturn(event);
        when(eventMapper.findEventActivities(990001L, "ja"))
            .thenReturn(List.of());

        EventDetailResponse result = eventService.getEventDetail(
            990001L,
            "ja",
            null);

        assertEquals(
            "https://example.com/links-reservation",
            result.getReservationUrl()
        );
    }

    @Test
    void getEventDetail_passesMemberIdToMapper() {
        EventDetailResponse event = EventDetailResponse.builder()
            .eventId(990001L)
            .build();

        when(eventMapper.findEventDetail(
            eq(990001L), eq("ja"), eq(7L), any(LocalDate.class)))
            .thenReturn(event);
        when(eventMapper.findEventActivities(990001L, "ja"))
            .thenReturn(List.of());

        eventService.getEventDetail(990001L, "ja", 7L);

        verify(eventMapper).findEventDetail(
            eq(990001L), eq("ja"), eq(7L), any(LocalDate.class));
    }

    @Test
    void getEventDetail_usesEnglishAsDefaultLanguage() {
        EventDetailResponse event = EventDetailResponse.builder()
            .eventId(990001L)
            .build();

        when(eventMapper.findEventDetail(
            eq(990001L), eq("en"), isNull(Long.class), any(LocalDate.class)))
            .thenReturn(event);
        when(eventMapper.findEventActivities(990001L, "en"))
            .thenReturn(List.of());

        eventService.getEventDetail(990001L, null, null);

        verify(eventMapper).findEventDetail(
            eq(990001L), eq("en"), isNull(Long.class), any(LocalDate.class));
        verify(eventMapper).findEventActivities(990001L, "en");
    }

    /**
     * 상세가 zh-TW를 소문자로 접지 않고 그대로 매퍼에 넘겨야 한다.
     *
     * <p>예전에는 요청 값을 통째로 {@code toLowerCase}해 {@code zh-tw}가 넘어갔고, 번역
     * 테이블의 {@code zh-TW}와 맞지 않아 한국어 원문만 나갔다(#531).
     */
    @Test
    void getEventDetail_keepsZhTwCasing() {
        EventDetailResponse event = EventDetailResponse.builder()
            .eventId(990001L)
            .build();

        when(eventMapper.findEventDetail(
            eq(990001L), eq("zh-TW"), isNull(Long.class), any(LocalDate.class)))
            .thenReturn(event);
        when(eventMapper.findEventActivities(990001L, "zh-TW"))
            .thenReturn(List.of());

        eventService.getEventDetail(990001L, "zh-TW", null);

        verify(eventMapper).findEventDetail(
            eq(990001L), eq("zh-TW"), isNull(Long.class), any(LocalDate.class));
    }

    /** 목록도 같은 정책을 써야 목록과 상세가 다른 언어를 돌려주지 않는다. */
    @Test
    void searchEvents_keepsZhTwCasing() {
        EventSearchRequest request = new EventSearchRequest();
        request.setLanguage("zh-TW");

        when(eventMapper.searchEvents(
            any(EventSearchRequest.class), anyInt(), isNull(Long.class),
            any(LocalDate.class)))
            .thenReturn(List.of());
        when(eventMapper.countEvents(
            any(EventSearchRequest.class), isNull(Long.class),
            any(LocalDate.class)))
            .thenReturn(0L);

        eventService.searchEvents(request, null);

        assertEquals("zh-TW", request.getLanguage());
    }

    /** 지원 목록 밖의 언어는 조용히 한국어로 떨어지지 않고 400으로 끊는다. */
    @Test
    void getEventDetail_rejectsUnsupportedLanguage() {
        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> eventService.getEventDetail(990001L, "ko", null)
        );

        assertEquals(CommonErrorCode.INVALID_INPUT, exception.getErrorCode());
        verifyNoInteractions(eventMapper);
    }
}
