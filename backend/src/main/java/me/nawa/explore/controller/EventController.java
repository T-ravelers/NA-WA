package me.nawa.explore.controller;

import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import me.nawa.auth.security.AuthenticatedMember;
import me.nawa.common.response.ApiResponse;
import me.nawa.explore.dto.request.EventSearchRequest;
import me.nawa.explore.dto.response.EventDetailResponse;
import me.nawa.explore.dto.response.EventListResponse;
import me.nawa.explore.service.EventService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/explore/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    @GetMapping
    @ApiOperation("탐색 Event 목록 조회")
    public ApiResponse<EventListResponse> searchEvents(
        @ModelAttribute EventSearchRequest request,
        @AuthenticationPrincipal AuthenticatedMember member
    ) {
        Long memberId = member == null ? null : member.getMemberId();
        return ApiResponse.success(eventService.searchEvents(request, memberId));
    }

    @GetMapping("/{eventId}")
    @ApiOperation("탐색 Event 상세 조회")
    public ApiResponse<EventDetailResponse> getEventDetail(
        @PathVariable Long eventId,
        // TODO(국제화 후속 이슈): 크롤링 원본 국제화 전까지 en을 기본 언어로 사용한다.
        @RequestParam(name = "language", defaultValue = "en") String language,
        // 상세 화면을 연 요청만 조회수를 올린다. 기본값을 거짓으로 둬서 이 API로 값만
        // 읽어 가는 호출부가 모르고 조회수를 부풀리지 않게 한다.
        @RequestParam(name = "countView", defaultValue = "false") boolean countView,
        @AuthenticationPrincipal AuthenticatedMember member
    ) {
        Long memberId = member == null ? null : member.getMemberId();
        EventDetailResponse event = eventService.getEventDetail(eventId, language, memberId);
        // 상세를 다 읽은 뒤에 센다. 읽기 트랜잭션 안에서 집계하면 상세 요청 하나가
        // 커넥션을 두 개 잡는다(EventService#recordEventView).
        if (countView) {
            eventService.recordEventView(eventId);
        }
        return ApiResponse.success(event);
    }
}
