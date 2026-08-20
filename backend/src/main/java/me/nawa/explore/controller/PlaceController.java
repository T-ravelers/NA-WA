package me.nawa.explore.controller;

import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import me.nawa.auth.security.AuthenticatedMember;
import me.nawa.common.response.ApiResponse;
import me.nawa.explore.dto.request.PlaceSearchRequest;
import me.nawa.explore.dto.response.PlaceDetailResponse;
import me.nawa.explore.dto.response.PlaceListResponse;
import me.nawa.explore.service.PlaceService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/explore/places")
@RequiredArgsConstructor
public class PlaceController {

    private final PlaceService placeService;

    @GetMapping
    @ApiOperation("탐색 Place 목록 조회")
    public ApiResponse<PlaceListResponse> searchPlaces(
        @ModelAttribute PlaceSearchRequest request,
        @AuthenticationPrincipal AuthenticatedMember member
    ) {
        Long memberId = member == null ? null : member.getMemberId();
        return ApiResponse.success(placeService.searchPlaces(request, memberId));
    }

    @GetMapping("/{placeId}")
    @ApiOperation("탐색 Place 상세 조회")
    public ApiResponse<PlaceDetailResponse> getPlaceDetail(
        @PathVariable Long placeId,
        @RequestParam(name = "language", defaultValue = "en") String language,
        @AuthenticationPrincipal AuthenticatedMember member
    ) {
        Long memberId = member == null ? null : member.getMemberId();
        return ApiResponse.success(
            placeService.getPlaceDetail(placeId, language, memberId)
        );
    }
}
