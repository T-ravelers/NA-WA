package me.nawa.explore.controller;

import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import me.nawa.common.response.ApiResponse;
import me.nawa.explore.dto.request.EventSearchRequest;
import me.nawa.explore.dto.response.EventDetailResponse;
import me.nawa.explore.dto.response.EventListResponse;
import me.nawa.explore.service.EventService;
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
        @ModelAttribute EventSearchRequest request
    ) {
        return ApiResponse.success(eventService.searchEvents(request));
    }

    @GetMapping("/{eventId}")
    @ApiOperation("탐색 Event 상세 조회")
    public ApiResponse<EventDetailResponse> getEventDetail(
        @PathVariable Long eventId,
        // TODO(국제화 후속 이슈): 크롤링 원본 국제화 전까지 ko를 임시 기본 언어로 사용한다.
        @RequestParam(name = "language", defaultValue = "ko") String language
    ) {
        return ApiResponse.success(
            eventService.getEventDetail(eventId, language)
        );
    }
}
