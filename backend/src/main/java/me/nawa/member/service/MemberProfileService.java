package me.nawa.member.service;

import me.nawa.member.dto.MemberProfileResponse;
import me.nawa.member.dto.MemberAppointmentProfileResponse;
import me.nawa.member.dto.MerchantRegisterRequest;
import me.nawa.member.dto.OnboardingProfileRequest;
import me.nawa.member.dto.UpdateMemberProfileRequest;

public interface MemberProfileService {
    MemberProfileResponse getProfile(long memberId);

    MemberProfileResponse updateProfile(long memberId, UpdateMemberProfileRequest request);

    MemberProfileResponse completeOnboarding(long memberId, OnboardingProfileRequest request);

    MemberProfileResponse registerAsMerchant(long memberId, MerchantRegisterRequest request);

    /** 본인은 항상, 타인은 같은 약속의 ACTIVE 참여자일 때만 조회한다(#173). */
    MemberAppointmentProfileResponse getAppointmentProfile(long viewerId, long memberId);
}
