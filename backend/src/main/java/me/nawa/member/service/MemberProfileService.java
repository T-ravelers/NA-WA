package me.nawa.member.service;

import me.nawa.member.dto.MemberProfileResponse;
import me.nawa.member.dto.MemberAppointmentProfileResponse;
import me.nawa.member.dto.OnboardingProfileRequest;
import me.nawa.member.dto.UpdateMemberProfileRequest;

public interface MemberProfileService {
    MemberProfileResponse getProfile(long memberId);

    MemberProfileResponse updateProfile(long memberId, UpdateMemberProfileRequest request);

    MemberProfileResponse completeOnboarding(long memberId, OnboardingProfileRequest request);

    MemberAppointmentProfileResponse getAppointmentProfile(long memberId);
}
