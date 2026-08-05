package me.nawa.explore.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import me.nawa.common.exception.BusinessException;
import me.nawa.explore.dto.EventListResponse;
import me.nawa.explore.dto.EventSearchRequest;
import me.nawa.explore.dto.EventSummaryResponse;
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
            null,
            null
        );

        when(eventMapper.searchEvents(any(EventSearchRequest.class), eq(2)))
            .thenReturn(List.of(event));
        when(eventMapper.countEvents(any(EventSearchRequest.class)))
            .thenReturn(3L);

        EventListResponse result = eventService.searchEvents(request);

        assertEquals(1, result.getContent().size());
        assertEquals(1, result.getPage());
        assertEquals(2, result.getSize());
        assertEquals(3L, result.getTotalElements());
        assertEquals(2, result.getTotalPages());
        assertEquals(false, result.isHasNext());
        assertEquals(990001L, result.getContent().get(0).getItemId());
    }

    @Test
    void searchEvents_usesDefaultPageAndSize() {
        EventSearchRequest request = new EventSearchRequest();

        when(eventMapper.searchEvents(any(EventSearchRequest.class), eq(0)))
            .thenReturn(List.of());
        when(eventMapper.countEvents(any(EventSearchRequest.class)))
            .thenReturn(0L);

        EventListResponse result = eventService.searchEvents(request);

        assertEquals(0, result.getPage());
        assertEquals(20, result.getSize());
        assertEquals(0L, result.getTotalElements());
        assertEquals(0, result.getTotalPages());
        assertEquals(false, result.isHasNext());
        verify(eventMapper).searchEvents(request, 0);
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
}
