package me.nawa.member.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import me.nawa.auth.security.AuthenticatedMember;
import me.nawa.common.response.ApiResponse;
import me.nawa.member.dto.MemberProfileResponse;
import me.nawa.member.dto.MemberAppointmentProfileResponse;
import me.nawa.member.dto.UpdateMemberProfileRequest;
import me.nawa.member.service.MemberProfileService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/members")
@RequiredArgsConstructor
@Log4j2
public class MemberController {

    private final MemberProfileService memberProfileService;

    @GetMapping("/me")
    public ApiResponse<MemberProfileResponse> getMe(
            @AuthenticationPrincipal AuthenticatedMember member) {
        return ApiResponse.success(
                memberProfileService.getProfile(member.getMemberId())
        );
    }

    @GetMapping("/{memberId}/appointment-profile")
    public ApiResponse<MemberAppointmentProfileResponse> getAppointmentProfile(
            @PathVariable Long memberId) {
        return ApiResponse.success(memberProfileService.getAppointmentProfile(memberId));
    }

    @PatchMapping("/me")
    public ApiResponse<MemberProfileResponse> patchMe(
            @AuthenticationPrincipal AuthenticatedMember member,
            @RequestBody UpdateMemberProfileRequest request) {
        return ApiResponse.success(
                memberProfileService.updateProfile(member.getMemberId(), request)
        );
    }
}
