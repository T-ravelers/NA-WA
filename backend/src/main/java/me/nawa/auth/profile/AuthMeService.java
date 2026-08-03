package me.nawa.auth.profile;

public interface AuthMeService {
    AuthMeResponse getCurrentMember(long memberId);
}
