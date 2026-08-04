package me.nawa.explore.controller;

import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import me.nawa.common.exception.BusinessException;
import me.nawa.common.exception.CommonErrorCode;
import me.nawa.common.response.ApiResponse;
import me.nawa.explore.dto.EventListResponse;
import me.nawa.explore.dto.EventSearchRequest;
import me.nawa.explore.service.EventService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/explore")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    @GetMapping
    @ApiOperation("탐색 Event 목록 조회")
    public ApiResponse<EventListResponse> searchEvents(
        @RequestParam(name = "itemType", defaultValue = "EVENT") String itemType,
        @ModelAttribute EventSearchRequest request
    ) {
        if (!"EVENT".equalsIgnoreCase(itemType)) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }

        return ApiResponse.success(eventService.searchEvents(request));
    }
}
