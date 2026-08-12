package me.nawa.review.controller;

import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import me.nawa.auth.security.AuthenticatedMember;
import me.nawa.common.response.ApiResponse;
import me.nawa.review.dto.request.MemberReviewCreateRequest;
import me.nawa.review.service.ReviewService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/appointments/{appointmentId}/reviews")
@RequiredArgsConstructor
public class ReviewController {
    private final ReviewService reviewService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @ApiOperation("약속 회원 후기 등록")
    public ApiResponse<Void> createReview(
            @AuthenticationPrincipal AuthenticatedMember member,
            @PathVariable Long appointmentId,
            @RequestBody MemberReviewCreateRequest request) {
        reviewService.createReview(
                member.getMemberId(),
                appointmentId,
                request
        );
        return ApiResponse.success();
    }
}
