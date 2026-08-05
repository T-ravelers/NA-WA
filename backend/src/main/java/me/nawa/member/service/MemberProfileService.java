package me.nawa.member.service;

import me.nawa.member.dto.MemberProfileResponse;

public interface MemberProfileService {
    MemberProfileResponse getProfile(long memberId);
}
