package me.nawa.member.service;

import me.nawa.member.dto.MemberProfileResponse;
import me.nawa.member.dto.UpdateMemberProfileRequest;

public interface MemberProfileService {
    MemberProfileResponse getProfile(long memberId);

    MemberProfileResponse updateProfile(long memberId, UpdateMemberProfileRequest request);
}
