package me.nawa.auth.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthenticatedMemberTest {
    @Test
    void constructor_validMemberId_exposesPrincipalName() {
        AuthenticatedMember member = new AuthenticatedMember(42L);

        assertEquals(42L, member.getMemberId());
        assertEquals("42", member.getName());
    }

    @Test
    void constructor_nonPositiveMemberId_throwsException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new AuthenticatedMember(0L)
        );
    }
}
