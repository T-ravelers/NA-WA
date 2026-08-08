package me.nawa.journey.controller;

import io.swagger.annotations.ApiOperation;
import java.util.List;
import lombok.RequiredArgsConstructor;
import me.nawa.auth.security.AuthenticatedMember;
import me.nawa.common.response.ApiResponse;
import me.nawa.journey.dto.request.JourneyCreateRequest;
import me.nawa.journey.dto.response.JourneyResponse;
import me.nawa.journey.dto.response.JourneyTimelineResponse;
import me.nawa.journey.dto.response.JourneySummaryResponse;
import me.nawa.journey.service.JourneyService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/journeys")
@RequiredArgsConstructor
public class JourneyController {

    private final JourneyService journeyService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @ApiOperation("Journey 생성")
    public ApiResponse<JourneyResponse> createJourney(
        @AuthenticationPrincipal AuthenticatedMember member,
        @RequestBody JourneyCreateRequest request
    ) {
        return ApiResponse.success(
            journeyService.createJourney(member.getMemberId(), request)
        );
    }

    @GetMapping
    @ApiOperation("내 Journey 목록 조회")
    public ApiResponse<List<JourneySummaryResponse>> getJourneys(
        @AuthenticationPrincipal AuthenticatedMember member
    ) {
        return ApiResponse.success(
            journeyService.getJourneys(member.getMemberId())
        );
    }

    @GetMapping("/{tripId}")
    @ApiOperation("Journey 상세 조회")
    public ApiResponse<JourneyResponse> getJourney(
        @AuthenticationPrincipal AuthenticatedMember member,
        @PathVariable Long tripId
    ) {
        return ApiResponse.success(
            journeyService.getJourney(member.getMemberId(), tripId)
        );
    }

    @GetMapping("/{tripId}/timeline")
    @ApiOperation("Journey 타임라인 조회")
    public ApiResponse<JourneyTimelineResponse> getTimeline(
        @AuthenticationPrincipal AuthenticatedMember member,
        @PathVariable Long tripId
    ) {
        return ApiResponse.success(
            journeyService.getTimeline(member.getMemberId(), tripId)
        );
    }
}
