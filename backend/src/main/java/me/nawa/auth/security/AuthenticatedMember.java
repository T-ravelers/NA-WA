package me.nawa.auth.security;

import lombok.Getter;

import java.security.Principal;

@Getter
public final class AuthenticatedMember implements Principal {
    private final long memberId;

    public AuthenticatedMember(long memberId) {
        if (memberId <= 0) {
            throw new IllegalArgumentException("Member ID must be positive");
        }
        this.memberId = memberId;
    }

    @Override
    public String getName() {
        return Long.toString(memberId);
    }
}
