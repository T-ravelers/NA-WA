package me.nawa.explore.controller;

import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import me.nawa.auth.security.AuthenticatedMember;
import me.nawa.common.response.ApiResponse;
import me.nawa.explore.dto.response.ExploreItemLikeResponse;
import me.nawa.explore.service.ExploreItemLikeService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/explore/items/{itemId}/like")
@RequiredArgsConstructor
public class ExploreItemLikeController {

    private final ExploreItemLikeService likeService;

    @PostMapping
    @ApiOperation("탐색 항목 찜 등록")
    public ApiResponse<ExploreItemLikeResponse> like(
        @PathVariable Long itemId,
        @AuthenticationPrincipal AuthenticatedMember member
    ) {
        return ApiResponse.success(likeService.like(member.getMemberId(), itemId));
    }

    @DeleteMapping
    @ApiOperation("탐색 항목 찜 취소")
    public ApiResponse<ExploreItemLikeResponse> unlike(
        @PathVariable Long itemId,
        @AuthenticationPrincipal AuthenticatedMember member
    ) {
        return ApiResponse.success(likeService.unlike(member.getMemberId(), itemId));
    }
}
